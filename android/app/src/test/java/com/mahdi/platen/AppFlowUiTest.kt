package com.mahdi.platen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.TypeEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = TestPlatenApp::class, qualifiers = "w393dp-h852dp-xhdpi")
class AppFlowUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestPlatenApp>()

    private fun waitForTag(tag: String, timeout: Long = 5000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGoneTag(tag: String, timeout: Long = 5000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun typedTextIs(expected: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected))

    private fun sheetText(): String =
        rule.onAllNodes(hasTestTag("typedText"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.let { c -> if (SemanticsProperties.ContentDescription in c) c[SemanticsProperties.ContentDescription].firstOrNull() else null } ?: ""

    /** The machine prints with real delays; move Robolectric's main-looper clock along until done. */
    private fun printUntil(condition: () -> Boolean) {
        repeat(400) {
            if (condition()) return
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            rule.waitForIdle()
        }
        throw AssertionError("still printing; sheet=[${sheetText()}]")
    }

    private fun openNewNote() {
        waitForTag("newNote")
        rule.onNodeWithTag("newNote").performClick()
        waitForTag("typeInput")
        rule.waitForIdle()
    }

    @Test fun emptyDeskThenTypeStrikeAndAutosave() {
        runBlocking { app.settingsRepo.update { it.copy(correction = CorrectionMode.STRIKE) } } // typewriter backspace
        waitForTag("emptyState")
        openNewNote()
        val input = rule.onNodeWithTag("typeInput")
        input.performTextInput("Helo")
        rule.onNodeWithTag("counter").assertTextEquals("4 / 3,000")
        // Backspace from the IME only steps the carriage back; the "o" stays on the page.
        input.performTextReplacement("Hel")
        rule.onNodeWithTag("counter").assertTextEquals("4 / 3,000")
        // Typing "l" over the "o" overstrikes that cell; the rest is new paper.
        input.performTextInput("lo, world")
        rule.onNodeWithTag("typedText", useUnmergedTree = true).assert(typedTextIs("Hello, world"))
        rule.onNodeWithTag("counter").assertTextEquals("12 / 3,000")
        assertTrue(app.sound.played.contains(TypeEvent.BACKSPACE))
        assertTrue(app.sound.played.contains(TypeEvent.STRIKE))
        assertTrue(app.sound.played.contains(TypeEvent.SPACE))

        rule.onNodeWithTag("back").performClick()
        // Leaving the editor flushes the save; the list preview shows the overstruck cell as typed.
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTag("notesList")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5000) {
            val n = runBlocking { app.container.notes.get(1) }
            n?.glyphs?.size == 12
        }
        val note = runBlocking { app.container.notes.get(1)!! }
        assertEquals("Hello, world", note.activeText)
        assertEquals(Glyph("o", struck = true, over = "l"), note.glyphs[3])
        rule.waitUntil(5000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Hello, world", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun eraseModeAndNewlines() {
        runBlocking { app.settingsRepo.update { it.copy(correction = CorrectionMode.DELETE) } }
        openNewNote()
        val input = rule.onNodeWithTag("typeInput")
        input.performTextInput("ab")
        input.performTextReplacement("a")
        input.performTextInput("\nc")
        rule.onNodeWithTag("typedText", useUnmergedTree = true).assert(typedTextIs("a\nc"))
        rule.onNodeWithTag("counter").assertTextEquals("3 / 3,000")
        assertTrue(app.sound.played.contains(TypeEvent.ERASE))
        assertTrue(app.sound.played.contains(TypeEvent.RETURN))
    }

    @Test fun leavingEmptyNoteDiscardsIt() {
        openNewNote()
        rule.onNodeWithTag("back").performClick()
        waitForTag("emptyState")
        rule.waitUntil(5000) { runBlocking { app.container.notes.count() } == 0 }
    }

    @Test fun persianTypingDoesNotCrash() {
        openNewNote()
        val text = "سلام دنیا\nمی\u200Cخواهم بنویسم"
        rule.onNodeWithTag("typeInput").performTextInput(text)
        rule.onNodeWithTag("typedText", useUnmergedTree = true).assert(typedTextIs(text))
        // Stepping back and re-typing the same letter just re-inks it.
        rule.onNodeWithTag("typeInput").performTextReplacement(text.dropLast(1))
        rule.onNodeWithTag("typeInput").performTextReplacement(text)
        rule.onNodeWithTag("typedText", useUnmergedTree = true).assert(typedTextIs(text))
        assertFalse(runBlocking { app.container.notes.get(1) }?.glyphs.orEmpty().any { it.struck })
    }

    @Test fun promptFillsFirstLine() {
        openNewNote()
        rule.onNodeWithTag("promptButton").performClick()
        rule.onNodeWithTag("promptText").assertIsDisplayed()
        rule.onNodeWithTag("anotherPrompt").performClick()
        rule.onNodeWithTag("usePrompt").performClick()
        waitGoneTag("promptButton")
        val count = rule.onNodeWithTag("counter").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertTrue(count, !count.startsWith("0 "))
    }

    @Test fun freeLimitShowsStudioAndDemoUnlockLiftsIt() {
        runBlocking {
            repeat(3) { i -> app.container.notes.create(false, listOf(Glyph("n"), Glyph("$i"))) as CreateResult.Created }
        }
        waitForTag("notesList")
        rule.onNodeWithText("Free · 3/3").assertIsDisplayed()
        rule.onNodeWithTag("newNote").performClick()
        waitForTag("limitDialog")
        rule.onNodeWithText("See Studio").performClick()
        waitForTag("studioToggle")
        rule.onNodeWithTag("studioToggle").performClick()
        rule.waitUntil(3000) { app.settingsRepo.settings.value.studio }
        waitForTag("studioState")
        rule.onNodeWithTag("back").performClick()
        openNewNote()
        rule.onNodeWithTag("typeInput").performTextInput("four")
        rule.onNodeWithTag("back").performClick()
        rule.waitUntil(5000) { runBlocking { app.container.notes.count() } == 4 }
    }

    @Test fun deleteNoteFromList() {
        runBlocking { app.container.notes.create(false, listOf(Glyph("z"))) }
        waitForTag("note_1")
        rule.onNodeWithTag("delete_1").performClick()
        rule.onNodeWithTag("confirmDelete").performClick()
        waitForTag("emptyState")
        assertEquals(0, runBlocking { app.container.notes.count() })
    }

    @Test fun lampIconAndCarriageLongPressToggleNightLamp() {
        openNewNote()
        assertTrue(rule.onAllNodes(hasTestTag("lampOverlay")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("tb_lamp").performClick()
        waitForTag("lampOverlay")
        rule.waitUntil(3000) { app.settingsRepo.settings.value.lampOn }
        // Typing still works under the lamp (the overlay doesn't steal input), and the chrome stays usable.
        rule.onNodeWithTag("typeInput").performTextInput("dark")
        rule.onNodeWithTag("counter").assertTextEquals("4 / 3,000")
        rule.onNodeWithTag("tb_lamp").performTouchInput { longClick() }
        waitForTag("lampControls")
        rule.onNodeWithText("Done").performClick()
        waitGoneTag("lampControls")
        rule.onNodeWithTag("capsule").performTouchInput { longClick() }
        waitGoneTag("lampOverlay")
        assertFalse(app.settingsRepo.settings.value.lampOn)
    }

    @Test fun toolbarPanelsAndNewSheet() {
        openNewNote()
        rule.onNodeWithTag("typeInput").performTextInput("one")
        rule.onNodeWithTag("tb_theme").performClick()
        waitForTag("panel_theme_MINT")
        rule.onNodeWithTag("panel_theme_MINT").performClick()
        rule.waitUntil(3000) { app.settingsRepo.settings.value.theme == PaperTheme.MINT }
        rule.onNodeWithTag("panelDone").performClick()
        rule.onNodeWithTag("tb_sound").performClick()
        waitForTag("panel_sw_sound")
        rule.onNodeWithTag("panel_sw_sound").performClick()
        rule.waitUntil(3000) { !app.settingsRepo.settings.value.sound }
        rule.onNodeWithTag("panelDone").performClick()
        rule.onNodeWithTag("tb_newSheet").performClick()
        rule.waitUntil(5000) { runBlocking { app.container.notes.count() } == 2 }
        waitForTag("typeInput")
        rule.waitUntil(3000) {
            rule.onAllNodes(hasTestTag("counter")).fetchSemanticsNodes().firstOrNull()
                ?.config?.get(SemanticsProperties.Text)?.first()?.text == "0 / 3,000"
        }
    }

    @Test fun morseModeThroughTheKeyboardAndPanel() {
        openNewNote()
        val input = rule.onNodeWithTag("typeInput")
        input.performTextInput("<morsecode>\n")
        printUntil { sheetText().endsWith("2. English to Morse\n") }
        input.performTextInput("1\n")
        waitForTag("morsePanel", 8000)
        printUntil { sheetText().endsWith("Empty line = exit.\n") } // the hint, typed by the machine
        // a tile types its code plus the letter gap
        rule.onNodeWithTag("morse_S").performClick()
        rule.onNodeWithTag("morse_O").performClick()
        rule.onNodeWithTag("morse_S").performClick()
        input.performTextInput("\n")
        printUntil { sheetText().endsWith("... --- ... \n=> SOS\n") }
        rule.onNodeWithTag("morseCollapse").performClick()
        waitForTag("morseExpand")
        rule.onNodeWithTag("morseExpand").performClick()
        rule.onNodeWithTag("morseExit").performClick()
        waitGoneTag("morsePanel")
    }

    @Test fun settingsTogglesPersist() {
        waitForTag("settings")
        rule.onNodeWithTag("settings").performClick()
        waitForTag("sw_sound")
        rule.onNodeWithTag("sw_sound").performClick()
        rule.onNodeWithTag("sw_haptics").performClick()
        rule.onNodeWithTag("sw_autocorrect").performClick()
        rule.onNodeWithTag("sw_erase").performClick() // on by default -> off
        rule.waitForIdle()
        assertEquals(CorrectionMode.STRIKE, app.settingsRepo.settings.value.correction)
        rule.onNodeWithTag("sw_erase").performClick() // and back on
        rule.onNodeWithTag("theme_CHARCOAL").performClick()
        rule.waitForIdle()
        val s = app.settingsRepo.settings.value
        assertFalse(s.sound)
        assertFalse(s.haptics)
        assertTrue(s.autocorrect)
        assertEquals(CorrectionMode.DELETE, s.correction)
        assertEquals(PaperTheme.CHARCOAL, s.theme)
        // Silent-mode option is disabled while sounds are off.
        rule.onNodeWithTag("sw_silent").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled).not())
        rule.onNodeWithTag("back").performClick()
        waitForTag("newNote")
    }

    @Test fun exportDialogGatesPdfBehindStudio() {
        openNewNote()
        rule.onNodeWithTag("typeInput").performTextInput("Letter")
        rule.onNodeWithTag("export").performClick()
        waitForTag("export_story")
        rule.onNodeWithText("PDF A4  · Studio").assertIsDisplayed()
        rule.onNodeWithTag("export_A4").performClick()
        waitForTag("studioToggle")
    }
}
