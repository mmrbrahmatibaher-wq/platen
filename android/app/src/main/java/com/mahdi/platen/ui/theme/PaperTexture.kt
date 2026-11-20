package com.mahdi.platen.ui.theme

import kotlin.random.Random

/**
 * Procedural, seamlessly tileable paper texture: low-frequency mottling, fine grain and a few
 * short fibres. Pure Kotlin so it can be unit tested and reused for exports.
 */
object PaperTexture {
    fun generate(size: Int, baseArgb: Int, seed: Int = 7, dark: Boolean = false): IntArray {
        require(size in 16..1024)
        val rnd = Random(seed)
        val lum = FloatArray(size * size)
        // Low-frequency mottling: wrapped value noise on a coarse grid.
        val grid = 4
        val g = Array(grid) { FloatArray(grid) { rnd.nextFloat() } }
        val cell = size.toFloat() / grid
        for (y in 0 until size) for (x in 0 until size) {
            val gx = x / cell; val gy = y / cell
            val x0 = gx.toInt() % grid; val y0 = gy.toInt() % grid
            val x1 = (x0 + 1) % grid; val y1 = (y0 + 1) % grid
            val fx = smooth(gx - gx.toInt()); val fy = smooth(gy - gy.toInt())
            val top = g[y0][x0] * (1 - fx) + g[y0][x1] * fx
            val bot = g[y1][x0] * (1 - fx) + g[y1][x1] * fx
            lum[y * size + x] = (top * (1 - fy) + bot * fy - 0.5f) * 4.5f
        }
        // Grain.
        for (i in lum.indices) lum[i] += (rnd.nextFloat() - 0.5f) * 4.5f
        // Fibres.
        repeat(size / 6) {
            var x = rnd.nextFloat() * size; var y = rnd.nextFloat() * size
            var angle = rnd.nextFloat() * 6.283f
            val len = 6 + rnd.nextInt(18)
            val shade = if (rnd.nextBoolean()) -6f else 5f
            repeat(len) {
                val ix = ((x.toInt() % size) + size) % size
                val iy = ((y.toInt() % size) + size) % size
                lum[iy * size + ix] += shade
                angle += (rnd.nextFloat() - 0.5f) * 0.5f
                x += kotlin.math.cos(angle); y += kotlin.math.sin(angle)
            }
        }
        val br = (baseArgb shr 16) and 0xFF; val bg = (baseArgb shr 8) and 0xFF; val bb = baseArgb and 0xFF
        val k = if (dark) 0.8f else 1f
        return IntArray(size * size) { i ->
            val d = lum[i] * k
            val r = (br + d).toInt().coerceIn(0, 255)
            val gg = (bg + d).toInt().coerceIn(0, 255)
            val b = (bb + d * 0.9f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
        }
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}
