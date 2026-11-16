package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The status strip must distinguish microphone activity from valid pitch
 * tracking. "Listening" means waiting for a reliable reading; a live reading
 * shows a tracking status (never a redundant "In tune" — that lives in the
 * readout). Lost/weak signal is an honest non-measuring state; mic off/error
 * are distinct and actionable.
 */
class P15StatusWordingTest {

    private fun snapshot(status: TunerStatus, note: Note? = null, freq: Double = 0.0, cents: Int = 0) =
        TunerSnapshot(
            reading = TunerReading(status, note, freq, cents, PitchDirection.FLAT, false, 1.0),
            rms = 0.3,
            clipping = status == TunerStatus.CLIPPING,
            sampleRate = 48000,
        )

    private fun state(
        status: TunerStatus,
        note: Note? = null,
        freq: Double = 0.0,
        cents: Int = 0,
        captureActive: Boolean = true,
        captureError: Boolean = false,
    ) = MainScreenUi.toUiState(
        snapshot = snapshot(status, note, freq, cents),
        permission = MicPermissionState.GRANTED,
        mode = TunerMode.CHROMATIC,
        preset = null,
        selectedStringIndex = null,
        captureActive = captureActive,
        captureError = captureError,
    )

    @Test
    fun validReadingShowsTrackingNotListening() {
        val s = state(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3)
        assertEquals("Tracking", s.micStatusText)
        assertEquals(true, s.meterActive)
    }

    @Test
    fun inTuneReadingDoesNotRepeatInTuneInTheStrip() {
        val s = MainScreenUi.toUiState(
            snapshot = TunerSnapshot(
                reading = TunerReading(TunerStatus.MEASURED, Note.fromLabel("A4"), 440.0, 0, PitchDirection.INTUNE, true, 1.0),
                rms = 0.3, clipping = false, sampleRate = 48000,
            ),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC, preset = null, selectedStringIndex = null,
        )
        assertEquals("Tracking", s.micStatusText)
        assertEquals("In tune", s.directionText) // the readout carries "In tune", once
    }

    @Test
    fun waitingIsListeningButLostSignalIsCoherent() {
        // Waiting reads "Listening"; a lost note reads "No signal" so the
        // header never contradicts the readout's lost/weak guidance.
        assertEquals("Listening", state(TunerStatus.LISTENING).micStatusText)
        assertEquals("No signal", state(TunerStatus.STALE).micStatusText)
        assertEquals("No signal", state(TunerStatus.INSUFFICIENT_SIGNAL).micStatusText)
        assertEquals(false, state(TunerStatus.STALE).meterActive)
    }

    @Test
    fun micOffAndErrorAreDistinct() {
        assertEquals("Microphone off", state(TunerStatus.LISTENING, captureActive = false).micStatusText)
        assertEquals("Microphone unavailable", state(TunerStatus.LISTENING, captureError = true).micStatusText)
    }
}