// Port of domain/SheetLayout.kt: monospace grid, word wrap at `columns`, wide glyphs take two cells.
import { isNewline, isSpace } from './glyph.js';

export function width(g) {
  if (isNewline(g)) return 0;
  const cp = g.c.codePointAt(0);
  const wide = cp >= 0x1f000 || (cp >= 0x2600 && cp <= 0x27bf) || (cp >= 0x1100 && cp <= 0x115f) || (cp >= 0x2e80 && cp <= 0xa4cf) ||
    (cp >= 0xac00 && cp <= 0xd7a3) || (cp >= 0xf900 && cp <= 0xfaff) || (cp >= 0xfe30 && cp <= 0xfe4f) || (cp >= 0xff00 && cp <= 0xff60) ||
    (cp >= 0xffe0 && cp <= 0xffe6) || cp >= 0x20000;
  return wide ? 2 : 1;
}

export function sheetLayout(glyphs, columns, cursor = glyphs.length) {
  const cpl = Math.max(1, columns);
  const n = glyphs.length;
  const rows = new Int32Array(n), cols = new Int32Array(n), widths = new Int32Array(n);
  for (let i = 0; i < n; i++) widths[i] = width(glyphs[i]);
  let row = 0, col = 0, i = 0;
  while (i < n) {
    const g = glyphs[i];
    if (isNewline(g)) { rows[i] = row; cols[i] = col; row++; col = 0; i++; }
    else if (isSpace(g)) {
      // A space at the margin hangs off the end of its row, so the wrapped word starts flush left.
      rows[i] = row; cols[i] = col; col++; i++;
    } else {
      let j = i, wl = 0;
      while (j < n && !isNewline(glyphs[j]) && !isSpace(glyphs[j])) { wl += widths[j]; j++; }
      if (col > 0 && col + wl > cpl && wl <= cpl) { row++; col = 0; }
      for (let k = i; k < j; k++) {
        if (col + widths[k] > cpl && col > 0) { row++; col = 0; }
        rows[k] = row; cols[k] = col; col += widths[k];
      }
      i = j;
    }
  }
  const c = Math.min(Math.max(cursor, 0), n);
  let cr, cc;
  if (c < n) { cr = rows[c]; cc = cols[c]; }
  else if (col >= cpl) { cr = row + 1; cc = 0; }
  else { cr = row; cc = col; }
  return { columns: cpl, rows, cols, widths, cursorRow: cr, cursorCol: cc, rowCount: Math.max(row, cr) + 1 };
}
