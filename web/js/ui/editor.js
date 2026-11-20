// The editor: port of ui/editor/EditorViewModel.kt (typing, Morse hooks, auto-typing, autosave) and
// ui/editor/EditorScreen.kt (stage, top chrome, toolbar, panels, Morse chart, hidden input field).
import { TypewriterEngine, inputDiff, bellFor } from '../engine/engine.js';
import { TypeEvent as E, CorrectionMode } from '../engine/glyph.js';
import { MorseController, LETTERS, MorseDir } from '../engine/morse.js';
import * as Title from '../engine/title.js';
import { randomPrompt } from '../engine/prompts.js';
import { ImageFormat, PaperSize, FREE_ACTIVE_NOTES } from '../engine/limits.js';
import { TypewriterStage } from '../stage/stage.js';
import { paletteFor, rgba, lerpColor, luminance, PORTRAIT_VOID, THEMES, THEME_LABEL } from '../palette.js';
import { renderImage, renderPdf, deliver } from '../export.js';
import { h, icon, switchEl, slider, dialog, grouped } from './dom.js';

const PRINT_LEAD_IN_MS = 220, PRINT_DELAY_MS = 35, AUTOSAVE_MS = 500;
const PANEL_W = 248, PANEL_INK = '#F1E9DC', ACCENT = '#FFC27A';
const MORSE_COLUMNS = 4, MORSE_ROWS = Math.ceil(26 / MORSE_COLUMNS);
const morseGridHeight = (tile) => 44 + tile * MORSE_ROWS + 8 * (MORSE_ROWS - 1) + 76 + 24;
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

export class Editor {
  constructor(app, noteId) {
    this.app = app;
    this.noteId = noteId;
    this.engine = new TypewriterEngine();
    this.morse = new MorseController();
    this.strokes = 0;
    this.mut = 0;
    this.printToken = 0;
    this.printing = false;
    this.dirty = false;
    this.panel = null;
    this.collapsed = false;
    this.morseCollapsed = false;
    this.typing = false;
    this.missing = false;
    this.disposers = [];
  }

  // ======================================================================= view model
  load() {
    const note = this.app.store.get(this.noteId);
    if (!note) { this.missing = true; return false; }
    this.engine.reset(note.glyphs);
    this.engine.mode = this.settings.correction === 'STRIKE' ? CorrectionMode.STRIKE : CorrectionMode.DELETE;
    return true;
  }
  get settings() { return this.app.store.settings; }

  /** Every IME / keyboard change of the hidden field. */
  onFieldChange(value) {
    if (this.printing) { this.publish(); return; }
    const old = this.engine.fieldText();
    if (value === old) return;
    const diff = inputDiff(old, value);
    const events = [];
    for (let i = 0; i < diff.deleteGlyphs; i++) { const e = this.engine.backspace(); if (e) events.push(e); }
    const print = this.typeWithHooks(diff.insert, events);
    if (events.length || diff.deleteGlyphs || diff.insert) this.mut++;
    this.publish({ stroke: events.length > 0 });
    // Holding backspace (or a word-delete) can erase many cells in one edit: one sound.
    let erased = false;
    this.emit(events.filter((e) => { if (e !== E.ERASE) return true; if (erased) return false; erased = true; return true; }));
    if (events.some((e) => e !== E.BACKSPACE)) this.changed();
    if (print != null) this.autoType(print);
  }

  typeWithHooks(text, events) {
    let i = 0;
    while (i < text.length) {
      const nl = text.indexOf('\n', i);
      if (nl < 0) { events.push(...this.engine.type(text.slice(i))); break; }
      if (nl > i) events.push(...this.engine.type(text.slice(i, nl)));
      const ev = this.engine.type('\n');
      events.push(...ev);
      i = nl + 1;
      if (ev.includes(E.RETURN)) {
        const print = this.morse.onEnter(this.engine.lastFinishedLine(), this.engine.limit - this.engine.size);
        if (print != null) return print;
      }
    }
    return null;
  }

  /** The machine types [text] by itself, a key at a time, with sound. */
  autoType(text) {
    const token = ++this.printToken;
    this.printing = true;
    this.publish();
    (async () => {
      await sleep(PRINT_LEAD_IN_MS);
      const chars = [...text];
      for (let i = 0; i < chars.length; i++) {
        if (token !== this.printToken) return;
        if (i > 0) await sleep(PRINT_DELAY_MS);
        if (token !== this.printToken) return;
        const ev = this.engine.type(chars[i]);
        this.mut++;
        this.emit(ev);
        if (ev.includes(E.LIMIT)) break;
        this.publish({ stroke: true });
      }
      this.changed();
      if (token === this.printToken) { this.printing = false; this.publish(); }
    })();
  }

