package dev.anima.resonance.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic synthetic plucked-string signals for the pitch-lab harness.
 *
 * NOT a real guitar, and never to be described as one. It models the
 * *mechanisms* that break a tuner so the failures are reproducible offline:
 *  - partial-specific decay: higher partials die first (real string physics),
 *    with a [Timbre] knob for the opposite (a fundamental that dies first,
 *    which is what makes an octave error likely);
 *  - inharmonicity f_h = h*f0*sqrt(1 + B*h^2) (stiff, wound bass strings);
 *  - a short noisy attack (plectrum transient) before the tone settles;
 *  - a body/microphone resonance that can favour a given partial;
 *  - a stationary noise floor at a controlled SNR (measured at the attack),
 *    white or pink, plus optional mains hum and low rumble below the
 *    tuning band — the two floor components that actually corrupt YIN's
 *    long lags;
 *  - optional sympathetic string partials (the G3/D3 fourth relationship).
 *
 * Everything is seeded: identical input on every run and platform.
 */
object PitchLabSignals {

    const val SR = 48000

    /** Standard guitar strings, low to high (A4 = 440 Hz). */
    val STANDARD = listOf(
        "E2" to 82.406889,
        "A2" to 110.0,
        "D3" to 146.832384,
        "G3" to 195.997718,
        "B3" to 246.941651,
        "E4" to 329.627557,
    )

    /**
     * Partial amplitude/decay shape of a plucked steel string.
     * [brightness] is the attack tilt (1.0 = 1/h, lower = mellower);
     * [harmonicPeak]/[harmonicGain] scale one partial (dominant-harmonic tests).
     */
    data class Timbre(
        val partials: Int = 10,
        val brightness: Double = 1.1,
        val fundamentalGain: Double = 1.0,
        val harmonicPeak: Int? = null,
        val harmonicGain: Double = 1.0,
        /** Amplitude of the pick transient relative to the partial sum. */
        val attackNoise: Double = 0.08,
        /**
         * Per-partial decay law: tau_h = tau_1 / (1 + partialDecay*(h-1)).
         * 0 = every partial rings for the same time; 0.42 = higher partials
         * die faster (the physical case); set [fundamentalDecayFactor] > 1
         * to model a fundamental that decays *faster* than the rest, which is
         * what lets a decaying note look like its own octave.
         */
        val partialDecay: Double = 0.42,
        val fundamentalDecayFactor: Double = 1.0,
    )

    /** Body/microphone response: a broad resonance at [resonanceHz]. */
    data class Mic(
        val resonanceHz: Double = 0.0,
        val resonanceGainDb: Double = 0.0,
        val resonanceWidthOctaves: Double = 0.5,
        /** Rolloff below [lowCutHz] (0 = none). Models a phone mic. */
        val lowCutHz: Double = 0.0,
    ) {
        fun gainAt(f: Double): Double {
            var g = 1.0
            if (resonanceHz > 0.0 && resonanceGainDb != 0.0) {
                val octaves = kotlin.math.ln(f / resonanceHz) / kotlin.math.ln(2.0) / resonanceWidthOctaves
                val w = exp(-octaves * octaves)
                g *= Math.pow(10.0, resonanceGainDb * w / 20.0)
            }
            if (lowCutHz > 0.0 && f < lowCutHz) {
                // 12 dB/oct-ish rolloff below the cut.
                g *= Math.pow(f / lowCutHz, 2.0)
            }
            return g
        }
    }

