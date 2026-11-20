package com.mahdi.platen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.domain.MorseController
import com.mahdi.platen.domain.MorseState
import com.mahdi.platen.ui.editor.EditorScreen
import com.mahdi.platen.ui.editor.EditorUiState
import com.mahdi.platen.ui.editor.ToolPanel
import com.mahdi.platen.ui.theme.PlatenTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * v3 stage screenshots. The landscape ones use the reference iPad's exact pixel size (1280x889 at mdpi)
 * so they can be laid side by side with ref/reference_screenshot.jpg.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestPlatenApp::class, qualifiers = "w1280dp-h889dp-land-mdpi")
class ScreenshotV3Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("platen.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        var drawn: Bitmap? = null
        rule.runOnUiThread {
            val root = rule.activity.window.decorView
            val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(b))
            drawn = b
        }
        val bmp = drawn!!
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (y in 0 until bmp.height step 23) for (x in 0 until bmp.width step 17) colors += bmp.getPixel(x, y)
        assertTrue("screenshot looks blank", colors.size > 40)
        return bmp
    }

    private fun editor(
        glyphs: List<Glyph>, cursor: Int = glyphs.size, settings: Settings = Settings(), panel: ToolPanel? = null,
        morse: MorseState = MorseState.Off, morseCollapsed: Boolean = false,
    ) {
        val state = EditorUiState(
            noteId = 7, loading = false, glyphs = glyphs, activeCount = glyphs.size, cursor = cursor, morse = morse,
            field = TextFieldValue(glyphs.take(cursor).joinToString("") { it.over ?: it.text }, TextRange(cursor)),
        )
        rule.setContent {
            PlatenTheme(settings.theme) {
                EditorScreen(
                    state = state, settings = settings, onFieldChange = {}, onInsert = {}, onCharsPerLine = {},
                    onSettingsChange = {}, onBack = {}, onOpenSettings = {}, onExport = {}, autoFocus = false,
                    cursorBlink = false, initialPanel = panel, initialMorseCollapsed = morseCollapsed,
                )
            }
        }
    }

    /** "I'm a developer", with a typo fixed by backspacing and overstriking, as in the reference video. */
    private fun letter(): Pair<List<Glyph>, Int> {
        val e = TypewriterEngine()
        e.columns = 42
        e.type("Dear diary,\n\nI'm a developer and I like the sound of\nevery key. I wrote ")
        e.type("teh")
        repeat(3) { e.backspace() }
        e.type("the")       // t re-inked, e over h... overstrikes
        e.type(" wrong word and kept it.\nTomorrow I will type a")
        e.backspace(); e.backspace()
        return e.glyphs to e.cursor
    }

    @Test fun editorReference() {
        editor(emptyList())
        capture("v3_editor_reference")
    }

    @Test fun textWithCursorAndOverstrike() {
        val (g, c) = letter()
        editor(g, c)
        capture("v3_text_cursor_overstrike")
    }

    @Test fun nightLamp() {
        val (g, c) = letter()
        editor(g, c, Settings(lampOn = true, lampWarmth = 0.7f, lampIntensity = 0.7f, lampBeam = 0.5f))
        capture("v3_night_lamp")
    }

    @Test fun toolbarPanel() {
        val (g, c) = letter()
        editor(g, c, panel = ToolPanel.THEME)
        capture("v3_toolbar_panel")
    }

    @Test fun nearRightMarginLedTurnsRed() {
        val e = TypewriterEngine()
        e.type("Hello my name is Mohammad Mahdi. I am a dev")
        editor(e.glyphs, e.cursor)
        capture("v3_margin_led")
    }

    /** A Morse session typed through the real engine and state machine. */
    private fun morseSession(): Pair<List<Glyph>, MorseState> {
        val e = TypewriterEngine()
        val m = MorseController()
        fun line(t: String) {
            e.type(t + "\n")
            m.onEnter(e.lastFinishedLine(), e.limit - e.size)?.let { e.type(it) }
        }
        line("<morsecode>")
        line("1")
        line(".... . .-.. .-.. ---")
        line(".... .. / - .... . .-. .")
        line("-- --- .-. ... . / .. ... / ..-. ..- -.")
        e.type("... --- ... ")
        return e.glyphs to m.state
    }

    @Test fun morseWithPanel() {
        val (g, st) = morseSession()
        editor(g, morse = st)
        capture("v3_1_morse")
    }

    @Test fun morseTextOnly() {
        val (g, st) = morseSession()
        editor(g, morse = st, morseCollapsed = true)
        capture("v3_1_morse_text")
    }

    @Test @Config(qualifiers = "w393dp-h852dp-port-xxhdpi")
    fun morsePhone() {
        val (g, st) = morseSession()
        editor(g, morse = st)
        capture("v3_1_morse_phone")
    }

    @Test fun charcoalTheme() {
        val (g, c) = letter()
        editor(g, c, Settings(theme = PaperTheme.CHARCOAL))
        capture("v3_theme_charcoal")
    }

    @Test @Config(qualifiers = "w393dp-h852dp-port-xxhdpi")
    fun phonePortrait() {
        val (g, c) = letter()
        editor(g, c)
        capture("v3_editor_phone")
    }

    @Test @Config(qualifiers = "w393dp-h852dp-port-xxhdpi")
    fun phonePortraitEmpty() {
        editor(emptyList())
        capture("v3_editor_phone_empty")
    }
}
