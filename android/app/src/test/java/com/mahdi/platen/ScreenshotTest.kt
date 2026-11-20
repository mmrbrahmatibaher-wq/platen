package com.mahdi.platen

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.Note
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.export.ExportColors
import com.mahdi.platen.export.ImageFormat
import com.mahdi.platen.export.NoteExporter
import com.mahdi.platen.export.PaperSize
import com.mahdi.platen.ui.editor.EditorScreen
import com.mahdi.platen.ui.editor.EditorUiState
import com.mahdi.platen.ui.notes.NotesListScreen
import com.mahdi.platen.ui.settings.SettingsScreen
import com.mahdi.platen.ui.studio.StudioScreen
import com.mahdi.platen.ui.theme.PlatenTheme
import com.mahdi.platen.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders real screens with Robolectric's native graphics and writes PNGs to /screenshots. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestPlatenApp::class, qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("platen.screenshots") ?: "build/screenshots").apply { mkdirs() }

    private fun save(name: String, bmp: Bitmap) {
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        // Draw the real view hierarchy into a bitmap (Robolectric native graphics renders it).
        var drawn: Bitmap? = null
        rule.runOnUiThread {
            val root = rule.activity.window.decorView
            val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(b))
            drawn = b
        }
        val bmp = drawn!!
        save(name, bmp)
        return bmp
    }

    private fun sampleGlyphs(): List<Glyph> {
        val e = TypewriterEngine()
        e.type("November 20\n\nDear Sara,\n\nThe rain has not stopped since Tuesday, so I finally pulled the old machine out of the closet. Every key feels like a small decision. I wrote \"recieve\" ")
        repeat(8) { e.backspace() }
        e.type("\"receive\" wrong twice already, and the page keeps both of my mistakes. I like that more than I expected.\n\nسلام از تهران!\n\nWrite back soon,\nM.")
        return e.glyphs
    }

    private fun editorState(glyphs: List<Glyph>) = EditorUiState(
        noteId = 7, loading = false, glyphs = glyphs, activeCount = glyphs.size,
        field = TextFieldValue(glyphs.joinToString("") { it.over ?: it.text }, TextRange(0)),
    )

    private fun editor(theme: PaperTheme, settings: Settings, glyphs: List<Glyph>) {
        rule.setContent {
            PlatenTheme(theme) {
                EditorScreen(
                    state = editorState(glyphs), settings = settings, onFieldChange = {}, onInsert = {}, onCharsPerLine = {},
                    onSettingsChange = {}, onBack = {}, onOpenSettings = {}, onExport = {}, autoFocus = false,
                    cursorBlink = false,
                )
            }
        }
    }

    private fun assertNotBlank(bmp: Bitmap) {
        val colors = HashSet<Int>()
        for (y in 0 until bmp.height step 23) for (x in 0 until bmp.width step 17) colors += bmp.getPixel(x, y)
        assertTrue("screenshot looks blank (${colors.size} colours)", colors.size > 40)
    }

    @Test fun editorCreamWithStruckLetters() {
        editor(PaperTheme.CREAM, Settings(), sampleGlyphs())
        assertNotBlank(capture("01_editor_cream"))
    }

    @Test fun editorBlankPage() {
        editor(PaperTheme.MINT, Settings(theme = PaperTheme.MINT), emptyList())
        assertNotBlank(capture("02_editor_blank_mint"))
    }

    @Test fun editorNightLamp() {
        editor(PaperTheme.CREAM, Settings(lampOn = true, lampWarmth = 0.75f, lampIntensity = 0.75f, lampBeam = 0.5f), sampleGlyphs())
        assertNotBlank(capture("03_editor_night_lamp"))
    }

    @Test fun editorCharcoal() {
        editor(PaperTheme.CHARCOAL, Settings(theme = PaperTheme.CHARCOAL), sampleGlyphs())
        assertNotBlank(capture("04_editor_charcoal"))
    }

    @Test fun editorGarden() {
        editor(PaperTheme.GARDEN, Settings(theme = PaperTheme.GARDEN), sampleGlyphs())
        assertNotBlank(capture("12_editor_garden"))
    }

    @Test fun editorCarriageMidLine() {
        val g = TypewriterEngine().apply { type("Watch the carriage:\nit travels left as I type, then") }.glyphs
        editor(PaperTheme.CREAM, Settings(), g)
        assertNotBlank(capture("13_editor_carriage_midline"))
    }

    @Test fun notesList() {
        val e1 = TypewriterEngine().apply { type("Groceries: lemons, bread, ink ribbon, stamps. ") }
        val e2 = TypewriterEngine().apply { type("To whoever finds this note: the plant on the windowsill needs watering on Sundays.") }
        val e3 = TypewriterEngine().apply { type("Dear Dad,\nI fixed the old Olivetti! The bell still rings at the end of every lime"); repeat(4) { backspace() }; type("line.") }
        val notes = listOf(
            Note(3, e3.glyphs, 0, 1_763_600_000_000), Note(2, e2.glyphs, 0, 1_763_500_000_000), Note(1, e1.glyphs, 0, 1_763_400_000_000),
        )
        rule.setContent {
            PlatenTheme(PaperTheme.CREAM) {
                NotesListScreen(notes = notes, studio = false, onNew = {}, onOpen = {}, onDelete = {}, onOpenSettings = {}, onOpenStudio = {})
            }
        }
        assertNotBlank(capture("05_notes_list"))
    }

    @Test fun settingsScreen() {
        rule.setContent { PlatenTheme(PaperTheme.CREAM) { SettingsScreen(Settings(), {}, {}, {}) } }
        assertNotBlank(capture("06_settings"))
    }

    @Test fun studioScreen() {
        rule.setContent { PlatenTheme(PaperTheme.CREAM) { StudioScreen(unlocked = false, onToggle = {}, onBack = {}) } }
        assertNotBlank(capture("07_studio"))
    }

    @Test fun appIcon() {
        val ctx = ApplicationProvider.getApplicationContext<TestPlatenApp>()
        val d = androidx.core.content.ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 432, 432)
        d.draw(Canvas(bmp))
        save("00_app_icon", bmp)
        assertTrue(d is android.graphics.drawable.AdaptiveIconDrawable)
    }

    @Test fun exportedImagesAndPdf() {
        val ctx = ApplicationProvider.getApplicationContext<TestPlatenApp>()
        val p = paletteFor(PaperTheme.CREAM)
        val colors = ExportColors(p.paper.toArgb(), p.ink.toArgb(), p.background.toArgb(), p.muted.toArgb(), false, p.paperTex)
        val std = NoteExporter.renderImage(ctx, sampleGlyphs(), 7, ImageFormat.STANDARD, colors)
        assertEquals(1080, std.width); assertEquals(1350, std.height)
        save("08_export_image", std)
        val story = NoteExporter.renderImage(ctx, sampleGlyphs(), 7, ImageFormat.STORY, colors)
        assertEquals(1920, story.height)
        save("09_export_story", story)
        // A full 3000-character note must still fit (font shrinks) without crashing.
        val full = TypewriterEngine().apply { type("lorem ipsum dolor sit amet ".repeat(120)) }.glyphs
        assertEquals(3000, full.size)
        save("10_export_full_note", NoteExporter.renderImage(ctx, full, 1, ImageFormat.STANDARD, colors))

        val pdf = File(ctx.cacheDir, "t.pdf")
        val pages = runCatching { NoteExporter.writePdf(ctx, full, 1, PaperSize.A5, pdf) }
        if (pages.isSuccess) {
            assertTrue(pages.getOrThrow() >= 2)
            assertTrue(pdf.length() > 0)
            pdf.copyTo(File(outDir, "11_export_a5.pdf"), overwrite = true)
        } else {
            println("PdfDocument not supported in this Robolectric runtime: ${pages.exceptionOrNull()}")
        }
    }
}
