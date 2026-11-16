package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

/** Why the detector produced (or declined to produce) its answer. Diagnostics. */
enum class PitchReason {
    /** No answer: silence, noise, out of band, or nothing that passed the guards. */
    NONE,

    /** Classic YIN rule: the first lag under the absolute threshold. */
    FIRST_CROSSING,

    /** The global in-band dip won (deeper than the first crossing). */
    GLOBAL_DIP,

    /** An octave-up recovery of a rejected octave-below candidate. */
    OCTAVE_RECOVERY,

    /**
     * A tonal but sub-threshold dip was recovered from an aperiodic
     * (sympathetic) mix. The dip anchors that periodicity exists; the reported
     * frequency comes from its harmonic refinement, because the dip's own
     * position drifts with the beat phase.
     */
    SUBTHRESHOLD_RECOVERY,

    /**
     * The waveform carried no usable periodicity in band, so the pitch was
     * taken from the spectrum (harmonic energy sum) instead — a played note
     * accompanied by a string that is not harmonically related.
     */
    HARMONIC_SPECTRAL,
}

/**
 * One pitch-detection result over a window.
 * [frequencyHz] == 0.0 means "no confident pitch" (silence, noise, out-of-band,
 * or unstable). [confidence] is in 0..1 and always means the same thing:
 * 1 − the aperiodicity of the waveform at the reported period.
 */
