package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.PitchTracker
import dev.anima.resonance.presentation.PitchValidity
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine
import dev.anima.resonance.presentation.TunerStatus
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end acceptance through the real pipeline (chunks → analyzer →
 * detector → tracker), on deterministic synthetic strings.
 *
 * Synthetic audio is NOT proof of real-guitar performance (see
 * docs/QUALITY_REPORT.md for the honest limits and the device checklist); it is
 * proof that the mechanisms the user reported are fixed and stay fixed.
 */
class PitchPipelineAcceptanceTest {

    private val sr = PitchLabSignals.SR

    /** Evaluation windows are chosen where the modelled SNR is still ≥ 12 dB. */
    private fun report(
        audio: FloatArray,
        note: Note,
        toMs: Long,
        trueCents: Double = 0.0,
        machine: TunerStateMachine = TunerStateMachine(),
    ): PitchLab.Report = PitchLab.report(
        label = note.label,
        frames = PitchLab.run(audio, sampleRate = sr, machine = machine),
        expected = note,
        trueCents = trueCents,
        toMs = toMs,
    )

    // ── Every string, decaying ────────────────────────────────────────────

    @Test
    fun everyStringIsAcquiredPromptlyAndTrackedThroughItsDecay() {
        for ((label, f0) in PitchLabSignals.STANDARD) {
            val note = Note.fromLabel(label)
            val audio = PitchLabSignals.tone(f0, 3.2, snrDb = 25.0)
            val r = report(audio, note, toMs = 2400)
            assertTrue(r.firstCorrectMs != null, "$label: never locked")
            assertTrue(r.firstCorrectMs!! <= 250, "$label: acquisition took ${r.firstCorrectMs} ms")
            assertEquals(0, r.dropouts, "$label: ${r.dropouts} frames without a live reading while ringing")
            assertEquals(0, r.wrongNotes, "$label: displayed a different note")
            assertEquals(0, r.noteChanges, "$label: label flicker")
            assertTrue(r.medianAbsErrCents <= 2.0, "$label: median error ${r.medianAbsErrCents} cents")
            assertTrue(r.p95AbsErrCents <= 4.0, "$label: p95 error ${r.p95AbsErrCents} cents")
            assertTrue(r.inTuneReached, "$label: an in-tune string must read in tune")
        }
    }

    @Test
    fun detunedStringsReportTheirRealDeviation() {
        for (centsOff in listOf(-30.0, -8.0, 8.0, 30.0)) {
            val audio = PitchLabSignals.tone(195.997718, 3.0, cents = centsOff, snrDb = 25.0)
            val r = report(audio, Note.fromLabel("G3"), toMs = 2200, trueCents = centsOff)
            assertEquals(0, r.dropouts, "${centsOff}c: ${r.dropouts} dropouts")
            assertTrue(
                r.medianAbsErrCents <= 2.0,
                "${centsOff}c off: median error ${"%.1f".format(r.medianAbsErrCents)} cents",
            )
        }
    }

    // ── The reported symptom: lose the note after 1-2 s ───────────────────

    @Test
    fun g3WithSympatheticFourthKeepsTrackingWhileItRings() {
        val g3 = PitchLabSignals.tone(195.997718, 3.6, snrDb = 25.0)
        for (db in listOf(-10.0, -14.0)) {
            val mix = PitchLabSignals.withSympathetic(g3, 146.83, db, rampMs = 700)
            val r = report(mix, Note.fromLabel("G3"), toMs = 2800)
            assertEquals(0, r.dropouts, "G3+D3 ${db}dB: ${r.dropouts} frames lost the note")
            assertEquals(0, r.wrongNotes, "G3+D3 ${db}dB: wandered to another note")
            assertEquals(0, r.noteChanges, "G3+D3 ${db}dB: label flicker")
            assertTrue(
                r.p95AbsErrCents <= 4.0,
                "G3+D3 ${db}dB: p95 error ${r.p95AbsErrCents} cents, swing ${r.swingCents}",
            )
        }
    }

    @Test
    fun e4WithSympatheticMajorThirdKeepsTrackingWhileItRings() {
        val e4 = PitchLabSignals.tone(329.627557, 3.6, snrDb = 25.0)
        val mix = PitchLabSignals.withSympathetic(e4, 246.94, -12.0, rampMs = 700)
        val r = report(mix, Note.fromLabel("E4"), toMs = 2800)
        assertEquals(0, r.dropouts, "E4+B3: ${r.dropouts} frames lost the note (the old pipeline went silent)")
        assertEquals(0, r.wrongNotes, "E4+B3: wandered to another note")
        assertTrue(r.p95AbsErrCents <= 4.0, "E4+B3: p95 error ${r.p95AbsErrCents} cents")
    }

