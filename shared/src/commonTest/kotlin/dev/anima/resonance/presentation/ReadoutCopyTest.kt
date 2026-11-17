package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Readout copy is derived from the SAME presentation phase as the header,
 * so the two can never contradict each other (the old bug: header said
 * "Listening" while a retained reading was on screen).
 */
class ReadoutCopyTest {

    @Test
    fun aHeldReadingIsNeverDescribedAsListening() {
        assertEquals("Reading held", ReadoutCopy.headerText(SignalPhase.HOLDING))
        // and it must not be mistaken for either live tracking or silence
        assertEquals("Tracking", ReadoutCopy.headerText(SignalPhase.TRACKING))
        assertEquals("Listening", ReadoutCopy.headerText(SignalPhase.WAITING))
        assertEquals("No signal", ReadoutCopy.headerText(SignalPhase.LOST))
    }

    @Test
    fun theHeaderNeverClaimsAvailabilityWithoutAPhase() {
        SignalPhase.entries.forEach { phase ->
            val text = ReadoutCopy.headerText(phase)
            if (phase != SignalPhase.WAITING) {
                assert(text.isNotBlank()) { "blank header for $phase" }
            }
        }
    }

    @Test
    fun anOffScaleReadingIsLabelledInsteadOfLookingInRange() {
        assertEquals("off scale", ReadoutCopy.scaleCaption(clamped = true))
        assertNull(ReadoutCopy.scaleCaption(clamped = false))
    }

    @Test
    fun aHeldReadingIsCaptionedAsRetained() {
        assertEquals("held", ReadoutCopy.retentionCaption(SignalPhase.HOLDING))
        assertNull(ReadoutCopy.retentionCaption(SignalPhase.TRACKING))
        assertNull(ReadoutCopy.retentionCaption(SignalPhase.WAITING))
    }

    @Test
    fun instrumentModeIsExplicitAboutWhichNoteTheCentsReferTo() {
        // A manual lock compares the measured pitch against a chosen TARGET;
        // auto/chromatic read the DETECTED note. The readout must say which.
        assertEquals("target", ReadoutCopy.noteRole(TunerMode.INSTRUMENT, locked = true))
        assertNull(ReadoutCopy.noteRole(TunerMode.INSTRUMENT, locked = false))
        assertNull(ReadoutCopy.noteRole(TunerMode.CHROMATIC, locked = false))
    }

    @Test
    fun theGuidanceLineMatchesThePhase() {
        assertEquals("Play a string", ReadoutCopy.guidance(SignalPhase.WAITING, hint = null))
        assertEquals("Signal lost — play again.", ReadoutCopy.guidance(SignalPhase.LOST, hint = "Signal lost — play again."))
        assertEquals("Reading held", ReadoutCopy.guidance(SignalPhase.HOLDING, hint = null))
        assertEquals("Microphone is off.", ReadoutCopy.guidance(SignalPhase.MIC_OFF, hint = null))
        assertEquals("Microphone is off.", ReadoutCopy.guidance(SignalPhase.MIC_OFF, hint = "Microphone is off."))
    }
}