package com.mahdi.platen

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mahdi.platen.audio.RecordingSoundPlayer
import com.mahdi.platen.data.InMemorySettingsRepository
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.Settings
import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.ui.editor.EditorViewModel
import com.mahdi.platen.ui.notes.NotesViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeNoteDao
    private lateinit var repo: NoteRepository
    private lateinit var settings: InMemorySettingsRepository
    private lateinit var sound: RecordingSoundPlayer

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeNoteDao()
        var t = 1000L
        repo = NoteRepository(dao) { t++ }
        settings = InMemorySettingsRepository()
        sound = RecordingSoundPlayer()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newNote(initial: List<Glyph> = emptyList()): Long {
        var id = 0L
        kotlinx.coroutines.runBlocking { id = (repo.create(false, initial) as CreateResult.Created).id }
        return id
    }

    private fun TestScope.vm(id: Long) = EditorViewModel(id, repo, settings, sound, backgroundScope).also { advanceUntilIdle() }

    private fun EditorViewModel.input(text: String) = onFieldChange(TextFieldValue(text, TextRange(text.length)))

    private fun EditorViewModel.clear() {
        val m: Method = EditorViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        m.invoke(this)
    }

    @Test fun loadsExistingNote() = runTest(dispatcher) {
        val id = newNote(listOf(Glyph("h"), Glyph("i"), Glyph("x", true)))
        val vm = vm(id)
        val s = vm.state.value
        assertEquals(3, s.glyphs.size)
        assertEquals(3, s.activeCount)
        assertEquals(3, s.cursor)
        assertEquals("hix", s.field.text)
        assertEquals(TextRange(3), s.field.selection)
    }

    @Test fun missingNoteIsFlaggedAndIgnoresInput() = runTest(dispatcher) {
        val vm = vm(999)
        assertTrue(vm.state.value.missing)
        vm.input("abc")
        assertTrue(vm.state.value.glyphs.isEmpty())
    }

    @Test fun typingStrikingAndSounds() = runTest(dispatcher) {
        settings.update { it.copy(correction = CorrectionMode.STRIKE) } // "Backspace erases" off
        val vm = vm(newNote())
        vm.input("H")
        vm.input("Hi")
        vm.input("Hi ")
        vm.input("Hi")       // IME backspace -> the carriage steps back over the space
        vm.input("H")        // ...and over the i
        assertEquals(1, vm.state.value.cursor)
        vm.input("Ho")       // a different letter on the i: overstrike
        vm.input("Ho ")      // space over a space just advances
        vm.input("Ho \n")
        val s = vm.state.value
        assertEquals(listOf(Glyph("H"), Glyph("i", true, "o"), Glyph(" "), Glyph("\n")), s.glyphs)
        assertEquals("Ho \n", s.field.text)
        assertEquals(4, s.cursor)
        assertEquals(
            listOf(TypeEvent.KEY, TypeEvent.KEY, TypeEvent.SPACE, TypeEvent.BACKSPACE, TypeEvent.BACKSPACE, TypeEvent.STRIKE, TypeEvent.SPACE, TypeEvent.RETURN),
            sound.played,
        )
    }

    @Test fun deleteModeFromSettings() = runTest(dispatcher) {
        settings.update { it.copy(correction = CorrectionMode.DELETE) }
        val vm = vm(newNote())
        vm.input("ab")
        vm.input("a")
        assertEquals(listOf(Glyph("a")), vm.state.value.glyphs)
    }

    @Test fun imeComposingIsKept() = runTest(dispatcher) {
        val vm = vm(newNote())
        vm.onFieldChange(TextFieldValue("he", TextRange(2), composition = TextRange(0, 2)))
        assertEquals(TextRange(0, 2), vm.state.value.field.composition)
        vm.onFieldChange(TextFieldValue("hel", TextRange(3), composition = TextRange(0, 3)))
        assertEquals("hel", vm.state.value.field.text)
        assertEquals(TextRange(0, 3), vm.state.value.field.composition)
        // Committing an autocorrected word replaces the composing region.
        vm.onFieldChange(TextFieldValue("help ", TextRange(5)))
        assertEquals("help ", vm.state.value.field.text)
        assertNull(vm.state.value.field.composition)
    }

    @Test fun caretIsPinnedToEnd() = runTest(dispatcher) {
        val vm = vm(newNote())
        vm.input("abc")
        vm.onFieldChange(TextFieldValue("abc", TextRange(1)))
        assertEquals(TextRange(3), vm.state.value.field.selection)
    }

    @Test fun limitRejectsAndResetsField() = runTest(dispatcher) {
        val vm = vm(newNote())
        val big = "a".repeat(2999)
        vm.input(big)
        vm.input(big + "bcd")
        assertEquals(3000, vm.state.value.activeCount)
        assertEquals(big + "b", vm.state.value.field.text)
        assertTrue(vm.state.value.isFull)
        assertEquals(TypeEvent.LIMIT, sound.played.last())
    }

    @Test fun autosaveIsDebounced() = runTest(dispatcher) {
        val id = newNote()
        val vm = vm(id)
        vm.input("a"); advanceTimeBy(200)
        vm.input("ab"); advanceTimeBy(200)
        vm.input("abc"); advanceTimeBy(200)
        runCurrent()
        assertEquals(0, dao.updates)
        advanceTimeBy(400); runCurrent(); advanceUntilIdle()
        assertEquals(1, dao.updates)
        assertEquals("abc", repo.get(id)!!.activeText)
    }

    @Test fun clearingSavesPendingWork() = runTest(dispatcher) {
        val id = newNote()
        val vm = vm(id)
        vm.input("xyz")
        vm.clear()
        advanceTimeBy(10); runCurrent() // appScope work runs in backgroundScope
        assertEquals("xyz", repo.get(id)!!.activeText)
    }

    @Test fun clearingAnEmptyNoteDeletesIt() = runTest(dispatcher) {
        val id = newNote()
        val vm = vm(id)
        vm.clear()
        advanceTimeBy(10); runCurrent() // appScope work runs in backgroundScope
        assertNull(repo.get(id))
    }

    @Test fun promptInsertion() = runTest(dispatcher) {
        val vm = vm(newNote())
        vm.insertText("Dear you,")
        assertEquals("Dear you,", vm.state.value.field.text)
        assertEquals(9, vm.state.value.activeCount)
    }

    @Test fun bellFollowsLineWidth() = runTest(dispatcher) {
        val vm = vm(newNote())
        vm.setCharsPerLine(30)
        vm.input("x".repeat(25))
        assertTrue(TypeEvent.BELL !in sound.played)
        vm.input("x".repeat(27)) // bell at 85 % of the line: column 26 of 30
        assertTrue(sound.played.contains(TypeEvent.BELL))
    }

    @Test fun notesViewModelEnforcesLimitAndStudio() = runTest(dispatcher) {
        val nvm = NotesViewModel(repo, settings)
        repeat(3) { assertTrue(nvm.create() is CreateResult.Created) }
        assertEquals(CreateResult.LimitReached, nvm.create())
        settings.update { it.copy(studio = true) }
        assertTrue(nvm.create() is CreateResult.Created)
        advanceUntilIdle()
        assertEquals(4, nvm.notes.value!!.size)
        nvm.delete(nvm.notes.value!!.first().id)
        advanceUntilIdle()
        assertEquals(3, nvm.notes.value!!.size)
    }

    @Test fun settingsDefaults() {
        val s = Settings()
        assertEquals(CorrectionMode.DELETE, s.correction) // 3.2: "Backspace erases" is on by default
        assertTrue(s.backspaceErases)
        assertEquals(false, s.autocorrect)
        assertEquals(false, s.studio)
    }
}
