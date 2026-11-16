package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Cents
import dev.anima.resonance.music.Note
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Temporal tracking + presentation contracts.
 *
 * The tracker is the layer between per-frame estimates and what the screen
 * shows. It owns four separate concepts that used to be conflated:
 *  - note identity (stable, hysteretic, harmonic-aware),
 *  - measurement validity (live / held / none) with an age in milliseconds,
 *  - the accepted pitch,
 *  - the display-smoothed pitch (log domain, time-aware, outlier-robust).
 *
 * These tests are deterministic: a virtual clock, fixed frames.
 */
class PitchTrackerTest {

    private fun hz(f: Double, conf: Double = 0.95): PitchEstimate = PitchEstimate(f, conf)
    private fun noPitch(): PitchEstimate = PitchEstimate(0.0, 0.0)
    private fun cents(dev: Double, ref: Double = 440.0): Double = ref * 2.0.pow(dev / 1200.0)
    private fun dev(a: Double, b: Double): Double = 1200.0 * ln(a / b) / ln(2.0)

    /** Feeds frames at a fixed cadence and returns every result. */
    private fun feed(
        tracker: PitchTracker,
        frames: List<PitchEstimate>,
        startMs: Long = 0,
        stepMs: Long = 21,
    ): List<TrackedPitch> = frames.mapIndexed { i, est -> tracker.onPitch(est, startMs + i * stepMs) }

    // ── Identity: no flicker, prompt switching ───────────────────────────

    @Test
    fun aSingleOctaveOutlierDoesNotChangeTheNote() {
        val t = PitchTracker()
        feed(t, List(6) { hz(196.0) })
        // One frame of pure 2nd-harmonic content (a pluck transient / a
        // sympathetic string), then back to the fundamental.
        val out = feed(t, listOf(hz(392.0), hz(196.0), hz(196.0)), startMs = 126)
        assertTrue(out.all { it.note == Note.fromLabel("G3") }, "octave outlier changed the note: ${out.map { it.note }}")
        assertTrue(out.last().validity == PitchValidity.LIVE, "tracking must stay live after the outlier")
        assertTrue(abs(out.last().centsFine) < 2.0, "cents jumped on the outlier frame: ${out.last().centsFine}")
    }

    @Test
    fun aGenuinelyNewNoteAfterAGapIsFollowedPromptly() {
        val t = PitchTracker()
        feed(t, List(8) { hz(196.0) })
        // 400 ms of silence, then D3 (a real string change).
        var now = 8 * 21L
        repeat(6) { now += 21; t.onPitch(noPitch(), now) }
        now += 400
        val out = ArrayList<TrackedPitch>()
        repeat(6) { out.add(t.onPitch(hz(146.83), now)); now += 21 }
        val switchedAfter = out.indexOfFirst { it.note == Note.fromLabel("D3") }
        assertTrue(switchedAfter in 0..2, "new note took ${switchedAfter + 1} frames (${switchedAfter * 21} ms) to be followed")
    }

    @Test
    fun sustainedConflictingEvidenceReleasesAstaleLock() {
        val t = PitchTracker()
        feed(t, List(8) { hz(196.0) })
        // The measurement has left G3 by more than a semitone and stays there:
        // the tracker must follow within the confirm window, never display a
        // note the waveform contradicts.
        val out = feed(t, List(10) { hz(293.66) }, startMs = 200)
        val switchedAfter = out.indexOfFirst { it.note == Note.fromLabel("D4") }
        assertTrue(switchedAfter in 0..3, "stale lock lasted ${switchedAfter * 21} ms")
        assertTrue(
            out.none { it.note == Note.fromLabel("G3") && abs(it.centsFine) > 200 },
            "an impossible display was emitted: ${out.filter { abs(it.centsFine) > 200 }}",
        )
    }

