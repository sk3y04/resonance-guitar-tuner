package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Synthetic-signal acceptance matrix for the YIN detector.
 * Deterministic: fixed sample rate, fixed windows, fixed seeds. No sleeps.
 */
class YinTest {

    private val sr = 48_000
    private val win = 2048

    private fun sine(freq: Double, phase: Double = 0.0, amplitude: Double = 0.5): FloatArray {
        val out = FloatArray(win)
        for (i in out.indices) {
            out[i] = (amplitude * sin(2.0 * PI * freq * i / sr + phase)).toFloat()
        }
        return out
    }

    private fun sawtooth(freq: Double): FloatArray {
        val out = FloatArray(win)
        val period = sr / freq
        for (i in out.indices) {
            out[i] = (2.0 * ((i % period) / period) - 1.0).toFloat() * 0.5f
        }
        return out
    }

    @Test
    fun detects440Hzi() {
        val e = Yin.default().detect(sine(440.0), sr)
        assertEquals(440.0, e.frequencyHz, 0.5)
        assertTrue(e.confidence > 0.7, "confidence=${e.confidence}")
    }

    @Test
    fun detectsLowE2() {
        val e = Yin.default().detect(sine(82.4069), sr)
        assertEquals(82.4069, e.frequencyHz, 0.4)
        assertTrue(e.confidence > 0.7, "confidence=${e.confidence}")
    }

    @Test
    fun detectsHighEndOfBand() {
        val e = Yin.default().detect(sine(1174.66), sr)
        assertEquals(1174.66, e.frequencyHz, 2.0)
        assertTrue(e.confidence > 0.5, "confidence=${e.confidence}")
    }

    @Test
    fun noOctaveErrorOnPureTone() {
        val e = Yin.default().detect(sine(440.0), sr)
        // Must land on 440, not 220 (octave down) or 880 (octave up).
        assertTrue(absDiff(e.frequencyHz, 440.0) < 0.5, "got ${e.frequencyHz}")
    }

    @Test
    fun sawtoothResolvesFundamental() {
        val e = Yin.default().detect(sawtooth(220.0), sr)
        assertEquals(220.0, e.frequencyHz, 1.5)
    }

    @Test
    fun silenceYieldsNoPitch() {
        val e = Yin.default().detect(FloatArray(win), sr)
        assertEquals(0.0, e.frequencyHz, 0.0)
        assertEquals(0.0, e.confidence, 1e-9)
    }

    @Test
    fun noiseYieldsNoConfidentPitch() {
        // Deterministic LCG — fixed seed, no platform randomness.
        var seed = 0x1234_5678L
        fun next(): Double {
            seed = (seed * 6364_1362_2384_679_300L + 1_442_695_040_888_963_407L)
            return ((seed ushr 32).toDouble() / Double.MAX_VALUE) * 2.0 - 1.0
        }
        val noise = FloatArray(win) { next().toFloat() * 0.3f }
        val e = Yin.default().detect(noise, sr)
        assertTrue(e.confidence < 0.3, "noise confidence=${e.confidence}")
    }

    @Test
    fun outOfBandToneIsNotReported() {
        // 3000 Hz is far above the 55..1200 Hz band: expect "no pitch",
        // never a spurious in-band harmonic answer with high confidence.
        val e = Yin.default().detect(sine(3000.0), sr)
        assertTrue(e.frequencyHz == 0.0 || e.confidence < 0.3,
            "out-of-band: freq=${e.frequencyHz} conf=${e.confidence}")
    }

    @Test
    fun callerBufferIsNotMutated() {
        val frame = sine(440.0)
        val before = frame.copyOf()
        Yin.default().detect(frame, sr)
        assertTrue(before.contentEquals(frame), "YIN must not mutate its input window")
    }

    @Test
    fun windowTooSmallIsRejected() {
        val small = FloatArray(20)
        assertFailsWith<IllegalArgumentException> { Yin.default().detect(small, sr) }
    }

    @Test
    fun sensitivityKnobChangesThreshold() {
        // A very strict threshold rejects even a clean tone; a loose one keeps it.
        val frame = sine(440.0)
        val strict = Yin(absoluteThreshold = 0.001).detect(frame, sr)
        val loose = Yin(absoluteThreshold = 0.5).detect(frame, sr)
        assertTrue(strict.confidence <= loose.confidence || strict.frequencyHz == 0.0,
            "strict=${strict} loose=${loose}")
        assertTrue(loose.frequencyHz in 435.0..445.0, "loose should still find 440: ${loose.frequencyHz}")
    }

    private fun absDiff(a: Double, b: Double): Double = if (a >= b) a - b else b - a
}
