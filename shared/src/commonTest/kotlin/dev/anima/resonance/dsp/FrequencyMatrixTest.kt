package dev.anima.resonance.dsp

import dev.anima.resonance.presentation.TunerStateMachine
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The FULL chromatic correctness map. Every
 * semitone A1 (55 Hz) .. D6 (1174.66 Hz) — the spec's 55..1200 Hz band —
 * is measured through the complete pipeline in three physical flavors:
 * pure sine (the floor), plucked string (harmonic stack + decay), sine in
 * 20 dB room-tone noise (the realistic-hostile gate; 10 dB is measured as
 * a documented stress limit in docs/QUALITY_REPORT.md, DSP correctness map).
 * Assertions: the displayed note must be the exact pitch class
 * (enharmonics folded via Note.pitchClass), median |cents| on
 * correct-note frames under the per-flavor budget, and an in-tune lock
 * must be reached. A per-variant summary line
 * is printed for the quality report (read from the test XML system-out).
 */
class FrequencyMatrixTest {

    private companion object {
        const val SR = 44100
        const val CHUNK = 512
        const val SECONDS = 1.5
        const val A1_MIDI = 33   // 55.00 Hz — range floor
        const val D6_MIDI = 77   // 1174.66 Hz — last semitone under 1200
    }

    private fun midiToHz(midi: Int, concert: Double = 440.0) =
        concert * 2.0.pow((midi - 69) / 12.0)

    private fun sine(f0: Double, seconds: Double): FloatArray {
        val n = (seconds * SR).toInt()
        return FloatArray(n) { 0.3f * sin(2.0 * Math.PI * f0 * it / SR).toFloat() }
    }

