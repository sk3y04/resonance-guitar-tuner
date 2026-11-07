package dev.anima.resonance.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anima.resonance.persistence.DataStoreSettingsRepository
import dev.anima.resonance.persistence.createContextDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android persistence path on a REAL device (real Context,
 * real file system, real IO scheduler). Complements the Robolectric host
 * test with the actual framework — no shadows.
 *
 * Note: this writes to the app's real preferences file (concert pitch
 * 432.0 — a valid in-range value) on the TEST device; harmless for a
 * debug build used only for verification.
 */
@RunWith(AndroidJUnit4::class)
class DevicePersistenceTest {

    @Test
    fun realContextStoreRoundTrips() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = DataStoreSettingsRepository(createContextDataStore(ctx))

        repo.update { it.copy(concertPitchHz = 432.0) }
        assertEquals(432.0, repo.get().concertPitchHz, 1e-9)

        // Second caller in the same process: the per-process singleton
        // must hand back the same store and the persisted value.
        val fresh = DataStoreSettingsRepository(createContextDataStore(ctx))
        assertEquals(432.0, fresh.get().concertPitchHz, 1e-9)
    }
}
