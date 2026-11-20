// Port of domain/TypewriterEngine.kt — the pure typewriter model shared by the whole app.
import * as Graphemes from './graphemes.js';
import { CorrectionMode, TypeEvent, glyph, isNewline, isSpace } from './glyph.js';
import { sheetLayout } from './layout.js';

export const MAX_ACTIVE = 3000;
export const OVERSTRIKE = '\u25A0';
export const MARGIN_SHARE = 0.85;
export const bellFor = (columns) => (columns >= 12 ? Math.ceil(columns * MARGIN_SHARE) : 0);

export class TypewriterEngine {
  constructor(initial = [], limit = MAX_ACTIVE) {
    this.limit = limit;
    this.list = [];
    this.mode = CorrectionMode.STRIKE;
    this._columns = 40;
    this.bellColumn = 0;
    this.cursor = 0;
    this.reset(initial);
  }
  get columns() { return this._columns; }
  set columns(v) { this._columns = Math.max(1, v); }
  get glyphs() { return this.list.map((g) => ({ ...g })); }
  get size() { return this.list.length; }
  get activeCount() { return this.list.length; }
  get isFull() { return this.list.length >= this.limit; }

  reset(glyphs) {
    this.list = [];
    for (const g of glyphs) if (g.c && g.c.length > 0) this.list.push({ ...g });
    this.cursor = this.list.length;
  }

  lineStart() {
    for (let i = this.list.length - 1; i >= 0; i--) if (isNewline(this.list[i])) return i + 1;
    return 0;
  }
  column() { return this.cursor - this.lineStart(); }

  /** Mirror for the hidden input field: what sits left of the carriage (overstrikes as typed). */
  fieldText() {
    let s = '';
    for (let i = 0; i < this.cursor; i++) { const g = this.list[i]; s += g.s && g.o != null ? g.o : g.c; }
    return s;
  }
  activeText() { return this.fieldText(); }
  layout() { return sheetLayout(this.list, this.columns, this.cursor); }
  lineCursorCol() {
    const ls = this.lineStart();
    return sheetLayout(this.list.slice(ls), this.columns, this.cursor - ls).cursorCol;
  }

  type(input) {
    if (!input) return [];
    const normalized = input.replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\t/g, ' ');
    const events = [];
    for (const cluster of Graphemes.split(normalized)) {
      const clean = sanitize(cluster);
      if (clean == null) continue;
      if (clean === '\n') {
        if (this.list.length >= this.limit) { events.push(TypeEvent.LIMIT); break; }
        this.list.push(glyph('\n')); this.cursor = this.list.length;
        events.push(TypeEvent.RETURN);
        continue;
      }
      const bell = this.bellColumn > 0 ? this.bellColumn : 0;
      const before = bell > 0 ? this.lineCursorCol() : 0;
      if (this.cursor < this.list.length) {
        const g = this.list[this.cursor];
        if (clean === ' ') events.push(TypeEvent.SPACE);
        else if (!g.s && g.c === clean) events.push(TypeEvent.KEY);
        else if (isSpace(g) && !g.s) { this.list[this.cursor] = glyph(clean); events.push(TypeEvent.KEY); }
        else { this.list[this.cursor] = glyph(g.c, true, clean); events.push(TypeEvent.STRIKE); }
        this.cursor++;
      } else {
        const last = this.list[this.list.length - 1];
        if (last && !last.s && !isNewline(last) && Graphemes.split(last.c + clean).length === 1) {
          this.list[this.list.length - 1] = { ...last, c: last.c + clean };
          continue;
        }
        if (this.list.length >= this.limit) { events.push(TypeEvent.LIMIT); break; }
        this.list.push(glyph(clean)); this.cursor = this.list.length;
        events.push(clean === ' ' ? TypeEvent.SPACE : TypeEvent.KEY);
      }
      if (bell > 0) {
        const after = this.lineCursorCol();
        if (before < bell && after >= bell) events.push(TypeEvent.BELL);
      }
    }
    return events;
  }

  /** STRIKE mode: step back one cell (or null at the margin). DELETE mode: erase, newlines too. */
  backspace() {
    if (this.mode === CorrectionMode.DELETE) {
      if (this.cursor === 0) return null;
      this.list.splice(this.cursor - 1, 1);
      this.cursor--;
      return TypeEvent.ERASE;
    }
    if (this.cursor <= this.lineStart()) return null;
    this.cursor--;
    return TypeEvent.BACKSPACE;
  }

  lastFinishedLine() {
    const l = this.list;
    if (l.length === 0 || !isNewline(l[l.length - 1])) return '';
    let start = l.length - 1;
    while (start > 0 && !isNewline(l[start - 1])) start--;
    let s = '';
    for (let i = start; i < l.length - 1; i++) s += visible(l[i]);
    return s;
  }
  currentLine() {
    let s = '';
    for (let i = this.lineStart(); i < this.list.length; i++) s += visible(this.list[i]);
    return s;
  }
  toLineEnd() { this.cursor = this.list.length; }
}

const visible = (g) => (g.s ? OVERSTRIKE : g.c);

function sanitize(cluster) {
  if (cluster === '\n') return cluster;
  let sb = '';
  for (let i = 0; i < cluster.length;) {
    const cp = cluster.codePointAt(i);
    const len = cp > 0xffff ? 2 : 1;
    const control = cp < 0x20 || (cp >= 0x7f && cp <= 0x9f);
    if (!Graphemes.isLoneSurrogate(cp) && !control && cp !== 0xffff) sb += cluster.slice(i, i + len);
    i += len;
  }
  return sb.length ? sb : null;
}

/** Port of InputDiff.kt: what the IME did to the hidden field, as typewriter operations. */
export function inputDiff(old, now) {
  if (old === now) return { deleteGlyphs: 0, insert: '' };
  let p = 0;
  const max = Math.min(old.length, now.length);
  while (p < max && old[p] === now[p]) p++;
  const bounds = Graphemes.boundaries(old);
  let aligned = 0;
  for (const b of bounds) { if (b <= p) aligned = b; else break; }
  const low = (c) => c >= 0xdc00 && c <= 0xdfff;
  const high = (c) => c >= 0xd800 && c <= 0xdbff;
  if (aligned > 0 && aligned < now.length && low(now.charCodeAt(aligned))) {
    const prev = bounds.filter((b) => b < aligned);
    aligned = prev.length ? prev[prev.length - 1] : 0;
  }
  const deleted = Graphemes.count(old.slice(aligned));
  const tail = now.slice(aligned);
  let insert = '';
  for (let i = 0; i < tail.length; i++) {
    const c = tail.charCodeAt(i);
    if (high(c)) { if (i + 1 < tail.length && low(tail.charCodeAt(i + 1))) insert += tail[i]; }
    else if (low(c)) { if (i > 0 && high(tail.charCodeAt(i - 1))) insert += tail[i]; }
    else insert += tail[i];
  }
  return { deleteGlyphs: deleted, insert };
}

/** Carriage.nearMargin */
export const nearMargin = (cursorCol, columns) => columns >= 12 && cursorCol >= Math.ceil(columns * MARGIN_SHARE);