    /**
     * Karplus-Strong pluck with a PHYSICAL excitation: the
     * delay line starts as a pluck shape (linear ramp up over 30%, down over
     * 70% of the period) — a string displaced at 30% of its length. This
     * gives the realistic 1/h harmonic falloff (fundamental dominant).
     * A white-noise initial buffer instead yields a FLAT spectrum — some
     * seeds emphasize upper partials 7x over the fundamental, which no
     * physical pluck produces.
     */
    private fun pluck(f0: Double, seconds: Double, seed: Long): FloatArray {
        val n = (seconds * SR).toInt()
        val period = SR / f0
        val M = period.toInt().coerceAtLeast(2)
        val alpha = period - M
        val L = M + 1
        val pluckPos = (L * 0.3).toInt().coerceAtLeast(1)
        val buf = DoubleArray(L) { i ->
            if (i < pluckPos) i.toDouble() / pluckPos
            else (L - i).toDouble() / (L - pluckPos)
        }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val a = buf[(i - M + L) % L]
            val b = buf[(i - M - 1 + L) % L]
            out[i] = a.toFloat()
            buf[i % L] = 0.996 * ((1 - alpha) * a + alpha * b)
        }
        val peak = out.maxOf { abs(it.toDouble()) }.coerceAtLeast(1e-9)
        for (i in out.indices) out[i] = (out[i] / peak * 0.5).toFloat()
        return out
    }

    /**
     * 20 dB SNR (realistic room tone): noise RMS = signal RMS / 10. It was
     * measured that 10 dB (noise at 31% of signal) breaks note identity in
     * the 87–150 Hz register on this pipeline (41–74% identity; full table
     * in docs/QUALITY_REPORT.md), so the GATE runs at 20 dB — the contract
     * a real room demands — while 10 dB stays a documented stress limit.
     */
    private fun withNoise(signal: FloatArray, seed: Long): FloatArray {
        val rms = sqrt(signal.map { it.toDouble() * it }.average())
        val rng = Random(seed)
        val noise = { rng.nextDouble() + rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 2.0 }
        return FloatArray(signal.size) {
            (signal[it] + noise() * 1.732 * rms / sqrt(100.0)).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private data class Tail(
        val stable: Int,
        val correct: Int,
        val medianCents: Double,
        val correctMedianCents: Double,
        val maxCents: Double,
        val inTune: Boolean,
    )

    /** Analyzes [audio]; returns settled-tail stats against [expectedPc]. */
    private fun tailStats(audio: FloatArray, expectedPc: Int): Tail {
        val analyzer = StreamingAnalyzer(SR, stateMachine = TunerStateMachine())
        val snaps = mutableListOf<dev.anima.resonance.presentation.TunerSnapshot>()
        var sawInTune = false
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + CHUNK, audio.size)
            val nowMs = end * 1000L / SR
            analyzer.processChunk(audio.copyOfRange(i, end), nowMs)?.let {
                if (it.reading.inTune) sawInTune = true
                snaps.add(it)
            }
            i = end
        }
        val perChunkMs = CHUNK.toLong() * 1000 / SR
        val keep = (300 / perChunkMs).toInt().coerceAtLeast(10)
        val tail = snaps.takeLast(keep)
        val stable = tail.filter { it.reading.note != null }
        val cents = stable.map { abs(it.reading.cents.toDouble()) }.sorted()
        // Cents on CORRECT-note frames only: at low SNR a few frames may
        // flicker to an adjacent note (identity is judged separately), and
        // their large deviations would poison the intonation median.
        val correctCents = stable.filter { it.reading.note!!.pitchClass == expectedPc }
            .map { abs(it.reading.cents.toDouble()) }.sorted()
        return Tail(
            stable = stable.size,
            correct = stable.count { it.reading.note!!.pitchClass == expectedPc },
            medianCents = if (cents.isEmpty()) 999.0 else cents[cents.size / 2],
            correctMedianCents = if (correctCents.isEmpty()) 999.0 else correctCents[correctCents.size / 2],
            maxCents = if (cents.isEmpty()) 999.0 else cents.last(),
            inTune = sawInTune,
        )
    }

    private fun sweep(variantName: String, minMedianCents: Double, minCorrectRatio: Double) {
        val medians = mutableListOf<Pair<Int, Double>>()
        for (midi in A1_MIDI..D6_MIDI) {
            val f0 = midiToHz(midi)
            val pc = midi % 12
            val audio = when (variantName) {
                "sine" -> sine(f0, SECONDS)
                "pluck" -> pluck(f0, SECONDS, seed = midi.toLong())
                "noisy" -> withNoise(sine(f0, SECONDS), seed = midi.toLong())
                else -> error("unknown variant $variantName")
            }
            val t = tailStats(audio, pc)
            assertTrue(t.stable >= 10,
                "$variantName m=$midi ${"%.2f".format(f0)}Hz: only ${t.stable} stable tail frames")
            val ratio = t.correct.toDouble() / t.stable
            assertTrue(ratio >= minCorrectRatio,
                "$variantName m=$midi ${"%.2f".format(f0)}Hz (pitch class $pc): " +
                    "correct ${t.correct}/${t.stable} frames")
            // Intonation is judged on correct-note frames: if a note ever
            // flickers to a neighbor (identity judged above), its large
            // deviation must not poison the intonation median.
            assertTrue(t.correctMedianCents <= minMedianCents,
                "$variantName m=$midi ${"%.2f".format(f0)}Hz: median |cents| (correct-note frames) " +
                    "${"%.1f".format(t.correctMedianCents)} > $minMedianCents")
            assertTrue(t.inTune, "$variantName m=$midi ${"%.2f".format(f0)}Hz: never in-tune")
            medians.add(midi to t.medianCents)
        }
        val vals = medians.map { it.second }.sorted()
        val worst = medians.maxBy { it.second }
        println("MATRIX $variantName: notes=${medians.size} median-of-medians=${"%.2f".format(vals[vals.size / 2])} " +
            "p90=${"%.2f".format(vals[(vals.size * 0.9).toInt().coerceAtMost(vals.size - 1)])} " +
            "worst=${"%.2f".format(worst.second)}@m${worst.first}")
    }

    @Test
    fun fullChromaticSweepSine() = sweep("sine", minMedianCents = 5.0, minCorrectRatio = 1.0)

    @Test
    fun fullChromaticSweepPlucked() = sweep("pluck", minMedianCents = 5.0, minCorrectRatio = 0.95)

    @Test
    fun fullChromaticSweepNoisy20dB() =
        sweep("noisy", minMedianCents = 10.0, minCorrectRatio = 1.0)

    @Test
    fun outOfRangeTonesMapToBoundariesWithoutFalseInTune() {
        for ((f0, label) in listOf(45.0 to "45Hz (below range)", 1300.0 to "1300Hz (above range)")) {
            val t = tailStats(sine(f0, SECONDS), expectedPc = -1)
            // Contract: NEVER claim in-tune out of range. If a note is
            // displayed it must be the nearest boundary note with a large
            // deviation; silence (no stable note) is the correct behavior
            // when the tone is outside the 55..1200 Hz band.
            assertTrue(!t.inTune, "$label: must not report in-tune out of range")
            if (t.stable > 0) {
                assertTrue(t.medianCents > 50.0,
                    "$label: boundary note expected clearly out of tune (|cents| > 50), got ${"%.1f".format(t.medianCents)}")
            }
        }
    }
}
