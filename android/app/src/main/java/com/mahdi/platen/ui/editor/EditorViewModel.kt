package com.mahdi.platen.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahdi.platen.audio.SoundPlayer
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.Settings
import com.mahdi.platen.data.SettingsRepository
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.InputDiff
import com.mahdi.platen.domain.MorseController
import com.mahdi.platen.domain.MorseState
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.domain.TypewriterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUiState(
    val noteId: Long,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val glyphs: List<Glyph> = emptyList(),
    val activeCount: Int = 0,
    val limit: Int = TypewriterEngine.MAX_ACTIVE,
    /** Mirror of the hidden IME field: the text left of the carriage, caret at its end. */
    val field: TextFieldValue = TextFieldValue(),
    /** Index of the next cell the carriage will strike (glyphs.size when at the end). */
    val cursor: Int = glyphs.size,
    /** Increments on every keystroke (typing, backspace, return); drives "is typing" UI. */
    val strokes: Long = 0,
    /** Morse mode (never saved: a reopened note always starts Off). */
    val morse: MorseState = MorseState.Off,
    /** The machine is typing by itself; user input is ignored until it finishes. */
    val printing: Boolean = false,
) {
    val isFull: Boolean get() = activeCount >= limit
}

@OptIn(FlowPreview::class)
class EditorViewModel(
    private val noteId: Long,
    private val repo: NoteRepository,
    private val settingsRepo: SettingsRepository,
    private val sound: SoundPlayer,
    private val appScope: CoroutineScope,
    autosaveMillis: Long = 500,
    /** Pause per auto-typed character, like the machine typing by itself. */
    private val printDelayMillis: Long = 35,
) : ViewModel() {

    private val morse = MorseController()
    private var printJob: Job? = null
    private val printing: Boolean get() = printJob?.isActive == true

    private val engine = TypewriterEngine()
    private val _state = MutableStateFlow(EditorUiState(noteId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _events = MutableSharedFlow<TypeEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<TypeEvent> = _events.asSharedFlow()

    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val saveLock = Mutex()
    @Volatile private var dirty = false
    @Volatile private var version = 0L
    @Volatile private var savedVersion = 0L

    init {
        viewModelScope.launch { settings.collect { engine.mode = it.correction } }
        viewModelScope.launch {
            val note = repo.get(noteId)
            if (note == null) {
                _state.update { it.copy(loading = false, missing = true) }
            } else {
                engine.reset(note.glyphs)
                publish(composition = null, loading = false)
            }
        }
        viewModelScope.launch { saveRequests.debounce(autosaveMillis).collect { appScope.launch { saveNow() } } }
    }

    /** Called by the hidden text field for every IME change. */
    fun onFieldChange(value: TextFieldValue) {
        val s = _state.value
        if (s.loading || s.missing) return
        // Printing lock: while the machine types by itself, keys do nothing (the field snaps back).
        if (printing) { publish(null); return }
        val old = s.field.text
        if (value.text == old) {
            // Caret moves / composition changes only: keep the caret pinned at the end.
            val pinned = value.copy(selection = TextRange(old.length))
            if (pinned != s.field) _state.update { it.copy(field = pinned) }
            return
        }
        val diff = InputDiff.diff(old, value.text)
        val events = ArrayList<TypeEvent>()
        repeat(diff.deleteGlyphs) { engine.backspace()?.let(events::add) }
        val print = typeWithHooks(diff.insert, events)
        val keepComposition = engine.fieldText() == value.text
        publish(composition = if (keepComposition) value.composition else null, stroke = events.isNotEmpty())
        // Holding backspace (or a word-delete) can erase many cells in one IME edit: one sound.
        emit(events.filterIndexed { i, e -> e != TypeEvent.ERASE || events.subList(0, i).none { it == TypeEvent.ERASE } })
        if (events.any { it != TypeEvent.BACKSPACE }) changed()
        print?.let(::autoType)
    }

    /**
     * Types [text], showing every carriage return the user made to the Morse state machine.
     * Returns the text the machine should then print by itself; anything typed after that
     * Enter in the same burst (a paste) is dropped, since the machine is busy printing.
     */
    private fun typeWithHooks(text: String, events: MutableList<TypeEvent>): String? {
        var i = 0
        while (i < text.length) {
            val nl = text.indexOf('\n', i)
            if (nl < 0) { events += engine.type(text.substring(i)); break }
            if (nl > i) events += engine.type(text.substring(i, nl))
            val ev = engine.type("\n")
            events += ev
            i = nl + 1
            if (ev.contains(TypeEvent.RETURN)) {
                val print = morse.onEnter(engine.lastFinishedLine(), engine.limit - engine.size)
                if (print != null) return print
            }
        }
        return null
    }

    /** The machine types [text] by itself, a key at a time, with sound. */
    private fun autoType(text: String) {
        printJob?.cancel()
        printJob = viewModelScope.launch {
            delay(PRINT_LEAD_IN_MS) // let the carriage return land first
            for ((i, ch) in text.withIndex()) {
                if (i > 0) delay(printDelayMillis)
                val ev = engine.type(ch.toString())
                emit(ev)
                if (ev.contains(TypeEvent.LIMIT)) break
                publish(null, stroke = true)
            }
            changed()
        }
        printJob?.invokeOnCompletion { publish(null) }
        publish(null) // shows printing = true (and the new mode) right away
    }

    /** Text from the Morse reference panel: typed like keys, never treated as a command. */
    fun typeKeys(text: String) {
        val s = _state.value
        if (s.loading || s.missing || printing) return
        val events = engine.type(text)
        publish(null, stroke = true)
        emit(events)
        if (events.any { it != TypeEvent.LIMIT }) changed()
    }

    /** Leaves Morse mode (the panel's close button). */
    fun exitMorse() {
        morse.reset()
        publish(null)
    }

    /** Hardware-style backspace (e.g. a correction key in the UI). */
    fun backspace() {
        if (_state.value.loading || printing) return
        val e = engine.backspace() ?: return
        emit(listOf(e))
        publish(null, stroke = true)
        if (e != TypeEvent.BACKSPACE) changed()
    }

    /** Types a whole string at once, e.g. a starter prompt. */
    fun insertText(text: String) {
        if (_state.value.loading || _state.value.missing || printing) return
        val ev = engine.type(text)
        publish(null, stroke = true)
        emit(listOfNotNull(if (ev.contains(TypeEvent.LIMIT)) TypeEvent.LIMIT else TypeEvent.RETURN))
        changed()
    }

    /** The editor's measured line length in cells (word wrap, margin bell and LED use it). */
    fun setCharsPerLine(chars: Int) {
        engine.columns = chars
        engine.bellColumn = TypewriterEngine.bellFor(chars)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun currentGlyphs(): List<Glyph> = engine.glyphs

    private fun emit(events: List<TypeEvent>) {
        events.forEach { e ->
            sound.play(e)
            _events.tryEmit(e)
        }
    }

    private fun changed() {
        dirty = true
        version++
        saveRequests.tryEmit(Unit)
    }

    private fun publish(composition: TextRange?, loading: Boolean = false, stroke: Boolean = false) {
        val text = engine.fieldText()
        _state.update {
            it.copy(
                loading = loading,
                glyphs = engine.glyphs,
                activeCount = engine.activeCount,
                cursor = engine.cursor,
                strokes = if (stroke) it.strokes + 1 else it.strokes,
                field = TextFieldValue(text, TextRange(text.length), composition?.takeIf { c -> c.max <= text.length }),
                morse = morse.state,
                printing = printing,
            )
        }
    }

    suspend fun saveNow() = saveLock.withLock {
        val v = version
        if (!dirty || v == savedVersion) return@withLock
        val snapshot = _state.value.glyphs
        repo.save(noteId, snapshot)
        savedVersion = v
        if (version == v) dirty = false
    }

    private companion object { const val PRINT_LEAD_IN_MS = 220L }

    override fun onCleared() {
        val empty = engine.size == 0 && !_state.value.missing && !_state.value.loading
        appScope.launch {
            if (empty) repo.delete(noteId) else saveNow()
        }
    }
}
