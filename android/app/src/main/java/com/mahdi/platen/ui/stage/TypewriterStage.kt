package com.mahdi.platen.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.platen.R
import com.mahdi.platen.domain.Carriage
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphJitter
import com.mahdi.platen.domain.SheetLayout
import com.mahdi.platen.domain.NoteTitle
import com.mahdi.platen.domain.Strike
import com.mahdi.platen.ui.theme.CourierPrime
import com.mahdi.platen.ui.theme.LocalPalette
import com.mahdi.platen.ui.theme.SpecialElite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Lamp parameters, all 0..1. */
data class LampState(val on: Boolean, val warmth: Float, val intensity: Float, val beam: Float)

/**
 * The machine: dark stage, the arched sheet riding up one line per Return, the steel rod with
 * its rubber stops, and the carriage pill that steps one cell per keystroke with its T-lever
 * under the red typing cursor. The night lamp (if on) darkens all of it and opens a warm cone
 * from the lever; screen chrome drawn after this composable stays bright.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TypewriterStage(
    glyphs: List<Glyph>,
    cursor: Int,
    seed: Long,
    strokes: Long,
    lamp: LampState,
    charms: Boolean,
    blink: Boolean,
    showPrompt: Boolean,
    onColumns: (Int) -> Unit,
    onTapPaper: () -> Unit,
    onTapCarriage: () -> Unit,
    onLongPressCarriage: () -> Unit,
    onPrompt: () -> Unit,
    modifier: Modifier = Modifier,
    /** Layout family (null: from this box's own aspect). The editor passes the window's. */
    portrait: Boolean? = null,
    /** Space reserved at the bottom (portrait toolbar) is part of the geometry, not the box. */
    onGeometry: (StageGeometry) -> Unit = {},
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val g = remember(wPx, hPx, density, portrait) {
            StageGeometry.of(wPx, hPx, density.density, density.fontScale, portrait ?: (hPx > wPx))
        }
        LaunchedEffect(g.columns) { onColumns(g.columns) }
        LaunchedEffect(g) { onGeometry(g) }

        // ---- the strike choreography -------------------------------------------------------
        // `shown` is what is inked on the sheet right now; it trails the real text by one hammer
        // blow: lever up -> glyph stamped at impact -> lever drops -> carriage steps.
        var shown by remember { mutableStateOf(Visual(glyphs, cursor)) }
        var lastReal by remember { mutableStateOf(Visual(glyphs, cursor)) }
        var lastStrokes by remember { mutableLongStateOf(strokes) }
        val lever = remember { Animatable(0f) }
        val measurer = rememberTextMeasurer(cacheSize = 64)
        val glyphStyle = remember(g.fontPx, density) {
            TextStyle(fontFamily = SpecialElite, fontSize = with(density) { g.fontPx.toSp() }, textDirection = TextDirection.Ltr)
        }
        val glyphCache = remember(glyphStyle) { HashMap<String, TextLayoutResult>() }
        fun measureGlyph(t: String): TextLayoutResult = glyphCache.getOrPut(t) { measurer.measure(t, glyphStyle) }
        fun measureRtl(s: String): Float = measurer.measure(s, glyphStyle.copy(textDirection = TextDirection.Rtl)).size.width.toFloat()

        val layout = remember(shown, g.columns) { SheetLayout.of(shown.glyphs, g.columns, shown.cursor) }
        val rtl = remember(shown.glyphs) { rtlParagraphs(shown.glyphs) }
        val near = Carriage.nearMargin(layout.cursorCol, g.columns)
        val titleLen = remember(shown.glyphs) { NoteTitle.length(shown.glyphs) }
        // Where the next cell is: the cursor is drawn there at once, the carriage follows.
        val targetX = remember(layout, rtl, g) { cursorX(shown.glyphs, layout, rtl, g, shown.cursor, ::measureRtl) }
        val carX = remember { Animatable(targetX) }

        LaunchedEffect(glyphs, cursor, g) {
            val prev = lastReal
            val next = Visual(glyphs, cursor)
            lastReal = next
            val keyed = strokes != lastStrokes
            lastStrokes = strokes
            val k = if (keyed) Strike.struckCell(prev.glyphs, prev.cursor, glyphs, cursor) else -1
            if (k < 0) {
                // No hammer blow: space, Return, erase, paste, load. The carriage just moves.
                val returned = SheetLayout.of(prev.glyphs, g.columns, prev.cursor).cursorRow != SheetLayout.of(glyphs, g.columns, cursor).cursorRow
                shown = next
                if (lever.value != 0f) launch { lever.animateTo(0f, tween(60)) }
                val x = cursorX(glyphs, SheetLayout.of(glyphs, g.columns, cursor), rtlParagraphs(glyphs), g, cursor, ::measureRtl)
                if (!keyed) carX.snapTo(x)
                else carX.animateTo(
                    x,
                    if (returned) spring(dampingRatio = 0.42f, stiffness = 380f) else tween(Strike.STEP_MS, easing = FastOutSlowInEasing),
                )
                return@LaunchedEffect
            }
            // 1. The carriage must stand under cell k (it may still be finishing the last step).
            shown = Visual(prev.glyphs, k)
            val atK = cursorX(prev.glyphs, SheetLayout.of(prev.glyphs, g.columns, k), rtlParagraphs(prev.glyphs), g, k, ::measureRtl)
            val settle = launch { if (carX.value != atK) carX.animateTo(atK, tween(Strike.RISE_MS)) }
            // 2. Lever shoots up to the paper.
            lever.animateTo(1f, tween(Strike.RISE_MS, easing = LinearOutSlowInEasing))
            settle.cancel(); carX.snapTo(atK)
            // 3. Impact: the letter is on the paper.
            shown = next
            // 4. Lever drops back with a little dip below rest; carriage steps once it is down.
            launch { lever.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 1100f)) }
            val x = cursorX(glyphs, SheetLayout.of(glyphs, g.columns, cursor), rtlParagraphs(glyphs), g, cursor, ::measureRtl)
            carX.animateTo(x, tween(Strike.STEP_MS, delayMillis = Strike.STEP_DELAY_MS, easing = FastOutSlowInEasing))
        }

        // Amber "busy" LED while keys are coming in, green once you pause (frame-clock timed).
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(strokes) {
            if (strokes == 0L) return@LaunchedEffect
            busy = true
            frameDelay(Strike.TYPING_LED_MS)
            busy = false
        }

        val row by animateFloatAsState(layout.cursorRow.toFloat(), tween(260, easing = FastOutSlowInEasing), label = "lineFeed")

        // A fresh sheet hangs flat and fills the column; the first keystroke rolls it onto the
        // drum, whose top edge then stays put while the lines roll up and over it.
        val onDrum = shown.glyphs.isNotEmpty()
        val drop = remember { Animatable(if (onDrum) 1f else 0f) }
        LaunchedEffect(onDrum) { drop.animateTo(if (onDrum) 1f else 0f, tween(if (onDrum) 460 else 320, easing = FastOutSlowInEasing)) }

        // Drag the sheet down to reread earlier lines; the next keystroke rolls it back.
        var review by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(strokes) { review = 0f }
        val reviewAnim by animateFloatAsState(review, tween(200), label = "review")
        val dragState = rememberDraggableState { d ->
            review = (review + d).coerceIn(0f, (layout.cursorRow * g.lineHeight).coerceAtLeast(0f))
        }

        // Cursor: solid while typing, hard on/off blink when idle.
        var caretOn by remember { mutableStateOf(true) }
        LaunchedEffect(strokes, blink) {
            caretOn = true
            if (!blink) return@LaunchedEffect
            delay(700)
            while (true) { caretOn = !caretOn; delay(530) }
        }

        val paperTex = ImageBitmap.imageResource(palette.paperTex)
        val carriageTex = ImageBitmap.imageResource(palette.carriageTex)
        val leverTex = ImageBitmap.imageResource(R.drawable.lever_t)
        val stopTex = ImageBitmap.imageResource(R.drawable.rod_stop)
        val charmTex = palette.charms.take(2).map { ImageBitmap.imageResource(it) }
        val paperBrush = remember(paperTex) { ShaderBrush(ImageShader(paperTex, TileMode.Repeated, TileMode.Repeated)) }
        val ink = palette.ink
        val readable = remember(glyphs) { readableText(glyphs) }
        val vis = shown // one consistent snapshot for this frame's drawing (layout matches it)
        val void = if (g.portrait) lerp(palette.stageSide, PortraitVoid, 0.8f) else palette.stageVoid

        Canvas(
            Modifier
                .fillMaxSize()
                .draggable(dragState, Orientation.Vertical)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTapPaper)
                .testTag("paper"),
        ) {
            val d = drop.value
            val hidden = -(g.cornerRy + 6f * g.unit)
            val paperTop = hidden + (g.drumTop - hidden) * d
            val corner = 0.35f + 0.65f * d
            drawStageBack(g, palette.stageSide, void)
            drawPaper(g, paperTop, g.cornerRx * corner, g.cornerRy * corner, paperBrush, palette.isDark, d)
            val sheet = paperSheetPath(g, paperTop, g.cornerRx * corner, g.cornerRy * corner)
            clipPath(sheet) {
                val typingRowFlat = g.baseline + reviewAnim
                drawSheetText(g, vis.glyphs, layout, rtl, seed, ink, titleLen, { r -> typingRowFlat + (r - row) * g.lineHeight }, d, ::measureGlyph, measurer, glyphStyle)
                if (vis.glyphs.isEmpty()) {
                    // Laid on the same monospace grid as real type, so the first keystroke lands on its "B".
                    PLACEHOLDER.forEachIndexed { i, ch ->
                        if (ch != ' ') {
                            val m = measureGlyph(ch.toString())
                            val cx = g.textLeft + i * g.cell + (g.cell - m.size.width) / 2f
                            drawText(m, color = ink.copy(alpha = ink.alpha * 0.45f), topLeft = Offset(cx, g.baseline - m.firstBaseline), alpha = 1f)
                        }
                    }
                }
                drawRodShadowOnPaper(g)
            }
            if (caretOn) {
                val cw = g.cell * 0.95f; val ch = (g.cell * 0.15f).coerceAtLeast(1.5f * density.density)
                drawRect(Color(0xFFD9352B), Offset(targetX - cw / 2, g.cursorY - ch / 2), Size(cw, ch))
            }
            drawRod(g)
            drawStops(g, stopTex)
            drawCarriage(g, carX.value, leverTex, carriageTex, near, busy, lever.value, charms, charmTex)
        }

        // Accessibility / tests: the readable sheet text (the real text, not the trailing visual).
        Box(Modifier.size(1.dp).semantics { contentDescription = readable }.testTag("typedText"))
        // Tests: what is inked at this instant and where the lever is.
        Box(
            Modifier.size(1.dp).semantics { contentDescription = "inked=${readableText(shown.glyphs)}|lever=${"%.2f".format(java.util.Locale.US, lever.value)}" }
                .testTag("strikeState"),
        )

        // Touch target over the carriage: tap = keyboard, long-press = lamp.
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset((carX.value - g.carriageWidth / 2).roundToInt(), g.carriageTop.roundToInt()) }
                    .size(g.carriageWidth.toDp(), g.carriageHeight.toDp())
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = onTapCarriage, onLongClick = onLongPressCarriage,
                    )
                    .testTag("capsule"),
            )
        }

        if (showPrompt) {
            val y = g.baseline - 2.8f * g.lineHeight
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset(g.paperLeft.roundToInt(), (y - 12.dp.toPx()).roundToInt()) }
                        .size(width = g.paperWidth.toDp(), height = 24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        "Need a first line?",
                        fontFamily = CourierPrime, fontSize = 11.sp,
                        color = ink.copy(alpha = 0.62f),
                        modifier = Modifier.clickable(onClick = onPrompt).testTag("promptButton"),
                    )
                }
            }
        }

        if (lamp.on) {
            NightLamp(g, carX.value, lamp, near)
        }
    }
}

