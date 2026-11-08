package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import dev.anima.resonance.music.TuningPresetJson
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fuzz/property tests for the engine's CRUD + settings surface
 * ([AppTuner]). Random op sequences must preserve the invariants:
 *  - preset list: unique IDs, built-ins intact and immutable, favorites
 *    reference live IDs;
 *  - settings: concert pitch always inside 400..480 after any op;
 *  - failed imports mutate nothing and report an error;
 *  - the engine survives: a tone still locks after any op storm.
 */
class FuzzCrudTest {

    private class ToneInput(private val hz: Double, override val sampleRate: Int = 48000) : AudioInput {
        private var phase = 0.0
        override fun open(): Boolean = true
        override fun readChunk(): FloatArray {
            val out = FloatArray(1024)
            repeat(1024) {
                out[it] = (0.3 * sin(phase)).toFloat()
                phase += 2.0 * PI * hz / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            return out
        }
        override fun close() {}
    }

    private val rng = Random(0xC0FFEE01L)
    private val letters = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val builtInIds = BuiltInTunings.all.map { it.id }.toSet()

    private fun randomNotes(n: Int): List<Note> =
        List(n) { Note.fromLabel("${letters[rng.nextInt(letters.size)]}${2 + rng.nextInt(4)}") }

    private fun assertEngineInvariants(engine: AppTuner, repo: InMemorySettingsRepository) {
        val view = engine.presetsView
        val ids = view.map { it.id }
        assertEquals("preset IDs must be unique", ids.toSet().size, ids.size)
        // Built-ins: all present, in their original order, all flagged.
        val builtIns = BuiltInTunings.all
        assertEquals(builtIns.size, view.take(builtIns.size).filter { it.builtIn }.size)
        view.forEachIndexed { i, p ->
            if (i < builtIns.size) {
                assertEquals("built-in order/identity", builtIns[i].id, p.id)
            } else {
                assertFalse("custom must not be builtIn: ${p.id}", p.builtIn)
            }
        }
        assertTrue("concert pitch in 400..480: ${engine.currentSettings.concertPitchHz}",
            engine.currentSettings.concertPitchHz in 400.0..480.0)
        assertTrue("selected preset still resolvable", view.any { it.id == engine.currentSettings.selectedPresetId })
        // Persistence hook: repo snapshot == engine customs (stable IDs).
        assertEquals("repo customs mirror engine customs",
            repo.customsSnapshot.map { it.id }, view.filter { !it.builtIn }.map { it.id })
    }

    @Test
    fun randomOpStormPreservesInvariants() = runBlocking {
        val input = ToneInput(Note("E", 2).frequency(440.0))
        val repo = InMemorySettingsRepository()
        val engine = AppTuner(input, repo, PresetStore(), persistCustoms = { repo.saveCustomPresets(it) })
        engine.start()

        var customs = emptyList<TuningPreset>()
        for (i in 0 until 600) {
            when (val op = rng.nextInt(10)) {
                0 -> engine.setConcertPitch(400.0 + rng.nextDouble() * 80.0)
                1 -> engine.stepConcertPitch(rng.nextBoolean())
                2 -> engine.setMode(if (rng.nextBoolean()) TunerMode.CHROMATIC else TunerMode.INSTRUMENT)
                3 -> engine.setPreset(engine.presetsView[rng.nextInt(engine.presetsView.size)].id)
                4 -> {
                    engine.selectString(rng.nextInt(7))
                }
                5 -> {
                    val name = if (rng.nextInt(20) == 0) "   " else "p" + rng.nextLong(1_000_000).toString(36)
                    try {
                        val created = engine.createCustomPreset(name, randomNotes(1 + rng.nextInt(6)))
                        customs += created
                    } catch (e: IllegalArgumentException) {
                        // blank names must be rejected, not crash
                        assertTrue("blank name must be rejected", name.isBlank())
                    }
                }
                6 -> {
                    val id = if (customs.isNotEmpty() && rng.nextBoolean())
                        customs[rng.nextInt(customs.size)].id
                    else engine.presetsView[rng.nextInt(engine.presetsView.size)].id
                    try {
                        val edited = engine.editCustomPreset(id, "edit", randomNotes(1 + rng.nextInt(6)))
                        customs = customs.map { if (it.id == edited.id) edited else it }
                    } catch (e: IllegalArgumentException) {
                        assertTrue("edit IAE only for built-ins or deleted customs (got $id)",
                            id in builtInIds || customs.none { it.id == id })
                    }
                }
                7 -> {
                    val id = if (customs.isNotEmpty() && rng.nextBoolean())
                        customs[rng.nextInt(customs.size)].id
                    else engine.presetsView[rng.nextInt(engine.presetsView.size)].id
                    val deleted = engine.deleteCustomPreset(id)
                    if (deleted) {
                        customs = customs.filter { it.id != id }
                    } else {
                        // A failed delete is expected for built-ins and for
                        // customs already removed earlier in the storm.
                        assertTrue("no-delete only for built-ins or already-deleted customs ($id)",
                            id in builtInIds || customs.none { it.id == id })
                    }
                }
                8 -> {
                    val id = engine.presetsView[rng.nextInt(engine.presetsView.size)].id
                    try {
                        engine.toggleFavorite(id)
                    } catch (e: IllegalArgumentException) {
                        throw AssertionError("toggleFavorite(valid id) must not throw", e)
                    }
                }
                9 -> {
                    val payload = when {
                        rng.nextInt(3) == 0 -> randomImportPayload()
                        else -> ByteArray(1 + rng.nextInt(2047)).let { arr -> rng.nextBytes(arr); String(arr) }
                    }
                    val result = engine.importCustomPresets(payload) // never throws
                    if (result.error != null) {
                        assertEquals("failed import must import nothing", 0, result.importedCount)
                    }
                    assertEngineInvariants(engine, repo)
                }
            }
            if (i % 37 == 0) assertEngineInvariants(engine, repo)
        }
        engine.stop()
    }

