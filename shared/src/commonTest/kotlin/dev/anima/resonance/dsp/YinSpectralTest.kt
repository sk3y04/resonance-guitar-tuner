package dev.anima.resonance.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Detector accuracy contracts for the failure modes measured in the pitch lab
 * (docs/QUALITY_REPORT.md, pitch-tracking stability section):
 *
 *  1. Inharmonic (stiff) strings: a pure period estimate locks a period that
 *     is pulled by the stretched partials, reading 7-11 cents SHARP on
 *     E2/A2/G3 at B = 4e-4 (measured through the full pipeline).
 *  2. Sympathetic mixes: a second string tuned a fourth above/below (or a
 *     major third) makes the waveform aperiodic inside the search band. The
 *     dip then sits off the played period and moves with the beat phase —
 *     measured -10 to -28 cents with a 20-cent swing on G3+D3 at -14 dB, and
 *     a complete loss of the reading on E4+B3 at -12 dB (no dip passes).
 *  3. The fix must not turn noise into pitch.
 *
 * The contract is expressed on single frames so the detector is judged on its
 * own, independently of the temporal tracker.
 */
class YinSpectralTest {

    private val sr = 48_000
    private val yin = Yin()

    private fun cents(hz: Double, ref: Double): Double = 1200.0 * ln(hz / ref) / ln(2.0)

    /** Frame ending at [ms] from a signal, DC-removed (what the analyzer feeds). */
    private fun frameAt(audio: FloatArray, ms: Long, size: Int = 4096): FloatArray {
        val end = (ms * sr / 1000).toInt().coerceIn(size, audio.size)
        return Conditioning.removeDc(audio.copyOfRange(end - size, end))
    }

    // ── 1. Inharmonic strings ────────────────────────────────────────────

    @Test
    fun inharmonicStringsAreNotReportedSharp() {
        // B = 4e-4 is the stiff end of a real wound bass string.
        for ((label, f0) in listOf("E2" to 82.406889, "A2" to 110.0, "G3" to 195.997718)) {
            val audio = PitchLabSignals.tone(f0, 1.2, snrDb = 28.0, inharmonicity = 4e-4)
            val est = yin.detect(frameAt(audio, 800), sr)
            assertTrue(est.frequencyHz > 0.0, "$label: no pitch on an inharmonic string")
            val dev = cents(est.frequencyHz, f0)
            assertTrue(
                abs(dev) <= 2.0,
                "$label inharmonic: reported ${"%.2f".format(est.frequencyHz)} Hz " +
                    "(${"%.1f".format(dev)} cents, contract ±2)",
            )
        }
    }

    // ── 2. Sympathetic-string mixes ──────────────────────────────────────

    @Test
    fun sympatheticFourthMixStaysCenteredOnThePlayedNote() {
        // G3 (196 Hz) excites the open D string (146.83 Hz). 196:147 = 4:3, so
        // the mix is only periodic at 49 Hz — outside the 55 Hz band edge.
        val g3 = PitchLabSignals.tone(195.997718, 4.8, snrDb = 25.0)
        for (db in listOf(-10.0, -14.0)) {
            val mix = PitchLabSignals.withSympathetic(g3, 146.83, db, rampMs = 700)
            val devs = ArrayList<Double>()
            for (ms in listOf(1200L, 1600L, 2000L, 2600L, 3200L, 3800L, 4300L)) {
                val est = yin.detect(frameAt(mix, ms), sr)
                assertTrue(est.frequencyHz > 0.0, "G3+D3 ${db}dB at ${ms}ms: lost the note entirely")
                devs.add(cents(est.frequencyHz, 195.997718))
                assertTrue(
                    abs(devs.last()) <= 3.0,
                    "G3+D3 ${db}dB at ${ms}ms: ${"%.1f".format(devs.last())} cents (contract ±3)",
                )
            }
            // The bias must not breathe with the beat phase.
            // The beat-phase sensitivity is the user-visible symptom: the
            // reading used to breathe over ~20 cents as the sympathetic string
            // built and rang. Measured after the harmonic refinement: 3.6 cents at -10 dB.
            val spread = devs.max() - devs.min()
            assertTrue(spread <= 4.0, "G3+D3 ${db}dB: ${"%.1f".format(spread)} cent swing across the ring")
        }
    }

