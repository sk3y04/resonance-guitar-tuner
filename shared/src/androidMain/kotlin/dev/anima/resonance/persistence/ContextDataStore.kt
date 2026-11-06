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
 * Android factory (compile-verified 2026-11-06 after the first real Android
 * build): builds the same file-backed [DataStore<Preferences>] the JVM
 * host builds (same factory API — datastore 1.3.0's File-based factory
 * lives in the jvmAndAndroid source set), rooted at the app's files dir.
 * The store's scope is IO-dispatched and app-lifetime (the repository is
 * created once by the ViewModel).
 */
fun createContextDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(Dispatchers.IO),
        produceFile = { File(context.applicationContext.filesDir, "resonance.preferences") },
    )
