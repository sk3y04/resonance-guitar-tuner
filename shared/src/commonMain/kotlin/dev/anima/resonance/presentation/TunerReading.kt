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

/**
 * Immutable display reading produced by the tuner state machine.
 *
 * The last four fields are the presentation contract (all defaulted, so
 * existing construction sites keep working):
 *  - [centsFine] is the continuous deviation — the value a needle or any
 *    proportional animation should use. [cents] is only for the numeric
 *    readout; rounding it is what used to make the meter move in steps.
 *  - [validity] says whether this is fresh evidence ([PitchValidity.LIVE]),
 *    a holdable recent reading ([PitchValidity.HELD] — never move the needle)
 *    or nothing ([PitchValidity.NONE]).
 *  - [ageMs] is the monotonic age of the evidence behind a HELD reading.
 *  - [targetHz] is the reference [cents]/[centsFine] are measured against.
 */
data class TunerReading(
    val status: TunerStatus,
    val note: Note?,           // stable displayed note (null while unstable/none)
    val frequencyHz: Double,   // smoothed measurement; 0.0 when none
    val cents: Int,            // signed deviation from target (unclamped)
    val direction: PitchDirection,
    val inTune: Boolean,
    val confidence: Double,
    val centsFine: Double = cents.toDouble(),
    val validity: PitchValidity = if (note != null && frequencyHz > 0.0) {
        PitchValidity.LIVE
    } else {
        PitchValidity.NONE
    },
    val ageMs: Long = 0,
    val targetHz: Double = 0.0,
) {
    companion object {
        val idle = TunerReading(TunerStatus.IDLE, null, 0.0, 0, PitchDirection.FLAT, false, 0.0)
    }
}