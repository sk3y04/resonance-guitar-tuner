package dev.anima.resonance.presentation

/**
 * A bounded, honest hold for brief signal interruptions (note decay /
 * the gap between plucks). When a reliable reading drops out, the last valid
 * reading is shown for at most [HOLD_MS], visibly fading, with the needle
 * deactivated — never presented as a live measurement. After the window the
 * readout is fully invalidated (placeholders, hidden needle).
 *
 * Pure and deterministic: the caller supplies the elapsed time since the last
 * reliable reading (the host ticker drives it). No pitch values are invented;
 * only the previously displayed reliable reading is reused, clearly faded.
 */
object SignalHold {

    /** Named, bounded hold window. Beyond this the reading is invalidated. */
    const val HOLD_MS: Long = 400L

    /** Floor alpha so a held reading stays legible while clearly not live. */
    const val MIN_ALPHA: Float = 0.35f

    /**
     * Returns the state to display. A live reading passes through unchanged.
     * A drop-out within [HOLD_MS] of [lastReliable] shows that reading faded
     * (phase [SignalPhase.HOLDING], needle inactive). Otherwise the current
     * (invalidated) state passes through.
     */
    fun apply(
        current: MainScreenUiState,
        lastReliable: MainScreenUiState?,
        elapsedMs: Long,
    ): MainScreenUiState {
        if (current.meterActive) return current
        // Never hold over a microphone problem — those need their own message.
        if (current.signalPhase == SignalPhase.MIC_OFF || current.signalPhase == SignalPhase.MIC_ERROR) {
            return current
        }
        if (lastReliable == null || elapsedMs < 0L || elapsedMs >= HOLD_MS) return current
        if (lastReliable.noteLabel == null) return current
        val alpha = (1f - elapsedMs.toFloat() / HOLD_MS.toFloat()).coerceIn(MIN_ALPHA, 1f)
        return current.copy(
            signalPhase = SignalPhase.HOLDING,
            readingAlpha = alpha,
            noteLabel = lastReliable.noteLabel,
            octave = lastReliable.octave,
            frequencyText = lastReliable.frequencyText,
            centsText = lastReliable.centsText,
            directionText = lastReliable.directionText,
            // A held reading is not live: never claim "in tune", never move the
            // needle, never show a guidance hint over the fading values.
            inTune = false,
            meterActive = false,
            statusHint = null,
            micStatusText = SignalPresentation.micStatusText(SignalPhase.HOLDING),
        )
    }
}