    @Test
    fun aDecayingStringStaysLiveUntilTheEvidenceIsGoneAndThenReleases() {
        val audio = PitchLabSignals.tone(82.406889, 4.0, snrDb = 20.0, noisePink = true)
        val frames = PitchLab.run(audio, sampleRate = sr)
        // 2 s of ringing is clearly usable; the reading must be live there.
        val ringing = frames.filter { it.ms in 500..2000 }
        assertTrue(ringing.isNotEmpty())
        val liveShare = ringing.count { it.live }.toDouble() / ringing.size
        assertTrue(liveShare > 0.97, "only ${"%.0f".format(liveShare * 100)}% of usable frames were live")

        // After the tone is gone (silence appended) the reading must go invalid.
        val withSilence = PitchLabSignals.concat(
            PitchLabSignals.tone(82.406889, 2.0, snrDb = 20.0, noisePink = true),
            PitchLabSignals.silence(1.5),
        )
        val f2 = PitchLab.run(withSilence, sampleRate = sr)
        val silenceStart = 2000L
        val invalidAt = f2.firstOrNull { it.ms > silenceStart + 40 && !it.live }?.ms
        assertTrue(invalidAt != null, "silence never invalidated the reading")
        assertTrue(
            invalidAt!! - silenceStart <= 800,
            "invalidation took ${invalidAt - silenceStart} ms after true silence",
        )
        assertTrue(f2.filter { it.ms > silenceStart + 800 }.none { it.live }, "a dead signal stayed live")
    }

    // ── Chromatic identity across a boundary ──────────────────────────────

    @Test
    fun crossingASemitoneBoundaryChangesTheLabelExactlyOnce() {
        val frames = PitchLab.run(
            PitchLabSignals.ramp(seconds = 3.0, f0 = 195.997718, cents = 200.0, startMs = 600, ms = 1400),
            sampleRate = sr,
        )
        // A 200-cent glide passes the G#3 boundary: three identities, in order,
        // never backwards.
        val labels = frames.map { it.reading.note }.filter { it != null }.distinct()
        assertEquals(
            listOf(Note.fromLabel("G3"), Note.fromLabel("G#3"), Note.fromLabel("A3")),
            labels,
            "a 200-cent glide must pass G3 → G#3 → A3, got $labels",
        )
        // No oscillation back and forth across the boundary.
        val changes = run {
            var n = 0
            var prev: Note? = null
            for (f in frames) {
                val note = f.reading.note ?: continue
                if (prev != null && note != prev) n++
                prev = note
            }
            n
        }
        assertEquals(2, changes, "label changed $changes times during one glide (expected 2)")
    }

    @Test
    fun aSlowTuningRampIsFollowedWithoutVisibleLag() {
        val audio = PitchLabSignals.ramp(seconds = 3.0, f0 = 110.0, cents = 25.0, startMs = 500, ms = 1800)
        val frames = PitchLab.run(audio, sampleRate = sr)
        var worst = 0.0
        for (f in frames) {
            if (!f.live) continue
            if (f.ms < 900 || f.ms > 2400) continue
            val truth = PitchLabSignals.rampCentsAt(25.0, 500, 1800, f.ms.toDouble())
            worst = maxOf(worst, abs(f.reading.centsFine - truth))
        }
        assertTrue(worst <= 3.0, "ramp lag ${"%.2f".format(worst)} cents (contract ≤ 3)")
    }

    // ── Presentation contract from the pipeline ───────────────────────────

    @Test
    fun snapshotValidityIsCoherentEndToEnd() {
        val audio = PitchLabSignals.concat(
            PitchLabSignals.silence(0.3),
            PitchLabSignals.tone(195.997718, 1.5, snrDb = 25.0),
            PitchLabSignals.silence(1.2),
        )
        val frames = PitchLab.run(audio, sampleRate = sr)
        var sawListening = false
        var sawLive = false
        var sawHeldOrNone = false
        var previousAge = 0L
        for (f in frames) {
            val r = f.reading
            when (r.validity) {
                PitchValidity.LIVE -> {
                    sawLive = true
                    previousAge = 0L
                    assertTrue(r.note != null && r.frequencyHz > 0.0, "live without a measurement")
                    assertEquals(0L, r.ageMs)
                    assertTrue(abs(r.centsFine - r.cents) <= 1.0, "cents and centsFine disagree")
                }
                PitchValidity.HELD, PitchValidity.NONE -> {
                    assertEquals(0.0, r.frequencyHz, "non-live reading exposed a measurement")
                    assertEquals(0, r.cents)
                    assertTrue(!r.inTune)
                    if (f.ms > 1800) sawHeldOrNone = true
                    // Age must grow inside one interruption; a new episode
                    // legitimately restarts it at 0.
                    assertTrue(previousAge == Long.MAX_VALUE || r.ageMs == Long.MAX_VALUE ||
                        r.ageMs >= previousAge || r.ageMs == 0L,
                        "age went backwards inside an interruption: $previousAge → ${r.ageMs}")
                    previousAge = r.ageMs
                }
            }
            if (r.status == TunerStatus.LISTENING) sawListening = true
        }
        assertTrue(sawListening, "the stream must start in LISTENING before any pluck")
        assertTrue(sawLive, "the tone must produce a live reading")
        assertTrue(sawHeldOrNone, "the tail silence must invalidate the reading")
        assertTrue(
            frames.last().reading.validity == PitchValidity.NONE,
            "after 1.2 s of silence the reading must be invalid, got ${frames.last().reading.validity}",
        )
    }

