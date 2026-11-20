package com.mahdi.platen.domain

/** What the IME did to the hidden field, expressed as typewriter operations. */
data class EditDiff(val deleteGlyphs: Int, val insert: String)

object InputDiff {
    /**
     * Compares the previous field text with the new one. The change is anchored at the longest
     * common prefix, rounded down to a grapheme boundary of [old] so a surrogate pair or a
     * letter+mark cluster is never split.
     */
    fun diff(old: String, new: String): EditDiff {
        if (old == new) return EditDiff(0, "")
        var p = 0
        val max = minOf(old.length, new.length)
        while (p < max && old[p] == new[p]) p++
        val bounds = Graphemes.boundaries(old)
        var aligned = 0
        for (b in bounds) if (b <= p) aligned = b else break
        // Don't let the insertion start in the middle of a surrogate pair of the new text.
        if (aligned > 0 && aligned < new.length && Character.isLowSurrogate(new[aligned])) {
            aligned = bounds.lastOrNull { it < aligned } ?: 0
        }
        val deleted = Graphemes.count(old.substring(aligned))
        // A dangling half of a surrogate pair is never a real keystroke; don't pass it on.
        val tail = new.substring(aligned)
        val insert = tail.filterIndexed { i, c ->
            when {
                Character.isHighSurrogate(c) -> i + 1 < tail.length && Character.isLowSurrogate(tail[i + 1])
                Character.isLowSurrogate(c) -> i > 0 && Character.isHighSurrogate(tail[i - 1])
                else -> true
            }
        }
        return EditDiff(deleted, insert)
    }
}