    @Test
    fun sympatheticMajorThirdMixIsNotSilenced() {
        // E4 (329.63) + B3 (246.94) at -12 dB: 988.9 Hz (E4's 3rd partial) sits
        // ~2 cents from B3's 4th harmonic (987.8), so no in-band lag is
        // periodic and every guard rejects the subharmonic. The played note is
        // still overwhelmingly present in the spectrum.
        val e4 = PitchLabSignals.tone(329.627557, 4.0, snrDb = 25.0)
        val mix = PitchLabSignals.withSympathetic(e4, 246.94, -12.0, rampMs = 700)
        for (ms in listOf(2000L, 3000L, 3500L)) {
            val est = yin.detect(frameAt(mix, ms), sr)
            assertTrue(est.frequencyHz > 0.0, "E4+B3 at ${ms}ms: no pitch while the note rings")
            val dev = cents(est.frequencyHz, 329.627557)
            assertTrue(
                abs(dev) <= 5.0,
                "E4+B3 at ${ms}ms: ${"%.2f".format(est.frequencyHz)} Hz " +
                    "(${"%.1f".format(dev)} cents, contract ±5)",
            )
            assertTrue(est.confidence >= 0.5, "E4+B3 at ${ms}ms: confidence ${est.confidence} below the floor")
        }
    }

    // ── 3. No false pitch ────────────────────────────────────────────────

    @Test
    fun spectralPathsNeverInventPitchFromNoise() {
        var falsePitches = 0
        for (seed in 0 until 30) {
            val white = PitchLabSignals.noise(0.4, rms = 0.05, seed = seed)
            val pink = PitchLabSignals.pink(0.4, rms = 0.05, seed = seed)
            for (audio in listOf(white, pink)) {
                val est = yin.detect(frameAt(audio, 300), sr)
                if (est.frequencyHz > 0.0 && est.confidence >= 0.5) falsePitches++
            }
        }
        assertEquals(0, falsePitches, "noise produced a confident pitch")
    }

    @Test
    fun silenceNeverProducesPitch() {
        val silence = PitchLabSignals.silence(0.5)
        val est = yin.detect(frameAt(silence, 300), sr)
        assertEquals(0.0, est.frequencyHz)
    }

    // ── 4. Clean tones keep their accuracy ───────────────────────────────

    @Test
    fun cleanTonesKeepTheirAccuracy() {
        // 4096-sample windows: the refinement must not introduce the
        // window-alignment bias it can (a rectangular window measured −5.2
        // cents on a clean 82.4 Hz sine before the Hann taper).
        for (f0 in listOf(82.406889, 110.0, 146.832384, 195.997718, 246.941651, 329.627557, 440.0, 1174.66)) {
            val audio = PitchLabSignals.tone(f0, 0.6, snrDb = 60.0, timbre = PitchLabSignals.Timbre(partials = 1))
            val est = yin.detect(frameAt(audio, 400), sr)
            val dev = cents(est.frequencyHz, f0)
            assertTrue(
                abs(dev) <= 1.0,
                "clean $f0 Hz: reported ${"%.3f".format(est.frequencyHz)} (${"%.2f".format(dev)} cents, contract ±1)",
            )
            assertTrue(est.confidence > 0.7, "clean $f0 Hz confidence ${est.confidence}")
        }
    }

    @Test
    fun detunedStringReportsTheTrueDeviation() {
        for (centsOff in listOf(-40.0, -15.0, -1.0, 1.0, 15.0, 40.0)) {
            val f0 = 195.997718
            val audio = PitchLabSignals.tone(f0, 0.8, cents = centsOff, snrDb = 30.0)
            val est = yin.detect(frameAt(audio, 500), sr)
            val dev = cents(est.frequencyHz, f0)
            assertTrue(
                abs(dev - centsOff) <= 1.5,
                "detuned ${centsOff}c: reported ${"%.1f".format(dev)}c (error ${"%.1f".format(dev - centsOff)})",
            )
        }
    }

