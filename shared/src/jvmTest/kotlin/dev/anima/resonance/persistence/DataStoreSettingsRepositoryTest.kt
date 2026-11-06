package dev.anima.resonance.persistence

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.TunerMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host (JVM) tests for the REAL DataStore-backed repository: fresh-file
 * defaults, cross-instance round trip, corruption recovery, and the
 * read-side guard for out-of-range stored values. Real temp files under
 * build/tmp (gitignored) — no fakes.
 *
 * NOTE: the repository gets a REAL coroutine scope (Dispatchers.Default),
 * never a TestScope — DataStore keeps long-lived jobs in its scope and
 * runTest would wait for them forever.
 */
class DataStoreSettingsRepositoryTest {

    private fun testFile(): String {
        File("build/tmp").mkdirs()
        return "build/tmp/resonance-test-settings-${Random.nextLong().toULong().toString(16)}.preferences_pb"
    }

    private fun withRepo(
        fileName: String,
        keepFile: Boolean = false,
        block: suspend (DataStoreSettingsRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = createDataStoreSettingsRepository(scope, fileName)
        try {
            runBlocking { block(repo) }
        } finally {
            scope.cancel()
            if (!keepFile) File(fileName).delete()
        }
    }

    /**
     * DataStore 1.3.0 persists asynchronously after updateData returns; this
     * waits (bounded) for the expected value's bytes to actually hit disk —
     * a real durability check, not a sleep.
     */
    private fun waitForFileToContain(file: File, bytes: ByteArray, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        fun ByteArray.contains(seq: ByteArray): Boolean {
            if (seq.isEmpty() || size < seq.size) return false
            outer@ for (i in 0..size - seq.size) {
                for (j in seq.indices) {
                    if (this[i + j] != seq[j]) continue@outer
                }
                return true
            }
            return false
        }
        while (System.currentTimeMillis() < deadline) {
            if (file.exists() && file.readBytes().contains(bytes)) return
            Thread.sleep(25)
        }
        throw AssertionError("file never contained the expected persisted value (timeout ${timeoutMs}ms)")
    }

    @Test
    fun freshFileYieldsDefaults() {
        withRepo(testFile()) { repo ->
            assertEquals(Settings.DEFAULTS, repo.get())
        }
    }

    @Test
    fun roundTripPersistsAcrossInstances() {
        val file = testFile()
        try {
            withRepo(file, keepFile = true) { repo ->
                repo.update {
                    it.copy(
                        concertPitchHz = 442.0,
                        mode = TunerMode.INSTRUMENT,
                        favorites = listOf("standard"),
                        accidentalSpelling = Spelling.FLAT,
                    )
                }
                // 442000.0 mHz as an 8-byte little-endian double in the protobuf
                waitForFileToContain(File(file), byteArrayOf(0, 0, 0, 0, 64, (-6).toByte(), 26, 65))
            }
            withRepo(file) { repo ->
                val s = repo.get()
                assertEquals(442.0, s.concertPitchHz)
                assertEquals(TunerMode.INSTRUMENT, s.mode)
                assertEquals(listOf("standard"), s.favorites)
                assertEquals(Spelling.FLAT, s.accidentalSpelling)
            }
        } finally {
            File(file).delete()
        }
    }

    @Test
    fun corruptFileRecoversToDefaultsAndRemainsUsable() {
        val file = testFile()
        try {
            File("build/tmp").mkdirs()
            File(file).writeBytes(byteArrayOf(0x00, (-1).toByte(), 0x01, (-103).toByte(), 0x02))
            withRepo(file) { repo ->
                assertEquals(Settings.DEFAULTS, repo.get(), "corrupt file must recover to defaults")
                repo.update { it.copy(concertPitchHz = 438.0) }
                assertEquals(438.0, repo.get().concertPitchHz)
            }
        } finally {
            File(file).delete()
        }
    }

    @Test
    fun readSideGuardRejectsOutOfRangeConcertPitch() {
        // Same decode path used when reading the file: an out-of-bounds
        // stored concert pitch (e.g. hand-edited protobuf) must not leak
        // into Settings.
        val prefs = preferencesOf(
            doublePreferencesKey(DataStoreSettingsRepository.KEY_CONCERT_MHZ) to 999_000.0,
            stringPreferencesKey(DataStoreSettingsRepository.KEY_SELECTED_PRESET_ID) to "standard",
        )
        assertEquals(Settings.DEFAULTS, DataStoreSettingsRepository.decode(prefs))

        val valid = preferencesOf(
            doublePreferencesKey(DataStoreSettingsRepository.KEY_CONCERT_MHZ) to 442_000.0,
            stringPreferencesKey(DataStoreSettingsRepository.KEY_MODE) to "INSTRUMENT",
        )
        val decoded = DataStoreSettingsRepository.decode(valid)
        assertEquals(442.0, decoded.concertPitchHz)
        assertEquals(TunerMode.INSTRUMENT, decoded.mode)
        assertEquals(440.0, DataStoreSettingsRepository.decode(preferencesOf()).concertPitchHz)
    }
}
