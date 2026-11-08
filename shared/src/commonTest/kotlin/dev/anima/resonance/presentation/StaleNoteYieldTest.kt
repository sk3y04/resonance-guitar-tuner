package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finding (fuzz: 20k random frames + setting changes): the state
 * machine could keep displaying a note whose smoothed measurement had
 * drifted up to ~40 semitones away — the stale stable note survived because
 * rapid target/concert-pitch changes reset the hysteresis counter before it
 * could re-lock on the new pitch, while the 5-frame smoothing ring filled
 * with the new (far) frequency. A tuner displaying A1 with +4955¢ is wrong:
 * when the measurement has left the displayed note's semitone, the note is
 * not what the player is hearing and the reading must go unstable.
 */
class StaleNoteYieldTest {

    private val rng = Random(0x5EED1L)

    @Test
    fun staleNoteCannotDisplayAnImpossibleDeviation() {
        val sm = TunerStateMachine()
        var t = 0L
        // Lock A1 (55 Hz).
        repeat(8) {
            t += 11
            sm.onPitch(PitchEstimate(55.0, 0.95), t)
        }
        assertEquals(Note.fromLabel("A1"), sm.onPitch(PitchEstimate(55.0, 0.95), t + 11).note)
        t += 11

        // The signal jumps ~2 octaves higher (1090 Hz) while the user keeps
        // nudging the A4 reference (legal in-band changes). Each change
        // resets the candidate counter, so under the pre-fix behavior the
        // old A1 lock survives forever and the smoothed cents run away.
        // Contract: an out-of-semitone display may last at most one full
        // re-lock window (hysteresisFrames + 1 frames), never unbounded.
        var lastRef = 440.0
        var hugeRun = 0
        var maxHugeRun = 0
        var last: TunerReading? = null
        repeat(40) {
            repeat(2) {
                t += 11
                last = sm.onPitch(PitchEstimate(1090.0, 0.95), t)
            }
            t += 11
            lastRef = 430.0 + rng.nextDouble() * 20.0
            sm.setConcertPitch(lastRef)
            last?.let { r ->
                val huge = r.note != null && kotlin.math.abs(r.cents) > 50
                hugeRun = if (huge) hugeRun + 1 else 0
                maxHugeRun = maxOf(maxHugeRun, hugeRun)
            }
        }
        // Contract: a displayed note may never deviate from the measurement
        // by more than a semitone for longer than one re-lock window
        // (pre-fix: the run lasted the entire storm — measured 5210¢).
        assertNotNull("a reading must be produced", last)
        assertTrue("stale display lasted $maxHugeRun frames (max ${TunerStateMachine().hysteresisFrames + 1})",
            maxHugeRun <= TunerStateMachine().hysteresisFrames + 1)
        // After the storm, clean frames must lock the NEW pitch region —
        // never the stale A1.
        repeat(8) {
            t += 11
            last = sm.onPitch(PitchEstimate(1090.0, 0.95), t)
        }
        assertNotNull(last)
        val finalNote = last!!.note
        if (finalNote != null) {
            assertTrue("1090 Hz must lock C#6 or C6, got $finalNote",
                finalNote == Note.fromLabel("C#6") || finalNote == Note.fromLabel("C6"))
            assertTrue("final lock must be within tolerance (${last!!.cents}¢)", kotlin.math.abs(last!!.cents) <= 50)
        }
    }
}
