package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Cents
import dev.anima.resonance.music.Note
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunerStateMachineTest {

    private fun tone(cents: Double, base: Double = 440.0, conf: Double = 1.0): PitchEstimate =
        PitchEstimate(base * 2.0.pow(cents / 1200.0), conf)

    private fun noPitch(conf: Double = 0.0): PitchEstimate = PitchEstimate(0.0, conf)

    private fun hz(frequencyHz: Double, conf: Double = 1.0): PitchEstimate =
        PitchEstimate(frequencyHz, conf)

    private fun machine() = TunerStateMachine(
        hysteresisFrames = 3,
        staleMs = 600,
        inTuneCents = 5.0,
        inTuneExitCents = 8.0,
        minConfidence = 0.5,
    )

    private fun TunerStateMachine.feed(frames: List<Pair<PitchEstimate, Long>>): List<TunerReading> {
        val out = ArrayList<TunerReading>(frames.size)
        for ((est, t) in frames) out += onPitch(est, t)
        return out
    }

    @Test
    fun firstPitchIsUnstableUntilHysteresisFramesAgree() {
        val m = machine()
        val r = m.feed(listOf(
            tone(0.0) to 0L,
            tone(0.0) to 20L,
            tone(0.0) to 40L,
        ))
        assertEquals(TunerStatus.UNSTABLE, r[0].status)
        assertNull(r[0].note)
        assertEquals(TunerStatus.UNSTABLE, r[1].status)
        assertEquals(TunerStatus.MEASURED, r[2].status)
        assertEquals(Note.fromLabel("A4"), r[2].note)
        assertEquals(0, r[2].cents)
    }

    @Test
    fun noteSwitchRequiresConsecutiveAgreement() {
        val m = machine()
        // stable A4
        m.feed(listOf(tone(0.0) to 0L, tone(0.0) to 20L, tone(0.0) to 40L))
        // two A#4 frames, then a break, then three consecutive
        val seq = m.feed(listOf(
            tone(100.0) to 60L,   // A#4 exactly
            tone(100.0) to 80L,
            tone(0.0) to 100L,    // break
            tone(100.0) to 120L,
            tone(100.0) to 140L,
            tone(100.0) to 160L,
        ))
        assertEquals(Note.fromLabel("A4"), seq[0].note, "2 frames must not switch target")
        assertEquals(Note.fromLabel("A4"), seq[2].note, "break must reset the candidate")
        assertEquals(Note.fromLabel("A4"), seq[3].note)
        assertEquals(Note.fromLabel("A4"), seq[4].note)
        assertEquals(Note.fromLabel("A#4"), seq[5].note, "3 consecutive frames switch target")
        assertEquals(TunerStatus.MEASURED, seq[5].status)
    }

    @Test
    fun noPitchClearsSmoothingWindow() {
        val m = machine()
        // stabilize on drifting-down pitches so the median is well below 440
        m.feed(listOf(
            tone(-15.0) to 0L,
            tone(-20.0) to 20L,
            tone(-25.0) to 40L,
            tone(-30.0) to 60L,
            tone(-30.0) to 80L,
        ))
        val r = m.feed(listOf(
            noPitch() to 100L,   // discontinuity: resets smoothing + candidate
            tone(0.0) to 120L,
        ))
        assertEquals(440.0, r[1].frequencyHz, 0.01, "smoothing must not blend across a discontinuity")
    }

    @Test
    fun goesStaleAfterSilenceWindowThenRecovers() {
        val m = machine()
        m.feed(listOf(tone(0.0) to 0L, tone(0.0) to 20L, tone(0.0) to 40L))
        val r = m.feed(listOf(
            noPitch() to 60L,    // 20 ms since last pitch: not stale yet
            noPitch() to 650L,   // 610 ms > 600 ms stale window
            tone(0.0) to 700L,
        ))
        assertEquals(TunerStatus.UNSTABLE, r[0].status)
        assertEquals(TunerStatus.STALE, r[1].status)
        assertEquals(TunerStatus.MEASURED, r[2].status, "fresh pitch recovers from STALE")
        assertEquals(Note.fromLabel("A4"), r[2].note)
    }

    @Test
    fun inTuneEnterAndExitBandsHysteresis() {
        val m = machine()
        m.feed(listOf(tone(0.0) to 0L, tone(0.0) to 20L, tone(0.0) to 40L))
        fun settle(cents: Double, t0: Long) = m.feed(List(6) { i -> tone(cents) to (t0 + i * 20L) }).last()
        val exited = settle(10.0, 80L)
        assertFalse(exited.inTune, "+10c exits the +8c exit band")
        val at7 = settle(7.0, 300L)
        assertFalse(at7.inTune, "+7c outside +5c enter band")
        assertEquals(PitchDirection.SHARP, at7.direction)
        val at3 = settle(3.0, 500L)
        assertTrue(at3.inTune, "+3c inside +5c enter band")
        assertEquals(PitchDirection.INTUNE, at3.direction)
        val at10 = settle(10.0, 700L)
        assertFalse(at10.inTune, "+10c outside +8c exit band")
        val at4 = settle(4.0, 900L)
        assertTrue(at4.inTune, "+4c re-enters")
    }

    @Test
    fun signedCentsPreservedFarOutsideMeterRange() {
        val m = machine()
        m.feed(listOf(tone(0.0) to 0L, tone(0.0) to 20L, tone(0.0) to 40L))
        // 452.0 Hz: ~+47c from A4 — outside a +/-40c meter, still A4-side of
        // the chromatic midpoint, so the full signed value must be preserved.
        val r = m.feed(List(6) { i -> PitchEstimate(452.0, 1.0) to (100L + i * 20L) }).last()
        assertEquals(Note.fromLabel("A4"), r.note)
        val expected = Cents.deviation(452.0, 440.0)
        assertEquals(expected, r.cents, "cents must be signed and unclamped")
        assertTrue(expected in 40..50, "fixture sanity: expected 40..50c, got $expected")
        assertEquals(PitchDirection.SHARP, r.direction)
        assertFalse(r.inTune)
    }

    @Test
    fun lowConfidenceIsItsOwnInsufficientSignalState() {
        val m = machine()
        val r = m.feed(listOf(
            tone(0.0, conf = 0.4) to 0L,
            tone(0.0, conf = 0.4) to 20L,
        ))
        assertNull(r[0].note)
        assertEquals(
            TunerStatus.INSUFFICIENT_SIGNAL,
            r[0].status,
            "a present-but-untrustworthy pitch must be distinguishable from a note transition",
        )
        assertEquals(0.0, r[1].frequencyHz, 1e-9)
    }

    @Test
    fun lowConfidenceAfterAStableNoteDoesNotKeepANumericReading() {
        val m = machine()
        val stable = m.feed(List(5) { i -> tone(0.0) to (i * 20L) }).last()
        assertEquals(TunerStatus.MEASURED, stable.status)
        val weak = m.onPitch(PitchEstimate(440.0, 0.2), 200L)
        assertEquals(TunerStatus.INSUFFICIENT_SIGNAL, weak.status)
        assertEquals(0.0, weak.frequencyHz, 1e-9, "a weak frame must not expose a measurement")
        assertNull(weak.note, "a weak frame must not keep presenting the old note as current")
    }

    @Test
    fun manualTargetChangesCentsNotMeasurement() {
        val m = machine()
        m.setManualTarget(Note.fromLabel("A2")) // 110.00 Hz @440
        // 105.0 Hz vs target A2: ~-81 cents — far outside any meter range.
        val r = m.feed(List(6) { i -> PitchEstimate(105.0, 1.0) to (i * 20L) }).last()
        assertEquals(Note.fromLabel("A2"), r.note, "manual target is not overridden by chromatic matching")
        assertEquals(Cents.deviation(105.0, Note.fromLabel("A2").frequency(440.0)), r.cents)
        assertTrue(r.cents < -50, "cents must be signed and preserved out-of-meter, got ${r.cents}")
        assertEquals(PitchDirection.FLAT, r.direction)
        m.setManualTarget(null)
        val after = m.feed(List(6) { i -> PitchEstimate(440.0, 1.0) to (300L + i * 20L) }).last()
        assertEquals(Note.fromLabel("A4"), after.note, "chromatic matching resumes after clear")
    }

    // G4 (392 Hz) on a guitar can excite the open G string (196 Hz, one
    // octave below) sympathetically. The combined waveform is periodic at
    // 196 Hz, so the detector reports the subharmonic while the player is
    // still holding G4. A continuous octave-down measurement must not steal
    // a lock the player already established; a real octave change (after a
    // playing gap) still must.

    @Test
    fun continuousOctaveBelowKeepsTheUpperStringLocked() {
        val m = machine()
        m.feed(List(3) { i -> hz(392.0) to i * 20L })
        val r = m.feed(List(30) { i -> hz(196.0) to (60L + i * 20L) })
        assertTrue(
            r.all { it.note == Note.fromLabel("G4") },
            "octave-down bleed must not steal the lock: ${r.map { it.note }}",
        )
        assertEquals(392.0, r.last().frequencyHz, 2.0)
        assertEquals(TunerStatus.MEASURED, r.last().status)
    }

    @Test
    fun shortNoPitchHoleDoesNotDisableOctaveFold() {
        val m = machine()
        m.feed(List(3) { i -> hz(392.0) to i * 20L })
        m.feed(listOf(noPitch() to 80L)) // 40 ms hole, still the same note
        val r = m.feed(List(5) { i -> hz(196.0) to (100L + i * 20L) })
        assertEquals(Note.fromLabel("G4"), r.last().note)
        assertEquals(TunerStatus.MEASURED, r.last().status)
    }

    @Test
    fun realOctaveDropAfterAPlayingGapSwitches() {
        val m = machine()
        m.feed(List(3) { i -> hz(392.0) to i * 20L })
        m.feed(listOf(noPitch() to 80L, noPitch() to 500L)) // 460 ms gap
        val r = m.feed(List(7) { i -> hz(196.0) to (520L + i * 20L) })
        assertEquals(Note.fromLabel("G3"), r.last().note, "a real octave drop must be followed")
    }

    @Test
    fun nonOctaveChangeStillSwitchesWhilePlaying() {
        val m = machine()
        m.feed(List(3) { i -> hz(392.0) to i * 20L })
        val r = m.feed(List(7) { i -> hz(370.0) to (60L + i * 20L) }) // F#4
        assertEquals(Note.fromLabel("F#4"), r.last().note)
    }

    @Test
    fun manualTargetAlsoRejectsOctaveBelowBleed() {
        val m = machine()
        m.setManualTarget(Note.fromLabel("G4"))
        val r = m.feed(List(6) { i -> hz(196.0) to (i * 20L) })
        assertEquals(Note.fromLabel("G4"), r.last().note)
        assertEquals(392.0, r.last().frequencyHz, 2.0)
    }
}