private val PortraitVoid = Color(0xFF1B1B1B)

/** What is on the sheet: glyphs plus the carriage position. */
@androidx.compose.runtime.Immutable
private class Visual(val glyphs: List<Glyph>, val cursor: Int)

/** A delay measured on the frame clock (so animation tests and screenshots can drive it). */
private suspend fun frameDelay(ms: Long) {
    val start = withFrameNanos { it }
    while (withFrameNanos { it } - start < ms * 1_000_000L) { /* next frame */ }
}

// ------------------------------------------------------------------------------------ drawing

private fun DrawScope.drawStageBack(g: StageGeometry, side: Color, void: Color) {
    if (g.portrait) { drawRect(void); return }
    drawRect(side)
    // Very soft falloff on the side walls toward the column (depth).
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startX = 0f, endX = g.paperLeft), size = Size(g.paperLeft, g.height))
    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startX = g.paperRight, endX = g.width), Offset(g.paperRight, 0f), Size(g.width - g.paperRight, g.height))
    drawRect(void, Offset(g.paperLeft, 0f), Size(g.paperWidth, g.height))
}

internal fun paperSheetPath(g: StageGeometry, top: Float, rx: Float = g.cornerRx, ry: Float = g.cornerRy): Path {
    val l = g.paperLeft; val r = g.paperRight; val b = g.height + 4f
    return Path().apply {
        moveTo(l, b)
        lineTo(l, top + ry)
        arcTo(Rect(l, top, l + 2 * rx, top + 2 * ry), 180f, 90f, false)
        lineTo(r - rx, top)
        arcTo(Rect(r - 2 * rx, top, r, top + 2 * ry), 270f, 90f, false)
        lineTo(r, b)
        close()
    }
}

