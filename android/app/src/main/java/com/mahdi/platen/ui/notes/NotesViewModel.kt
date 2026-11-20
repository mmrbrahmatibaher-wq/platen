package com.mahdi.platen.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahdi.platen.data.Note
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.Settings
import com.mahdi.platen.data.SettingsRepository
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repo: NoteRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val notes: StateFlow<List<Note>?> = repo.observeNotes().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    suspend fun create(initial: List<Glyph> = emptyList()): CreateResult =
        repo.create(studio = settingsRepo.settings.first().studio, initial = initial)

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
