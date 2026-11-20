package com.mahdi.platen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.MorseCodec
import com.mahdi.platen.domain.SheetLayout
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.ui.editor.EditorScreen
import com.mahdi.platen.ui.editor.EditorUiState
import com.mahdi.platen.ui.stage.StageGeometry
import com.mahdi.platen.ui.theme.PlatenTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** v3.2 screenshots at the reference images' pixel sizes (mdpi, so 1 dp = 1 px). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestPlatenApp::class, qualifiers = "w1280dp-h889dp-land-mdpi")
class ScreenshotV32Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("platen.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private fun grab(): Bitmap {
        var drawn: Bitmap? = null
        rule.runOnUiThread {
            val root = rule.activity.window.decorView
            val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(b))
            drawn = b
        }
        return drawn!!
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val bmp = grab()
        save(bmp, name)
        val colors = HashSet<Int>()
        for (y in 0 until bmp.height step 23) for (x in 0 until bmp.width step 17) colors += bmp.getPixel(x, y)
        assertTrue("screenshot looks blank", colors.size > 40)
        return bmp
    }

    private fun stateOf(glyphs: List<Glyph>, cursor: Int = glyphs.size, strokes: Long = 0) = EditorUiState(
        noteId = 7, loading = false, glyphs = glyphs, activeCount = glyphs.size, cursor = cursor, strokes = strokes,
        field = TextFieldValue(glyphs.take(cursor).joinToString("") { it.over ?: it.text }, TextRange(cursor)),
    )

    private fun editor(glyphs: List<Glyph>, settings: Settings = Settings(), keyboard: Boolean = false) {
        val state = stateOf(glyphs)
        rule.setContent {
            PlatenTheme(settings.theme) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EditorScreen(
                            state = state, settings = settings, onFieldChange = {}, onInsert = {}, onCharsPerLine = {},
                            onSettingsChange = {}, onBack = {}, onOpenSettings = {}, onExport = {}, autoFocus = false,
                            cursorBlink = false,
                        )
                    }
                    if (keyboard) {
                        // Stand-in for the system keyboard (not part of the app): the stage above it is what counts.
                        Box(Modifier.fillMaxWidth().height(365.dp).background(Color(0xFFD6D6D9)), contentAlignment = Alignment.Center) {
                            Text("system keyboard", color = Color(0xFF8A8A90), fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    private fun typed(text: String, columns: Int): List<Glyph> {
        val e = TypewriterEngine(); e.columns = columns; e.mode = CorrectionMode.DELETE
        e.type(text)
        return e.glyphs
    }

    private val letter = "A letter from the desk\nHello my name is mr.rahmati baher\nHow are you?\nI typed this on Platen, one key at a time,\nand erased a typo without a trace."

    @Test @Config(qualifiers = "w889dp-h1280dp-port-mdpi")
    fun portraitKeyboard() {
        val morse = (1..14).joinToString("\n") { i -> MorseCodec.toMorse("the quick brown fox jumps $i over") }
        editor(typed("Morse practice\n$morse\nJniu88 the end", 60), keyboard = true)
        capture("v3_2_portrait_keyboard")
    }

    @Test fun landscape() {
        val many = (1..22).joinToString("\n") { i -> MorseCodec.toMorse("line $i hello world how are you") }
        editor(typed("Dots and dashes\n$many\n... --- ...", 58))
        capture("v3_2_landscape")
    }

    @Test fun landscapeFullSheet() {
        editor(emptyList())
        capture("v3_2_landscape_full_sheet")
    }

    @Test fun title() {
        editor(typed(letter, 58))
        capture("v3_2_title")
    }

    @Test @Config(qualifiers = "w393dp-h852dp-port-xxhdpi")
    fun phonePortrait() {
        editor(typed(letter, 36), keyboard = false)
        capture("v3_2_phone_portrait")
    }

    /** One keystroke frame by frame: lever at rest -> lever up (strike) -> glyph stamped -> carriage stepped. */
    @Test fun strikeStrip() {
        val base = typed("Strike\nHell", 58)
        var state by mutableStateOf(stateOf(base, strokes = 1))
        rule.setContent {
            PlatenTheme(Settings().theme) {
                EditorScreen(
                    state = state, settings = Settings(), onFieldChange = {}, onInsert = {}, onCharsPerLine = {},
                    onSettingsChange = {}, onBack = {}, onOpenSettings = {}, onExport = {}, autoFocus = false,
                    cursorBlink = false,
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        val g = StageGeometry.of(1280f, 889f, 1f, portrait = false)
        val col = SheetLayout.of(base, g.columns, base.size).cursorCol
        val cx = g.cellCenterX(col).toInt()
        val crop = { b: Bitmap ->
            val w = 300; val top = (g.baseline - 3 * g.lineHeight).toInt()
            val h = (g.carriageBottom + 20 - top).toInt()
            Bitmap.createBitmap(b, (cx - w / 2).coerceIn(0, b.width - w), top, w, h)
        }
        val frames = ArrayList<Pair<String, Bitmap>>()
        frames += "0 ms rest" to crop(grab())
        rule.mainClock.autoAdvance = false
        state = stateOf(base + Glyph("o"), strokes = 2)
        fun strike(): String = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("strikeState"), useUnmergedTree = true)
            .fetchSemanticsNodes().first().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first()
        fun lever() = strike().substringAfter("lever=").toFloat()
        var t = 0
        while (lever() <= 0.3f && t < 300) { rule.mainClock.advanceTimeBy(4); t += 4 }
        val t0 = t
        frames += "lever rising" to crop(grab())
        while (!strike().contains("Hello") && t < 600) { rule.mainClock.advanceTimeBy(4); t += 4 }
        rule.mainClock.advanceTimeByFrame(); t += 16 // the frame that draws the impact
        frames += "impact: stamped" to crop(grab())
        rule.mainClock.advanceTimeBy(60); t += 60
        frames += "+${t - t0} ms lever down" to crop(grab())
        rule.mainClock.advanceTimeBy(400); t += 400
        frames += "+${t - t0} ms carriage stepped" to crop(grab())
        val fw = frames[0].second.width; val fh = frames[0].second.height
        val strip = Bitmap.createBitmap(fw * frames.size, fh + 28, Bitmap.Config.ARGB_8888)
        val c = Canvas(strip)
        c.drawColor(android.graphics.Color.rgb(24, 22, 20))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(230, 220, 200); textSize = 16f }
        frames.forEachIndexed { i, (label, bmp) ->
            c.drawBitmap(bmp, (i * fw).toFloat(), 28f, null)
            c.drawText(label, i * fw + 8f, 20f, p)
        }
        save(strip, "v3_2_strike_strip")
    }
}