private fun DrawScope.drawPaper(g: StageGeometry, top: Float, rx: Float, ry: Float, brush: ShaderBrush, dark: Boolean, onDrum: Float) {
    // Multilayer drop shadow of the curved top edge onto the column behind it.
    for ((dy, a) in listOf(2f to 0.30f, 5f to 0.16f, 10f to 0.08f)) {
        drawPath(paperSheetPath(g, top - dy * g.unit * 0.6f, rx, ry), Color.Black.copy(alpha = a))
    }
    val sheet = paperSheetPath(g, top, rx, ry)
    // Texture stays on the sheet (shader space follows the translation).
    translate(g.paperLeft, top) {
        drawPath(paperSheetPath(g, top, rx, ry).apply { translate(Offset(-g.paperLeft, -top)) }, brush)
    }
    clipPath(sheet) {
        // Where the sheet bends back over the drum it turns slightly away from the light.
        if (onDrum > 0f) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (if (dark) 0.22f else 0.07f) * onDrum), 1f to Color.Transparent,
                    startY = g.drumTop, endY = g.bendStart,
                ),
                Offset(g.paperLeft, g.drumTop), Size(g.paperWidth, g.bendStart - g.drumTop),
            )
        }
        // Cylindrical shading: the sheet bends slightly away at its edges.
        val e = if (dark) 0.26f else 0.05f
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = e), 0.04f to Color.Transparent, 0.96f to Color.Transparent, 1f to Color.Black.copy(alpha = e),
                startX = g.paperLeft, endX = g.paperRight,
            ),
            Offset(g.paperLeft, top), Size(g.paperWidth, g.height - top + 4f),
        )
        // A faint highlight just inside the arched edge.
        drawPath(sheet, Color.White.copy(alpha = if (dark) 0.05f else 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * g.unit))
    }
}