    /**
     * One sustained tone with a plucked envelope.
     *
     * @param snrDb signal-to-noise ratio at the attack (30 = clearly usable,
     *   20 = a quiet room, 10 = buried)
     * @param noisePink true = 1/f noise floor (rooms, handling, air) instead
     *   of white
     * @param humHz mains hum fundamental (0 = none); adds humHz + 2·humHz
     * @param rumbleHz low-frequency floor component below the tuning band
     */
    fun tone(
        f0: Double,
        seconds: Double,
        cents: Double = 0.0,
        decayTau: Double = 2.2,
        snrDb: Double = 30.0,
        timbre: Timbre = Timbre(),
        mic: Mic = Mic(),
        inharmonicity: Double = 0.0,
        riseMs: Double = 4.0,
        peak: Double = 0.5,
        noisePink: Boolean = false,
        humHz: Double = 0.0,
        humDb: Double = 0.0,
        rumbleHz: Double = 0.0,
        rumbleDb: Double = 0.0,
        seed: Int = 7,
    ): FloatArray {
        val n = (seconds * SR).toInt()
        val freq = f0 * Math.pow(2.0, cents / 1200.0)
        val out = DoubleArray(n)
        // Partial phases are seeded so the harmonics are not all in phase (an
        // all-zero-phase stack is an unrealistically impulse-like waveform).
        val rng = Random(seed)
        val phase = DoubleArray(timbre.partials + 1) { if (it == 0) 0.0 else rng.nextDouble() * 2.0 * PI }
        val rise = (riseMs / 1000.0).coerceAtLeast(1e-4)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val attack = 1.0 - exp(-t / (rise / 3.0))
            var v = 0.0
            for (h in 1..timbre.partials) {
                val fh = h * freq * sqrt(1.0 + inharmonicity * h * h)
                if (fh >= SR / 2.0) break
                val tauH = decayTau / (1.0 + timbre.partialDecay * (h - 1)) /
                    if (h == 1) timbre.fundamentalDecayFactor else 1.0
                var amp = 1.0 / Math.pow(h.toDouble(), timbre.brightness)
                if (h == 1) amp *= timbre.fundamentalGain
                if (timbre.harmonicPeak == h) amp *= timbre.harmonicGain
                amp *= mic.gainAt(fh)
                v += amp * exp(-t / tauH) * sin(2.0 * PI * fh * t + phase[h])
            }
            out[i] = attack * v
        }
        // Attack transient: broadband pick noise, gone in a few ms.
        val attackN = (0.012 * SR).toInt()
        val arng = Random(seed + 1)
        var norm = 0.0
        for (i in out.indices) norm = maxOf(norm, abs(out[i]))
        if (norm <= 0.0) norm = 1.0
        for (i in 0 until minOf(attackN, n)) {
            val env = exp(-i.toDouble() / (0.004 * SR))
            out[i] += timbre.attackNoise * norm * env * (arng.nextDouble() * 2.0 - 1.0)
        }
        // Scale to the requested peak, then build the floor.
        var scale = 1.0
        norm = 0.0
        for (v in out) norm = maxOf(norm, abs(v))
        if (norm > 0.0) scale = peak / norm
        val rms = sqrt(out.sumOf { (it * scale) * (it * scale) } / n)
        val floor = if (snrDb >= 100.0) 0.0 else rms / Math.pow(10.0, snrDb / 20.0)
        val nrng = Random(seed + 2)
        val pinkRaw = if (noisePink) pinkUnit(n, nrng) else null
        val pinkRms = pinkRaw?.let { p -> sqrt(p.sumOf { it * it } / p.size) } ?: 1.0
        // hum/rumble levels are relative to the tone's RMS (negative = quieter).
        val humAmp = if (humDb <= -100.0) 0.0 else rms * Math.pow(10.0, humDb / 20.0)
        val rumbleAmp = if (rumbleDb <= -100.0) 0.0 else rms * Math.pow(10.0, rumbleDb / 20.0)
        return FloatArray(n) { i ->
            val t = i.toDouble() / SR
            var v = out[i] * scale
            val w = if (pinkRaw != null) pinkRaw[i] / pinkRms else nrng.nextDouble() * 2.0 - 1.0
            v += floor * w
            if (humAmp > 0.0) {
                v += humAmp * (0.7 * sin(2.0 * PI * humHz * t + 0.3) + 0.3 * sin(4.0 * PI * humHz * t + 1.1))
            }
            if (rumbleAmp > 0.0) {
                v += 0.5 * rumbleAmp * (sin(2.0 * PI * rumbleHz * t + 0.9) + sin(2.0 * PI * rumbleHz * 1.37 * t))
            }
            v.coerceIn(-1.0, 1.0).toFloat()
        }
    }

    /** White-ish noise shaped to a 1/f-ish spectrum (three-pole smoothing). */
    private fun pinkUnit(n: Int, rng: Random): DoubleArray {
        val out = DoubleArray(n)
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        for (i in 0 until n) {
            val w = rng.nextDouble() * 2.0 - 1.0
            b0 = 0.99765 * b0 + w * 0.0990460
            b1 = 0.96300 * b1 + w * 0.2965164
            b2 = 0.57000 * b2 + w * 1.0526913
            out[i] = b0 + b1 + b2 + w * 0.1848
        }
        return out
    }

    /** Karplus-Strong pluck (a different physical model — no harmonic series). */
    fun pluck(
        f0: Double,
        seconds: Double,
        cents: Double = 0.0,
        decay: Double = 0.9955,
        peak: Double = 0.5,
        snrDb: Double = 100.0,
        seed: Int = 11,
    ): FloatArray {
        val n = (seconds * SR).toInt()
        val freq = f0 * Math.pow(2.0, cents / 1200.0)
        val period = SR / freq
        val m = period.toInt().coerceAtLeast(2)
        val alpha = period - m
        val l = m + 1
        val rng = Random(seed)
        val pluckPos = (l * 0.3).toInt().coerceAtLeast(1)
        val buf = DoubleArray(l) { i ->
            // Pluck shape plus a little pick noise (a real excitation is not
            // a perfect triangle).
            val shape = if (i < pluckPos) i.toDouble() / pluckPos else (l - i).toDouble() / (l - pluckPos)
            shape + 0.05 * (rng.nextDouble() * 2.0 - 1.0)
        }
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val a = buf[(i - m + l) % l]
            val b = buf[(i - m - 1 + l) % l]
            out[i] = a
            buf[i % l] = decay * ((1 - alpha) * a + alpha * b)
        }
        var norm = 0.0
        for (v in out) norm = maxOf(norm, abs(v))
        if (norm <= 0.0) norm = 1.0
        val scale = peak / norm
        val rms = sqrt(out.sumOf { (it * scale) * (it * scale) } / n)
        val floor = if (snrDb >= 100.0) 0.0 else rms / Math.pow(10.0, snrDb / 20.0)
        val nrng = Random(seed + 3)
        return FloatArray(n) { i ->
            (out[i] * scale + floor * (nrng.nextDouble() * 2.0 - 1.0)).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    /**
     * Adds a second string that a pluck excites sympathetically (the G3/D3
     * fourth relationship reported by the user). [relDb] < 0 = quieter than
     * the played note; the energy builds up over [rampMs].
     */
    fun withSympathetic(
        main: FloatArray,
        fSymp: Double,
        relDb: Double,
        rampMs: Long,
        partial: Int = 1,
        tau: Double = 3.0,
        amp: Double = 0.35,
    ): FloatArray {
        val rel = Math.pow(10.0, relDb / 20.0)
        return FloatArray(main.size) { i ->
            val t = i.toDouble() / SR
            val buildUp = ((t * 1000.0) / rampMs).coerceAtMost(1.0)
            val f = fSymp * partial
            val symp = rel * buildUp * exp(-t / tau) * sin(2.0 * PI * f * t)
            (main[i] + amp * symp).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    /**
     * Single-tone glide by [cents] over [ms] starting at [startMs] (used to
     * measure tracking lag during a real tuning adjustment).
     */
    fun ramp(
        seconds: Double,
        f0: Double,
        cents: Double,
        startMs: Long = 0,
        ms: Long = 2000,
        amplitude: Double = 0.4,
    ): FloatArray {
        val n = (seconds * SR).toInt()
        var phase = 0.0
        val out = FloatArray(n)
        for (i in 0 until n) {
            val tMs = i.toDouble() * 1000.0 / SR
            val frac = ((tMs - startMs) / ms).coerceIn(0.0, 1.0)
            val eased = 0.5 - 0.5 * cos(PI * frac)
            val f = f0 * Math.pow(2.0, cents * eased / 1200.0)
            phase += 2.0 * PI * f / SR
            out[i] = (amplitude * sin(phase)).toFloat()
        }
        return out
    }

    /** True instantaneous deviation of [ramp] at [tMs] (ground truth). */
    fun rampCentsAt(cents: Double, startMs: Long, ms: Long, tMs: Double): Double {
        val frac = ((tMs - startMs) / ms).coerceIn(0.0, 1.0)
        return cents * (0.5 - 0.5 * cos(PI * frac))
    }

    fun silence(seconds: Double): FloatArray = FloatArray((seconds * SR).toInt())

    /** Pink (1/f) noise at a given RMS — rooms, handling, air. */
    fun pink(seconds: Double, rms: Double = 0.02, seed: Int = 22): FloatArray {
        val n = (seconds * SR).toInt()
        val raw = pinkUnit(n, Random(seed))
        val cur = sqrt(raw.sumOf { it * it } / n).coerceAtLeast(1e-12)
        val scale = rms / cur
        return FloatArray(n) { (raw[it] * scale).toFloat() }
    }

    /** White noise at a given RMS. */
    fun noise(seconds: Double, rms: Double = 0.02, seed: Int = 21): FloatArray {
        val rng = Random(seed)
        val amp = rms * sqrt(3.0) // uniform(-1,1) has RMS 1/sqrt(3)
        return FloatArray((seconds * SR).toInt()) { (amp * (rng.nextDouble() * 2.0 - 1.0)).toFloat() }
    }

    /** Concatenates segments in order. */
    fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(out, at)
            at += p.size
        }
        return out
    }
}