    @Test
    fun octaveFoldKeepsTheStringWhileItRingsAndFollowsARealOctaveChange() {
        val t = PitchTracker()
        feed(t, List(6) { hz(392.0) })
        // Continuous octave-down bleed (the combined waveform is periodic at
        // the lower octave) — still the same string.
        val bleed = feed(t, List(20) { hz(196.0) }, startMs = 130)
        assertTrue(bleed.all { it.note == Note.fromLabel("G4") }, "bleed stole the lock: ${bleed.map { it.note }}")

        // A real octave change after a playing gap is followed.
        var now = 130 + 20 * 21L
        repeat(10) { now += 21; t.onPitch(noPitch(), now) }
        now += 300
        val after = ArrayList<TrackedPitch>()
        repeat(8) { after.add(t.onPitch(hz(196.0), now)); now += 21 }
        assertEquals(Note.fromLabel("G3"), after.last().note, "a real octave change must be followed")
    }

    // ── Validity: live / held / none, driven by accepted evidence ────────

    @Test
    fun liveReadingsRefreshAndInterruptionsHoldThenExpire() {
        val t = PitchTracker()
        val live = feed(t, List(6) { hz(196.0) }).last()
        assertEquals(PitchValidity.LIVE, live.validity)
        assertEquals(0L, live.ageMs)
        assertEquals(Note.fromLabel("G3"), live.note)

        // 200 ms of no evidence: still holdable (faded by the UI), not live.
        var now = 6 * 21L
        var held: TrackedPitch? = null
        repeat(10) { now += 21; held = t.onPitch(noPitch(), now) }
        assertEquals(PitchValidity.HELD, held!!.validity, "a brief interruption must stay holdable")
        assertTrue(held.ageMs >= 200, "age must report the real elapsed time, got ${held.ageMs}")
        assertEquals(0.0, held.frequencyHz, "a held reading must not present a measurement")
        assertEquals(null, held.note, "a held reading must not present a live note")

        // Beyond the hold window: nothing to show.
        repeat(20) { now += 21; held = t.onPitch(noPitch(), now) }
        assertEquals(PitchValidity.NONE, held!!.validity)
        assertEquals(TunerStatus.STALE, held.status)
    }

    @Test
    fun heldReadingsDoNotRefreshThemselves() {
        val t = PitchTracker()
        feed(t, List(6) { hz(196.0) })
        var now = 6 * 21L
        // 3 seconds of silence: the age must keep growing, never reset.
        var last: TrackedPitch? = null
        repeat(140) { now += 21; last = t.onPitch(noPitch(), now) }
        assertTrue(last!!.ageMs > 2900, "a held/invalid reading refreshed its own validity: age ${last!!.ageMs}")
    }

    @Test
    fun decayToTheSustainFloorKeepsTrackingThenReleases() {
        val t = PitchTracker()
        feed(t, List(8) { hz(196.0) })
        // A decaying string: the estimate stays correct but its confidence
        // falls. Above the sustain floor it is still live evidence.
        val decay = feed(t, List(6) { hz(195.9, conf = 0.40) }, startMs = 170)
        assertTrue(decay.all { it.validity == PitchValidity.LIVE }, "usable decay was dropped: ${decay.map { it.validity }}")
        // Below it, the reading is honestly released.
        val gone = feed(t, listOf(hz(195.9, conf = 0.20)), startMs = 300).last()
        assertTrue(gone.validity != PitchValidity.LIVE, "a below-floor frame was presented as live")
        assertEquals(0.0, gone.frequencyHz)
    }

    @Test
    fun silenceInvalidatesOnlyAfterActualEvidenceStops() {
        val t = PitchTracker()
        // 2 s of steady tracking, then silence.
        var now = 0L
        repeat(95) { t.onPitch(hz(196.0), now); now += 21 }
        val silenceStart = now
        var invalidAt: Long? = null
        while (now < silenceStart + 3000) {
            now += 21
            val r = t.onPitch(noPitch(), now)
            if (r.validity == PitchValidity.NONE && invalidAt == null) invalidAt = now - silenceStart
        }
        assertTrue(invalidAt != null && invalidAt!! <= 800, "invalidation took ${invalidAt}ms (contract ≤ 800)")
    }

    // ── Smoothing: smooth but not sticky ─────────────────────────────────