private fun DrawScope.drawRodShadowOnPaper(g: StageGeometry) {
    val top = g.rodCenter - g.rodThickness / 2
    val bot = g.rodCenter + g.rodThickness / 2
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)), startY = top - g.rodThickness * 0.6f, endY = top),
        Offset(0f, top - g.rodThickness * 0.6f), Size(g.width, g.rodThickness * 0.6f),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.10f), Color.Transparent), startY = bot, endY = bot + g.rodThickness * 0.9f),
        Offset(0f, bot), Size(g.width, g.rodThickness * 0.9f),
    )
}

private val RodStops = arrayOf(
    0f to Color(0xFF2F3535), 0.12f to Color(0xFF3A3F43), 0.25f to Color(0xFF5C6165), 0.36f to Color(0xFFA5AAAD),
    0.44f to Color(0xFFB4B9BD), 0.53f to Color(0xFF707579), 0.62f to Color(0xFF52575D), 0.72f to Color(0xFF40454B),
    0.82f to Color(0xFF292E31), 0.92f to Color(0xFF1A1C1E), 1f to Color(0xFF131315),
)

private fun DrawScope.drawRod(g: StageGeometry) {
    val top = g.rodCenter - g.rodThickness / 2
    drawRect(Brush.verticalGradient(*RodStops, startY = top, endY = top + g.rodThickness), Offset(0f, top), Size(g.width, g.rodThickness))
    // Sheen: a crisp specular line inside the bright band, brighter towards the light (left).
    drawRect(
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.18f))),
        Offset(0f, top + g.rodThickness * 0.38f), Size(g.width, g.rodThickness * 0.05f),
    )
}

