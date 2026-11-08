package dev.anima.resonance.dsp

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Defect fix: under broadband noise, YIN's "first lag below threshold"
 * rule can latch a SHALLOW noise dip that appears before the true-period
 * dip. Measured (55 Hz sine + 10 dB noise, seed 33): false dip at lag 741
 * (d'=0.145) beat the true dip at lag ~796 (d'=0.069) → 59.5 Hz reported
 * with confidence 0.85 — a confidently wrong note on the guitar's low E.
 * The fix reselects a substantially deeper global in-band dip; these tests
 * pin both the fix and its octave regression risk.
 */
class YinNoiseRobustnessTest {

    private companion object {
        const val SR = 44100
        const val FRAME = 2048
    }

    private fun sineTail(f0: Double, seconds: Double): FloatArray {
        val n = (seconds * SR).toInt()
        val full = FloatArray(n) { 0.3f * sin(2.0 * Math.PI * f0 * it / SR).toFloat() }
        return full.copyOfRange(full.size - FRAME, full.size)
    }

    private fun noisy55Hz(seed: Long): FloatArray {
        val sig = sineTail(55.0, 1.5)
        val rms = sqrt(sig.map { it.toDouble() * it }.average())
        val rng = Random(seed)
        val noise = { rng.nextDouble() + rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 2.0 }
        return FloatArray(sig.size) {
            (sig[it] + noise() * 1.732 * rms / sqrt(10.0)).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun detect(frame: FloatArray): PitchEstimate =
        Yin().detect(Conditioning.removeDc(frame), SR)

    @Test
    fun noisyLowEIsNotMisreadByShallowNoiseDip() {
        // The recorded failure: 59.47 Hz (A#1) at confidence 0.85.
        val est = detect(noisy55Hz(33L))
        if (est.frequencyHz > 0.0) {
            assertTrue(
                abs(est.frequencyHz - 55.0) / 55.0 < 0.02,
                "55 Hz @ 10 dB must read ≈55 Hz or no pitch, got ${"%.2f".format(est.frequencyHz)} Hz",
            )
        }
    }

    @Test
    fun noisyLowEIsCorrectAcrossSeeds() {
        // No single lucky seed: the fix must hold across the noise population.
        var bad = 0
        for (seed in 1L..20L) {
            val est = detect(noisy55Hz(seed))
            if (est.frequencyHz > 0.0 && abs(est.frequencyHz - 55.0) / 55.0 >= 0.02) bad++
        }
        assertTrue(bad == 0, "$bad/20 seeds still misread 55 Hz by >2%")
    }

    @Test
    fun cleanSineStillLocksToFundamentalNotSubharmonic() {
        // Regression guard for the deeper-dip reselection: a pure 440 Hz
        // sine has deep dips at BOTH 440 Hz and 220 Hz lags; the fundamental
        // (smaller lag) must win.
        val est = detect(sineTail(440.0, 1.5))
        assertTrue(est.frequencyHz > 0.0, "clean 440 Hz must be detected")
        assertTrue(
            abs(est.frequencyHz - 440.0) / 440.0 < 0.01,
            "expected 440 Hz, got ${"%.2f".format(est.frequencyHz)}",
        )
    }
}
