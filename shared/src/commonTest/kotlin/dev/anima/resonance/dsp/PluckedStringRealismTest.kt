package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.TunerStateMachine
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DSP realism: a REAL guitar tone is not a sine — it is a decaying
 * harmonic stack (Karplus-Strong models exactly that: a plucked string).
 * These tests feed plucked-string audio through the full StreamingAnalyzer
 * pipeline (conditioning -> YIN -> state machine) and demand the same
 * quality the device will need: correct note, tight cents, in-tune lock.
 */
class PluckedStringRealismTest {

    private companion object {
        const val SR = 44100
        const val CHUNK = 512

        // (frequency, expected note) across the instrument range.
        // Standard tuning: E2 82.41, A2 110.0, D3 146.83, G3 196.0,
        // B3 246.94, E4 329.63 + C3 (130.81) and E5 (659.25) for coverage.
        val STRINGS = mapOf(
            82.41 to Note("E", 2),   // standard 6th
            110.0 to Note("A", 2),   // 5th
            146.83 to Note("D", 3),  // 4th
            196.0 to Note("G", 3),   // 3rd
            246.94 to Note("B", 3),  // 2nd
            329.63 to Note("E", 4),  // 1st
            130.81 to Note("C", 3),  // fill between strings
            659.25 to Note("E", 5),  // high register
        )
    }

    /**
     * Deterministic Karplus-Strong pluck: white-noise initial burst through
     * a feedback delay line (exact period via fractional interpolation —
     * y[n] = damp * ((1-α) y[n-M] + α y[n-M-1]), M = floor(sr/f0), so the
     * synthesized fundamental matches f0 to well under a cent) with damping
     * and a 1-pole low-pass edge — the classic plucked-string model
     * (decaying harmonics). Peak-normalized to 0.5 (a moderate pluck).
     */
    private fun pluck(f0: Double, seconds: Double, seed: Long): FloatArray {
        val n = (seconds * SR).toInt()
        val period = SR / f0
        val M = period.toInt().coerceAtLeast(2)
        val alpha = period - M
        val L = M + 1
        val rng = Random(seed)
        val buf = DoubleArray(L) { rng.nextDouble() - 0.5 }
        val out = FloatArray(n)
        for (i in 0 until n) {
            // +L before % because Kotlin % keeps the dividend's sign and
            // i-M is negative while the delay line is still filling (i < M).
            val a = buf[(i - M + L) % L]      // = y[i-M]
            val b = buf[(i - M - 1 + L) % L]  // = y[i-M-1]
            out[i] = a.toFloat()
            buf[i % L] = 0.996 * ((1 - alpha) * a + alpha * b)
        }
        val peak = out.maxOf { abs(it.toDouble()) }.coerceAtLeast(1e-9)
        for (i in out.indices) out[i] = (out[i] / peak * 0.5).toFloat()
        return out
    }

    /** Adds gaussian-ish background noise at ~20 dB SNR (room tone). */
    private fun withNoise(signal: FloatArray, seed: Long): FloatArray {
        val rng = Random(seed)
        val rms = kotlin.math.sqrt(signal.map { it.toDouble() * it }.average())
        val noiseAmp = rms / 10.0 // 20 dB
        // CLT sum of 4 uniforms ≈ gaussian (kotlin.random has no nextGaussian).
        val noise = { rng.nextDouble() + rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 2.0 }
        return FloatArray(signal.size) {
            (signal[it] + noise() * 1.732 * noiseAmp).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private data class Run(
        val last: List<dev.anima.resonance.presentation.TunerSnapshot>,
        val firstInTuneMs: Long?,
    )

    /** Feeds [audio] at sample-accurate wall time; keeps the final 300 ms. */
    private fun analyze(audio: FloatArray, seedNowMs: Long = 0): Run {
        val analyzer = StreamingAnalyzer(SR, stateMachine = TunerStateMachine())
        val snapshots = mutableListOf<dev.anima.resonance.presentation.TunerSnapshot>()
        var firstInTuneMs: Long? = null
        var nowMs = seedNowMs
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + CHUNK, audio.size)
            val chunk = audio.copyOfRange(i, end)
            nowMs += (end - i).toLong() * 1000 / SR
            analyzer.processChunk(chunk, nowMs)?.let {
                if (it.reading.inTune && firstInTuneMs == null) firstInTuneMs = nowMs
                snapshots.add(it)
            }
            i = end
        }
        // Keep the last ~300 ms (settled decay, past the pluck attack).
        val perChunkMs = CHUNK.toLong() * 1000 / SR
        val keep = (300 / perChunkMs).toInt().coerceAtLeast(10)
        return Run(snapshots.takeLast(keep), firstInTuneMs)
    }

