package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One pitch-detection result over a window.
 * [frequencyHz] == 0.0 means "no confident pitch" (silence, noise, out-of-band,
 * or unstable). [confidence] is in 0..1.
 */
data class PitchEstimate(
    val frequencyHz: Double,
    val confidence: Double,
)

/**
 * YIN monophonic pitch detector (de Cheveigné & Kawahara 2002), implemented
 * in pure Kotlin. Reads the caller's window but never mutates it.
 *
 * Range is bounded by [minHz]..[maxHz] (design band 55..1200 Hz): lags outside
 * the range are never considered, so out-of-band tones cannot alias into a
 * confident in-band answer.
 *
 * Policy: if NO lag reaches [absoluteThreshold], the result is "no pitch"
 * (frequency 0, confidence 0) rather than a best-guess low-quality number.
 * [absoluteThreshold] is the sensitivity knob: lower = stricter, higher =
 * more permissive. Valid 0.001..0.6 (default 0.15).
 */
class Yin(
    val absoluteThreshold: Double = 0.15,
    val minHz: Double = 55.0,
    val maxHz: Double = 1200.0,
) {
    init {
        require(absoluteThreshold in 0.001..0.6) { "absoluteThreshold out of bounds" }
        require(minHz in 20.0..200.0) { "minHz out of bounds" }
        require(maxHz > minHz) { "maxHz must exceed minHz" }
    }

    fun detect(frame: FloatArray, sampleRate: Int): PitchEstimate {
        require(sampleRate in 8000..96000) { "sampleRate out of bounds: $sampleRate" }
        require(frame.size >= 256) { "window too small: ${frame.size} < 256" }

        val n = frame.size
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / minHz).toInt().coerceAtMost(n - 1)
        require(minLag < maxLag) { "window too small for band ${minHz}..${maxHz} Hz" }

        // 1) Difference function d(tau).
        val d = DoubleArray(maxLag + 1)
        for (tau in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until n - tau) {
                val delta = (frame[i] - frame[i + tau]).toDouble()
                sum += delta * delta
            }
            d[tau] = sum
        }

        // 2) Normalized cumulative mean difference d'(tau) = d(tau)*tau / cumSum(d, 1..tau).
        val dNorm = DoubleArray(maxLag + 1)
        var cum = 0.0
        for (tau in 1..maxLag) {
            cum += d[tau]
            dNorm[tau] = if (cum <= 0.0) 1.0 else d[tau] * tau / cum
        }

        // 3) Absolute threshold: first tau below threshold, descend to its local minimum.
        var tauMin = -1
        outer@ for (tau in minLag..maxLag) {
            if (dNorm[tau] < absoluteThreshold) {
                var t = tau
                while (t + 1 <= maxLag && dNorm[t + 1] < dNorm[t]) t++
                tauMin = t
                break@outer
            }
        }
        if (tauMin == -1) {
            return PitchEstimate(0.0, 0.0)
        }

        // 4) Parabolic interpolation around the minimum for sub-sample precision.
        val tau = if (tauMin in 2 until maxLag) {
            val x1 = dNorm[tauMin - 1]
            val x2 = dNorm[tauMin]
            val x3 = dNorm[tauMin + 1]
            val denom = x1 - 2.0 * x2 + x3
            val delta = if (denom != 0.0) 0.5 * (x1 - x3) / denom else 0.0
            tauMin + delta.coerceIn(-0.5, 0.5)
        } else {
            tauMin.toDouble()
        }

        val confidence = (1.0 - dNorm[tauMin]).coerceIn(0.0, 1.0)
        val frequency = sampleRate / tau

        // 5) Spectral guard: the candidate must carry real energy AT that
        //    frequency. Rejects subharmonic artifacts from out-of-band content
        //    (e.g. a 3000 Hz tone masquerading as 1000 Hz through a 3x-period
        //    lag) — the classic octave-error shortcut.
        val omega = 2.0 * PI * frequency / sampleRate
        var re = 0.0
        var im = 0.0
        for (i in frame.indices) {
            val v = frame[i].toDouble()
            re += v * cos(omega * i)
            im += v * sin(omega * i)
        }
        var total = 0.0
        for (v in frame) total += v * v
        val ratio = if (total > 0.0) (re * re + im * im) / total else 0.0
        if (ratio < 0.25) return PitchEstimate(0.0, 0.0)

        return PitchEstimate(frequency, confidence)
    }

    companion object {
        fun default(): Yin = Yin()
    }
}
