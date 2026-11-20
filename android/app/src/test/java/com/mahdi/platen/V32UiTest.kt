package com.mahdi.platen

import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.PaperTheme
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.export.NoteExporter
import com.mahdi.platen.ui.stage.LampState
import com.mahdi.platen.ui.stage.TypewriterStage
import com.mahdi.platen.ui.theme.PlatenTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v3.2 through the real app: erase, title in the Box, rotation. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestPlatenApp::class, qualifiers = "w393dp-h852dp-port-xhdpi")
class V32UiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<TestPlatenApp>()

    private fun waitForTag(tag: String) =
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun sheetText(): String =
        rule.onAllNodes(hasTestTag("typedText"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.let { c -> if (SemanticsProperties.ContentDescription in c) c[SemanticsProperties.ContentDescription].firstOrNull() else null } ?: ""

    private fun openNewNote() {
        waitForTag("newNote")
        rule.onNodeWithTag("newNote").performClick()
        waitForTag("typeInput")
        rule.waitForIdle()
    }

    @Test fun eraseFromTheKeyboardAcrossALineBreak() {
        openNewNote()
        val input = rule.onNodeWithTag("typeInput")
        input.performTextInput("Shopping\nmilk")
        rule.onNodeWithTag("counter").assertTextEquals("13 / 3,000")
        input.performTextReplacement("Shopping")          // IME backspace x5, through the Return
        rule.waitForIdle()
        assertEquals("Shopping", sheetText())
        rule.onNodeWithTag("counter").assertTextEquals("8 / 3,000")
        input.performTextInput("!")
        rule.waitForIdle()
        assertEquals("Shopping!", sheetText())
    }

    @Test fun firstLineNamesTheNoteInTheBox() {
        openNewNote()
        rule.onNodeWithTag("typeInput").performTextInput("Letter to Sara\nHi Sara, how are you?")
        rule.waitForIdle()
        rule.onNodeWithTag("back").performClick()
        rule.waitUntil(5000) { rule.onAllNodes(hasTestTag("noteTitle_1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("noteTitle_1", useUnmergedTree = true).assertTextEquals("Letter to Sara")
    }

    @Test fun rotationKeepsTheSheet() {
        openNewNote()
        rule.onNodeWithTag("typeInput").performTextInput("Before\nrotation")
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()            // configuration change, e.g. rotating the device
        waitForTag("typeInput")
        rule.waitUntil(5000) { sheetText() == "Before\nrotation" }
        rule.onNodeWithTag("typeInput").performTextInput(" ok")
        rule.waitForIdle()
        assertEquals("Before\nrotation ok", sheetText()) // the IME field came back in sync: nothing doubled
        rule.onNodeWithTag("counter").assertTextEquals("18 / 3,000")
    }

    @Test fun exportsTypeTheTitleBold() {
        val e = TypewriterEngine(); e.type("Title\nbody")
        val sp = NoteExporter.spannable(e.glyphs, android.graphics.Color.BLACK, 1L)
        val bold = sp.getSpans(0, sp.length, StyleSpan::class.java).filter { it.style == android.graphics.Typeface.BOLD }
        assertTrue(bold.isNotEmpty())
        assertTrue(bold.all { sp.getSpanEnd(it) <= 5 })        // only the first line
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, sp.getSpanFlags(bold.first()))
    }
}

/** The stage's own choreography, frame by frame on a paused clock. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestPlatenApp::class, qualifiers = "w1280dp-h889dp-land-mdpi")
class StrikeStageTest {
    @get:Rule val rule = createComposeRule()

    private fun strikeState(): String =
        rule.onAllNodes(hasTestTag("strikeState"), useUnmergedTree = true).fetchSemanticsNodes().first()
            .config[SemanticsProperties.ContentDescription].first()

    @Test fun leverStrikesFirstThenTheGlyphIsStamped() {
        var glyphs by mutableStateOf(listOf(Glyph("H"), Glyph("i")))
        var strokes by mutableStateOf(0L)
        rule.setContent {
            PlatenTheme(PaperTheme.CREAM) {
                Box(Modifier.size(1280.dp, 889.dp)) {
                    TypewriterStage(
                        glyphs = glyphs, cursor = glyphs.size, seed = 1, strokes = strokes,
                        lamp = LampState(false, 0f, 0f, 0f), charms = false, blink = false, showPrompt = false,
                        onColumns = {}, onTapPaper = {}, onTapCarriage = {}, onLongPressCarriage = {}, onPrompt = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue(strikeState(), strikeState().startsWith("inked=Hi|lever=0.00"))
        rule.mainClock.autoAdvance = false
        glyphs = glyphs + Glyph("!"); strokes = 1
        // Step the frame clock until the lever leaves its rest position.
        var t = 0
        fun lever() = strikeState().substringAfter("lever=").toFloat()
        while (lever() <= 0f && t < 200) { rule.mainClock.advanceTimeBy(8); t += 8 }
        val rising = strikeState()
        assertTrue(rising, lever() > 0f && lever() < 1f)
        assertTrue(rising, rising.startsWith("inked=Hi|"))                       // the lever moves first: not on the paper yet
        val start = t
        while (!strikeState().startsWith("inked=Hi!") && t < 400) {
            assertTrue(strikeState(), lever() >= 0f)
            rule.mainClock.advanceTimeBy(8); t += 8
        }
        val stamped = strikeState()
        assertTrue(stamped, stamped.startsWith("inked=Hi!|"))                    // impact: stamped...
        assertTrue(stamped, lever() > 0.9f)                                       // ...while the bar is up at the paper
        assertTrue("stamped ${t - start} ms after the lever moved", t - start in 0..64)
        rule.mainClock.advanceTimeBy(600)
        assertTrue(strikeState(), strikeState().matches(Regex("inked=Hi!\\|lever=-?0\\.0\\d")))  // lever back at rest
        // Fast typing: three keys inside one strike never drop a character.
        glyphs = glyphs + Glyph("a"); strokes = 2
        rule.mainClock.advanceTimeBy(10)
        glyphs = glyphs + Glyph("b"); strokes = 3
        rule.mainClock.advanceTimeBy(10)
        glyphs = glyphs + Glyph("c"); strokes = 4
        val allowed = listOf("Hi!", "Hi!a", "Hi!ab", "Hi!abc")
        var seen = 0
        repeat(80) {
            rule.mainClock.advanceTimeBy(8)
            val inked = strikeState().substringAfter("inked=").substringBefore("|")
            val idx = allowed.indexOf(inked)
            assertTrue("unexpected sheet [$inked]", idx >= 0)
            assertTrue("a key went missing: [$inked]", idx >= seen)              // only ever forwards
            seen = idx
        }
        assertTrue(strikeState(), strikeState().startsWith("inked=Hi!abc|"))
        assertFalse(strikeState().contains("abcc"))
    }
}
