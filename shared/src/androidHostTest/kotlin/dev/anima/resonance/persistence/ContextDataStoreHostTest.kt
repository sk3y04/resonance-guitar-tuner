package dev.anima.resonance.persistence

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The ANDROID persistence path against a real (Robolectric)
 * Application context and a real file — behavior-verified on the JVM,
 * independent of any device. Proves createContextDataStore wires
 * the same durable store the rest of the app relies on: a FRESH
 * repository over the same context must read the value written earlier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextDataStoreHostTest {

    @org.junit.Before
    fun resetStoreSingleton() {
        // Robolectric apps (and filesDirs) are per-test — drop the
        // per-process singleton so each test sees its own files dir.
        resetContextDataStoreForTesting()
    }

    @Test
    fun contextBackedStoreRoundTripsThroughRealFile() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val repo = DataStoreSettingsRepository(createContextDataStore(app))
        assertEquals(Settings.DEFAULTS, repo.get())

        repo.update { it.copy(concertPitchHz = 432.0) }
        assertEquals(432.0, repo.get().concertPitchHz, 1e-9)
        assertTrue(
            "the value must be on DISK (resonance.preferences_pb)",
            File(app.filesDir, "resonance.preferences_pb").exists(),
        )

        // A second caller (e.g. a recreated ViewModel) must get the same
        // per-process singleton — DataStore 1.3.0 forbids two active
        // stores on one file — and read the persisted value.
        val fresh = DataStoreSettingsRepository(createContextDataStore(app))
        assertEquals(432.0, fresh.get().concertPitchHz, 1e-9)
    }
}