    private fun randomImportPayload(): String {
        val presets = (0 until 1 + rng.nextInt(4)).map { i ->
            TuningPreset(id = "imp-${rng.nextLong(1_000_000).toString(36)}", name = "imp$i",
                builtIn = false,
                strings = randomNotes(1 + rng.nextInt(4)).mapIndexed { j, n -> TuningString("s${j + 1}", n) })
        }
        return TuningPresetJson.encode(presets)
    }

    @Test
    fun garbageImportMutatesNothingAndReportsError() = runBlocking {
        val input = ToneInput(Note("E", 2).frequency(440.0))
        val repo = InMemorySettingsRepository()
        val engine = AppTuner(input, repo, PresetStore(), persistCustoms = { repo.saveCustomPresets(it) })
        engine.start()
        val before = engine.presetsView.map { it.id }
        val garbage = listOf(
            "not json at all",
            """{"resonanceVersion":1,"tunings":[{"id":"a","name":"a","strings":[]}]}""",
            """{"resonanceVersion":99,"tunings":[]}""",
            "x".repeat(TuningPresetJson.MAX_PAYLOAD_BYTES + 1),
            "",
        )
        garbage.forEach { payload ->
            val result = engine.importCustomPresets(payload)
            assertNotNull("garbage import must report a reason: ${payload.take(40)}", result.error)
            assertEquals(0, result.importedCount)
            assertEquals("failed import must not mutate state", before, engine.presetsView.map { it.id })
        }
        engine.stop()
    }

    @Test
    fun engineSurvivesAnOpStormAndStillLocks() = runBlocking {
        val input = ToneInput(Note("E", 2).frequency(440.0))
        val repo = InMemorySettingsRepository()
        val engine = AppTuner(input, repo, PresetStore())
        engine.start()
        repeat(300) { i ->
            engine.setConcertPitch(400.0 + (i % 81) * 1.0) // full documented range sweep
            engine.setPreset(engine.presetsView[i % engine.presetsView.size].id)
            engine.setMode(if (i % 2 == 0) TunerMode.CHROMATIC else TunerMode.INSTRUMENT)
        }
        engine.stop()
        engine.setConcertPitch(440.0) // storm left the reference somewhere in 400..480
        engine.setMode(TunerMode.CHROMATIC) // storm may have left an instrument preset locked
        engine.start()
        var t = 0L
        var last: dev.anima.resonance.presentation.TunerSnapshot? = null
        repeat(40) {
            t += 21
            engine.poll(t)
            last = engine.lastSnapshot
        }
        assertNotNull("engine must produce frames after the storm", last)
        assertEquals("E2 must lock after the storm", Note.fromLabel("E2"), last!!.reading.note)
        assertTrue(last!!.reading.inTune)
        engine.stop()
    }
}
