package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.audio.CaptureState
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerStatus
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end integration of the deterministic pieces — capture
 * orchestrator, streaming analyzer, state machine, presets, settings —
 * behind one engine API. Fake audio + virtual clock: no threads, no sleep.
 */
class AppTunerTest {

    /** Deterministic mono sine source; [dropNext] chunks return null (device loss). */
    private class ToneInput(
        private val hz: Double,
        override val sampleRate: Int = 48000,
        private val chunkSize: Int = 1024,
    ) : AudioInput {
        var opens = 0
        var closes = 0
        var openOk = true
        var dropNext = 0
        private var phase = 0.0

        override fun open(): Boolean {
            opens++
            return openOk
        }

        override fun readChunk(): FloatArray? {
            if (dropNext > 0) {
                dropNext--
                return null
            }
            val out = FloatArray(chunkSize)
            repeat(chunkSize) {
                out[it] = (0.3 * sin(phase)).toFloat()
                phase += 2.0 * PI * hz / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            return out
        }

        override fun close() {
            closes++
        }
    }

    private fun freq(note: String, octave: Int, concert: Double = 440.0): Double =
        Note(note, octave).frequency(concert)

    private fun pump(engine: AppTuner, frames: Int, fromMs: Long = 0, stepMs: Long = 21): Long {
        var t = fromMs
        repeat(frames) {
            t += stepMs
            engine.poll(t)
        }
        return t
    }

    private fun harness(
        input: AudioInput,
        initial: Settings = Settings.DEFAULTS,
    ): Pair<AppTuner, InMemorySettingsRepository> {
        val repo = InMemorySettingsRepository(initial)
        val engine = AppTuner(input, repo, PresetStore())
        return engine to repo
    }

    @Test
    fun startAppliesDefaultsAndMeasuresTone() = runBlocking {
        val (engine, repo) = harness(ToneInput(freq("E", 2)))
        val settings = engine.start()
        assertEquals(Settings.DEFAULTS, settings)
        assertEquals("standard", repo.get().selectedPresetId)

        pump(engine, 12)
        val snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(TunerStatus.MEASURED, snap.status)
        assertEquals(Note("E", 2), snap.reading.note)
        assertTrue(snap.reading.inTune, "exact sine must be in tune, cents=${snap.reading.cents}")
    }

    @Test
    fun stopHaltsDeliveryAndResetsReading() = runBlocking {
        val input = ToneInput(freq("E", 2))
        val (engine, _) = harness(input)
        engine.start()
        pump(engine, 8)
        assertNotNull(engine.lastSnapshot)

        engine.stop()
        assertNull(engine.lastSnapshot, "stop must clear the reading (screen shows idle)")
        pump(engine, 5)
        assertNull(engine.lastSnapshot, "no chunks may flow after stop")
        assertTrue(input.closes >= 1)
    }

    @Test
    fun instrumentModeAutoSelectsNearestString() = runBlocking {
        // Spec §2: instrument mode AUTO-FOLLOWS the nearest preset string.
        // A G3 tone under Standard must read G3 in tune (no lock active).
        val (engine, _) = harness(
            ToneInput(freq("G", 3)),
            Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT, selectedPresetId = "standard"),
        )
        engine.start()
        pump(engine, 12)
        val snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(TunerStatus.MEASURED, snap.status)
        assertEquals(Note("G", 3), snap.reading.note, "auto mode must follow the played string")
        assertTrue(snap.reading.inTune)
    }

    @Test
    fun selectStringLocksThenTogglesBackToAuto() = runBlocking {
        val (engine, _) = harness(
            ToneInput(freq("G", 3)),
            Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT, selectedPresetId = "standard"),
        )
        engine.start()
        pump(engine, 8)
        // Manual lock on the 6th string (E2): the target moves, not the measurement.
        engine.selectString(0)
        pump(engine, 8)
        var snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(Note("E", 2), snap.reading.note, "locked target must be E2")
        assertTrue(snap.reading.cents > 800, "G3 vs E2 lock, got ${snap.reading.cents}")
        // Tapping the same string again releases the lock → back to auto.
        engine.selectString(0)
        pump(engine, 8)
        snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(Note("G", 3), snap.reading.note, "unlock must return to auto selection")
        assertTrue(snap.reading.inTune)
    }

    @Test
    fun chromaticModeMeasuresFreeNote() = runBlocking {
        val (engine, _) = harness(
            ToneInput(freq("G", 3)),
            Settings.DEFAULTS.copy(mode = TunerMode.CHROMATIC, selectedPresetId = "standard"),
        )
        engine.start()
        pump(engine, 12)
        val snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(Note("G", 3), snap.reading.note, "chromatic mode must match freely")
        assertTrue(snap.reading.inTune)
    }

    @Test
    fun setPresetPersistsAndRetargets() = runBlocking {
        val (engine, repo) = harness(
            ToneInput(freq("D", 2)),
            Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT),
        )
        engine.start()
        val updated = engine.setPreset("drop-d")
        assertEquals("drop-d", updated.selectedPresetId)
        assertEquals("drop-d", repo.get().selectedPresetId, "selection must persist")
        // Drop D's low string is D2 — the tone now plays in tune.
        pump(engine, 12)
        val snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(Note("D", 2), snap.reading.note)
        assertTrue(snap.reading.inTune, "D2 tone under Drop D must be in tune, cents=${snap.reading.cents}")
    }

    @Test
    fun setConcertPitchPersistsAndRetunes() = runBlocking {
        val (engine, repo) = harness(ToneInput(432.0))
        engine.start()
        val updated = engine.setConcertPitch(432.0)
        assertEquals(432.0, updated.concertPitchHz)
        assertEquals(432.0, repo.get().concertPitchHz, "concert pitch must persist")
        pump(engine, 12)
        val snap = engine.lastSnapshot
        assertNotNull(snap)
        assertEquals(Note("A", 4), snap.reading.note)
        assertTrue(
            snap.reading.cents in -1..1 && snap.reading.inTune,
            "432 Hz A4 under A4=432 must read in tune, cents=${snap.reading.cents}",
        )
    }

    @Test
    fun captureFailureStopsEngineAfterBudget() = runBlocking {
        val input = ToneInput(freq("E", 2))
        input.openOk = true
        input.dropNext = 3 // three consecutive device losses
        val seen = mutableListOf<CaptureState>()
        val repo = InMemorySettingsRepository()
        val engine = AppTuner(input, repo, PresetStore(), onCaptureState = { seen.add(it) })
        engine.start()
        assertEquals(CaptureState.CAPTURING, engine.captureState)
        pump(engine, 4)
        assertEquals(CaptureState.ERROR, engine.captureState)
        assertTrue(seen.contains(CaptureState.RECOVERING), "transient state must be reported")
        engine.stop() // must be safe from ERROR
        assertEquals(CaptureState.IDLE, engine.captureState)
    }
}
