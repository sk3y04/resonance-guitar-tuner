package dev.anima.resonance.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.createDataStore

/**
 * Android factory: builds the same [DataStore<Preferences>] the JVM
 * host builds from a File, but from a Context (app-scoped, IO dispatch).
 * Injected into the common [DataStoreSettingsRepository].
 *
 * Status: WRITTEN — COMPILE VERIFICATION BLOCKED (SDK platform /
 * licenses not accepted → android target auto-disabled).
 */
fun createContextDataStore(context: Context): DataStore<Preferences> =
    context.createDataStore(fileName = "resonance.preferences")
