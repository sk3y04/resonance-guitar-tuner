package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Note
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Instrument-mode AUTO target-string selection ("automatic
 * target-string selection and manual string lock"). With a target set
 * (a preset's strings), nearest matching is restricted to those strings;
 * a manual lock still overrides the set.
 */
class TargetSetTest {

    private val standard = listOf(
        Note("E", 2), Note("A", 2), Note("D", 3), Note("G", 3), Note("B", 3), Note("E", 4),
    )

    private fun machine() = TunerStateMachine(
        hysteresisFrames = 3,
        inTuneCents = 5.0,
        inTuneExitCents = 8.0,
    )

    private fun TunerStateMachine.feedHz(hz: Double, frames: Int = 6): List<TunerReading> =
        (0 until frames).map { onPitch(PitchEstimate(hz, 1.0), (it * 20).toLong()) }

    private fun freq(note: String, octave: Int, cents: Double = 0.0): Double =
        Note(note, octave).frequency(440.0) * 2.0.pow(cents / 1200.0)

    @Test
    fun targetSetAutoSelectsTheNearestString() {
        val m = machine()
        m.setTargetSet(standard)
        val r = m.feedHz(freq("G", 3)).last()
        assertEquals(TunerStatus.MEASURED, r.status)
        assertEquals(Note("G", 3), r.note)
        assertTrue(r.inTune)
    }

    @Test
    fun targetSetRestrictsMatchingAwayFromChromaticNeighbours() {
        // A#4 exists in chromatic space but is NOT a standard string.
        // Auto-selection must land on the nearest STRING (E4), not A#4.
        val m = machine()
        m.setTargetSet(standard)
        val r = m.feedHz(freq("A#", 4)).last()
        assertEquals(TunerStatus.MEASURED, r.status)
        assertEquals(Note("E", 4), r.note)
        assertTrue(r.cents >= 590 && r.cents <= 610, "A#4 vs E4 target is +600 cents, got ${r.cents}")
        assertFalse(r.inTune)
    }

    @Test
    fun manualLockOverridesTheTargetSet() {
        val m = machine()
        m.setTargetSet(standard)
        m.setManualTarget(Note("E", 2))
        val r = m.feedHz(freq("G", 3)).last()
        assertEquals(Note("E", 2), r.note)
        assertTrue(r.cents > 800, "G3 vs locked E2 is ~+863..+1500, got ${r.cents}")
        assertFalse(r.inTune)
    }

    @Test
    fun clearingTheTargetSetRestoresChromaticMatching() {
        val m = machine()
        m.setTargetSet(standard)
        m.setTargetSet(null)
        val r = m.feedHz(freq("A#", 4)).last()
        assertEquals(Note.fromLabel("A#4"), r.note)
        assertTrue(r.inTune)
    }
}
