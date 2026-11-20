// Port of domain/Glyph.kt. A glyph is a plain object using the same JSON keys as the Android app:
//   { c: text (one grapheme cluster or "\n"), s: struck (overstruck cell), o: key typed over it }
export const CorrectionMode = Object.freeze({ STRIKE: 'STRIKE', DELETE: 'DELETE' });
export const TypeEvent = Object.freeze({
  KEY: 'KEY', SPACE: 'SPACE', RETURN: 'RETURN', BELL: 'BELL', STRIKE: 'STRIKE',
  BACKSPACE: 'BACKSPACE', ERASE: 'ERASE', LIMIT: 'LIMIT',
});

export function glyph(c, s = false, o = null) {
  const g = { c };
  if (s) g.s = true;
  if (o != null) g.o = o;
  return g;
}
export const isNewline = (g) => g.c === '\n';
export const isSpace = (g) => g.c === ' ';
export const isStruck = (g) => g.s === true;
export const sameGlyph = (a, b) => a.c === b.c && !!a.s === !!b.s && (a.o ?? null) === (b.o ?? null);

/** GlyphCodec.plainText: struck cells read as the key typed over them; legacy struck cells vanish. */
export function plainText(glyphs, includeStruck = false) {
  let s = '';
  for (const g of glyphs) {
    if (!g.s) s += g.c;
    else if (includeStruck) s += g.c;
    else if (g.o != null) s += g.o;
  }
  return s;
}

export function encode(glyphs) { return JSON.stringify(glyphs); }
export function decode(raw) {
  if (!raw) return [];
  try {
    const v = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return Array.isArray(v) ? v.filter((g) => g && typeof g.c === 'string' && g.c.length > 0).map((g) => glyph(g.c, !!g.s, g.o ?? null)) : [];
  } catch { return []; }
}