data class PitchEstimate(
    val frequencyHz: Double,
    val confidence: Double,
    val reason: PitchReason = PitchReason.FIRST_CROSSING,
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
 * (frequency 0, confidence 0) rather than a best-guess low-quality number —
 * unless the *spectrum* shows a clear harmonic series whose periodicity is
 * destroyed by a second, non-harmonically-related tone ([PitchReason.HARMONIC_SPECTRAL]).
 * That path is gated on absolute spectral evidence (presence + peakiness), so
 * noise can never reach it.
 *
 * Harmonic refinement: the period minimizing the difference function is a
 * compromise whenever the partials are not exactly harmonic (stiff strings) or
 * a sympathetic string perturbs the waveform. Measured through the full
 * pipeline: 7-11 cents SHARP on E2/A2/G3 at B = 4e-4 (inharmonicity), and
 * −10..−28 cents with a 20-cent beat-phase swing on G3+D3. Every candidate is
 * therefore refined on the harmonic energy sum of its fundamental, which is
 * magnitude-only (phase insensitive) and centered on the fundamental partial
 * by construction.
 *
 * Cost note: the guards evaluate the DTFT at up to ~50 frequencies per frame,
 * using a Goertzel recurrence (no per-sample trig). That removed ~0.9 ms of
 * the previous ~3.7 ms per frame; the difference function (~2.1 ms) and the
 * harmonic refinement (~0.4 ms) remain, measured 3.17 ms/frame in total. The
 * detector runs on the single capture thread, so its cost is a backlog risk,
 * not just a CPU number — hence [StreamingAnalyzer] owns the analysis hop.
 *
 * Threading: one instance is single-threaded by contract (it caches the Hann
 * taper and a scratch buffer between calls). The engine calls it from the
 * capture thread only.
 *
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

    /** Cached Hann taper for the spectral paths (see [spectralWindow]). */
    private var hannTable: DoubleArray = DoubleArray(0)

    /** Reused windowed-frame buffer: no per-frame garbage on the capture thread. */
    private var scratch: FloatArray = FloatArray(0)

    companion object {
        /**
         * Refinement search half-width around a periodic candidate. Bounded
         * well inside a semitone (100 cents) so a refinement can never change
         * note identity — it only corrects the period's bias.
         */
        private const val REFINE_CENTS = 60.0

        /**
         * Hard ceiling for the boundary climb: the total reach of a refinement
         * stays within a whole tone of the period estimate.
         */
        private const val REFINE_MAX_CENTS = 120.0

        /** Coarse refinement grid, then a fine grid around the coarse maximum. */
        private const val REFINE_COARSE_STEP_CENTS = 7.5
        private const val REFINE_FINE_STEP_CENTS = 2.0

        /** Harmonics used by the harmonic energy sum (weights 1/h). */
        private const val SCORE_HARMONICS = 4

        /**
         * Absolute spectral presence required by the spectral path. A real
         * string at 4096 samples measures 1200-1700; broadband noise measures
         * 0.02-2.1 (40 seeds measured). 20 is a 10x margin on both sides.
         */
        private const val SPECTRAL_RATIO_FLOOR = 20.0

        /**
         * How much the winning pitch must stand out from the median of the
         * spectral grid before a spectral answer is accepted. Measured: 2.0-100
         * on real mixes, ~1 on noise.
         */
        private const val SPECTRAL_PEAKINESS_FLOOR = 1.5

        /**
         * Grid step of the spectral scan. Finer than the DTFT main lobe at the
         * lowest supported note (a 4096-sample window at 82 Hz has a ±245-cent
         * lobe) and coarse enough to stay cheap.
         */
        private const val SPECTRAL_STEP_CENTS = 25.0

        /** Spectral peaks considered as note candidates. */
        private const val SPECTRAL_SEEDS = 3

        /**
         * The winning harmonic series must beat the strongest competing series
         * (an unrelated string) by this factor. A real tone naturally scores
         * ~2x its own subharmonic (the sub-octave inherits half the
         * fundamental's term), so 1.5 never rejects a single string, while a
         * genuinely ambiguous two-string mix (-6 dB measured) is refused rather
         * than reported as a guess.
         */
        private const val SPECTRAL_RUNNER_UP_MARGIN = 1.5

        /**
         * A spectral answer requires the reported period itself to be periodic
         * at or below this aperiodicity (the sub-threshold recovery bound). Measured:
         * recoverable sympathetic mixes 0.18-0.36 at the played note; a mix
         * dominated by the other string 0.45-0.69 at both candidates; noise
         * 0.85+.
         */
        private const val SPECTRAL_PERIODICITY_BOUND = 0.40

        /**
         * Peak interpolation needs enough periods of the note inside the
         * window to resolve the fundamental's lobe: at 6 periods the lobe is
         * ~290 cents wide (2048 samples at 82 Hz is 3.5 periods — there the
         * spectrum cannot locate the peak and the refinement must not run).
         */
        private const val MIN_REFINE_PERIODS = 6.0

        fun default(): Yin = Yin()
    }

    /**
     * A Hann-windowed copy of the analysis frame and its energy.
     *
     * The spectral paths localize the fundamental's peak, and a rectangular
     * window displaces that peak whenever the window does not hold a whole
     * number of periods — measured −5.2 cents on a clean 82.4 Hz sine in 4096
     * samples (7.03 periods), −1.7 cents at 110 Hz, −0.04 cents at 196 Hz. A
     * Hann taper makes the leakage symmetric and the peak lands on the true
     * frequency at every supported pitch (verified 0.0 cents at 82.4/110/196).
     * The periodicity guards keep using the raw frame: their thresholds were
     * measured on it, and the refinement is the only phase-sensitive consumer.
     */
    private class SpectralWindow(val samples: FloatArray, val energy: Double)

    private fun spectralWindow(frame: FloatArray): SpectralWindow? {
        val n = frame.size
        if (n < 2) return null
        if (hannTable.size != n) {
            hannTable = DoubleArray(n) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1)) }
        }
        if (scratch.size != n) scratch = FloatArray(n)
        val out = scratch
        var energy = 0.0
        val w = hannTable
        for (i in 0 until n) {
            val v = frame[i] * w[i]
            out[i] = v.toFloat()
            energy += v * v
        }
        return if (energy > 0.0) SpectralWindow(out, energy) else null
    }

    fun detect(frame: FloatArray, sampleRate: Int): PitchEstimate {
        require(sampleRate in 8000..96000) { "sampleRate out of bounds: $sampleRate" }
        require(frame.size >= 256) { "window too small: ${frame.size} < 256" }

        val n = frame.size
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / minHz).toInt().coerceAtMost(n - 1)
        require(minLag < maxLag) { "window too small for band ${minHz}..${maxHz} Hz" }

        var totalEnergy = 0.0
        for (v in frame) totalEnergy += v.toDouble() * v
        if (totalEnergy <= 0.0) return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        val window = spectralWindow(frame)

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
        fun isFundamentalCandidate(tau: Int): Boolean {
            val f = sampleRate.toDouble() / tau
            val r = energyRatioAt(frame, f, sampleRate, totalEnergy)
            if (r < 0.25) return false
            for (h in 2..24) {
                val hf = f * h
                if (hf > sampleRate / 2.0) break
                if (energyRatioAt(frame, hf, sampleRate, totalEnergy) > 10.0 * r) return false
            }
            return true
        }

        if (tauMin == -1) {
            // Sub-threshold recovery (sympathetic mixes). A real G3 pluck
            // excites the open D string (G3's 3rd partial 588 Hz ~= D3's 4th
            // harmonic 587.3 Hz). A 4:3 mix is periodic only at 49 Hz — below
            // the 55 Hz band edge — so NO in-band dip crosses the absolute
            // threshold, yet the played fundamental still leaves a deep,
            // strongly tonal dip (measured d'=0.180 at 196 Hz with the
            // sympathetic at -14 dB, spectral ratio ~1000). Returning "no
            // pitch" here made the state machine drop a clearly audible G3.
            //
            // The dip's *position* is not trustworthy on this path: it is
            // pulled by the interfering tone and measured to drift up to 80
            // cents with the beat phase. So the dip only anchors the answer as
            // "there is periodicity here"; the frequency comes from the
            // harmonic refinement, which climbs out of a pulled dip while the
            // optimum sits on the search boundary.
            //
            // Guards are the same as the octave-bleed recovery below (tonal
            // energy ratio >= 10 + fundamental plausibility) with a 0.40 bound:
            // the mix's valley bottom rotates with phase around 0.34-0.38,
            // noise ratios are ~1-2, and a dominated mix (-6 dB, d'~0.53)
            // still honestly returns nothing.
            var recovered = -1
            var recoveredD = 0.40
            var tau = minLag + 1
            while (tau < maxLag) {
                if (dNorm[tau] < recoveredD &&
                    dNorm[tau] <= dNorm[tau - 1] && dNorm[tau] < dNorm[tau + 1]
                ) {
                    var t = tau
                    while (t + 1 <= maxLag && dNorm[t + 1] < dNorm[t]) t++
                    if (dNorm[t] < recoveredD &&
                        isFundamentalCandidate(t) &&
                        energyRatioAt(frame, sampleRate.toDouble() / t, sampleRate, totalEnergy) >= 10.0
                    ) {
                        recovered = t
                        recoveredD = dNorm[t]
                    }
                    tau = t + 1
                } else {
                    tau++
                }
            }
            if (window == null) return PitchEstimate(0.0, 0.0, PitchReason.NONE)
            if (recovered == -1) {
                return spectralPitch(window, dNorm, sampleRate, minLag, maxLag)
            }
            val seeded = sampleRate.toDouble() / recovered
            val refined = refineFundamental(window, seeded, sampleRate) ?: seeded
            val confidence = (1.0 - dNorm[recovered]).coerceIn(0.0, 1.0)
            return PitchEstimate(refined.coerceIn(minHz, maxHz), confidence, PitchReason.SUBTHRESHOLD_RECOVERY)
        }

        var reason = PitchReason.FIRST_CROSSING
        val candidates = (if (gMin == tauMin) listOf(tauMin) else listOf(tauMin, gMin)).distinct()
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
                    if (energyRatioAt(frame, f, sampleRate, totalEnergy) < 10.0) continue
                    recovered = tau
                    recoveredD = dNorm[tau]
                }
            }
            if (recovered == -1) {
                if (window == null) return PitchEstimate(0.0, 0.0, PitchReason.NONE)
                return spectralPitch(window, dNorm, sampleRate, minLag, maxLag)
            }
            tauMin = recovered
            reason = PitchReason.OCTAVE_RECOVERY
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
            // The candidate pick only overrides the provenance label when the
            // first-crossing candidate lost.
            if (reason == PitchReason.FIRST_CROSSING && tauMin != candidates[0]) {
                reason = PitchReason.GLOBAL_DIP
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

        val periodicHz = sampleRate / tau

        // 5) Harmonic refinement (see the class comment for the measured
        //    bias this corrects).
        val refinedHz = window?.let { refineFundamental(it, periodicHz, sampleRate) } ?: periodicHz

        // 6) Spectral guard: the candidate must carry real energy AT that
        //    frequency. Rejects subharmonic artifacts from out-of-band content
        //    (e.g. a 3000 Hz tone masquerading as 1000 Hz through a 3x-period
        //    lag) — the classic octave-error shortcut.
        if (energyRatioAt(frame, refinedHz, sampleRate, totalEnergy) < 0.25) {
            return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        }

        val confidence = (1.0 - dNorm[tauMin]).coerceIn(0.0, 1.0)
        return PitchEstimate(refinedHz.coerceIn(minHz, maxHz), confidence, reason)
    }

    /**
     * Refine a periodic candidate on the harmonic energy sum of the
     * fundamental. Returns null when no usable peak is found in the window
     * (the caller then keeps the period estimate).
     *
     * Score(f) = r(f) + 0.5·r(2f) + 1/3·r(3f) + 0.25·r(4f), where r is the
     * normalized DTFT energy at that frequency. Magnitudes only, so the result
     * has no dependence on the beat phase of an interfering tone. Restricting
     * the search to ±60 cents keeps the answer inside the same semitone as the
     * period estimate.
     */
    private fun refineFundamental(
        window: SpectralWindow,
        approxHz: Double,
        sampleRate: Int,
    ): Double? {
        if (approxHz <= 0.0 || window.energy <= 0.0) return null
        if (window.samples.size * approxHz / sampleRate < MIN_REFINE_PERIODS) return null
        return refineFundamentalAt(window, approxHz, sampleRate, REFINE_CENTS, depth = 0)
    }

    /**
     * One refinement pass with an explicit window. [depth] bounds the boundary
     * climb, so a search can never walk away from the period estimate.
     */
    private fun refineFundamentalAt(
        window: SpectralWindow,
        approxHz: Double,
        sampleRate: Int,
        windowCents: Double,
        depth: Int,
    ): Double? {
        if (approxHz <= 0.0 || window.energy <= 0.0) return null
        val lo = (approxHz * Math.pow(2.0, -windowCents / 1200.0)).coerceAtLeast(minHz)
        val hi = (approxHz * Math.pow(2.0, windowCents / 1200.0)).coerceAtMost(maxHz)
        if (hi <= lo) return null
        val steps = maxOf(2, (2 * REFINE_CENTS / REFINE_COARSE_STEP_CENTS).toInt())
        var bestHz = approxHz
        var bestScore = -1.0
        for (i in 0..steps) {
            // Logarithmic grid: uniform in pitch, not in Hz.
            val f = lo * Math.pow(hi / lo, i.toDouble() / steps)
            val s = harmonicScore(window, f, sampleRate)
            if (s > bestScore) {
                bestScore = s
                bestHz = f
            }
        }
        if (bestScore <= 0.0) return null
        // An optimum sitting on the window boundary means the period estimate
        // was pulled further than this window reaches: extend one step (bounded
        // to a whole tone in total) and search again. Identity decisions are
        // the tracker's job; this only corrects a bias.
        if (depth == 0 && (bestHz <= lo * 1.0001 || bestHz >= hi * 0.9999) &&
            REFINE_CENTS * 2 <= REFINE_MAX_CENTS
        ) {
            refineFundamentalAt(window, bestHz, sampleRate, REFINE_MAX_CENTS, depth = 1)
                ?.let { return it }
        }
        // Fine pass around the coarse maximum.
        val fineLo = bestHz * Math.pow(2.0, -REFINE_COARSE_STEP_CENTS / 1200.0)
        val fineHi = bestHz * Math.pow(2.0, REFINE_COARSE_STEP_CENTS / 1200.0)
        val fineSteps = (2 * REFINE_COARSE_STEP_CENTS / REFINE_FINE_STEP_CENTS).toInt()
        val scores = DoubleArray(fineSteps + 1)
        var bestI = 0
        for (i in 0..fineSteps) {
            val f = fineLo * Math.pow(fineHi / fineLo, i.toDouble() / fineSteps)
            scores[i] = harmonicScore(window, f, sampleRate)
            if (scores[i] > scores[bestI]) bestI = i
        }
        val stepRatio = Math.pow(fineHi / fineLo, 1.0 / fineSteps)
        var refined = fineLo * Math.pow(stepRatio, bestI.toDouble())
        if (bestI in 1 until fineSteps) {
            val denom = scores[bestI - 1] - 2.0 * scores[bestI] + scores[bestI + 1]
            if (denom != 0.0) {
                val delta = (0.5 * (scores[bestI - 1] - scores[bestI + 1]) / denom).coerceIn(-0.5, 0.5)
                refined *= Math.pow(stepRatio, delta)
            }
        }
        return refined.coerceIn(minHz, maxHz)
    }

    /**
     * Spectral answer: the waveform has no usable periodicity in band, but
     * the spectrum still shows a harmonic series.
     *
     * 1. Scan the band on a uniform-in-cents grid for fundamental presence and
     *    keep the [SPECTRAL_SEEDS] strongest peaks.
     * 2. For each peak, score the peak and its subharmonics (÷2, ÷3) with the
     *    harmonic energy sum — this is what keeps a strong 2nd partial from
     *    being reported as its own octave.
     * 3. Accept only when the winner has absolute spectral presence AND stands
     *    out from the median of the grid; noise is flat on both counts.
     *
     * The reported frequency is the fundamental partial, refined to ±60 cents.
     * The confidence keeps its usual meaning: 1 − aperiodicity at that period,
     * which is honest about the waveform not being periodic in band.
     */
    private fun spectralPitch(
        window: SpectralWindow,
        dNorm: DoubleArray,
        sampleRate: Int,
        minLag: Int,
        maxLag: Int,
    ): PitchEstimate {
        val lo = minHz
        val hi = maxHz.coerceAtMost(sampleRate / 2.0)
        if (hi <= lo) return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        val steps = ((1200.0 * ln(hi / lo) / ln(2.0)) / SPECTRAL_STEP_CENTS).toInt().coerceIn(1, 512)
        val presence = DoubleArray(steps + 1)
        for (i in 0..steps) {
            val f = lo * Math.pow(hi / lo, i.toDouble() / steps)
            presence[i] = windowedRatioAt(window, f, sampleRate)
        }
        // Local maxima, strongest first.
        val peaks = ArrayList<Int>()
        for (i in 1 until steps) {
            if (presence[i] >= presence[i - 1] && presence[i] >= presence[i + 1]) peaks.add(i)
        }
        if (peaks.isEmpty()) return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        val top = peaks.sortedByDescending { presence[it] }.take(SPECTRAL_SEEDS)

        var bestHz = 0.0
        var bestScore = -1.0
        val candidateHz = ArrayList<Double>()
        val candidateScore = ArrayList<Double>()
        for (i in top) {
            val peakHz = lo * Math.pow(hi / lo, i.toDouble() / steps)
            for (divisor in 1..3) {
                val candidate = peakHz / divisor
                if (candidate < minHz || candidate > maxHz) continue
                val s = harmonicScore(window, candidate, sampleRate)
                candidateHz.add(candidate)
                candidateScore.add(s)
                if (s > bestScore) {
                    bestScore = s
                    bestHz = candidate
                }
            }
        }
        if (bestHz <= 0.0) return PitchEstimate(0.0, 0.0, PitchReason.NONE)

        // Refuse to guess between two competing harmonic series: an unrelated
        // string 50 cents or more away must not be scored as the same note.
        var runnerUp = 0.0
        for (i in candidateHz.indices) {
            val centsApart = 1200.0 * ln(candidateHz[i] / bestHz) / ln(2.0)
            if (kotlin.math.abs(centsApart) > 50.0) runnerUp = maxOf(runnerUp, candidateScore[i])
        }
        if (runnerUp > 0.0 && bestScore / runnerUp < SPECTRAL_RUNNER_UP_MARGIN) {
            return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        }
        if (windowedRatioAt(window, bestHz, sampleRate) < SPECTRAL_RATIO_FLOOR) {
            return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        }
        val sortedPresence = presence.sorted()
        val medianPresence = sortedPresence[sortedPresence.size / 2]
        if (medianPresence > 0.0 && bestScore / medianPresence < SPECTRAL_PEAKINESS_FLOOR) {
            return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        }

        val refined = refineFundamental(window, bestHz, sampleRate) ?: bestHz
        val lag = (sampleRate / refined).toInt().coerceIn(minLag, maxLag)
        val confidence = (1.0 - dNorm[lag]).coerceIn(0.0, 1.0)
        // The reported pitch must itself be supported by the waveform. A
        // two-string mix in which the *other* string dominates has no periodic
        // evidence at either candidate's period (measured d' = 0.45-0.69 for
        // both notes at -6 dB), so the honest answer is "no pitch" rather than
        // the loudest harmonic series. A recoverable sympathetic mix measures
        // d' = 0.33-0.36 at the played note and is answered by the sub-threshold anchor
        // path instead.
        if (confidence < 1.0 - SPECTRAL_PERIODICITY_BOUND) {
            return PitchEstimate(0.0, 0.0, PitchReason.NONE)
        }
        return PitchEstimate(refined.coerceIn(minHz, maxHz), confidence, PitchReason.HARMONIC_SPECTRAL)
    }

    /** Weighted harmonic energy sum at [f0] (magnitude only — phase insensitive). */
    private fun harmonicScore(window: SpectralWindow, f0: Double, sampleRate: Int): Double {
        var s = 0.0
        for (h in 1..SCORE_HARMONICS) {
            val f = f0 * h
            if (f > sampleRate / 2.0) break
            s += windowedRatioAt(window, f, sampleRate) / h
        }
        return s
    }

    /** Normalized DTFT energy at [frequency] on the Hann-windowed frame. */
    private fun windowedRatioAt(window: SpectralWindow, frequency: Double, sampleRate: Int): Double {
        if (window.energy <= 0.0 || frequency <= 0.0) return 0.0
        val coeff = 2.0 * cos(2.0 * PI * frequency / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in window.samples) {
            val s0 = v + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return if (power > 0.0) power / window.energy else 0.0
    }

    /**
     * Fraction of the frame's total energy sitting at [frequency]
     * (|DTFT(f)|² / Σ|frame|²). Used by the spectral guard, the
     * dip-reselection veto and the harmonic refinement.
     *
     * Evaluated with a Goertzel recurrence: identical value, two multiplies and
     * an add per sample instead of a sin/cos pair. This function is called up
     * to ~50 times per frame by the guards; the trig version measured 3.7 ms
     * per frame on the JVM — a backlog risk on the single capture thread.
     */
    private fun energyRatioAt(
        frame: FloatArray,
        frequency: Double,
        sampleRate: Int,
        totalEnergy: Double,
    ): Double {
        if (totalEnergy <= 0.0 || frequency <= 0.0) return 0.0
        val coeff = 2.0 * cos(2.0 * PI * frequency / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in frame) {
            val s0 = v + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return if (power > 0.0) power / totalEnergy else 0.0
    }
}