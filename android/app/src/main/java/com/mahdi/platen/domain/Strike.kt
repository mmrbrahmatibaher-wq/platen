package com.mahdi.platen.domain

/**
 * One keystroke on the machine, timed from the reference recording (30 fps, frame by frame):
 *
 *  frame 0  (0 ms)    the T-lever's bar is already up at the letter's cap height (rise <= 1 frame)
 *  frame 1  (33 ms)   the letter is on the paper; bar on its way down
 *  frame 2-3 (67-100) bar back at rest with a small dip below it (spring overshoot)
 *  frame 4-6 (133-200) only now the carriage steps one cell, easing out over ~70-130 ms
 *
 * So the glyph is stamped at the moment of impact, and the carriage moves after the lever has
 * dropped. A space, Return or erase has no hammer blow: the carriage simply moves.
 */
object Strike {
    /** Lever travel from rest to the paper; the glyph is stamped when it arrives. */
    const val RISE_MS = 32
    /** Carriage waits this long after impact before stepping (measured ~110-130 ms; a bit snappier). */
    const val STEP_DELAY_MS = 70
    /** Length of the escapement step itself (measured 70-130 ms). */
    const val STEP_MS = 90
    /** The amber "typing" LED holds this long after the last key, then turns green again. */
    const val TYPING_LED_MS = 350L

    /**
     * The cell hit by the change [prev] -> [next], or -1 when nothing is struck (load, erase,
     * Return, space, paste, several keys at once). A strike is exactly one printable key at the
     * carriage: appended at the end, or typed over an existing cell (overstrike / re-ink).
     */
    fun struckCell(prev: List<Glyph>, prevCursor: Int, next: List<Glyph>, nextCursor: Int): Int {
        if (nextCursor != prevCursor + 1) return -1
        val k = prevCursor
        if (k !in next.indices) return -1
        val g = next[k]
        if (g.isNewline || (g.isSpace && !g.struck)) return -1
        return when (next.size) {
            prev.size + 1 -> if (k == next.lastIndex && sameUpTo(prev, next, k)) k else -1
            prev.size -> if (sameExcept(prev, next, k)) k else -1
            else -> -1
        }
    }

    /** Ordered visual phases of one strike, for tests and for the stage's choreography. */
    enum class Phase { LEVER_UP, IMPACT_STAMP, LEVER_DOWN, CARRIAGE_STEP }

    /** (phase, start ms) in the order they happen. */
    val timeline: List<Pair<Phase, Int>> = listOf(
        Phase.LEVER_UP to 0,
        Phase.IMPACT_STAMP to RISE_MS,
        Phase.LEVER_DOWN to RISE_MS,
        Phase.CARRIAGE_STEP to RISE_MS + STEP_DELAY_MS,
    )

    private fun sameUpTo(a: List<Glyph>, b: List<Glyph>, n: Int): Boolean {
        if (a.size < n || b.size < n) return false
        for (i in 0 until n) if (a[i] != b[i]) return false
        return true
    }

    private fun sameExcept(a: List<Glyph>, b: List<Glyph>, k: Int): Boolean {
        for (i in a.indices) if (i != k && a[i] != b[i]) return false
        return true
    }
}
