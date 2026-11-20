package com.mahdi.platen.domain

/**
 * Monospace grid layout of a sheet: every glyph gets a (row, col) cell, like type on a real
 * platen. Paragraphs (split at "\n") are word-wrapped at [columns]; a word longer than a whole
 * line is broken. Wide glyphs (emoji, CJK) take two cells.
 */
class SheetLayout private constructor(
    val columns: Int,
    val rows: IntArray,
    val cols: IntArray,
    val widths: IntArray,
    /** Row and column of the typing position (the next cell to be struck). */
    val cursorRow: Int,
    val cursorCol: Int,
    val rowCount: Int,
) {
    companion object {
        fun of(glyphs: List<Glyph>, columns: Int, cursor: Int = glyphs.size): SheetLayout {
            val cpl = columns.coerceAtLeast(1)
            val n = glyphs.size
            val rows = IntArray(n); val cols = IntArray(n); val widths = IntArray(n) { width(glyphs[it]) }
            var row = 0; var col = 0
            var i = 0
            while (i < n) {
                val g = glyphs[i]
                when {
                    g.isNewline -> {
                        rows[i] = row; cols[i] = col
                        row++; col = 0; i++
                    }
                    g.isSpace -> {
                        // A space at the margin hangs off the end of its row (as on a machine), so
                        // the wrapped word starts flush left instead of one cell in.
                        rows[i] = row; cols[i] = col; col++; i++
                    }
                    else -> {
                        var j = i; var wl = 0
                        while (j < n && !glyphs[j].isNewline && !glyphs[j].isSpace) { wl += widths[j]; j++ }
                        if (col > 0 && col + wl > cpl && wl <= cpl) { row++; col = 0 }
                        for (k in i until j) {
                            if (col + widths[k] > cpl && col > 0) { row++; col = 0 }
                            rows[k] = row; cols[k] = col; col += widths[k]
                        }
                        i = j
                    }
                }
            }
            val c = cursor.coerceIn(0, n)
            val (cr, cc) = when {
                c < n -> rows[c] to cols[c]
                col >= cpl -> (row + 1) to 0
                else -> row to col
            }
            return SheetLayout(cpl, rows, cols, widths, cr, cc, maxOf(row, cr) + 1)
        }

        fun width(g: Glyph): Int {
            if (g.isNewline) return 0
            val cp = g.text.codePointAt(0)
            val wide = cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF ||
                cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF || cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 ||
                cp in 0xFFE0..0xFFE6 || cp >= 0x20000
            return if (wide) 2 else 1
        }
    }
}
