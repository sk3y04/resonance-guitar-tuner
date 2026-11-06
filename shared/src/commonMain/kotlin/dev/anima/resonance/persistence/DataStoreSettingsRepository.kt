package dev.anima.resonance.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningPresetJson
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.flow.first

private val CONCERT_MHZ = doublePreferencesKey("concert_mhz")
private val MODE = stringPreferencesKey("mode")
private val SELECTED_PRESET = stringPreferencesKey("selected_preset_id")
private val SPELLING = stringPreferencesKey("spelling")
private val FAVORITES = stringPreferencesKey("favorites")
private val CUSTOMS = stringPreferencesKey("customs")

/**
 * Real persisted settings backed by androidx DataStore.
 *
 * The repository now lives in commonMain — its only platform
 * dependency is the INJECTED [DataStore] (the DataStore API itself is
 * KMP). Platform source sets provide the factory: JVM creates it from a
 * File, Android from a Context. The shared contract [SettingsRepository]
 * and [Settings] live here too.
 *
 * Recovery contract: a corrupt file yields [Settings.DEFAULTS] (the
 * factory installs a ReplaceFileCorruptionHandler); stored values outside
 * the product range are ignored on read and a valid update overwrites them.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    @Suppress("UnsafeOptInUsageError")
    override suspend fun get(): Settings = decode(dataStore.data.first())

    @Suppress("UnsafeOptInUsageError")
    override suspend fun update(transform: (Settings) -> Settings): Settings {
        val prefs = dataStore.data.first()
        val updated = transform(decode(prefs))
        // persist everything: a settings update must not clobber customs
        persist(updated, decodeCustoms(prefs[CUSTOMS].orEmpty()))
        return updated
    }

    /** Read user-created presets from the persisted JSON payload. */
    @Suppress("UnsafeOptInUsageError")
    suspend fun loadCustomPresets(): List<TuningPreset> =
        decodeCustoms(dataStore.data.first()[CUSTOMS].orEmpty())

    /** Replace the persisted customs (durable), preserving settings. */
    @Suppress("UnsafeOptInUsageError")
    suspend fun saveCustomPresets(customs: List<TuningPreset>): List<TuningPreset> {
        val prefs = dataStore.data.first()
        persist(decode(prefs), customs)
        return customs
    }

    /** Single durable write of settings + customs (updateData = persisted). */
    @Suppress("UnsafeOptInUsageError")
    private suspend fun persist(settings: Settings, customs: List<TuningPreset>) {
        dataStore.updateData { _ ->
            val m = mutablePreferencesOf()
            m.set(CONCERT_MHZ, settings.concertPitchHz * 1000.0)
            m.set(MODE, settings.mode.name)
            m.set(SELECTED_PRESET, settings.selectedPresetId)
            m.set(SPELLING, settings.accidentalSpelling.name)
            m.set(FAVORITES, settings.favorites.joinToString(","))
            m.set(CUSTOMS, TuningPresetJson.encode(customs))
            m
        }
    }

    companion object {
        const val KEY_CONCERT_MHZ = "concert_mhz"
        const val KEY_MODE = "mode"
        const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
        const val KEY_CUSTOMS = "customs"

        /**
         * Decode the persisted customs JSON. Any malformed payload
         * (corruption, tampering, future format) recovers to empty — the app
         * must never crash on its own storage.
         */
        internal fun decodeCustoms(payload: String): List<TuningPreset> {
            if (payload.isBlank()) return emptyList()
            return try {
                TuningPresetJson.decode(payload)
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
        }

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