    @Test
    fun smallJitterIsSmoothedWithoutAQuantizedDisplay() {
        val t = PitchTracker()
        feed(t, List(6) { hz(196.0) })
        // +-1.5 cents of alternating jitter.
        val out = feed(
            t,
            List(40) { i -> hz(cents(if (i % 2 == 0) 1.5 else -1.5, 195.997718)) },
            startMs = 130,
        ).drop(8)
        val values = out.map { it.centsFine }
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        assertTrue(sd <= 0.45, "display jitter ${"%.2f".format(sd)} cents (contract ≤ 0.45 vs 1.5 raw)")
        assertTrue(values.any { abs(it - Math.round(it)) > 0.05 }, "the display value is quantized: $values")
    }

    @Test
    fun aRealPitchStepIsFollowedWithinTwoFrames() {
        val t = PitchTracker()
        feed(t, List(10) { hz(195.997718) })
        val out = ArrayList<TrackedPitch>()
        var now = 10 * 21L
        repeat(6) { out.add(t.onPitch(hz(cents(18.0, 195.997718)), now)); now += 21 }
        val reach = out.indexOfFirst { abs(it.centsFine - 18.0) <= 1.0 }
        assertTrue(reach in 0..2, "an 18-cent step took ${reach + 1} frames to be displayed")
    }

    @Test
    fun aSlowTuningRampIsTrackedWithoutVisibleLag() {
        val t = PitchTracker()
        feed(t, List(10) { hz(195.997718) })
        // 20 cents over 2 s = 0.21 cents per 21 ms frame.
        var now = 10 * 21L
        var worst = 0.0
        for (i in 1..90) {
            val trueCents = 0.21 * i
            val shown = t.onPitch(hz(cents(trueCents, 195.997718)), now)
            now += 21
            if (i > 12) worst = maxOf(worst, abs(shown.centsFine - trueCents))
        }
        assertTrue(worst <= 2.0, "ramp lag ${"%.2f".format(worst)} cents (contract ≤ 2)")
    }

    @Test
    fun smoothingBehavesTheSameAtDifferentFrameCadences() {
        fun stepResponse(stepMs: Long): Int {
            val t = PitchTracker()
            var now = 0L
            repeat(10) { t.onPitch(hz(195.997718), now); now += stepMs }
            var frames = 0
            while (frames < 10) {
                val r = t.onPitch(hz(cents(15.0, 195.997718)), now)
                now += stepMs
                frames++
                if (abs(r.centsFine - 15.0) <= 1.0) return frames
            }
            return 99
        }
        val fast = stepResponse(11)
        val slow = stepResponse(21)
        assertTrue(fast <= 4 && slow <= 3, "step response depends on cadence: ${fast} frames @11ms, ${slow} @21ms")
        assertTrue(fast * 11 <= 70 && slow * 21 <= 70, "step response exceeded 70 ms: ${fast * 11}/${slow * 21}")
    }

    @Test
    fun anInvalidGapIsNeverInterpolatedAcross() {
        val t = PitchTracker()
        feed(t, List(8) { hz(195.997718) })
        var now = 8 * 21L
        // A gap, then a different pitch: the display must not slide across it.
        repeat(30) { now += 21; t.onPitch(noPitch(), now) }
        val resumed = t.onPitch(hz(cents(30.0, 195.997718)), now + 21)
        assertTrue(abs(resumed.centsFine - 30.0) <= 1.0, "the reading slid across an invalid gap: ${resumed.centsFine}")
    }

    // ── References and targets ───────────────────────────────────────────

    @Test
    fun centsFollowTheConfiguredReferencePitch() {
        val t = PitchTracker()
        t.setConcertPitch(432.0)
        val a4 = 432.0
        val out = feed(t, List(8) { hz(cents(0.0, a4)) }).last()
        assertEquals(Note.fromLabel("A4"), out.note)
        assertTrue(abs(out.centsFine) <= 0.5, "cents must be measured against A4=432, got ${out.centsFine}")
        assertTrue(abs(out.targetHz - 432.0) < 0.01, "target must follow the reference, got ${out.targetHz}")
    }

