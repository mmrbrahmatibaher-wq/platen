package com.mahdi.platen.domain

/**
 * Where the carriage sits for the current typing position. One cell per keystroke on a
 * monospace grid ([SheetLayout]); Return feeds a line and slams back to the left margin.
 */
object Carriage {
    /** Glyphs (overstruck ones included) after the last newline. */
    fun column(glyphs: List<Glyph>): Int {
        var n = 0
        for (i in glyphs.indices.reversed()) {
            if (glyphs[i].isNewline) break
            n++
        }
        return n
    }

    /** 0 at the start of a line, approaching 1 at the right margin. Soft-wrapped lines restart at 0. */
    fun progress(column: Int, charsPerLine: Int): Float {
        if (charsPerLine <= 1 || column <= 0) return 0f
        val inLine = column % charsPerLine
        return (inLine.toFloat() / charsPerLine).coerceIn(0f, 1f)
    }

    /** True once the carriage has passed [TypewriterEngine.MARGIN_SHARE] of the line (margin LED). */
    fun nearMargin(cursorCol: Int, columns: Int): Boolean =
        columns >= 12 && cursorCol >= kotlin.math.ceil(columns * TypewriterEngine.MARGIN_SHARE).toInt()

    /** Number of completed lines; each one turns the platen one line-space. */
    fun lineFeeds(glyphs: List<Glyph>): Int = glyphs.count { it.isNewline }
}
