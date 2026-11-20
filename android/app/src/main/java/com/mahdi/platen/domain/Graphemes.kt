package com.mahdi.platen.domain

/**
 * Small, dependency-free grapheme cluster splitter. It is not a full UAX #29 implementation but
 * covers what matters for a typewriter: combining marks (incl. Arabic/Persian harakat), ZWNJ/ZWJ,
 * variation selectors, emoji modifiers, ZWJ emoji sequences, flags (regional-indicator pairs),
 * tag sequences and CRLF. Lone surrogates become their own cluster so callers can drop them.
 */
object Graphemes {

    fun split(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var prevCp = -1
        var riRun = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = Character.charCount(cp)
            val join = sb.isNotEmpty() && joins(prevCp, cp, riRun, sb)
            if (!join && sb.isNotEmpty()) {
                out += sb.toString()
                sb.setLength(0)
            }
            if (!join) riRun = 0
            sb.appendCodePoint(cp)
            riRun = if (isRegionalIndicator(cp)) riRun + 1 else 0
            prevCp = cp
            i += len
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /** Offsets (in UTF-16 units) where clusters start, plus [s].length at the end. */
    fun boundaries(s: String): IntArray {
        val parts = split(s)
        val res = IntArray(parts.size + 1)
        var acc = 0
        parts.forEachIndexed { idx, p -> res[idx] = acc; acc += p.length }
        res[parts.size] = acc
        return res
    }

    fun count(s: String): Int = split(s).size

    private fun joins(prev: Int, cp: Int, riRun: Int, cluster: CharSequence): Boolean {
        if (prev == '\r'.code && cp == '\n'.code) return true
        if (cluster.length == 1 && (cluster[0] == '\n' || cluster[0] == '\r')) return false
        if (isLoneSurrogate(prev) || isLoneSurrogate(cp)) return false
        if (isExtender(cp)) return true
        if (prev == 0x200D && isPictographic(cp)) return true
        if (isRegionalIndicator(cp) && isRegionalIndicator(prev) && riRun % 2 == 1) return true
        return false
    }

    fun isLoneSurrogate(cp: Int): Boolean = cp in 0xD800..0xDFFF

    fun isExtender(cp: Int): Boolean {
        if (cp == 0x200C || cp == 0x200D) return true // ZWNJ (Persian half-space), ZWJ
        if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF) return true // variation selectors
        if (cp in 0x1F3FB..0x1F3FF) return true // emoji skin-tone modifiers
        if (cp in 0xE0020..0xE007F) return true // tag characters
        return when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
    }

    fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    fun isPictographic(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF || cp in 0x2B00..0x2BFF
}
