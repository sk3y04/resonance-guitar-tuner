package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note

/** Coarse signal/measurement state of the tuner. */
enum class TunerStatus {
    IDLE,                // no data yet (fresh engine)
    LISTENING,           // no pitch at all so far — welcoming hint, not a frozen state
    UNSTABLE,            // pitch present but the note is changing (hysteresis)
    INSUFFICIENT_SIGNAL, // pitch-like input below the confidence floor — too weak to trust
    MEASURED,            // stable note, reliable display
    STALE,               // no fresh pitch beyond the stale window — last data is old
    CLIPPING,            // input at full scale — measurement untrustworthy
}

enum class PitchDirection { FLAT, INTUNE, SHARP }

/** Immutable display reading produced by the tuner state machine. */
data class TunerReading(
    val status: TunerStatus,
    val note: Note?,           // stable displayed note (null while unstable/none)
    val frequencyHz: Double,   // smoothed measurement; 0.0 when none
    val cents: Int,            // signed deviation from target (unclamped)
    val direction: PitchDirection,
    val inTune: Boolean,
    val confidence: Double,
) {
    companion object {
        val idle = TunerReading(TunerStatus.IDLE, null, 0.0, 0, PitchDirection.FLAT, false, 0.0)
    }
}
