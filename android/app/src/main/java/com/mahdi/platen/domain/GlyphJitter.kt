package com.mahdi.platen.domain

/**
 * Deterministic per-glyph "imperfect type" values. Same note seed + glyph index always gives the
 * same result, so letters don't wiggle on recomposition.
 */
object GlyphJitter {
    const val MAX_ROTATION = 0.5f
    const val MIN_ALPHA = 0.74f

    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun unit(index: Int, seed: Long, salt: Long): Float {
        val v = mix(seed * 1_000_003L + index * 31L + salt) ushr 40 // 24 bits
        return v.toFloat() / (1 shl 24).toFloat()
    }

    /** Rotation in degrees within ±0.5°. */
    fun rotation(index: Int, seed: Long): Float = (unit(index, seed, 11) * 2f - 1f) * MAX_ROTATION

    /** Ink density 0.74..1.0, skewed towards dark. */
    fun alpha(index: Int, seed: Long): Float {
        val u = unit(index, seed, 23)
        return MIN_ALPHA + (1f - MIN_ALPHA) * (1f - u * u)
    }

    /** Vertical baseline wobble in -1..1 (callers scale to pixels). */
    fun offset(index: Int, seed: Long): Float = unit(index, seed, 37) * 2f - 1f

    /** Slight tilt of the strike line, -1..1. */
    fun strikeTilt(index: Int, seed: Long): Float = unit(index, seed, 53) * 2f - 1f

    /**
     * Ghost "double strike" for a minority of glyphs (a key that bounced on the ribbon):
     * 0 for most glyphs, otherwise the ghost's alpha in 0.22..0.42.
     */
    fun ghostAlpha(index: Int, seed: Long): Float {
        val u = unit(index, seed, 71)
        return if (u < GHOST_SHARE) 0.22f + 0.20f * (u / GHOST_SHARE) else 0f
    }

    /** Ghost displacement direction, each axis in -1..1. */
    fun ghostDx(index: Int, seed: Long): Float = unit(index, seed, 83) * 2f - 1f
    fun ghostDy(index: Int, seed: Long): Float = unit(index, seed, 97) * 2f - 1f

    const val GHOST_SHARE = 0.18f
}
