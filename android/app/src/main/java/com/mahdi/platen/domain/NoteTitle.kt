package com.mahdi.platen.domain

/**
 * The first line of a note (everything before the first Return) is its title: typed bolder on the
 * sheet, used as the note's name in the Box and for exported files. Old notes get theirs for free.
 */
object NoteTitle {
    const val UNTITLED = "Untitled sheet"

    /** Number of leading glyphs that belong to the title line (the newline itself excluded). */
    fun length(glyphs: List<Glyph>): Int {
        val nl = glyphs.indexOfFirst { it.isNewline }
        return if (nl < 0) glyphs.size else nl
    }

    /** True while the glyph at [index] is part of the title line. */
    fun isTitle(glyphs: List<Glyph>, index: Int, titleLength: Int = length(glyphs)): Boolean = index < titleLength

    /** The title as a reader sees it (overstruck cells read as the key typed over them), trimmed. */
    fun of(glyphs: List<Glyph>): String = GlyphCodec.plainText(glyphs.subList(0, length(glyphs))).trim()

    /** The name shown in lists and share sheets. */
    fun display(glyphs: List<Glyph>, maxChars: Int = 60): String {
        val t = of(glyphs).replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return UNTITLED
        return if (t.length <= maxChars) t else t.take(maxChars - 1).trimEnd() + "…"
    }

    /** The body: everything after the title line's Return. */
    fun body(glyphs: List<Glyph>): List<Glyph> {
        val n = length(glyphs)
        return if (n >= glyphs.size) emptyList() else glyphs.subList(n + 1, glyphs.size)
    }

    /**
     * A safe file name stem from the title: letters and digits of any script, other runs become a
     * single '-', at most 40 chars; falls back to "platen-note-<id>".
     */
    fun fileStem(glyphs: List<Glyph>, noteId: Long): String {
        val sb = StringBuilder()
        var dash = false
        for (ch in of(glyphs).lowercase()) {
            if (Character.isLetterOrDigit(ch)) { sb.append(ch); dash = false }
            else if (!dash && sb.isNotEmpty()) { sb.append('-'); dash = true }
            if (sb.length >= 40) break
        }
        val stem = sb.toString().trim('-')
        return if (stem.isEmpty()) "platen-note-$noteId" else "platen-$stem"
    }
}
