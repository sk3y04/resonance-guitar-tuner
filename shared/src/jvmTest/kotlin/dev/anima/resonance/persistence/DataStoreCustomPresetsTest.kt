package dev.anima.resonance.persistence

import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPresetJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host test: custom presets persisted through the REAL DataStore file as
 * the versioned JSON payload (TuningPresetJson). Round trip across
 * instances, replacement semantics, and malformed-payload recovery.
 */
class DataStoreCustomPresetsTest {

    private fun testFile(): String {
        File("build/tmp").mkdirs()
        return "build/tmp/resonance-test-customs-${Random.nextLong().toULong().toString(16)}.preferences_pb"
    }

    private fun withRepo(
        fileName: String,
        keepFile: Boolean = false,
        block: suspend (DataStoreSettingsRepository) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = DataStoreSettingsRepository(scope, fileName)
        try {
            runBlocking { block(repo) }
        } finally {
            scope.cancel()
            if (!keepFile) File(fileName).delete()
        }
    }

    private fun custom(id: String, name: String, vararg labels: String): TuningPreset =
        TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l -> TuningString("$id-s${i + 1}", Note.fromLabel(l)) },
        )

    @Test
    fun freshStoreHasNoCustomPresets() {
        withRepo(testFile()) { repo ->
            assertTrue(repo.loadCustomPresets().isEmpty())
        }
    }

    @Test
    fun customPresetsRoundTripAcrossInstances() {
        val file = testFile()
        try {
            val c1 = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
            val c2 = custom("c2", "Half Down", "Eb2", "Ab2", "Db3", "Gb3", "Bb3", "Eb4")
            withRepo(file, keepFile = true) { repo ->
                repo.saveCustomPresets(listOf(c1, c2))
            }
            withRepo(file) { repo ->
                val loaded = repo.loadCustomPresets()
                assertEquals(listOf(c1, c2), loaded, "deep equality incl. string ids/notes")
            }
        } finally {
            File(file).delete()
        }
    }

    @Test
    fun saveReplacesPreviousAndSurvivesSettingsUpdate() {
        val file = testFile()
        try {
            val c1 = custom("c1", "One", "D2", "A2", "D3", "G3", "B3", "E4")
            val c2 = custom("c2", "Two", "D2", "G2", "D3", "G3", "B3", "D4")
            withRepo(file, keepFile = true) { repo ->
                repo.saveCustomPresets(listOf(c1, c2))
            }
            withRepo(file, keepFile = true) { repo ->
                repo.saveCustomPresets(listOf(c1))
                // a settings update must not clobber the customs payload
                repo.update { it.copy(concertPitchHz = 443.0) }
                assertEquals(listOf(c1), repo.loadCustomPresets())
            }
            withRepo(file) { repo ->
                assertEquals(listOf(c1), repo.loadCustomPresets())
                assertEquals(443.0, repo.get().concertPitchHz)
            }
        } finally {
            File(file).delete()
        }
    }

    @Test
    fun malformedCustomsPayloadRecoversToEmpty() {
        // decode path is shared with file reads — corrupt payload must not
        // take the app down.
        assertEquals(emptyList<TuningPreset>(), DataStoreSettingsRepository.decodeCustoms("not json at all"))
        assertEquals(emptyList<TuningPreset>(), DataStoreSettingsRepository.decodeCustoms(""))
        val c1 = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val payload = TuningPresetJson.encode(listOf(c1))
        assertEquals(listOf(c1), DataStoreSettingsRepository.decodeCustoms(payload))
    }
}