    private fun assertTunes(run: Run, f0: Double, expected: Note, label: String) {
        val stable = run.last.filter { it.reading.note != null }
        assertTrue(stable.isNotEmpty(), "$label: no stable note was ever displayed")

        val correct = stable.filter { it.reading.note == expected }
        assertTrue(
            correct.size.toDouble() / stable.size >= 0.9,
            ">=90% of settled frames must show $expected ($label), got " +
                "${correct.size}/${stable.size}",
        )

        // Frequency must be within 1% of the true pluck (no octave errors).
        val medHz = stable.map { it.reading.frequencyHz }.sorted()[stable.size / 2]
        assertTrue(
            abs(medHz - f0) / f0 < 0.01,
            "median Hz $medHz not within 1% of $f0 ($label)",
        )

        // Tight intonation: median deviation under 5 cents.
        val cents = stable.map { abs(it.reading.cents.toDouble()) }.sorted()
        assertTrue(
            cents[cents.size / 2] < 5.0,
            "median |cents| ${cents[cents.size / 2]} too wide ($label)",
        )

        // The state machine must actually lock in-tune at some point.
        assertNotNull(run.firstInTuneMs, "$label: never reached in-tune lock")
    }

    @Test
    fun pluckedStringsAcrossTheRangeLockInTune() {
        STRINGS.forEach { (f0, note) ->
            val audio = pluck(f0, seconds = 2.0, seed = f0.toLong())
            assertTunes(analyze(audio), f0, note, "pluck ${note} ${f0}Hz")
        }
    }

    @Test
    fun pluckedStringUnderRoomNoiseStillLocks() {
        // 20 dB room tone over a G3 pluck. The pluck decays — near the end
        // it sits BELOW the noise floor, and the correct behavior there is
        // to report UNSTABLE, not to keep asserting. So this test judges
        // the AUDIBLE portion: a stable majority of the correct note, tight
        // cents, and at least one in-tune lock.
        val f0 = 196.0 // G3, the reference string
        val noisy = withNoise(pluck(f0, seconds = 1.0, seed = 7), seed = 99)
        val run = analyze(noisy, seedNowMs = 0)
        val stable = run.last.filter { it.reading.note != null }
        assertTrue(stable.isNotEmpty(), "noisy G3: no stable frame in the window")

        val correct = stable.filter { it.reading.note == Note("G", 3) }
        assertTrue(
            correct.size.toDouble() / stable.size >= 0.8,
            "noisy G3: >=80% of stable frames must show G3, got ${correct.size}/${stable.size}",
        )
        val cents = stable.filter { it.reading.note == Note("G", 3) }
            .map { abs(it.reading.cents.toDouble()) }.sorted()
        assertTrue(
            cents.isEmpty() || cents[cents.size / 2] < 5.0,
            "noisy G3: median |cents| too wide",
        )
        assertNotNull(run.firstInTuneMs, "noisy G3: never reached in-tune lock")
    }

    @Test
    fun successivePlucksOfDifferentStringsTrackBoth() {
        // A player checks string 6 then string 1: the analyzer must follow.
        val audio = pluck(82.41, 1.2, seed = 1) + pluck(329.63, 1.2, seed = 2)
        val run = analyze(audio)
        val stable = run.last.filter { it.reading.note != null }
        // The settled tail is the E4 pluck; it must show E4, not the old E2.
        val e4 = stable.filter { it.reading.note == Note("E", 4) }
        assertTrue(
            e4.size.toDouble() / stable.size > 0.9,
            "settled tail must show E4 (got ${stable.takeLast(5).map { it.reading.note }})",
        )
    }
}
