package com.mahdi.platen.domain

/**
 * Pure Kotlin typewriter model.
 *
 * Typewriter mode (STRIKE): Backspace never erases. It moves the carriage one cell left over the
 * text already on the current line. Typing then goes over the existing cells. The same key
 * re-inks the letter, a different key overstrikes it (a dark block), and a letter on a blank
 * (space) cell just prints. Return always feeds a new line at the bottom of the sheet.
 * DELETE mode erases the cell left of the carriage instead.
 *
 * Every cell on the paper (newlines too) counts against [limit]; overstrikes don't add cells.
 */
class TypewriterEngine(
    initial: List<Glyph> = emptyList(),
    val limit: Int = MAX_ACTIVE,
) {
    private val list = ArrayList<Glyph>()
    var mode: CorrectionMode = CorrectionMode.STRIKE

    /** Line length in cells used for word wrap, the margin bell and the margin LED. */
    var columns: Int = 40
        set(v) { field = v.coerceAtLeast(1) }

    /** Column at which the margin bell rings; 0 = off. Defaults to 85 % of [columns]. */
    var bellColumn: Int = 0

    /** Index of the next cell to strike (0..size). Always on the last line. */
    var cursor: Int = 0
        private set

    init { reset(initial) }

    val glyphs: List<Glyph> get() = list.toList()
    val size: Int get() = list.size
    val activeCount: Int get() = list.size
    val isFull: Boolean get() = list.size >= limit

    fun reset(glyphs: List<Glyph>) {
        list.clear()
        glyphs.forEach { g -> if (g.text.isNotEmpty()) list += g }
        cursor = list.size
    }

    private fun lineStart(): Int {
        for (i in list.indices.reversed()) if (list[i].isNewline) return i + 1
        return 0
    }

    /** Glyphs between the start of the current line and the carriage. */
    fun column(): Int = cursor - lineStart()

    /** Mirror for the hidden IME field: what sits left of the carriage (overstrikes as typed). */
    fun fieldText(): String = buildString {
        for (i in 0 until cursor) {
            val g = list[i]
            append(if (g.struck && g.over != null) g.over else g.text)
        }
    }

    /** Kept for callers that want the readable text of the sheet. */
    fun activeText(): String = fieldText()

    fun layout(): SheetLayout = SheetLayout.of(list, columns, cursor)

    /** Carriage column on its wrapped row; only the current paragraph needs laying out. */
    fun lineCursorCol(): Int {
        val ls = lineStart()
        return SheetLayout.of(list.subList(ls, list.size), columns, cursor - ls).cursorCol
    }

    private fun effectiveBell(): Int = if (bellColumn > 0) bellColumn else 0

    fun type(input: String): List<TypeEvent> {
        if (input.isEmpty()) return emptyList()
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ')
        val events = ArrayList<TypeEvent>()
        for (cluster in Graphemes.split(normalized)) {
            val clean = sanitize(cluster) ?: continue
            if (clean == "\n") {
                if (list.size >= limit) { events += TypeEvent.LIMIT; break }
                list += Glyph("\n"); cursor = list.size
                events += TypeEvent.RETURN
                continue
            }
            val before = if (effectiveBell() > 0) lineCursorCol() else 0
            if (cursor < list.size) {
                val g = list[cursor]
                events += when {
                    clean == " " -> TypeEvent.SPACE                        // space just advances
                    !g.struck && g.text == clean -> TypeEvent.KEY          // re-ink the same letter
                    g.isSpace && !g.struck -> { list[cursor] = Glyph(clean); TypeEvent.KEY }
                    else -> { list[cursor] = g.copy(struck = true, over = clean); TypeEvent.STRIKE }
                }
                cursor++
            } else {
                // A mark / modifier arriving on its own (IME composing) belongs to the previous glyph.
                val last = list.lastOrNull()
                if (last != null && !last.struck && !last.isNewline &&
                    Graphemes.split(last.text + clean).size == 1
                ) {
                    list[list.lastIndex] = last.copy(text = last.text + clean)
                    continue
                }
                if (list.size >= limit) { events += TypeEvent.LIMIT; break }
                list += Glyph(clean); cursor = list.size
                events += if (clean == " ") TypeEvent.SPACE else TypeEvent.KEY
            }
            val bell = effectiveBell()
            if (bell > 0) {
                val after = lineCursorCol()
                if (before < bell && after >= bell) events += TypeEvent.BELL
            }
        }
        return events
    }

    /**
     * Typewriter mode: steps the carriage back one cell (BACKSPACE), or null at the left margin.
     * DELETE mode: removes the cell left of the carriage (a newline too, un-feeding the paper).
     */
    fun backspace(): TypeEvent? {
        if (mode == CorrectionMode.DELETE) {
            if (cursor == 0) return null
            list.removeAt(cursor - 1)
            cursor--
            return TypeEvent.ERASE
        }
        if (cursor <= lineStart()) return null
        cursor--
        return TypeEvent.BACKSPACE
    }

    /**
     * What the reader sees on the line just finished with Enter (the paragraph before the last
     * newline). Overstruck cells read as '■', not as either letter. Empty unless the sheet ends in
     * a newline.
     */
    fun lastFinishedLine(): String {
        if (list.isEmpty() || !list.last().isNewline) return ""
        var start = list.size - 1
        while (start > 0 && !list[start - 1].isNewline) start--
        return buildString { for (i in start until list.size - 1) append(visible(list[i])) }
    }

    /** Visible text of the line the carriage is on. */
    fun currentLine(): String = buildString { for (i in lineStart() until list.size) append(visible(list[i])) }

    private fun visible(g: Glyph): String = if (g.struck) OVERSTRIKE else g.text

    /** Moves the carriage back to the end of the typed text on the current line. */
    fun toLineEnd() { cursor = list.size }

    /** Drops control characters and lone surrogates; returns null if nothing typeable is left. */
    private fun sanitize(cluster: String): String? {
        if (cluster == "\n") return cluster
        val sb = StringBuilder()
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            val ok = !Graphemes.isLoneSurrogate(cp) && !(Character.isISOControl(cp)) && cp != 0xFFFF
            if (ok) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().ifEmpty { null }
    }

    companion object {
        const val MAX_ACTIVE = 3000
        const val OVERSTRIKE = "\u25A0"
        /** The margin LED turns red / the bell rings at this share of the line. */
        const val MARGIN_SHARE = 0.85f
        fun bellFor(columns: Int): Int = if (columns >= 12) kotlin.math.ceil(columns * MARGIN_SHARE).toInt() else 0
    }
}
