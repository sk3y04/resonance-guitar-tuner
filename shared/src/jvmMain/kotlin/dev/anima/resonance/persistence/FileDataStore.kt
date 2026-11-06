package dev.anima.resonance.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * JVM factory: builds the DataStore from a File. Android builds the
 * same [DataStore<Preferences>] from a Context (shared/src/androidMain) and
 * injects it into the common [DataStoreSettingsRepository].
 */
fun createFileDataStore(
    scope: CoroutineScope,
    fileName: String,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = { File(fileName) },
)

/** Convenience: repository over a file-backed store (JVM host, tests). */
fun createDataStoreSettingsRepository(
    scope: CoroutineScope,
    fileName: String,
): DataStoreSettingsRepository =
    DataStoreSettingsRepository(createFileDataStore(scope, fileName))