private fun DrawScope.drawStops(g: StageGeometry, stop: ImageBitmap) {
    // Bitmap is 42 x 60 units with the 26 x 44 block at (8, 8).
    val u = g.stopWidth / 26f
    val w = 42f * u; val h = 60f * u
    for (x in listOf(g.paperLeft + 7f * g.unit, g.paperRight - 7f * g.unit - g.stopWidth)) {
        drawImage(stop, dstOffset = IntOffset((x - 8f * u).roundToInt(), (g.rodCenter - g.stopHeight / 2 - 8f * u).roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
    }
}

private val LedGreen = Color(0xFF55D86A)
private val LedRed = Color(0xFFF0402E)
private val LedOffGreen = Color(0xFF3B4A35)
private val LedOffDark = Color(0xFF3C1C15)
private val LedOffPink = Color(0xFFC9918A)

private val LedAmber = Color(0xFFF0912E)

/** Left LED: green when idle, amber while keys are coming in (as in the reference), dim near the margin. */
internal fun ledColors(near: Boolean, busy: Boolean = false): List<Pair<Color, Boolean>> = listOf(
    (if (near) LedOffGreen else if (busy) LedAmber else LedGreen) to !near,
    (if (near) LedRed else LedOffDark) to near,
    LedOffPink to false,
)

private fun DrawScope.drawLeds(g: StageGeometry, cx: Float, near: Boolean, glowBoost: Float = 1f, busy: Boolean = false) {
    val u = g.unit
    val x0 = cx - g.carriageWidth / 2; val y0 = g.carriageTop
    ledColors(near, busy).forEachIndexed { i, (c, lit) ->
        val center = Offset(x0 + (24f + 19f * i) * u, y0 + 36f * u)
        if (lit) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * glowBoost), Color.Transparent), center, 12f * u), 12f * u, center)
        }
        drawCircle(c, 4.6f * u, center)
        // Dome highlight.
        drawCircle(Color.White.copy(alpha = if (lit) 0.45f else 0.18f), 1.6f * u, center + Offset(-1.4f * u, -1.6f * u))
    }
}

private fun DrawScope.drawCarriage(
    g: StageGeometry, cx: Float, lever: ImageBitmap, body: ImageBitmap, near: Boolean, busy: Boolean, strike: Float,
    charms: Boolean, charmTex: List<ImageBitmap>,
) {
    val u = g.unit
    drawLever(g, cx, lever, strike * g.strikeRise)
    // Body bitmap: 208 x 114 units with the 164 x 70 pill at (22, 22) and a baked soft shadow.
    drawImage(
        body, dstOffset = IntOffset((cx - g.carriageWidth / 2 - 22f * u).roundToInt(), (g.carriageTop - 22f * u).roundToInt()),
        dstSize = IntSize((208f * u).roundToInt(), (114f * u).roundToInt()),
    )
    drawLeds(g, cx, near, busy = busy)
    if (charms) {
        val x0 = cx - g.carriageWidth / 2
        charmTex.forEachIndexed { i, t ->
            val s = 20f * u
            val c = Offset(x0 + (112f + 24f * i) * u, g.carriageTop + 36f * u)
            drawImage(t, dstOffset = IntOffset((c.x - s / 2).roundToInt(), (c.y - s / 2).roundToInt()), dstSize = IntSize(s.roundToInt(), s.roundToInt()))
        }
    }
}

/**
 * T-lever (40 x 47 units: bar rows 0-10, stem below), drawn before the body so the body hides its
 * foot. On a keystroke the bar shoots up by [rise] px and the stem stretches to follow it.
 */
private fun DrawScope.drawLever(g: StageGeometry, cx: Float, lever: ImageBitmap, rise: Float) {
    val u = g.unit
    val left = (cx - 20f * u).roundToInt()
    val w = (40f * u).roundToInt()
    val top = g.leverTop - 2f * u
    val bottom = top + 47f * u
    val pxPerUnit = lever.height / 47f
    val barSrcH = (10.5f * pxPerUnit).roundToInt()
    val barTop = top - rise
    val barH = 10.5f * u
    // Stem: everything under the bar, stretched from the raised bar down to the slot.
    drawImage(
        lever,
        srcOffset = IntOffset(0, barSrcH), srcSize = IntSize(lever.width, lever.height - barSrcH),
        dstOffset = IntOffset(left, (barTop + barH).roundToInt()),
        dstSize = IntSize(w, (bottom - barTop - barH).roundToInt().coerceAtLeast(1)),
    )
    drawImage(
        lever,
        srcOffset = IntOffset(0, 0), srcSize = IntSize(lever.width, barSrcH),
        dstOffset = IntOffset(left, barTop.roundToInt()), dstSize = IntSize(w, barH.roundToInt()),
    )
}

