package dev.anima.resonance.persistence

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.TunerMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

private val CONCERT_MHZ = doublePreferencesKey("concert_mhz")
private val MODE = stringPreferencesKey("mode")
private val SELECTED_PRESET = stringPreferencesKey("selected_preset_id")
private val SPELLING = stringPreferencesKey("spelling")
private val FAVORITES = stringPreferencesKey("favorites")

/**
 * Real persisted settings backed by androidx DataStore (JVM host).
 * The Android twin (same API surface — DataStore's File-based factory is
 * shared by the jvmAndAndroid actuals) lands when the Android SDK is available; the shared
 * contract [SettingsRepository] and [Settings] live in commonMain.
 *
 * Recovery contract: a corrupt file yields [Settings.DEFAULTS] (via
 * ReplaceFileCorruptionHandler); stored values outside the product range are
 * ignored on read and a valid update overwrites them.
 */
class DataStoreSettingsRepository(
    scope: CoroutineScope,
    /** Relative or absolute path of the preferences file. */
    fileName: String,
) : SettingsRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { File(fileName) },
    )

    @Suppress("UnsafeOptInUsageError")
    override suspend fun get(): Settings = decode(dataStore.data.first())

    @Suppress("UnsafeOptInUsageError")
    override suspend fun update(transform: (Settings) -> Settings): Settings {
        val updated = transform(decode(dataStore.data.first()))
        // updateData (not edit): suspends until the write is persisted, so
        // callers never observe a half-flushed file.
        dataStore.updateData { _ ->
            val m = mutablePreferencesOf()
            m.set(CONCERT_MHZ, updated.concertPitchHz * 1000.0)
            m.set(MODE, updated.mode.name)
            m.set(SELECTED_PRESET, updated.selectedPresetId)
            m.set(SPELLING, updated.accidentalSpelling.name)
            m.set(FAVORITES, updated.favorites.joinToString(","))
            m
        }
        return updated
    }

    companion object {
        const val KEY_CONCERT_MHZ = "concert_mhz"
        const val KEY_MODE = "mode"
        const val KEY_SELECTED_PRESET_ID = "selected_preset_id"

        /** Read-side guard: same path used for every file read. */
        internal fun decode(prefs: Preferences): Settings {
            val mhz = prefs[CONCERT_MHZ]
            val concert = if (mhz != null && mhz / 1000.0 in 400.0..480.0) mhz / 1000.0 else 440.0
            val mode = TunerMode.entries.firstOrNull { it.name == prefs[MODE] } ?: TunerMode.CHROMATIC
            val spelling = Spelling.entries.firstOrNull { it.name == prefs[SPELLING] } ?: Spelling.NATURAL
            val favorites =
                prefs[FAVORITES]?.takeIf { it.isNotBlank() }?.split(",")?.filter { it.isNotBlank() }
                    ?: emptyList()
            return Settings(
                concertPitchHz = concert,
                mode = mode,
                selectedPresetId = prefs[SELECTED_PRESET]?.takeIf { it.isNotBlank() } ?: "standard",
                favorites = favorites,
                accidentalSpelling = spelling,
            )
        }
    }
}
