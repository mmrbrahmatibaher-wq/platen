package com.mahdi.platen

import android.app.Application
import android.content.Context
import com.mahdi.platen.audio.SoundManager
import com.mahdi.platen.audio.SoundPlayer
import com.mahdi.platen.data.DataStoreSettingsRepository
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.PlatenDatabase
import com.mahdi.platen.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-rolled dependency container (keeps the build simple; no Hilt needed). */
open class AppContainer(
    val notes: NoteRepository,
    val settings: SettingsRepository,
    val sound: SoundPlayer,
    val appScope: CoroutineScope,
) {
    init {
        appScope.launch {
            settings.settings.map { it.sound to it.playInSilentMode }.distinctUntilChanged().collect { (on, silent) ->
                sound.configure(on, silent)
            }
        }
    }
}

interface ContainerOwner { val container: AppContainer }

fun Context.appContainer(): AppContainer = (applicationContext as ContainerOwner).container

class PlatenApp : Application(), ContainerOwner {
    override val container: AppContainer by lazy {
        AppContainer(
            notes = NoteRepository(PlatenDatabase.create(this).notes()),
            settings = DataStoreSettingsRepository(this),
            sound = SoundManager(this),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
