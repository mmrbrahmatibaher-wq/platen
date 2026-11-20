package com.mahdi.platen

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mahdi.platen.audio.RecordingSoundPlayer
import com.mahdi.platen.data.InMemorySettingsRepository
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphCodec
import com.mahdi.platen.domain.MorseState
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** v3.2 "Backspace erases" through the view model and the hidden IME field. */
@OptIn(ExperimentalCoroutinesApi::class)
class EraseV32ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: NoteRepository
    private lateinit var settings: InMemorySettingsRepository
    private lateinit var sound: RecordingSoundPlayer

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        var t = 1000L
        repo = NoteRepository(FakeNoteDao()) { t++ }
        settings = InMemorySettingsRepository() // defaults: Backspace erases ON
        sound = RecordingSoundPlayer()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(initial: List<Glyph> = emptyList()): EditorViewModel {
        var id = 0L
        kotlinx.coroutines.runBlocking { id = (repo.create(false, initial) as CreateResult.Created).id }
        return EditorViewModel(id, repo, settings, sound, backgroundScope).also { advanceUntilIdle() }
    }

    private fun EditorViewModel.key(text: String) {
        val t = state.value.field.text + text
        onFieldChange(TextFieldValue(t, TextRange(t.length)))
    }

    /** The IME's backspace: the field minus its last [n] chars. */
    private fun EditorViewModel.erase(n: Int = 1) {
        val t = state.value.field.text.dropLast(n)
        onFieldChange(TextFieldValue(t, TextRange(t.length)))
    }

    private fun EditorViewModel.page() = GlyphCodec.plainText(state.value.glyphs)

    @Test fun backspaceErasesAcrossLinesAndTheCounterFalls() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("Title\nbody")
        assertEquals(10, vm.state.value.activeCount)
        vm.erase(); vm.erase(); vm.erase(); vm.erase()
        assertEquals("Title\n", vm.page())
        vm.erase()                                    // through the line break, back onto the title
        assertEquals("Title", vm.page())
        assertEquals(5, vm.state.value.activeCount)
        assertEquals(5, vm.state.value.cursor)
        assertEquals(vm.page(), vm.state.value.field.text)   // the IME field mirrors the sheet exactly
        assertTrue(sound.played.contains(TypeEvent.ERASE))
        assertTrue(sound.played.none { it == TypeEvent.BACKSPACE || it == TypeEvent.STRIKE })
        vm.key("s\nok")
        assertEquals("Titles\nok", vm.page())
        advanceUntilIdle()
        vm.saveNow()
        assertEquals("Titles\nok", GlyphCodec.plainText(repo.get(vm.state.value.noteId)!!.glyphs))
    }

    @Test fun holdingBackspaceErasesManyWithOneSoundPerEdit() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("one two\nthree")
        sound.played.clear()
        vm.erase(10)                                   // a held / word delete: one IME edit
        assertEquals("one", vm.page())
        assertEquals(1, sound.played.count { it == TypeEvent.ERASE })
        repeat(10) { vm.erase() }                      // key repeat: one edit per tick, never below zero
        assertEquals("", vm.page())
        assertEquals(0, vm.state.value.activeCount)
        assertEquals("", vm.state.value.field.text)
        vm.key("again")
        assertEquals("again", vm.page())               // nothing duplicated, nothing lost
    }

    @Test fun eraseWorksInsideMorseMode() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n"); advanceUntilIdle()
        assertEquals(MorseState.Menu, vm.state.value.morse)
        vm.key("1\n"); advanceUntilIdle()
        assertTrue(vm.state.value.morse is MorseState.On)
        val before = vm.state.value.activeCount
        vm.key("...x")
        vm.erase()                                     // fix the typo
        assertEquals(before + 3, vm.state.value.activeCount)
        vm.key(" ---\n"); advanceUntilIdle()
        assertTrue(vm.page(), vm.page().endsWith("... ---\n=> SO\n"))
        // erasing the machine's own answer works too, and the counter follows
        val n = vm.state.value.activeCount
        vm.erase(3)
        assertEquals(n - 3, vm.state.value.activeCount)
        assertTrue(vm.page().endsWith("=> "))
    }

    @Test fun typewriterModeStillStepsBackAndOverstrikes() = runTest(dispatcher) {
        settings.update { it.copy(correction = com.mahdi.platen.domain.CorrectionMode.STRIKE) }
        val vm = newVm()
        vm.key("ab\ncd")
        vm.erase(); vm.erase(); vm.erase()             // stops at the left margin of line 2
        assertEquals("ab\ncd", vm.page())
        assertEquals(3, vm.state.value.cursor)
        vm.key("x")
        assertEquals(Glyph("c", struck = true, over = "x"), vm.state.value.glyphs[3])
    }
}
