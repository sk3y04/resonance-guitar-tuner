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

        var gMin = minLag
        for (t in minLag..maxLag) if (dNorm[t] < dNorm[gMin]) gMin = t

        // 3b) Two-candidate selection. Two candidates: the first-crossing
        // descent (fundamental-first, the classic YIN rule) and the global
        // in-band minimum (the deepest dip). Broadband noise can make a
        // SHALLOW dip cross the threshold before the true-period dip
        // (measured: 55 Hz + 10 dB → false dip at lag 741, d'=0.145, beat
        // the true dip at lag ~796, d'=0.069 → 59.5 Hz, confidence 0.85,
        // on 20/20 seeds). Each candidate is validated:
        //   A) spectral presence — real energy at its frequency (kills noise
        //      dips that carry no tonal content);
        //   B) fundamental plausibility — no in-band harmonic 2f..24f is
        //      >10x its own energy. Real strings may be overtone-rich
        //      (bridge plucks: 4th partial can exceed the fundamental), so
        //      the margin is wide; subharmonic ARTIFACTS (110 Hz for a
        //      440 Hz sine, 69.5 Hz for a 903 Hz sine — 13x!) have their
        //      "harmonics" 100x+ stronger and are disqualified. The range
        //      2..24 covers every subharmonic lag reachable in the
        //      55..1200 Hz band (maxLag period = 55.05 Hz).
        // Survivors: the deeper dip wins; comparable depths (within 25%)
        // → the smaller lag (fundamental-first, classic YIN behavior).
        val candidates = (if (gMin == tauMin) listOf(tauMin) else listOf(tauMin, gMin)).distinct()
        fun isFundamentalCandidate(tau: Int): Boolean {
            val f = sampleRate.toDouble() / tau
            val r = energyRatioAt(frame, f, sampleRate)
            if (r < 0.25) return false
            for (h in 2..24) {
                val hf = f * h
                if (hf > sampleRate / 2.0) break
                if (energyRatioAt(frame, hf, sampleRate) > 10.0 * r) return false
            }
            return true
        }
        val survivors = candidates.filter { isFundamentalCandidate(it) }
        if (survivors.isEmpty()) {
            // Octave-bleed recovery. The first-crossing/global minimum
            // can be an octave-below component (sympathetic string, body
            // resonance) whose own 2nd harmonic is >10x stronger — rejected
            // as a fundamental — while the true upper note's dip was too
            // shallow to cross [absoluteThreshold]. Returning "no pitch"
            // made a ringing G4 + open G3 drop out. Look one octave up from
            // each rejected candidate, require a real local dip and strong
            // tonal energy at the recovered frequency (noise ratios are
            // ~1; a tonal candidate is orders of magnitude higher).
            var recovered = -1
            var recoveredD = Double.MAX_VALUE
            for (candidate in candidates) {
                val approx = candidate / 2
                for (tau in (approx - 1)..(approx + 1)) {
                    if (tau !in minLag..maxLag) continue
                    if (dNorm[tau] >= 0.35 || dNorm[tau] >= recoveredD) continue
                    if (!isFundamentalCandidate(tau)) continue
                    val f = sampleRate.toDouble() / tau
                    if (energyRatioAt(frame, f, sampleRate) < 10.0) continue
                    recovered = tau
                    recoveredD = dNorm[tau]
                }
            }
            if (recovered == -1) return PitchEstimate(0.0, 0.0)
            tauMin = recovered
        } else {
            tauMin = if (survivors.size == 1) {
                survivors[0]
            } else {
                val a = survivors[0]
                val b = survivors[1]
                when {
                    dNorm[a] < 0.8 * dNorm[b] -> a
                    dNorm[b] < 0.8 * dNorm[a] -> b
                    else -> minOf(a, b)
                }
            }
        }

        // 4) Parabolic interpolation around the minimum for sub-sample
        //    precision (classic YIN). The correctness sweep investigated a symmetric
        //    least-squares fit over a wider window to remove the noise-trend
        //    bias of the 3-point fit (measured −12 cents at 87 Hz @ 10 dB):
        //    it fixes the tilted low-band dip but shifts clean high-end dips
        //    (1174 Hz: 1170–1270 Hz errors) because the d′ dip curvature
        //    over a multi-lag window is not quadratic for sharp dips. The
        //    3-point form is the proven behavior for the clean/plucked band
        //    (0-cent locks, plucked-string evidence); the residual low-band
        //    tilt at 10 dB is a documented known limitation (see
        //    docs/QUALITY_REPORT.md, DSP correctness map), not hidden by this choice.
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
        val ratio = energyRatioAt(frame, frequency, sampleRate)
        if (ratio < 0.25) return PitchEstimate(0.0, 0.0)

        return PitchEstimate(frequency, confidence)
    }

    /**
     * Fraction of the frame's total energy sitting at [frequency]
     * (|DTFT(f)|² / Σ|frame|²). Used by the spectral guard and the
     * dip-reselection veto.
     */
    private fun energyRatioAt(frame: FloatArray, frequency: Double, sampleRate: Int): Double {
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
        return if (total > 0.0) (re * re + im * im) / total else 0.0
    }

    companion object {
        fun default(): Yin = Yin()
    }
}
