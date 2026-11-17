package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The explicit signal presentation phase + the bounded, honest hold.
 * Pins: waiting vs tracking vs lost vs mic states; one coherent header status;
 * the needle is always drawn (idle wander without a pitch); a brief interruption holds the
 * last reading faded (never live, never "in tune") and then invalidates it.
 */
class SignalPresentationTest {

    private fun snapshot(
        status: TunerStatus,
        note: Note? = null,
        freq: Double = 0.0,
        cents: Int = 0,
        inTune: Boolean = false,
    ) = TunerSnapshot(
        reading = TunerReading(status, note, freq, cents, PitchDirection.FLAT, inTune, 1.0),
        rms = 0.3,
        clipping = status == TunerStatus.CLIPPING,
        sampleRate = 48000,
    )

    private fun state(
        status: TunerStatus,
        note: Note? = null,
        freq: Double = 0.0,
        cents: Int = 0,
        inTune: Boolean = false,
        permission: MicPermissionState = MicPermissionState.GRANTED,
        captureActive: Boolean = true,
        captureError: Boolean = false,
    ) = MainScreenUi.toUiState(
        snapshot = snapshot(status, note, freq, cents, inTune),
        permission = permission,
        mode = TunerMode.CHROMATIC,
        preset = null,
        selectedStringIndex = null,
        captureActive = captureActive,
        captureError = captureError,
    )

    private val e2 = Note.fromLabel("E2")

    // ── Phase resolver ────────────────────────────────────────────────────
    @Test
    fun waitingBeforeFirstPitch() {
        val s = state(TunerStatus.LISTENING)
        assertEquals(SignalPhase.WAITING, s.signalPhase)
        assertEquals("Listening", s.micStatusText)
        assertFalse(s.meterActive)
        assertEquals("Play a string", s.statusHint)
    }

    @Test
    fun trackingWithReliablePitch() {
        val s = state(TunerStatus.MEASURED, e2, 82.4, -3)
        assertEquals(SignalPhase.TRACKING, s.signalPhase)
        assertEquals("Tracking", s.micStatusText)
        assertTrue(s.meterActive)
    }

    @Test
    fun lostAfterPreviousReadingIsNotListening() {
        val s = state(TunerStatus.STALE)
        assertEquals(SignalPhase.LOST, s.signalPhase)
        // Coherent: header says "No signal", not "Listening" beside a lost hint.
        assertEquals("No signal", s.micStatusText)
        assertFalse(s.meterActive)
        assertNull(s.noteLabel)
        assertNull(s.frequencyText)
        assertNull(s.centsText)
    }

    @Test
    fun weakAndClippingAreLostNotTracking() {
        assertEquals(SignalPhase.LOST, state(TunerStatus.INSUFFICIENT_SIGNAL).signalPhase)
        assertEquals(SignalPhase.LOST, state(TunerStatus.CLIPPING, e2, 82.4, 0).signalPhase)
        assertFalse(state(TunerStatus.CLIPPING, e2, 82.4, 0).meterActive)
    }

    @Test
    fun microphoneOffAndErrorAreDistinct() {
        val off = state(TunerStatus.LISTENING, captureActive = false)
        assertEquals(SignalPhase.MIC_OFF, off.signalPhase)
        assertEquals("Microphone off", off.micStatusText)
        val err = state(TunerStatus.LISTENING, captureError = true)
        assertEquals(SignalPhase.MIC_ERROR, err.signalPhase)
        assertEquals("Microphone unavailable", err.micStatusText)
        val denied = state(TunerStatus.LISTENING, permission = MicPermissionState.DENIED)
        assertEquals(SignalPhase.MIC_OFF, denied.signalPhase)
    }

    // ── Needle visibility ─────────────────────────────────────────────────
    @Test
    fun theNeedleIsAlwaysOnTheDial() {
        // The needle is never hidden. Without a reliable pitch it wanders on the
        // idle sway around the flat-side rest — motion, not a frozen scale value,
        // is what keeps it from reading as a measurement.
        assertTrue(dev.anima.resonance.ui.instrument.AnalogMeterGeometry.needleShown(active = true))
        assertTrue(dev.anima.resonance.ui.instrument.AnalogMeterGeometry.needleShown(active = false))
    }

    // ── Bounded hold ──────────────────────────────────────────────────────
    @Test
    fun liveReadingPassesThroughUnchanged() {
        val live = state(TunerStatus.MEASURED, e2, 82.4, -3)
        val out = SignalHold.apply(live, lastReliable = null, elapsedMs = 0)
        assertEquals(live, out)
    }

    @Test
    fun briefInterruptionHoldsLastReadingFadedAndInactive() {
        val live = state(TunerStatus.MEASURED, e2, 82.4, -3, inTune = true)
        val dropped = state(TunerStatus.UNSTABLE)
        val held = SignalHold.apply(dropped, live, elapsedMs = 100)
        assertEquals(SignalPhase.HOLDING, held.signalPhase)
        assertEquals(live.noteLabel, held.noteLabel)
        assertEquals(live.frequencyText, held.frequencyText)
        assertEquals(live.centsText, held.centsText)
        assertFalse(held.meterActive, "a held reading never drives a live needle")
        assertFalse(held.inTune, "a held reading never claims in tune")
        assertNull(held.statusHint, "no guidance hint over the fading values")
        assertTrue(held.readingAlpha in SignalHold.MIN_ALPHA..1f)
        assertTrue(held.readingAlpha < 1f, "visibly faded")
    }

    @Test
    fun holdFadesProgressivelyThenInvalidates() {
        val live = state(TunerStatus.MEASURED, e2, 82.4, -3)
        val dropped = state(TunerStatus.STALE)
        val early = SignalHold.apply(dropped, live, elapsedMs = 0)
        val late = SignalHold.apply(dropped, live, elapsedMs = SignalHold.HOLD_MS - 1)
        assertTrue(early.readingAlpha > late.readingAlpha, "alpha fades over the window")
        val expired = SignalHold.apply(dropped, live, elapsedMs = SignalHold.HOLD_MS)
        assertEquals(SignalPhase.LOST, expired.signalPhase)
        assertNull(expired.noteLabel, "after the window the readout is invalidated")
        assertFalse(expired.meterActive)
    }

    @Test
    fun noHoldWithoutAPreviousReading() {
        val dropped = state(TunerStatus.LISTENING)
        val out = SignalHold.apply(dropped, lastReliable = null, elapsedMs = 0)
        assertEquals(SignalPhase.WAITING, out.signalPhase)
        assertNull(out.noteLabel)
    }

    @Test
    fun noHoldOverMicrophoneProblems() {
        val live = state(TunerStatus.MEASURED, e2, 82.4, -3)
        val off = state(TunerStatus.LISTENING, captureActive = false)
        assertEquals(SignalPhase.MIC_OFF, SignalHold.apply(off, live, 0).signalPhase)
        val err = state(TunerStatus.LISTENING, captureError = true)
        assertEquals(SignalPhase.MIC_ERROR, SignalHold.apply(err, live, 0).signalPhase)
    }
}