package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunerConcertPitchTest {

    private fun m() = TunerStateMachine(
        hysteresisFrames = 3,
        staleMs = 600,
        inTuneCents = 5.0,
        inTuneExitCents = 8.0,
        minConfidence = 0.5,
    )

    private fun settle(m: TunerStateMachine, freq: Double, t0: Long) =
        m.feed(List(6) { i -> PitchEstimate(freq, 1.0) to (t0 + i * 20L) }).last()

    private fun TunerStateMachine.feed(frames: List<Pair<PitchEstimate, Long>>): List<TunerReading> {
        val out = ArrayList<TunerReading>(frames.size)
        for ((est, t) in frames) out += onPitch(est, t)
        return out
    }

    @Test
    fun concertChangeUpdatesTargetAndReEvaluatesInTune() {
        val t = m()
        val inTuneAt440 = settle(t, 440.0, 0L)
        assertTrue(inTuneAt440.inTune)
        // A4 reference drops to 435: the SAME 440 Hz measurement is now +29c.
        t.setConcertPitch(435.0)
        val afterChange = settle(t, 440.0, 200L)
        assertFalse(afterChange.inTune, "stale in-tune must be re-evaluated against the new target")
        assertEquals(Note.fromLabel("A4"), afterChange.note, "note identity survives a concert change")
        assertEquals(PitchDirection.SHARP, afterChange.direction)
        assertTrue(afterChange.cents in 18..21, "cents vs 435 reference (1200*log2(440/435)=+19.8c), got ${afterChange.cents}")
        // And it re-enters when the string is actually at the new reference.
        val reTuned = settle(t, 435.0, 400L)
        assertTrue(reTuned.inTune, "in-tune re-enters at the new reference")
    }

    @Test
    fun concertChangeResetsCandidateAndSmoothing() {
        val t = m()
        settle(t, 440.0, 0L)
        t.setConcertPitch(442.0)
        // Immediately a fresh stream at the new reference stabilizes quickly
        // and shows exact reference cents — no stale candidate interference.
        val r = settle(t, 442.0, 300L)
        assertEquals(Note.fromLabel("A4"), r.note)
        assertEquals(0, r.cents)
        assertTrue(r.inTune)
    }

    @Test
    fun concertPitchValidatedTo400Through480() {
        val t = m()
        t.setConcertPitch(400.0)
        t.setConcertPitch(480.0)
        t.setConcertPitch(440.0)
        assertFailsWith<IllegalArgumentException> { t.setConcertPitch(399.9) }
        assertFailsWith<IllegalArgumentException> { t.setConcertPitch(480.1) }
        assertFailsWith<IllegalArgumentException> { t.setConcertPitch(0.0) }
    }
}
