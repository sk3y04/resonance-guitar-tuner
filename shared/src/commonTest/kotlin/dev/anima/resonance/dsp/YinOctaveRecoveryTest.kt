package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Octave-bleed recovery. A played note can be accompanied by a real
 * component one octave below (sympathetic string, body resonance, or a
 * strong subharmonic). The detector must not lose the note entirely when the
 * lower component is strong enough to win the first-crossing search but too
 * weak to be a plausible fundamental.
 */
class YinOctaveRecoveryTest {

    private val sr = 48_000
    private val n = 4096
    private val yin = Yin()

    private fun tone(freq: Double, amplitude: Double): FloatArray =
        FloatArray(n) { (amplitude * sin(2.0 * PI * freq * it / sr)).toFloat() }

    private fun mix(vararg parts: FloatArray): FloatArray =
        FloatArray(n) { i -> parts.sumOf { it[i].toDouble() }.toFloat() }

    @Test
    fun subharmonicAtMinus10DbRecoversTheUpperNote() {
        // 392 Hz (G4) with a 196 Hz (G3) component at -10 dB: the first
        // crossing is the lower octave, but its 2nd harmonic is 10x stronger,
        // so it is not a plausible fundamental. The true note is 392.
        val frame = mix(
            tone(392.0, 0.5),
            tone(196.0, 0.5 * 10.0.pow(-10.0 / 20.0)),
        )
        val e = yin.detect(frame, sr)
        assertEquals(392.0, e.frequencyHz, 3.0, "must recover the upper octave, not lose the note")
        assertTrue(e.confidence > 0.5, "recovered note must be confident, got ${e.confidence}")
    }

    @Test
    fun strongSecondHarmonicDoesNotFlipATrueLowNoteUp() {
        // The physically common inverse: 196 Hz fundamental with its 392 Hz
        // 2nd harmonic 10 dB down. This is a real G3 and must stay G3.
        val frame = mix(
            tone(196.0, 0.5),
            tone(392.0, 0.5 * 10.0.pow(-10.0 / 20.0)),
        )
        val e = yin.detect(frame, sr)
        assertEquals(196.0, e.frequencyHz, 3.0, "a real low note must not be flipped up")
    }

    @Test
    fun noiseDoesNotBecomeAConfidentOctaveRecovery() {
        repeat(20) { seed ->
            val rng = Random(seed.toLong())
            val frame = FloatArray(n) { ((rng.nextFloat() - 0.5f) * 0.4f) }
            val e = yin.detect(frame, sr)
            assertTrue(
                e.frequencyHz == 0.0 || e.confidence < 0.5,
                "noise seed $seed became a confident pitch ${e.frequencyHz} Hz (conf ${e.confidence})",
            )
        }
    }
}