// ---------------------------------------------------------------------------------- sheet text

private fun rtlParagraphs(glyphs: List<Glyph>): BooleanArray {
    val out = BooleanArray(glyphs.size)
    var start = 0
    for (i in 0..glyphs.size) {
        if (i == glyphs.size || glyphs[i].isNewline) {
            val rtl = (start until i).any { idx -> glyphs[idx].text.any(::isRtlChar) }
            if (rtl) for (k in start until i) out[k] = true
            start = i + 1
        }
    }
    return out
}

internal fun isRtlChar(c: Char): Boolean {
    val d = Character.getDirectionality(c)
    return d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

/** What a reader sees: overstruck cells read as the key typed on top. */
internal fun readableText(glyphs: List<Glyph>): String = buildString {
    glyphs.forEach { g ->
        when {
            g.struck && g.over != null -> append(g.over)
            g.struck -> Unit
            else -> append(g.text)
        }
    }
}

private fun cursorX(
    glyphs: List<Glyph>, layout: SheetLayout, rtl: BooleanArray, g: StageGeometry, cursor: Int, measure: (String) -> Float,
): Float {
    val rowRtl = when {
        cursor < glyphs.size -> rtl[cursor]
        cursor > 0 && !glyphs[cursor - 1].isNewline -> rtl[cursor - 1]
        else -> false
    }
    if (!rowRtl) return g.cellCenterX(layout.cursorCol)
    // RTL: the carriage travels leftwards from the right margin by the shaped width typed so far.
    val sb = StringBuilder()
    for (i in 0 until minOf(cursor, glyphs.size)) if (layout.rows[i] == layout.cursorRow && !glyphs[i].isNewline) sb.append(glyphs[i].text)
    val w = if (sb.isEmpty()) 0f else measure(sb.toString())
    return (g.textRight - w - g.cell / 2).coerceAtLeast(g.textLeft)
}

private const val PLACEHOLDER = "Begin here."

private fun DrawScope.drawSheetText(
    g: StageGeometry,
    glyphs: List<Glyph>,
    layout: SheetLayout,
    rtl: BooleanArray,
    seed: Long,
    ink: Color,
    titleLength: Int,
    flatBaseline: (Int) -> Float,
    onDrum: Float,
    measureGlyph: (String) -> TextLayoutResult,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
) {
    val wobble = g.cell * 0.05f
    val rtlRows = HashMap<Int, AnnotatedString.Builder>()
    val rows = HashMap<Int, StageGeometry.DrumRow?>()
    fun rowOf(r: Int): StageGeometry.DrumRow? = rows.getOrPut(r) {
        val flat = flatBaseline(r)
        if (flat > g.height + g.lineHeight) null
        else if (onDrum >= 1f) g.project(flat)
        else {
            // Rolling onto the drum: blend from flat to the bent projection.
            val p = g.project(flat)
            if (p == null) StageGeometry.DrumRow(flat, 1f, 1f, 1f - onDrum)
            else StageGeometry.DrumRow(flat + (p.baseline - flat) * onDrum, 1f + (p.scaleX - 1f) * onDrum, 1f + (p.scaleY - 1f) * onDrum, 1f + (p.alpha - 1f) * onDrum)
        }
    }
    val cx = g.paperCenterX
    for (i in glyphs.indices) {
        val gl = glyphs[i]
        if (gl.isNewline) continue
        val r = layout.rows[i]
        val pr = rowOf(r) ?: continue
        if (rtl[i]) {
            val b = rtlRows.getOrPut(r) { AnnotatedString.Builder() }
            if (gl.struck) {
                b.pushStyle(SpanStyle(background = ink.copy(alpha = 0.62f))); b.append(gl.text); b.pop()
            } else b.append(gl.text)
            continue
        }
        if (gl.isSpace && !gl.struck) continue
        val base = pr.baseline
        val cellX = g.textLeft + layout.cols[i] * g.cell
        val cellW = layout.widths[i] * g.cell
        val title = i < titleLength
        val a = (if (title) 1f else GlyphJitter.alpha(i, seed)) * pr.alpha
        scale(pr.scaleX, pr.scaleY, pivot = Offset(cx, base)) {
            if (gl.struck) {
                drawOverstrike(gl, cellX, cellW, base, g, ink, a, i, seed, measureGlyph)
            } else {
                val m = measureGlyph(gl.text)
                val rot = GlyphJitter.rotation(i, seed)
                val dy = GlyphJitter.offset(i, seed) * wobble
                drawGlyph(m, cellX, cellW, base, ink, a, rot, dy, i, seed, wobble)
                if (title) {
                    // The title is double-struck: a second blow a hair to the right, darker and bolder.
                    drawGlyph(m, cellX + g.cell * 0.075f, cellW, base, ink, a, rot, dy, i, seed, 0f)
                }
            }
        }
    }
    for ((r, b) in rtlRows) {
        val pr = rowOf(r) ?: continue
        val res = measurer.measure(b.toAnnotatedString(), style.copy(color = ink.copy(alpha = 0.9f * pr.alpha), textDirection = TextDirection.Rtl))
        scale(pr.scaleX, pr.scaleY, pivot = Offset(cx, pr.baseline)) {
            drawText(res, topLeft = Offset(g.textRight - res.size.width, pr.baseline - res.firstBaseline))
        }
    }
}

private fun DrawScope.drawGlyph(
    m: TextLayoutResult, cellX: Float, cellW: Float, baseline: Float, ink: Color, alpha: Float,
    rotation: Float, dy: Float, index: Int, seed: Long, wobble: Float,
) {
    val w = m.size.width.toFloat()
    val sx = if (w > cellW * 1.04f) cellW * 1.04f / w else 1f
    val left = cellX + (cellW - w * sx) / 2f
    val top = baseline - m.firstBaseline + dy
    // Uneven ribbon: each strike inks its top and bottom a little differently.
    val t = GlyphJitter.ghostDx(index, seed); val b = GlyphJitter.ghostDy(index, seed)
    val topA = (alpha * (0.86f + 0.14f * t)).coerceIn(0.35f, 1f)
    val botA = (alpha * (0.86f - 0.14f * b)).coerceIn(0.35f, 1f)
    val brush = Brush.verticalGradient(listOf(ink.copy(alpha = ink.alpha * topA), ink.copy(alpha = ink.alpha * botA)), 0f, m.size.height.toFloat())
    rotate(rotation, pivot = Offset(cellX + cellW / 2, baseline - m.firstBaseline * 0.35f)) {
        translate(left, top) {
            scale(sx, 1f, pivot = Offset.Zero) {
                drawText(m, brush = brush, alpha = 1f) // explicit: a cached layout keeps the last alpha it was drawn with
                val ghost = GlyphJitter.ghostAlpha(index, seed)
                if (ghost > 0f) {
                    translate(GlyphJitter.ghostDx(index, seed) * wobble * 1.4f, GlyphJitter.ghostDy(index, seed) * wobble * 0.8f) {
                        drawText(m, brush = brush, alpha = ghost)
                    }
                }
            }
        }
    }
}

/** Two different type faces hammered into one cell: a dense, smudged block of ink. */
private fun DrawScope.drawOverstrike(
    gl: Glyph, cellX: Float, cellW: Float, baseline: Float, g: StageGeometry, ink: Color, alpha: Float,
    index: Int, seed: Long, measureGlyph: (String) -> TextLayoutResult,
) {
    val capTop = baseline - g.fontPx * 0.68f
    val h = g.fontPx * 0.80f
    val j = GlyphJitter.strikeTilt(index, seed) * g.cell * 0.05f
    // Smudged ink block (two slightly offset passes give it torn edges).
    drawRect(ink.copy(alpha = ink.alpha * 0.55f * alpha), Offset(cellX + g.cell * 0.06f + j, capTop + j), Size(cellW - g.cell * 0.12f, h))
    drawRect(ink.copy(alpha = ink.alpha * 0.30f * alpha), Offset(cellX + g.cell * 0.12f - j, capTop - g.fontPx * 0.03f), Size(cellW - g.cell * 0.2f, h * 1.02f))
    val faces = listOfNotNull(gl.text, gl.over).ifEmpty { listOf(gl.text) }
    for ((k, f) in faces.withIndex()) {
        if (f.isBlank()) continue
        val m = measureGlyph(f)
        val w = m.size.width.toFloat()
        val sx = if (w > cellW * 1.04f) cellW * 1.04f / w else 1f
        val left = cellX + (cellW - w * sx) / 2f + (if (k == 0) -0.4f else 0.4f) * g.unit
        translate(left, baseline - m.firstBaseline) {
            scale(sx, 1f, pivot = Offset.Zero) { drawText(m, color = ink, alpha = 0.95f) }
        }
    }
}

// ----------------------------------------------------------------------------------- night lamp

@Composable
private fun NightLamp(g: StageGeometry, leverX: Float, lamp: LampState, near: Boolean) {
    val fadeAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fadeAnim.animateTo(1f, tween(450)) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = fadeAnim.value }
            .testTag("lampOverlay"),
    ) {
        val o = Offset(leverX, g.leverTop)
        // An explicit layer, so the DstOut cuts below only ever erase the lamp's own darkness, even
        // where the platform ignores CompositingStrategy.Offscreen (software canvases, screenshots).
        val layerCanvas = drawContext.canvas
        layerCanvas.saveLayer(Rect(Offset.Zero, size), Paint())
        // Night: the whole desk sinks into a warm dark.
        drawRect(Color(0xFF0B0906).copy(alpha = 0.74f + 0.14f * lamp.intensity))
        // A soft fan of light opening upward from the top of the T-lever. Many faint wedges, each
        // a little wider, cut the darkness away (DstOut) so the edge feathers instead of banding.
        val spread = (34f + 30f * lamp.beam) * (PI.toFloat() / 180f)          // half-angle from vertical
        val reach = size.height * (0.50f + 0.22f * lamp.intensity)
        val far = size.height * 2f
        val layers = 16
        val warm = lerp(Color(0xFFFFE7C2), Color(0xFFFFB45A), lamp.warmth)
        fun wedge(half: Float) = Path().apply {
            moveTo(o.x, o.y + 2f * g.unit)
            lineTo(o.x - far * sin(half), o.y - far * cos(half))
            lineTo(o.x + far * sin(half), o.y - far * cos(half))
            close()
        }
        for (k in 0 until layers) {
            val t = k / (layers - 1f)
            val w = wedge(spread * (0.45f + 0.75f * t))
            drawPath(
                w,
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.085f), 0.35f to Color.Black.copy(alpha = 0.07f), 0.75f to Color.Black.copy(alpha = 0.025f), 1f to Color.Transparent,
                    center = o, radius = reach * (0.75f + 0.35f * (1f - t)),
                ),
                blendMode = BlendMode.DstOut,
            )
        }
        // The pool of light right around the lever, so the line being typed is always lit.
        val pool = size.height * (0.16f + 0.06f * lamp.intensity)
        val pc = o + Offset(0f, -pool * 0.25f)
        drawCircle(
            Brush.radialGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Black.copy(alpha = 0.3f), 1f to Color.Transparent, center = pc, radius = pool),
            pool, pc, blendMode = BlendMode.DstOut,
        )
        // Colour what darkness is left inside the fan amber (SrcAtop only recolours, never lightens,
        // so the type keeps its contrast under the lamp).
        val amber = lerp(Color(0xFF8A7A62), Color(0xFFB8742C), lamp.warmth)
        drawRect(
            Brush.radialGradient(
                0f to amber.copy(alpha = 0.8f), 0.5f to amber.copy(alpha = 0.35f), 1f to Color.Transparent,
                center = o, radius = reach * 0.85f,
            ),
            blendMode = BlendMode.SrcAtop,
        )
        // The bulb under the lever's bar, and its glow on the paper right around it.
        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.6f), warm.copy(alpha = 0.08f), Color.Transparent), o, g.carriageWidth * 0.12f), g.carriageWidth * 0.12f, o)
        drawRect(warm.copy(alpha = 0.9f), Offset(o.x - 11f * g.unit, o.y + 7.6f * g.unit), Size(22f * g.unit, 1.4f * g.unit))
        layerCanvas.restore()
        // The LEDs keep glowing in the dark.
        drawLeds(g, leverX, near, glowBoost = 1.6f)
    }
}
