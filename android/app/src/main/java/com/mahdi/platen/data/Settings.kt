package com.mahdi.platen.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mahdi.platen.domain.CorrectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class PaperTheme(val label: String) { CREAM("Cream"), MINT("Mint"), CHARCOAL("Charcoal"), GARDEN("Garden") }

data class Settings(
    val sound: Boolean = true,
    val playInSilentMode: Boolean = false,
    val haptics: Boolean = true,
    /** DELETE = "Backspace erases" (on by default since 3.2); STRIKE = typewriter step-back + overstrike. */
    val correction: CorrectionMode = CorrectionMode.DELETE,
    val autocorrect: Boolean = false,
    val theme: PaperTheme = PaperTheme.CREAM,
    val studio: Boolean = false,
    val lampOn: Boolean = false,
    val lampWarmth: Float = 0.6f,
    val lampIntensity: Float = 0.7f,
    val lampBeam: Float = 0.5f,
    /** Decorative enamel pins on the machine body. */
    val charms: Boolean = true,
) {
    val backspaceErases: Boolean get() = correction == CorrectionMode.DELETE
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

class InMemorySettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) = state.update(transform)
}

private val Context.platenPrefs: DataStore<Preferences> by preferencesDataStore(name = "platen_settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.platenPrefs

    private object K {
        val sound = booleanPreferencesKey("sound")
        val silent = booleanPreferencesKey("play_in_silent")
        val haptics = booleanPreferencesKey("haptics")
        // 3.2: new key so "Backspace erases" starts ON for everyone (3.1 stored STRIKE on every save).
        val correction = stringPreferencesKey("backspace_mode")
        val autocorrect = booleanPreferencesKey("autocorrect")
        val theme = stringPreferencesKey("theme")
        val studio = booleanPreferencesKey("studio")
        val lampOn = booleanPreferencesKey("lamp_on")
        val warmth = floatPreferencesKey("lamp_warmth")
        val intensity = floatPreferencesKey("lamp_intensity")
        val beam = floatPreferencesKey("lamp_beam")
        val charms = booleanPreferencesKey("charms")
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            sound = p[K.sound] ?: d.sound,
            playInSilentMode = p[K.silent] ?: d.playInSilentMode,
            haptics = p[K.haptics] ?: d.haptics,
            correction = p[K.correction]?.let { v -> CorrectionMode.entries.firstOrNull { it.name == v } } ?: d.correction,
            autocorrect = p[K.autocorrect] ?: d.autocorrect,
            theme = p[K.theme]?.let { v -> PaperTheme.entries.firstOrNull { it.name == v } } ?: d.theme,
            studio = p[K.studio] ?: d.studio,
            lampOn = p[K.lampOn] ?: d.lampOn,
            lampWarmth = p[K.warmth] ?: d.lampWarmth,
            lampIntensity = p[K.intensity] ?: d.lampIntensity,
            lampBeam = p[K.beam] ?: d.lampBeam,
            charms = p[K.charms] ?: d.charms,
        )
    }

    override val settings: Flow<Settings> = store.data.map(::read)

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val cur = read(p)
            val n = transform(cur)
            p[K.sound] = n.sound
            p[K.silent] = n.playInSilentMode
            p[K.haptics] = n.haptics
            p[K.correction] = n.correction.name
            p[K.autocorrect] = n.autocorrect
            p[K.theme] = n.theme.name
            p[K.studio] = n.studio
            p[K.lampOn] = n.lampOn
            p[K.warmth] = n.lampWarmth.coerceIn(0f, 1f)
            p[K.intensity] = n.lampIntensity.coerceIn(0f, 1f)
            p[K.beam] = n.lampBeam.coerceIn(0f, 1f)
            p[K.charms] = n.charms
        }
    }
}
