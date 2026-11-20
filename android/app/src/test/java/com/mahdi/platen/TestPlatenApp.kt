package com.mahdi.platen

import android.app.Application
import com.mahdi.platen.audio.RecordingSoundPlayer
import com.mahdi.platen.data.InMemorySettingsRepository
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.PlatenDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application used by Robolectric UI tests: in-memory Room, in-memory settings, recorded sounds. */
class TestPlatenApp : Application(), ContainerOwner {
    val db: PlatenDatabase by lazy { PlatenDatabase.inMemory(this) }
    val settingsRepo = InMemorySettingsRepository()
    val sound = RecordingSoundPlayer()
    override val container: AppContainer by lazy {
        AppContainer(NoteRepository(db.notes()), settingsRepo, sound, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }
}