    @Test
    fun manualTargetChangesCentsNotTheMeasurement() {
        val t = PitchTracker()
        t.setManualTarget(Note.fromLabel("A2")) // 110 Hz @440
        val out = feed(t, List(8) { hz(105.0) }).last()
        assertEquals(Note.fromLabel("A2"), out.note, "a manual target is not overridden by matching")
        assertEquals(Cents.deviation(105.0, 110.0).toDouble(), out.centsFine, 0.6)
        assertTrue(abs(110.0 - out.targetHz) < 0.1)
    }

    @Test
    fun instrumentTargetSetRestrictsNoteIdentity() {
        val t = PitchTracker()
        t.setTargetSet(listOf("E2", "A2", "D3", "G3", "B3", "E4").map { Note.fromLabel(it) })
        // 207 Hz is nearer to G3 (196) than to B3 (247) but is not a target.
        val out = feed(t, List(8) { hz(207.0) }).last()
        assertTrue(out.note == Note.fromLabel("G3") || out.note == Note.fromLabel("B3"), "note ${out.note} outside the set")
        assertTrue(abs(out.centsFine) > 40, "a far-off string must show a large deviation")
    }

    @Test
    fun referenceChangeDoesNotRenewAStaleNote() {
        val t = PitchTracker()
        feed(t, List(8) { hz(196.0) })
        var now = 8 * 21L
        var worstRun = 0
        var run = 0
        repeat(40) {
            var r = t.onPitch(hz(1090.0), now)
            now += 21
            // A reference nudge must not reset the pending identity change.
            t.setConcertPitch(if (it % 2 == 0) 438.0 else 442.0)
            r = t.onPitch(hz(1090.0), now)
            now += 21
            if (r.note != null && abs(r.centsFine) > 50) {
                run++
                worstRun = maxOf(worstRun, run)
            } else {
                run = 0
            }
        }
        assertTrue(worstRun <= 2, "a stale note survived $worstRun consecutive frames of contradictory evidence")
    }

    // ── Contract consistency ─────────────────────────────────────────────

    @Test
    fun theReadingContractIsNeverSelfContradictory() {
        val t = PitchTracker()
        val rng = kotlin.random.Random(0x5EED)
        var now = 0L
        repeat(2000) {
            now += 1 + rng.nextLong(30)
            val present = rng.nextInt(10) < 8
            val est = if (present) {
                PitchEstimate(55.0 + rng.nextDouble() * 1145.0, 0.2 + rng.nextDouble() * 0.8)
            } else {
                PitchEstimate(0.0, 0.0)
            }
            if (it % 71 == 0) t.setConcertPitch(430.0 + rng.nextDouble() * 20.0)
            if (it % 97 == 0) t.setManualTarget(if (rng.nextBoolean()) null else Note.fromLabel("G3"))
            val r = t.onPitch(est, now)
            when (r.validity) {
                PitchValidity.LIVE -> {
                    assertTrue(r.note != null, "live reading without a note")
                    assertTrue(r.frequencyHz > 0.0, "live reading without a frequency")
                    assertEquals(0L, r.ageMs, "a live reading must be fresh")
                    assertEquals(r.centsFine, r.centsFine, "cents must be finite")
                    assertTrue(r.centsFine.isFinite())
                    assertTrue(abs(r.centsFine - Cents.deviation(r.frequencyHz, r.targetHz)) <= 1.2,
                        "cents (${r.centsFine}) disagree with the reported frequency/target")
                }
                PitchValidity.HELD, PitchValidity.NONE -> {
                    assertEquals(0.0, r.frequencyHz, "a non-live reading presented a measurement")
                    assertEquals(0, r.cents, "a non-live reading presented cents")
                    assertTrue(!r.inTune, "a non-live reading claimed in-tune")
                    assertTrue(r.ageMs >= 0)
                }
            }
            if (r.validity == PitchValidity.NONE) assertTrue(r.note == null, "no validity but a note was presented")
        }
    }
}