  /** Text from the Morse reference panel: typed like keys, never treated as a command. */
  typeKeys(text) {
    if (this.missing || this.printing) return;
    const events = this.engine.type(text);
    this.mut++;
    this.publish({ stroke: true });
    this.emit(events);
    if (events.some((e) => e !== E.LIMIT)) this.changed();
  }
  exitMorse() { this.morse.reset(); this.publish(); }
  insertText(text) {
    if (this.missing || this.printing) return;
    const ev = this.engine.type(text);
    this.mut++;
    this.publish({ stroke: true });
    this.emit([ev.includes(E.LIMIT) ? E.LIMIT : E.RETURN]);
    this.changed();
  }
  setCharsPerLine(chars) { this.engine.columns = chars; this.engine.bellColumn = bellFor(chars); }

  emit(events) {
    const s = this.settings;
    for (const e of events) {
      this.app.sound.play(e);
      if (s.haptics && e !== E.BELL && navigator.vibrate) { try { navigator.vibrate(e === E.LIMIT || e === E.RETURN ? 24 : 6); } catch { /* ignore */ } }
    }
  }
  changed() {
    this.dirty = true;
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.saveNow(), AUTOSAVE_MS);
  }
  saveNow() {
    clearTimeout(this.saveTimer);
    if (!this.dirty || this.missing) return;
    this.app.store.save(this.noteId, this.engine.glyphs);
    this.dirty = false;
  }

  publish({ stroke = false } = {}) {
    if (stroke) this.strokes++;
    const e = this.engine;
    if (this.stage) {
      if (this._lastMut !== this.mut || stroke) { this._glyphs = e.glyphs; this._lastMut = this.mut; }
      this.stage.setState(this._glyphs, e.cursor, this.strokes, this.noteId);
    }
    this.syncField();
    if (this.counterEls) for (const c of this.counterEls) this.paintCounter(c);
    if (stroke) this.onStroke();
    const morseKey = `${this.morse.state.kind}|${this.morse.state.dir}|${this.printing}|${e.size === 0}`;
    if (morseKey !== this._morseKey) { this._morseKey = morseKey; this.renderOverlays(); }
  }

  // ========================================================================= view
  mount(root) {
    this.root = root;
    const s = this.settings;
    this.palette = paletteFor(s.theme);
    this.el = h('div', { class: 'editor', 'data-tag': 'editor' });
    this.canvas = h('canvas', { class: 'stage', 'data-tag': 'paper' });
    this.chrome = h('div', { class: 'chrome' });
    this.overlays = h('div', { class: 'overlays' });
    this.field = h('textarea', {
      class: 'type-input', 'data-tag': 'typeInput', 'aria-label': 'Typewriter keys',
      autocomplete: 'off', autocorrect: s.autocorrect ? 'on' : 'off', autocapitalize: s.autocorrect ? 'sentences' : 'off',
      spellcheck: s.autocorrect ? 'true' : 'false', enterkeyhint: 'enter', rows: 1,
    });
    this.el.append(this.canvas, this.chrome, this.overlays, this.field);
    root.append(this.el);
    this.stage = new TypewriterStage(this.canvas, this.app.images, {
      onColumns: (c) => this.setCharsPerLine(c),
      onGeometry: (g) => { this.geo = g; },
    });
    this.applySettings(true);
    this.bindInput();
    this.layout();
    this.stage.setState(this.engine.glyphs, this.engine.cursor, this.strokes, this.noteId, { initial: true });
    this._glyphs = this.stage.shown.glyphs; this._lastMut = this.mut;
    this.publish();
    this.renderChrome();
    const onResize = () => this.layout();
    window.addEventListener('resize', onResize);
    window.visualViewport?.addEventListener('resize', onResize);
    window.visualViewport?.addEventListener('scroll', onResize);
    this.disposers.push(() => { window.removeEventListener('resize', onResize); window.visualViewport?.removeEventListener('resize', onResize); window.visualViewport?.removeEventListener('scroll', onResize); });
    this.disposers.push(this.app.store.subscribe(() => this.applySettings(false)));
    // auto focus (desktop keyboards; on phones the keyboard opens on the first tap on the paper)
    setTimeout(() => this.summonKeyboard(), 50);
  }

  unmount() {
    this.printToken++;
    for (const d of this.disposers) d();
    clearTimeout(this.typingTimer);
    this.saveNow();
    // An untouched blank sheet doesn't stay on the desk (EditorViewModel.onCleared).
    if (!this.missing && this.engine.size === 0) this.app.store.delete(this.noteId);
    this.closeDialog();
    this.el?.remove();
  }

  applySettings(first) {
    const s = this.settings;
    const p = paletteFor(s.theme);
    const themeChanged = this.palette?.theme !== p.theme;
    this.palette = p;
    this.engine.mode = s.correction === 'STRIKE' ? CorrectionMode.STRIKE : CorrectionMode.DELETE;
    this.app.sound.configure(s.sound);
    this.stage.setPalette(p);
    const st = document.body.style;
    st.setProperty('--dialog-bg', p.card); st.setProperty('--dialog-ink', p.onBackground); st.setProperty('--dialog-muted', p.muted);
    this.stage.setCharms(s.charms);
    this.stage.setLamp({ on: s.lampOn, warmth: s.lampWarmth, intensity: s.lampIntensity, beam: s.lampBeam });
    this.field.setAttribute('autocorrect', s.autocorrect ? 'on' : 'off');
    this.field.setAttribute('autocapitalize', s.autocorrect ? 'sentences' : 'off');
    this.field.setAttribute('spellcheck', s.autocorrect ? 'true' : 'false');
    if (!first) {
      if (themeChanged) this.layout();
      this.renderChrome();
    }
  }

  get portrait() { return window.innerHeight > window.innerWidth; }

  layout() {
    const vv = window.visualViewport;
    const w = Math.round(vv ? vv.width : window.innerWidth);
    const hgt = Math.round(vv ? vv.height : window.innerHeight);
    const top = vv ? vv.offsetTop : 0;
    const left = vv ? vv.offsetLeft : 0;
    Object.assign(this.el.style, { width: w + 'px', height: hgt + 'px', top: top + 'px', left: left + 'px' });
    const portrait = this.portrait;
    this.el.style.background = portrait ? lerpColor(this.palette.stageSide, PORTRAIT_VOID, 0.8) : this.palette.stageSide;
    const sizeKey = `${w}x${hgt}|${portrait}|${window.devicePixelRatio}`;
    if (sizeKey !== this._sizeKey) {
      this._sizeKey = sizeKey;
      this.w = w; this.h = hgt;
      this.stage.resize(w, hgt, portrait);
      this.renderChrome();
    }
  }

  // ---------------------------------------------------------------------------- input
  bindInput() {
    const f = this.field;
    f.addEventListener('input', () => this.onFieldChange(f.value));
    f.addEventListener('keydown', (e) => {
      if (e.key === 'Tab') { e.preventDefault(); this.onFieldChange(this.engine.fieldText() + ' '); return; }
      if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown'].includes(e.key)) { e.preventDefault(); return; }
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.shiftKey && !e.altKey) {
        const k = e.key.toLowerCase();
        if (k === 'l') { e.preventDefault(); this.app.store.updateSettings((s) => ({ lampOn: !s.lampOn })); return; }
        if (k === 'e') { e.preventDefault(); this.openExport(); return; }
        if (k === 'o') { e.preventDefault(); this.app.back(); return; }
      }
      if (mod && ['z', 'y', 'a', 'x'].includes(e.key.toLowerCase())) { e.preventDefault(); } // no undo / select-all on a typewriter
    });
    const pin = () => { const n = f.value.length; if (f.selectionStart !== n || f.selectionEnd !== n) f.setSelectionRange(n, n); };
    f.addEventListener('select', pin); f.addEventListener('click', pin); f.addEventListener('keyup', pin); f.addEventListener('focus', pin);
    document.addEventListener('selectionchange', this._sel = () => { if (document.activeElement === f) pin(); });
    this.disposers.push(() => document.removeEventListener('selectionchange', this._sel));
    f.addEventListener('paste', () => { /* handled by the input event like any IME commit */ });
    // Chrome buttons must not steal focus from the typing field (on Android the IME stays up
    // while you tap the toolbar or the Morse chart).
    this.root.addEventListener('mousedown', (e) => {
      if (e.target.closest('button') && !e.target.closest('.dialog')) e.preventDefault();
    });

    // Pointer on the stage: tap paper = keyboard (and close panels); tap carriage = keyboard,
    // long-press carriage = lamp; vertical drag = reread earlier lines.
    let down = null;
    this.canvas.addEventListener('pointerdown', (e) => {
      const g = this.geo; if (!g) return;
      const cx = this.stage.carX.value;
      const onCarriage = e.offsetX >= cx - g.carriageWidth / 2 && e.offsetX <= cx + g.carriageWidth / 2 && e.offsetY >= g.carriageTop && e.offsetY <= g.carriageBottom;
      down = { x: e.clientY, y: e.clientY, last: e.clientY, moved: false, onCarriage, long: false };
      if (onCarriage) down.timer = setTimeout(() => { down.long = true; this.app.store.updateSettings((s) => ({ lampOn: !s.lampOn })); }, 500);
      this.canvas.setPointerCapture?.(e.pointerId);
    });
    this.canvas.addEventListener('pointermove', (e) => {
      if (!down) return;
      const dy = e.clientY - down.last;
      if (Math.abs(e.clientY - down.y) > 6) { down.moved = true; clearTimeout(down.timer); }
      if (down.moved) { this.stage.dragBy(dy); down.last = e.clientY; }
    });
    const up = () => {
      if (!down) return;
      clearTimeout(down.timer);
      if (!down.moved && !down.long) {
        if (!down.onCarriage && this.panel) { this.panel = null; this.renderChrome(); }
        this.summonKeyboard();
      }
      down = null;
    };
    this.canvas.addEventListener('pointerup', up);
    this.canvas.addEventListener('pointercancel', () => { if (down) clearTimeout(down.timer); down = null; });
  }

  summonKeyboard() { try { this.field.focus({ preventScroll: true }); } catch { this.field.focus(); } this.syncField(); }

  syncField() {
    const f = this.field; if (!f) return;
    const text = this.engine.fieldText();
    if (f.value !== text) f.value = text;
    const n = text.length;
    if (document.activeElement === f && (f.selectionStart !== n || f.selectionEnd !== n)) f.setSelectionRange(n, n);
  }

  onStroke() {
    // The toolbar steps back while you type and returns when you pause.
    this.typing = true;
    if (this.panel) { this.panel = null; this.renderChrome(); }
    this.setToolbarFade();
    clearTimeout(this.typingTimer);
    this.typingTimer = setTimeout(() => { this.typing = false; this.setToolbarFade(); }, 1800);
  }
  setToolbarFade() {
    if (!this.toolbarEl) return;
    const fade = this.typing ? 0.32 : 1;
    this.toolbarEl.style.opacity = String(this.portrait ? 0.6 + 0.4 * fade : fade);
  }

  // ---------------------------------------------------------------------------- chrome
  paintCounter(el) {
    const n = this.engine.activeCount, lim = this.engine.limit;
    el.textContent = `${grouped(n)} / ${grouped(lim)}`;
    el.style.color = n >= lim - 100 ? '#E5654D' : rgba(this.palette.stageInk, 0.75);
  }

  stagePill(label, iconName, tag, wide, onClick) {
    const p = this.palette;
    return h('button', {
      class: 'stage-pill' + (wide ? ' wide' : ''), 'data-tag': tag, onClick,
      style: { color: p.stageAccent, borderColor: rgba(p.stageInk, 0.16) },
    }, icon(iconName, wide ? 19 : 16), h('span', { text: label }));
  }

  toolButton(iconName, label, tag, { width = 44, height = 44, active = false, activeTint = PANEL_INK, tint = null, onLong = null, onClick }) {
    const color = active ? activeTint : tint || rgba(this.palette.stageInk, 0.5);
    const b = h('button', { class: 'tool-button', 'data-tag': tag, 'aria-label': label, title: label, style: { width: width + 'px', height: height + 'px', color } }, icon(iconName, 21));
    let timer = 0, long = false;
    b.addEventListener('pointerdown', () => { long = false; if (onLong) timer = setTimeout(() => { long = true; onLong(); }, 500); });
    const cancel = () => clearTimeout(timer);
    b.addEventListener('pointerup', cancel); b.addEventListener('pointerleave', cancel);
    b.addEventListener('click', (e) => { e.preventDefault(); if (long) { long = false; return; } onClick(); });
    b.addEventListener('contextmenu', (e) => { if (onLong) { e.preventDefault(); } });
    return b;
  }

  togglePanel(p) { this.panel = this.panel === p ? null : p; this.renderChrome(); }

  renderChrome() {
    if (!this.chrome || !this.geo) return;
    const p = this.palette, s = this.settings, w = this.w, hh = this.h, geo = this.geo;
    const portrait = this.portrait;
    const wide = w > hh && w >= 600;
    this.chrome.replaceChildren();
    this.counterEls = [];
    const counter = (size) => { const c = h('div', { class: 'counter', 'data-tag': 'counter', style: { fontSize: size + 'px' } }); this.counterEls.push(c); this.paintCounter(c); return c; };
    const share = (size) => h('button', { class: 'share-button', 'data-tag': 'export', 'aria-label': 'Share or export', title: 'Share or export', onClick: () => this.openExport(), style: { color: rgba(p.stageInk, 0.9) } }, icon('ios_share', size));
    // ---- top chrome (never dimmed by the lamp)
    if (portrait) {
      this.chrome.append(h('div', { class: 'top-row', style: { padding: '10px 12px' } },
        this.stagePill('Box', 'inventory_2', 'back', false, () => this.app.back()),
        h('div', { class: 'spacer' }),
        h('div', { class: 'top-col' },
          h('div', { class: 'row-center' }, share(21), h('div', { style: { width: '6px' } }), this.stagePill('Studio', 'workspace_premium', 'studio', false, () => this.app.go('studio'))),
          h('div', { style: { height: '8px' } }), counter(11))));
    } else {
      this.chrome.append(h('div', { class: 'top-row', style: { padding: wide ? '26px 18px' : '10px 12px' } },
        this.stagePill('Box', 'inventory_2', 'back', wide, () => this.app.back()),
        h('div', { class: 'spacer' }),
        h('div', { class: 'top-col' },
          this.stagePill('Studio', 'workspace_premium', 'studio', wide, () => this.app.go('studio')),
          h('div', { style: { height: (wide ? 16 : 10) + 'px' } }), counter(wide ? 13 : 11),
          h('div', { style: { height: hh * 0.05 + 'px' } }), share(wide ? 25 : 22))));
    }
    const lampToggle = () => this.app.store.updateSettings((x) => ({ lampOn: !x.lampOn }));
    let panelPos;
    if (portrait) {
      // ---- portrait toolbar: a cream pill right under the rod
      const pillTop = geo.toolbarTop, pillH = geo.toolbarHeight;
      const pillW = Math.min(clamp(w * 0.78, 300, 620), w - 16);
      const pillInk = luminance(p.capsule) > 0.45 ? '#4A4541' : '#EDE6DA';
      const itemW = (pillW - 16 - 13) / 9;
      const item = (ic, label, tag, opts) => this.toolButton(ic, label, tag, { width: itemW, height: pillH, activeTint: '#C9731F', tint: pillInk, ...opts });
      const tb = h('div', { class: 'toolbar pill', 'data-tag': 'toolbar', style: { left: (w - pillW) / 2 + 'px', top: pillTop + 'px', width: pillW + 'px', height: pillH + 'px', background: p.capsule } });
      tb.append(item(this.collapsed ? 'chevron_left' : 'chevron_right', this.collapsed ? 'Show tools' : 'Hide tools', 'tb_collapse', { onClick: () => { this.collapsed = !this.collapsed; this.panel = null; this.renderChrome(); } }));
      if (!this.collapsed) {
        tb.append(
          item('inventory_2', 'Box: all notes', 'tb_box', { onClick: () => this.app.back() }),
          item('local_florist', 'Charms', 'tb_charms', { active: this.panel === 'CHARMS', onClick: () => this.togglePanel('CHARMS') }),
          item('note_add', 'New sheet', 'tb_newSheet', { onClick: () => this.newSheet() }),
          item('donut_large', 'Paper and machine', 'tb_theme', { active: this.panel === 'THEME', onClick: () => this.togglePanel('THEME') }),
          item('lightbulb', s.lampOn ? 'Night lamp off' : 'Night lamp on', 'tb_lamp', { active: s.lampOn || this.panel === 'LAMP', onLong: () => this.togglePanel('LAMP'), onClick: lampToggle }),
          item('tune', 'Settings', 'tb_settings', { onClick: () => this.app.go('settings') }),
          item('notifications_none', 'Sound and feel', 'tb_sound', { active: this.panel === 'SOUND', onClick: () => this.togglePanel('SOUND') }),
          h('div', { class: 'spacer' }),
          h('div', { style: { width: '1px', height: pillH * 0.55 + 'px', background: rgba(pillInk, 0.18) } }),
          h('div', { style: { width: '6px', flex: 'none' } }),
          item('explore', 'Writing', 'tb_writing', { active: this.panel === 'WRITING', onClick: () => this.togglePanel('WRITING') }));
      }
      this.chrome.append(tb);
      this.toolbarEl = tb;
      panelPos = { left: '50%', transform: 'translateX(-50%)', bottom: hh - pillTop + 10 + 'px' };
    } else {
      // ---- landscape toolbar: a vertical capsule in the margin, centred at 92.4 % of the width
      const itemH = clamp((hh * 0.585) / 9, 34, 60);
      const tbW = wide ? 46 : 40;
      const tbX = w * 0.924 - tbW / 2;
      const tbY = Math.min(Math.max(hh * 0.31, 150), hh - itemH * 9 - 8);
      const item = (ic, label, tag, opts) => this.toolButton(ic, label, tag, { width: 44, height: itemH, ...opts });
      const tb = h('div', { class: 'toolbar column', 'data-tag': 'toolbar', style: { left: tbX + 'px', top: tbY + 'px', width: tbW + 'px', borderRadius: tbW / 2 + 'px', borderColor: rgba(p.stageInk, 0.13) } });
      tb.append(item(this.collapsed ? 'chevron_left' : 'chevron_right', this.collapsed ? 'Show tools' : 'Hide tools', 'tb_collapse', { onClick: () => { this.collapsed = !this.collapsed; this.panel = null; this.renderChrome(); } }));
      if (!this.collapsed) {
        tb.append(
          item('inventory_2', 'Box: all notes', 'tb_box', { onClick: () => this.app.back() }),
          item('local_florist', 'Charms', 'tb_charms', { active: this.panel === 'CHARMS', onClick: () => this.togglePanel('CHARMS') }),
          item('note_add', 'New sheet', 'tb_newSheet', { onClick: () => this.newSheet() }),
          item('donut_large', 'Paper and machine', 'tb_theme', { active: this.panel === 'THEME', onClick: () => this.togglePanel('THEME') }),
          item('gesture', 'Writing', 'tb_writing', { active: this.panel === 'WRITING', onClick: () => this.togglePanel('WRITING') }),
          item('lightbulb', s.lampOn ? 'Night lamp off' : 'Night lamp on', 'tb_lamp', { active: s.lampOn || this.panel === 'LAMP', activeTint: ACCENT, onLong: () => this.togglePanel('LAMP'), onClick: lampToggle }),
          item('tune', 'Settings', 'tb_settings', { onClick: () => this.app.go('settings') }),
          item('explore', 'Sound and feel', 'tb_sound', { active: this.panel === 'SOUND', onClick: () => this.togglePanel('SOUND') }));
      }
      this.chrome.append(tb);
      this.toolbarEl = tb;
      panelPos = { left: Math.max(tbX - 12 - PANEL_W, 8) + 'px', top: tbY + 'px' };
    }
    this.setToolbarFade();
    if (this.panel) {
      const body = this.panelBody();
      Object.assign(body.style, panelPos, { position: 'absolute' });
      this.chrome.append(body);
    }
    this.renderOverlays();
  }

  panelCard(title, tag, ...content) {
    return h('div', { class: 'panel-card fade-in', 'data-tag': tag },
      h('div', { class: 'panel-head' }, h('div', { class: 'panel-title', text: title }), h('button', { class: 'text-button', 'data-tag': 'panelDone', style: { color: ACCENT }, text: 'Done', onClick: () => { this.panel = null; this.renderChrome(); } })),
      ...content);
  }
  panelToggle(label, checked, tag, onChange, enabled = true) {
    return h('div', { class: 'panel-toggle' }, h('div', { class: 'label', text: label, style: { opacity: enabled ? 1 : 0.45 } }), switchEl(checked, onChange, { tag, enabled, label }));
  }

  panelBody() {
    const s = this.settings, upd = (t) => this.app.store.updateSettings(t);
    switch (this.panel) {
      case 'LAMP':
        return h('div', { class: 'panel-card lamp fade-in', 'data-tag': 'lampControls' },
          h('div', { class: 'panel-head' }, h('div', { class: 'panel-title', text: 'Night lamp' }), h('button', { class: 'text-button', style: { color: ACCENT }, text: 'Done', onClick: () => { this.panel = null; this.renderChrome(); } })),
          ...[['Warmth', 'lampWarmth'], ['Intensity', 'lampIntensity'], ['Beam', 'lampBeam']].map(([label, key]) =>
            h('div', { class: 'lamp-slider' }, h('div', { class: 'label', text: label }), slider(s[key], (v) => upd(() => ({ [key]: v })), { tag: key, label }))));
      case 'CHARMS':
        return this.panelCard('Charms', 'panel_charms',
          this.panelToggle('Charms on the carriage', s.charms, 'panel_sw_charms', (v) => upd(() => ({ charms: v }))),
          h('div', { class: 'charm-row' }, [...new Set(this.palette.charms)].map((c) => h('img', { src: `assets/img/${c}.webp`, alt: '', width: 34, height: 34, style: { opacity: s.charms ? 1 : 0.4 } }))));
      case 'THEME':
        return this.panelCard('Paper & machine', 'panel_theme', h('div', { class: 'theme-row' }, THEMES.map((t) => this.themeChip(t, s.theme === t))));
      case 'WRITING':
        return this.panelCard('Writing', 'panel_writing',
          this.panelToggle('Autocorrect', s.autocorrect, 'panel_sw_autocorrect', (v) => upd(() => ({ autocorrect: v }))),
          this.panelToggle('Backspace erases', s.correction === 'DELETE', 'panel_sw_erase', (v) => upd(() => ({ correction: v ? 'DELETE' : 'STRIKE' }))),
          h('div', { class: 'panel-note', text: s.correction === 'DELETE' ? 'Backspace removes the last letter, across lines too. Hold it to keep erasing.' : 'Backspace steps the carriage back; typing over a letter overstrikes it.' }),
          h('div', { class: 'panel-note', style: { marginTop: '4px' }, text: 'The first line is the title.' }),
          h('button', { class: 'text-button', 'data-tag': 'panel_prompt', style: { color: ACCENT, alignSelf: 'flex-start' }, text: 'Need a first line?', onClick: () => { this.panel = null; this.renderChrome(); this.openPrompt(); } }));
      case 'SOUND':
        return this.panelCard('Sound & feel', 'panel_sound',
          this.panelToggle('Key sounds', s.sound, 'panel_sw_sound', (v) => upd(() => ({ sound: v }))),
          this.panelToggle('Play in silent mode', s.playInSilentMode, 'panel_sw_silent', (v) => upd(() => ({ playInSilentMode: v })), s.sound),
          this.panelToggle('Haptics', s.haptics, 'panel_sw_haptics', (v) => upd(() => ({ haptics: v }))));
      default: return h('div');
    }
  }

  themeChip(theme, selected) {
    const t = paletteFor(theme);
    return h('div', { class: 'theme-chip' },
      h('button', {
        class: 'chip', 'data-tag': `panel_theme_${theme}`, 'aria-label': `${THEME_LABEL[theme]} theme`,
        style: { background: t.stageSide, border: selected ? `2px solid ${ACCENT}` : '1px solid rgba(255,255,255,0.2)' },
        onClick: () => this.app.store.updateSettings(() => ({ theme })),
      }, h('span', { class: 'chip-paper', style: { background: t.paper } }), h('span', { class: 'chip-capsule', style: { background: t.capsule } })),
      h('div', { class: 'chip-label', text: THEME_LABEL[theme] }));
  }

  // -------------------------------------------------------- overlays: prompt link + Morse chart
  renderOverlays() {
    if (!this.overlays || !this.geo) return;
    const g = this.geo, w = this.w, hh = this.h, p = this.palette;
    const portrait = this.portrait, wide = w > hh && w >= 600;
    this.overlays.replaceChildren();
    if (this.engine.size === 0 && !this.missing) {
      const y = g.baseline - 2.8 * g.lineHeight;
      this.overlays.append(h('div', { class: 'prompt-wrap', style: { left: g.paperLeft + 'px', top: y - 12 + 'px', width: g.paperWidth + 'px' } },
        h('button', { class: 'prompt-link', 'data-tag': 'promptButton', style: { color: rgba(p.ink, 0.62) }, text: 'Need a first line?', onClick: () => this.openPrompt() })));
    }
    const st = this.morse.state;
    if (st.kind !== 'On') return;
    const tbX = w * 0.924 - (wide ? 46 : 40) / 2;
    const bottom = g.baseline - g.fontPx - 2.6 * g.lineHeight;
    const top = wide ? hh * 0.10 : 104;
    const mx = wide ? Math.max(tbX - 12 - 300, 8) : 10;
    const mw = wide ? 300 : portrait ? Math.min(w - 20, 360) : Math.min(tbX - 8 - mx, 330);
    const avail = bottom - top;
    let tile = (avail - morseGridHeight(0)) / MORSE_ROWS;
    tile = tile >= 34 ? Math.min(tile, 52) : null;
    const wrap = h('div', { class: 'morse-wrap', style: { left: mx + 'px', top: top + 'px' } });
    if (this.morseCollapsed) {
      wrap.append(h('button', { class: 'morse-pill', 'data-tag': 'morseExpand', onClick: () => { this.morseCollapsed = false; this.renderOverlays(); } },
        h('span', { text: 'Morse  \u00B7 \u2013' }), icon('expand_more', 18)));
    } else wrap.append(this.morsePanel(st.dir, !this.printing, mw, tile));
    this.overlays.append(wrap);
  }

  morsePanel(dir, enabled, width, tileHeight) {
    const key = (t) => { if (enabled) this.typeKeys(t); };
    const tileEl = (c, code, style) => h('button', {
      class: 'morse-tile', disabled: !enabled, 'data-tag': c ? `morse_${c}` : `morse_key_${code}`, 'aria-label': c ? `${c}, ${code}` : code, style,
      onClick: () => key(c ? `${code} ` : code === '/' ? '/ ' : code),
    }, c ? h('span', { class: 'tile-letter', text: c }) : null, h('span', { class: 'tile-code', text: code }));
    const head = h('div', { class: 'morse-head' },
      h('span', { class: 'morse-title', text: 'Morse' }),
      h('span', { class: 'morse-dir', 'data-tag': 'morseDir', text: dir === MorseDir.MORSE_TO_ENGLISH ? '\u00B7 \u2013 \u2192 ABC' : 'ABC \u2192 \u00B7 \u2013' }),
      h('button', { class: 'head-icon', 'data-tag': 'morseCollapse', 'aria-label': 'Hide Morse chart', onClick: () => { this.morseCollapsed = true; this.renderOverlays(); } }, icon('expand_less', 19)),
      h('button', { class: 'head-icon', 'data-tag': 'morseExit', 'aria-label': 'Leave Morse mode', onClick: () => this.exitMorse() }, icon('close', 19)));
    const panel = h('div', { class: 'morse-panel', 'data-tag': 'morsePanel', style: { width: width + 'px', opacity: enabled ? 1 : 0.55 } }, head, h('div', { style: { height: '6px' } }));
    if (tileHeight != null) {
      for (let r = 0; r < MORSE_ROWS; r++) {
        const row = LETTERS.slice(r * MORSE_COLUMNS, r * MORSE_COLUMNS + MORSE_COLUMNS);
        const rowEl = h('div', { class: 'morse-row', style: { marginTop: r > 0 ? '8px' : '0' } });
        row.forEach(([c, code]) => rowEl.append(tileEl(c, code, { height: tileHeight + 'px', flex: '1' })));
        for (let k = row.length; k < MORSE_COLUMNS; k++) rowEl.append(h('div', { style: { flex: '1' } }));
        panel.append(rowEl);
      }
      panel.append(h('div', { style: { height: '12px' } }), h('div', { class: 'morse-rule' }), h('div', { style: { height: '6px' } }),
        h('div', { class: 'morse-footer' },
          ...[['.', 'Dot \u00B7 short', 'morse_dot', '.'], ['-', 'Dash \u00B7 long', 'morse_dash', '-'], ['/', 'Word gap', 'morse_word', '/ ']].map(([sym, label, tag, t]) =>
            h('button', { class: 'footer-key', 'data-tag': tag, disabled: !enabled, onClick: () => key(t) }, h('span', { class: 'sym', text: sym }), h('span', { class: 'lbl', text: label })))));
    } else {
      const strip = h('div', { class: 'morse-strip' });
      for (const k of ['.', '-', '/']) strip.append(tileEl(null, k, { width: '44px', height: '50px', flex: 'none' }));
      for (const [c, code] of LETTERS) strip.append(tileEl(c, code, { width: '52px', height: '50px', flex: 'none' }));
      panel.append(strip);
    }
    return panel;
  }

  // ---------------------------------------------------------------------------- dialogs
  showDialog(d) { this.closeDialog(); this.dialogEl = d; document.body.append(d); }
  closeDialog() { this.dialogEl?.remove(); this.dialogEl = null; }

  openPrompt() {
    let prompt = randomPrompt();
    const text = h('div', { class: 'prompt-text', 'data-tag': 'promptText', text: prompt });
    this.showDialog(dialog({
      title: 'Need a first line?', tag: 'promptDialog', body: text, onDismiss: () => this.closeDialog(),
      buttons: [
        { text: 'Another', tag: 'anotherPrompt', onClick: () => { prompt = randomPrompt(prompt); text.textContent = prompt; } },
        { text: 'Use it', tag: 'usePrompt', onClick: () => { this.closeDialog(); this.insertText(prompt); this.summonKeyboard(); } },
      ],
    }));
  }

  openExport() {
    const studio = this.settings.studio;
    const row = (title, sub, tag, fn) => h('button', { class: 'export-row', 'data-tag': tag, onClick: fn }, h('div', { class: 'export-title', text: title }), h('div', { class: 'export-sub', text: sub }));
    const lock = studio ? '' : '  \u00B7 Studio';
    const body = h('div', { class: 'export-list' },
      row('Image', '4:5, for chats and posts', 'export_image', () => this.doExport({ image: ImageFormat.STANDARD })),
      row('Story', '9:16, Instagram Story size', 'export_story', () => this.doExport({ image: ImageFormat.STORY })),
      ...Object.values(PaperSize).map((ps) => row(`PDF ${ps.label}${lock}`, 'Print-ready typed letter', `export_${ps.key}`, () => this.doExport({ paper: ps }))));
    this.showDialog(dialog({ title: 'Share this note', tag: 'exportDialog', body, onDismiss: () => this.closeDialog(), buttons: [{ text: 'Close', onClick: () => this.closeDialog() }] }));
  }

  async doExport(choice) {
    this.closeDialog();
    if (choice.paper && !this.settings.studio) { this.app.go('studio'); return; }
    this.saveNow();
    const glyphs = this.engine.glyphs;
    const stem = Title.fileStem(glyphs, this.noteId);
    const title = Title.display(glyphs);
    try {
      let blob, name;
      if (choice.paper) {
        const { bytes } = await renderPdf(glyphs, this.noteId, choice.paper);
        blob = new Blob([bytes], { type: 'application/pdf' });
        name = `${stem}-${choice.paper.key.toLowerCase()}.pdf`;
      } else {
        const cv = renderImage(glyphs, this.noteId, choice.image, this.palette, this.app.images[this.palette.paperTex]);
        blob = await new Promise((r) => cv.toBlob(r, 'image/png'));
        name = `${stem}-${choice.image.key.toLowerCase()}.png`;
      }
      this.app.lastExport = { name, size: blob.size, type: blob.type, blob };
      await deliver(blob, name, title);
    } catch (e) {
      this.app.toast(`Couldn't export: ${e?.message || e}`);
    }
  }

  newSheet() {
    this.saveNow();
    const r = this.app.store.create();
    if (r.limit) { this.app.showLimitDialog(); return; }
    this.app.replace(`note/${r.id}`);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export { FREE_ACTIVE_NOTES };
