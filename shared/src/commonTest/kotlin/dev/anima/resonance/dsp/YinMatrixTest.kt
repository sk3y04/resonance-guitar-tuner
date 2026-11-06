package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deterministic synthetic acceptance matrix: the design band 55..1200 Hz must
 * be measured without octave/subharmonic errors. Tolerance 1% (≈ up to ~12 Hz
 * at the band top) is a fixed acceptance target, not a fitted fit.
 */
class YinMatrixTest {

    private companion object {
        const val SR = 48000
    }

    private fun sine(frequency: Double, n: Int = 2048, amp: Double = 0.5): FloatArray {
        return FloatArray(n) { (amp * sin(2.0 * PI * frequency * it / SR)).toFloat() }
    }

    private fun saw(frequency: Double, n: Int = 2048, amp: Double = 0.5): FloatArray {
        val period = SR / frequency
        return FloatArray(n) { i ->
            val t = (i % period) / period
            (amp * (2.0 * t - 1.0)).toFloat()
        }
    }

    @Test
    fun fullBandSineScanHasNoOctaveErrors() {
        val yin = Yin.default()
        var checked = 0
        var worst = 0.0
        for (k in 0..572) {
            val f = 55.0 + 2.0 * k
            val est = yin.detect(sine(f), SR)
            val ok = est.frequencyHz > 0.0 &&
                abs(est.frequencyHz - f) / f < 0.01 &&
                est.confidence > 0.5
            if (!ok) {
                error("sine matrix failure at f=$f Hz -> freq=${est.frequencyHz} conf=${est.confidence}")
            }
            worst = maxOf(worst, abs(est.frequencyHz - f) / f)
            checked++
        }
        assertTrue(checked > 500, "matrix too small: $checked")
        assertTrue(worst < 0.01, "worst relative error $worst >= 1%")
    }

    @Test
    fun sawtoothFundamentalsResolveWithoutOctaveErrors() {
        val yin = Yin.default()
        for (f in listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 392.0, 493.88, 659.25)) {
            val est = yin.detect(saw(f), SR)
            assertTrue(
                est.frequencyHz > 0.0 && abs(est.frequencyHz - f) / f < 0.01,
                "sawtooth $f Hz -> ${est.frequencyHz} Hz (conf=${est.confidence})",
            )
        }
    }

    @Test
    fun benchmarkReportsMedianDetectionLatency() {
        val yin = Yin.default()
        val frame = sine(220.0)
        val clock = kotlin.time.TimeSource.Monotonic
        // warm-up (JIT)
        repeat(50) { yin.detect(frame, SR) }
        val n = 300
        val times = LongArray(n)
        for (i in 0 until n) {
            val t0 = clock.markNow()
            yin.detect(frame, SR)
            times[i] = (clock.markNow() - t0).inWholeMicroseconds.toLong()
        }
        times.sort()
        val medianUs = times[n / 2]
        // Host measurement only — no Android latency claim.
        println("BENCHMARK yin.detect median=${medianUs}us p95=${times[(n * 95) / 100]}us n=$n frame=2048 sr=$SR (host)")
        assertTrue(medianUs in 1..20_000, "median ${medianUs}us outside sane 1..20000us host range")
    }
}
