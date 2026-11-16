package dev.anima.resonance.presentation

/**
 * The explicit presentation phase of the tuner's signal state. The DSP
 * state machine ([TunerStatus]) already distinguishes waiting / unstable /
 * stale / clipping; this is the UI-facing interpretation that drives ONE
 * coherent status presentation (header lamp + readout + needle) so the user
 * never has to reconcile contradictory messages.
 *
 * - [WAITING]: microphone open, no usable pitch yet — calm guidance.
 * - [TRACKING]: a reliable live measurement — note/Hz/cents + live needle.
 * - [HOLDING]: brief interruption after a reading — the last valid reading is
 *   shown briefly, visibly faded, with the needle deactivated (never live).
 * - [LOST]: no usable pitch after a previous reading — readout invalidated,
 *   needle hidden. Normal silence between plucks, not an error.
 * - [MIC_OFF]: capture not open / permission not granted.
 * - [MIC_ERROR]: capture failure — specific explanation + recovery action.
 */
enum class SignalPhase { WAITING, TRACKING, HOLDING, LOST, MIC_OFF, MIC_ERROR }

/**
 * Pure mapping from the pipeline conditions to a [SignalPhase]. Compose-free
 * and deterministic so the whole presentation matrix is host-testable.
 */
object SignalPresentation {

    fun phase(
        status: TunerStatus,
        permission: MicPermissionState,
        captureError: Boolean,
        captureActive: Boolean,
        reliable: Boolean,
    ): SignalPhase = when {
        captureError -> SignalPhase.MIC_ERROR
        permission != MicPermissionState.GRANTED || !captureActive -> SignalPhase.MIC_OFF
        reliable -> SignalPhase.TRACKING
        status == TunerStatus.IDLE || status == TunerStatus.LISTENING -> SignalPhase.WAITING
        else -> SignalPhase.LOST
    }

    /**
     * The short header status. Distinguishes "microphone is listening" from
     * "pitch is being tracked" without contradictory messages: a lost note
     * reads "No signal" (not "Listening" alongside a "Signal lost" readout).
     */
    fun micStatusText(phase: SignalPhase): String = when (phase) {
        SignalPhase.TRACKING -> "Tracking"
        SignalPhase.WAITING -> "Listening"
        SignalPhase.HOLDING -> "Listening"
        SignalPhase.LOST -> "No signal"
        SignalPhase.MIC_OFF -> "Microphone off"
        SignalPhase.MIC_ERROR -> "Microphone unavailable"
    }

    /** The lamp is lit only while a reliable pitch is actually tracked. */
    fun lampLit(phase: SignalPhase): Boolean = phase == SignalPhase.TRACKING
}