    @Test
    fun instrumentManualLockComparesAgainstTheLockedString() {
        val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4").map { Note.fromLabel(it) }
        val machine = TunerStateMachine().apply {
            setTargetSet(strings)
            setManualTarget(Note.fromLabel("G3"))
        }
        val audio = PitchLabSignals.tone(195.997718, 2.0, cents = 12.0, snrDb = 25.0)
        val frames = PitchLab.run(audio, sampleRate = sr, machine = machine).filter { it.ms > 600 }
        assertTrue(frames.all { it.reading.note == Note.fromLabel("G3") }, "manual lock was overridden")
        assertTrue(frames.all { abs(it.reading.centsFine - 12.0) <= 2.0 }, "manual lock changed the measurement")
    }

    @Test
    fun theTraceIsOffByDefaultAndBoundedWhenEnabled() {
        // Tracing is off unless asked for: an unwired analyzer records nothing
        // and the default trace instance stays disabled.
        val idleTrace = dev.anima.resonance.presentation.PitchTrace()
        assertTrue(!idleTrace.enabled, "traces must be off by default")
        assertTrue(idleTrace.beginFrame() == null, "a disabled trace must not even time a frame")
        val quiet = PitchLab.run(PitchLabSignals.tone(195.997718, 0.5, snrDb = 30.0), sampleRate = sr)
        assertTrue(quiet.isNotEmpty())

        val trace = dev.anima.resonance.presentation.PitchTrace(capacity = 8).apply { enabled = true }
        val analyzer = StreamingAnalyzer(sr, trace = trace)
        val audio = PitchLabSignals.tone(195.997718, 1.2, snrDb = 30.0)
        var now = 0L
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + PitchLab.CHUNK, audio.size)
            now += (end - i) * 1000L / sr
            analyzer.processChunk(audio.copyOfRange(i, end), now)
            i = end
        }
        assertTrue(trace.size == 8, "trace must stay bounded to its capacity, got ${trace.size}")
        assertTrue(trace.overwritten > 0, "a bounded ring must report what it overwrote")
        val frames = trace.snapshot()
        for (k in 1 until frames.size) {
            assertTrue(frames[k].nowMs >= frames[k - 1].nowMs, "trace timestamps must be monotonic")
        }
        assertTrue(frames.any { it.validity == PitchValidity.LIVE }, "the trace must record live frames")
        assertTrue(frames.all { it.rawReason != PitchReason.NONE || it.rawHz == 0.0 })
        assertTrue(analyzer.analyzedFrames > 0)
    }

    @Test
    fun theStateMachineIsAPureAdapterOverTheTracker() {
        // TunerStateMachine must not add, drop or reshape anything: the screen
        // contract is exactly the tracker's output.
        val machine = TunerStateMachine()
        val tracker = PitchTracker(onsetGapMs = 300)
        val rng = kotlin.random.Random(1234)
        var t = 0L
        repeat(400) {
            t += 1 + rng.nextLong(25)
            val est = if (rng.nextInt(10) < 8) {
                PitchEstimate(180.0 + rng.nextDouble() * 80.0, 0.4 + rng.nextDouble() * 0.6, PitchReason.FIRST_CROSSING)
            } else {
                PitchEstimate(0.0, 0.0, PitchReason.NONE)
            }
            val a = machine.onPitch(est, t)
            val b = tracker.onPitch(est, t)
            assertEquals(b.validity, a.validity)
            assertEquals(b.note, a.note)
            assertEquals(b.status, a.status)
            assertEquals(b.cents, a.cents)
            assertEquals(b.centsFine, a.centsFine, 1e-9)
            assertEquals(b.frequencyHz, a.frequencyHz, 1e-9)
            assertEquals(b.ageMs, a.ageMs)
            assertEquals(b.targetHz, a.targetHz, 1e-9)
        }
    }
}
