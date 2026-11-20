// Port of domain/NoteTitle.kt — the first line of a note is its title.
import { plainText } from './glyph.js';
export const UNTITLED = 'Untitled sheet';
export function length(glyphs) { const nl = glyphs.findIndex((g) => g.c === '\n'); return nl < 0 ? glyphs.length : nl; }
export const isTitle = (glyphs, index, titleLength = length(glyphs)) => index < titleLength;
export const of = (glyphs) => plainText(glyphs.slice(0, length(glyphs))).trim();
export function display(glyphs, maxChars = 60) {
  const t = of(glyphs).replace(/\s+/g, ' ');
  if (!t) return UNTITLED;
  return t.length <= maxChars ? t : t.slice(0, maxChars - 1).trimEnd() + '\u2026';
}
export function body(glyphs) { const n = length(glyphs); return n >= glyphs.length ? [] : glyphs.slice(n + 1); }
export function fileStem(glyphs, noteId) {
  let sb = '', dash = false;
  for (const ch of of(glyphs).toLowerCase()) {
    if (/[\p{L}\p{Nd}]/u.test(ch)) { sb += ch; dash = false; }
    else if (!dash && sb.length > 0) { sb += '-'; dash = true; }
    if (sb.length >= 40) break;
  }
  const stem = sb.replace(/^-+|-+$/g, '');
  return stem ? `platen-${stem}` : `platen-note-${noteId}`;
}
