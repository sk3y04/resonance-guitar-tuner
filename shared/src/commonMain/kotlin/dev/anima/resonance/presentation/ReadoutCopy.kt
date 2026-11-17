package dev.anima.resonance.presentation

/**
 * The copy shown next to the measurement, derived from the SAME
 * [SignalPhase] as the header.
 *
 * This exists because the previous screen could show "Listening" while a
 * retained (held) reading — note, Hz, cents, "Tune down" — was still on screen.
 * Both texts now come from one source, so "no usable signal" and an authoritative
 * tuning instruction can never appear together.
 */
object ReadoutCopy {

    /** Short header status; the lamp + text carry the same phase. */
    fun headerText(phase: SignalPhase): String = when (phase) {
        SignalPhase.TRACKING -> "Tracking"
        SignalPhase.WAITING -> "Listening"
        // A retained reading is a distinct state: not live, not silence.
        SignalPhase.HOLDING -> "Reading held"
        SignalPhase.LOST -> "No signal"
        SignalPhase.MIC_OFF -> "Microphone off"
        SignalPhase.MIC_ERROR -> "Microphone unavailable"
    }

    /** Caption under the cents column when the value lies beyond the dial. */
    fun scaleCaption(clamped: Boolean): String? = if (clamped) "off scale" else null

    /** Caption making a retained (not live) reading explicit. */
    fun retentionCaption(phase: SignalPhase): String? =
        if (phase == SignalPhase.HOLDING) "held" else null

    /**
     * What the displayed note IS: a manual lock compares the measured pitch
     * against a chosen target, so the note label is a target, not a detection.
     */
    fun noteRole(mode: TunerMode, locked: Boolean): String? =
        if (mode == TunerMode.INSTRUMENT && locked) "target" else null

    /** One calm prompt when there is no live reading. */
    fun guidance(phase: SignalPhase, hint: String?): String = when (phase) {
        SignalPhase.HOLDING -> "Reading held"
        SignalPhase.MIC_OFF -> hint ?: "Microphone is off."
        SignalPhase.MIC_ERROR -> hint ?: "Microphone unavailable"
        SignalPhase.LOST -> hint ?: "No signal"
        SignalPhase.WAITING -> hint ?: "Play a string"
        SignalPhase.TRACKING -> hint ?: ""
    }
}