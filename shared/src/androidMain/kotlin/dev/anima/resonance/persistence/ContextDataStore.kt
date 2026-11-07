package dev.anima.resonance.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Android factory (behavior-verified 2026-11-07 via the Robolectric
 * host suite): builds the same file-backed [DataStore<Preferences>]
 * the JVM host builds (same factory API — datastore 1.3.0's File-based
 * factory lives in the jvmAndAndroid source set), rooted at the app's
 * files dir.
 *
 * Fix: DataStore 1.3.0 enforces ONE active store per file per
 * process — calling the factory twice (e.g. a recreated ViewModel) threw
 * "multiple DataStores active for the same file". The store is therefore
 * a per-process singleton (its scope is IO-dispatched and app-lifetime).
 */
private val androidStoreLock = Any()
private var androidStore: DataStore<Preferences>? = null

fun createContextDataStore(context: Context): DataStore<Preferences> =
    synchronized(androidStoreLock) {
        androidStore ?: PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO),
            produceFile = {
                File(context.applicationContext.filesDir, "resonance.preferences_pb")
            },
        ).also { androidStore = it }
    }

/**
 * Test hook: drops the per-process singleton. Robolectric creates a fresh
 * Application (and filesDir) per test method, so host tests reset between
 * methods; production code never calls this.
 */
internal fun resetContextDataStoreForTesting() {
    synchronized(androidStoreLock) { androidStore = null }
}
