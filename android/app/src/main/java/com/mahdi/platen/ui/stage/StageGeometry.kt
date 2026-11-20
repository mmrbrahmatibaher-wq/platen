package com.mahdi.platen.ui.stage

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Proportions of the machine stage, measured on the reference layouts and expressed as fractions
 * so they scale to any window. All values are in px.
 *
 * Landscape (1280 x 889 reference): paper column from 15.3 % of the width, 69.5 % wide, between dark
 * side walls; the platen drum's top edge (where the sheet curls away) at 32.6 % of the height; rod
 * centred at 83.8 %.
 *
 * Portrait (889 x 1280 reference, keyboard open): the sheet spans the full width over a #1B1B1B
 * stage; drum top at 37.7 % of the stage above the keyboard; the rod sits just over the cream
 * toolbar pill, which sits just over the keyboard.
 *
 * Both: elliptical top corners rx = 14.6 %, ry = 37 % of the paper width (fitted to the reference
 * outline), carriage 18 % of the paper width (100-176 dp), typing baseline 1.29 carriage heights
 * above the rod centre, 0.6 em monospace cells, 1.75-cell line feed.
 */
class StageGeometry private constructor(
    val width: Float,
    val height: Float,
    val portrait: Boolean,
    val paperLeft: Float,
    val paperWidth: Float,
    val cornerRx: Float,
    val cornerRy: Float,
    /** Where the sheet's top edge sits once it is on the drum (it curls away out of sight above). */
    val drumTop: Float,
    val rodCenter: Float,
    val rodThickness: Float,
    val carriageWidth: Float,
    val carriageHeight: Float,
    /** One "reference unit": the carriage is 164 x 70 of these. */
    val unit: Float,
    val cell: Float,
    val fontPx: Float,
    val lineHeight: Float,
    val textLeft: Float,
    val columns: Int,
    /** Baseline of the fixed typing line. */
    val baseline: Float,
    val stopWidth: Float,
    val stopHeight: Float,
    /** Portrait: the horizontal toolbar pill under the rod (top, height). */
    val toolbarTop: Float,
    val toolbarHeight: Float,
) {
    val paperRight: Float get() = paperLeft + paperWidth
    val paperCenterX: Float get() = paperLeft + paperWidth / 2f
    val textRight: Float get() = textLeft + columns * cell
    val carriageTop: Float get() = rodCenter - carriageHeight / 2
    val carriageBottom: Float get() = rodCenter + carriageHeight / 2
    val leverTop: Float get() = carriageTop - 39f * unit
    /** Where the red underline sits, just under the baseline of the typing line. */
    val cursorY: Float get() = baseline + cell * 0.45f
    fun cellCenterX(col: Int, w: Int = 1): Float = textLeft + (col + w / 2f) * cell

    /**
     * How far the T-lever's bar shoots up on a keystroke: from rest to the cap height of the
     * typing line (measured: the bar top meets the letter's top, 27 units above rest).
     */
    val strikeRise: Float get() = (leverTop - (baseline - fontPx * 0.62f)).coerceAtLeast(6f * unit)

    /** Radius of the drum the sheet wraps over above the typing line. */
    val drumRadius: Float get() = (baseline - drumTop) * 0.62f
    /** Rows above this line start to bend away over the drum. */
    val bendStart: Float get() = drumTop + drumRadius

    /**
     * Projects a baseline laid out flat on the sheet onto the drum: rows below [bendStart] are
     * untouched; above it a row at arc length s sits at angle s / R, so it climbs ever slower,
     * gets foreshortened (scaleY = cos), narrower toward the paper's centre and a touch fainter.
     * Returns null once a row has rolled over the top and out of sight.
     */
    fun project(flatBaseline: Float): DrumRow? {
        if (flatBaseline >= bendStart) return DrumRow(flatBaseline, 1f, 1f, 1f)
        val r = drumRadius
        val theta = (bendStart - flatBaseline) / r
        if (theta >= MAX_THETA) return null
        val c = cos(theta)
        return DrumRow(
            baseline = bendStart - r * sin(theta),
            scaleX = 1f - 0.17f * sin(theta),
            scaleY = c.coerceAtLeast(0.08f),
            alpha = 0.62f + 0.38f * c,
        )
    }

    data class DrumRow(val baseline: Float, val scaleX: Float, val scaleY: Float, val alpha: Float)

    companion object {
        private const val MAX_THETA = (PI / 2 * 0.96).toFloat()

        fun of(
            widthPx: Float,
            heightPx: Float,
            density: Float,
            fontScale: Float = 1f,
            /** Layout family; the editor passes the window's orientation so the IME can't flip it. */
            portrait: Boolean = heightPx > widthPx,
        ): StageGeometry {
            val paperLeft = if (portrait) 0f else widthPx * 0.153f
            val paperWidth = if (portrait) widthPx else widthPx * 0.695f
            val carW = (paperWidth * (if (portrait) 0.18f else 0.184f)).coerceIn(100f * density, 176f * density)
            val carH = carW * 70f / 164f
            val unit = carW / 164f
            val toolbarH = if (portrait) 42f * density else 0f
            val toolbarTop = if (portrait) heightPx - 8f * density - toolbarH else heightPx
            val rodCenter = if (portrait) toolbarTop - 18f * density - carH / 2f else heightPx * 0.838f
            val cellShare = if (portrait) 0.0177f else 0.0155f
            val fontPx = (paperWidth * cellShare / 0.6f).coerceIn(12.5f * density * fontScale, 24f * density * fontScale)
            val cell = fontPx * 0.6f
            val margin = paperWidth * (if (portrait) 0.155f else 0.158f)
            val columns = floor((paperWidth - 2 * margin) / cell).toInt().coerceAtLeast(8)
            val textLeft = paperLeft + margin
            val baseline = rodCenter - 1.29f * carH
            val lh = cell * 1.75f
            val drumWanted = if (portrait) maxOf(heightPx * 0.36f, 118f * density) else heightPx * 0.326f
            val drumTop = drumWanted.coerceAtMost(baseline - 4f * lh).coerceAtLeast(0f)
            return StageGeometry(
                width = widthPx, height = heightPx, portrait = portrait,
                paperLeft = paperLeft, paperWidth = paperWidth,
                cornerRx = paperWidth * 0.146f, cornerRy = paperWidth * 0.37f,
                drumTop = drumTop,
                rodCenter = rodCenter, rodThickness = carH * 0.49f,
                carriageWidth = carW, carriageHeight = carH, unit = unit,
                cell = cell, fontPx = fontPx, lineHeight = lh,
                textLeft = textLeft, columns = columns, baseline = baseline,
                stopWidth = carH * 0.36f, stopHeight = carH * 0.62f,
                toolbarTop = toolbarTop, toolbarHeight = toolbarH,
            )
        }
    }
}