    @Test
    fun frameToFrameEstimateIsStableOnARingingString() {
        // The user-visible symptom: a steady ringing note whose reading moves.
        // Raw detector noise on a 20 dB pink floor must stay small enough that
        // the presentation smoother has something honest to work with.
        val audio = PitchLabSignals.tone(82.406889, 2.0, snrDb = 20.0, noisePink = true)
        val devs = ArrayList<Double>()
        var ms = 400L
        while (ms <= 1600L) {
            val est = yin.detect(frameAt(audio, ms), sr)
            if (est.frequencyHz > 0.0) devs.add(cents(est.frequencyHz, 82.406889))
            ms += 100L
        }
        assertTrue(devs.size >= 10, "too few estimates: ${devs.size}")
        val mean = devs.average()
        val sd = kotlin.math.sqrt(devs.sumOf { (it - mean) * (it - mean) } / devs.size)
        // Measured 2.86 cents raw (was 6.4 for the old *displayed* value). The
        // remaining noise is a property of a 4096-sample window at 82 Hz in a
        // 20 dB pink floor; the presentation smoother takes it below 1 cent.
        assertTrue(sd <= 3.5, "E2 @20 dB: raw estimate std ${"%.2f".format(sd)} cents (contract ≤3.5)")
    }

    // ── 5. Diagnostics: the detector states why it answered ───────────────

    @Test
    fun estimatesCarryTheirSelectionReason() {
        val clean = PitchLabSignals.tone(195.997718, 0.6, snrDb = 40.0)
        val cleanEst = yin.detect(frameAt(clean, 400), sr)
        assertEquals(PitchReason.FIRST_CROSSING, cleanEst.reason, "clean tone follows the classic rule")

        // A mix that is aperiodic in band must still answer, and must say
        // which rule answered (all four rules carry real information for
        // diagnostics; NONE means "nothing passed").
        val e4 = PitchLabSignals.tone(329.627557, 2.0, snrDb = 25.0)
        val mix = PitchLabSignals.withSympathetic(e4, 246.94, -12.0, rampMs = 700)
        val mixEst = yin.detect(frameAt(mix, 3000), sr)
        assertTrue(mixEst.frequencyHz > 0.0, "the mix lost its pitch")
        assertTrue(mixEst.reason != PitchReason.NONE, "an answered frame must carry a reason")

        // An irrational (non-sympathetic) second tone is the pure spectral
        // case: no lag in band is periodic, the spectrum is clear.
        val played = PitchLabSignals.tone(195.997718, 0.6, snrDb = 80.0)
        val other = PitchLabSignals.tone(152.3, 0.6, snrDb = 80.0, peak = 0.5 * Math.pow(10.0, -8.0 / 20.0))
        val aperiodic = FloatArray(played.size) { (played[it] + other[it]).toFloat() }
        val aperEst = yin.detect(frameAt(aperiodic, 400), sr)
        assertTrue(
            abs(cents(aperEst.frequencyHz, 195.997718)) <= 5.0,
            "aperiodic mix: ${"%.2f".format(aperEst.frequencyHz)} Hz " +
                "(${"%.1f".format(cents(aperEst.frequencyHz, 195.997718))} cents)",
        )

        val noise = PitchLabSignals.noise(0.5, rms = 0.05, seed = 3)
        assertEquals(PitchReason.NONE, yin.detect(frameAt(noise, 300), sr).reason)
    }

    @Test
    fun randomFramesNeverThrowOrEscapeTheBand() {
        val rng = Random(0xBEEF)
        val tri = Yin()
        repeat(60) {
            val frame = FloatArray(4096) {
                ((rng.nextDouble() * 2.0 - 1.0) * rng.nextDouble()).toFloat()
            }
            val est = tri.detect(frame, sr)
            assertTrue(est.frequencyHz.isFinite() && est.frequencyHz >= 0.0)
            if (est.frequencyHz > 0.0) {
                assertTrue(
                    est.frequencyHz in 55.0..1200.0,
                    "estimate ${est.frequencyHz} escaped the 55..1200 Hz band",
                )
            }
            assertTrue(est.confidence in 0.0..1.0, "confidence ${est.confidence} out of range")
        }
    }
}