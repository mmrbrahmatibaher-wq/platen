package com.mahdi.platen

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mahdi.platen.audio.RecordingSoundPlayer
import com.mahdi.platen.data.InMemorySettingsRepository
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphCodec
import com.mahdi.platen.domain.MorseController
import com.mahdi.platen.domain.MorseDir
import com.mahdi.platen.domain.MorseState
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MorseModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: NoteRepository
    private lateinit var settings: InMemorySettingsRepository
    private lateinit var sound: RecordingSoundPlayer

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        var t = 1000L
        repo = NoteRepository(FakeNoteDao()) { t++ }
        settings = InMemorySettingsRepository()
        sound = RecordingSoundPlayer()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(initial: List<Glyph> = emptyList()): EditorViewModel {
        var id = 0L
        kotlinx.coroutines.runBlocking { id = (repo.create(false, initial) as CreateResult.Created).id }
        return EditorViewModel(id, repo, settings, sound, backgroundScope).also { advanceUntilIdle() }
    }

    /** Types like the IME does: the whole field with the new text appended. */
    private fun EditorViewModel.key(text: String) {
        val t = state.value.field.text + text
        onFieldChange(TextFieldValue(t, TextRange(t.length)))
    }

    private fun EditorViewModel.page() = GlyphCodec.plainText(state.value.glyphs)

    @Test fun fullSessionMorseToEnglish() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n")
        assertEquals(MorseState.Menu, vm.state.value.morse)
        assertTrue(vm.state.value.printing)
        advanceUntilIdle()
        assertFalse(vm.state.value.printing)
        assertEquals("<morsecode>\n" + MorseController.MENU, vm.page())
        // the machine typed it with sound, a key at a time
        assertTrue(sound.played.count { it == TypeEvent.KEY } > 30)

        vm.key("1\n"); advanceUntilIdle()
        assertEquals(MorseState.On(MorseDir.MORSE_TO_ENGLISH), vm.state.value.morse)
        vm.key(".... . .-.. .-.. ---\n"); advanceUntilIdle()
        assertTrue(vm.page().endsWith(".... . .-.. .-.. ---\n=> HELLO\n"))
        vm.key("\u2026 \u2014\u2014\u2014 \u2022..\n"); advanceUntilIdle() // phone punctuation
        assertTrue(vm.page().endsWith("=> SOS\n"))

        vm.key("\n"); advanceUntilIdle()   // empty line: leave the mode
        assertEquals(MorseState.Off, vm.state.value.morse)
        vm.key(".... ..\n"); advanceUntilIdle()
        assertTrue(vm.page().endsWith("\n\n.... ..\n"))     // no translation once Off
    }

    @Test fun englishToMorse() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n"); advanceUntilIdle()
        vm.key("2\n"); advanceUntilIdle()
        vm.key("SOS\n"); advanceUntilIdle()
        assertTrue(vm.page().endsWith("SOS\n=> ... --- ...\n"))
    }

    @Test fun triggerNeedsTheWholeLine() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("say <morsecode>\n"); advanceUntilIdle()
        assertEquals(MorseState.Off, vm.state.value.morse)
        vm.key("<morsecode> now\n"); advanceUntilIdle()
        assertEquals(MorseState.Off, vm.state.value.morse)
        assertEquals("say <morsecode>\n<morsecode> now\n", vm.page())
    }

    @Test fun overstruckCellsNeverMatchTheTrigger() = runTest(dispatcher) {
        settings.update { it.copy(correction = com.mahdi.platen.domain.CorrectionMode.STRIKE) }
        val vm = newVm()
        vm.key("<morsecodx")
        // typewriter backspace, then type the right letter over the wrong one: an overstrike
        val t = vm.state.value.field.text.dropLast(1)
        vm.onFieldChange(TextFieldValue(t, TextRange(t.length)))
        vm.key("e>\n"); advanceUntilIdle()
        assertTrue(vm.state.value.glyphs.any { it.struck })
        assertEquals(MorseState.Off, vm.state.value.morse)
    }

    @Test fun printingLockIgnoresInput() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n")
        advanceTimeBy(400); runCurrent()
        assertTrue(vm.state.value.printing)
        val before = vm.state.value.glyphs.size
        vm.key("1\n")                       // pressed while the machine is still typing
        runCurrent()
        assertEquals(MorseState.Menu, vm.state.value.morse) // not taken as the menu choice
        advanceUntilIdle()
        assertEquals("<morsecode>\n" + MorseController.MENU, vm.page())
        assertTrue(vm.state.value.glyphs.size > before)
        // the typed menu lines themselves never trigger anything
        assertEquals(MorseState.Menu, vm.state.value.morse)
    }

    @Test fun autoPrintedTextCountsAndNoSpaceWhenFull() = runTest(dispatcher) {
        val vm = newVm(List(2960) { Glyph("x") } + Glyph("\n"))
        vm.key("<morsecode>\n"); advanceUntilIdle()
        assertTrue(vm.page().endsWith("<morsecode>\n" + MorseController.NO_SPACE))
        assertEquals(MorseState.Off, vm.state.value.morse)
        assertEquals(2961 + 12 + MorseController.NO_SPACE.length, vm.state.value.activeCount)
    }

    @Test fun panelKeysTypeWithSoundButNeverTrigger() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n"); advanceUntilIdle()
        vm.key("1\n"); advanceUntilIdle()
        sound.played.clear()
        vm.typeKeys("... "); vm.typeKeys("--- "); vm.typeKeys("... ")
        assertEquals(listOf(TypeEvent.KEY, TypeEvent.KEY, TypeEvent.KEY, TypeEvent.SPACE), sound.played.take(4))
        vm.key("\n"); advanceUntilIdle()
        assertTrue(vm.page().endsWith("... --- ... \n=> SOS\n"))
        vm.exitMorse()
        assertEquals(MorseState.Off, vm.state.value.morse)
    }

    @Test fun reopeningANoteStartsOff() = runTest(dispatcher) {
        val vm = newVm()
        vm.key("<morsecode>\n"); advanceUntilIdle()
        vm.key("1\n"); advanceUntilIdle()
        vm.saveNow()
        val again = EditorViewModel(vm.state.value.noteId, repo, settings, sound, backgroundScope).also { advanceUntilIdle() }
        assertEquals(MorseState.Off, again.state.value.morse)
        assertTrue(again.state.value.glyphs.isNotEmpty())
    }
}
