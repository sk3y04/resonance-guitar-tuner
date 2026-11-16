package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.ChromaticTargets
import dev.anima.resonance.music.Note

/**
 * Tuner state machine: the public, tested reading producer consumed by
 * the UI mapper and the engine.
 *
 * This type is a thin adapter over [PitchTracker]: the temporal
 * logic (evidence floors, time-based hysteresis, harmonic continuity, validity
 * and log-domain smoothing) lives in the tracker, while [onPitch] and the
 * setters keep their original signatures and their original observable
 * contract, so every existing caller and test keeps working.
 *
 * Policies (unchanged in intent, now measured in time rather than frames):
 * - Hysteresis: a note becomes displayable only after [hysteresisFrames]
 *   consecutive agreeing frames — converted to the equivalent time window at
 *   the design 20 ms cadence, so the behavior no longer depends on the chunk
 *   size or the analysis rate.
 * - Smoothing: robust median + time-aware one-pole in log-pitch space; cleared
 *   on any discontinuity (no-pitch / below-floor frame) so stale data never
 *   blends with new data.
 * - Staleness: no trustworthy pitch for > [staleMs] → STALE.
 * - In-tune hysteresis: enter at |cents| <= [inTuneCents], exit at
 *   |cents| > [inTuneExitCents].
 * - Manual target (Instrument mode): changes the target and therefore the
 *   signed cents — never the measurement — and bypasses chromatic matching.
 * - Octave fold: a continuous octave-doubled/halved measurement of the
 *   established note is the same string ([octaveFoldTolerance]); a real octave
 *   change after [octaveFoldGapMs] of no evidence is followed.
 */
class TunerStateMachine(
    val hysteresisFrames: Int = 3,
    val staleMs: Long = 600,
    val inTuneCents: Double = 5.0,
    val inTuneExitCents: Double = 8.0,
    val minConfidence: Double = 0.5,
    /**
     * Octave-bleed guard. A guitar note can excite a string one octave
     * below it (e.g. G4 → open G3); the combined waveform is periodic at the
     * lower octave, so the detector may report 196 Hz while the player holds
     * G4. If a measurement is within [octaveFoldTolerance] (relative) of half
     * the current target, and there was no playing gap longer than
     * [octaveFoldGapMs], it is folded onto the established note instead of
     * stealing the lock. A real octave change after a gap is still followed.
     */
    val octaveFoldTolerance: Double = 0.02,
    val octaveFoldGapMs: Long = 300,
    targets: List<Note> = ChromaticTargets.inRange(440.0),
    targetSet: List<Note>? = null,
    concertPitchHz: Double = 440.0,
) {
    /** Time-based confirmation window equivalent to [hysteresisFrames]. */
    val confirmMs: Long = ((hysteresisFrames - 1).coerceAtLeast(1) * 20L)

    private val tracker = PitchTracker(
        acquireConfidence = minConfidence,
        sustainConfidence = minConfidence * SUSTAIN_RATIO,
        staleMs = staleMs,
        // The hold window equals the stale window: once a reading is no longer
        // holdable it is also stale, so the UI never shows "hold the note
        // steady" for a signal that is already gone. (The visual fade inside
        // that window is SignalHold's own 400 ms decision.)
        holdMs = staleMs,
        confirmMs = confirmMs,
        octaveFoldTolerance = octaveFoldTolerance,
        onsetGapMs = octaveFoldGapMs,
        inTuneCents = inTuneCents,
        inTuneExitCents = inTuneExitCents,
        targets = targets,
        targetSet = targetSet,
        concertPitchHz = concertPitchHz,
    )

    /** Processes one detector frame at virtual time [nowMs]. */
    fun onPitch(estimate: PitchEstimate, nowMs: Long): TunerReading {
        val t = tracker.onPitch(estimate, nowMs)
        return TunerReading(
            status = t.status,
            note = t.note,
            frequencyHz = t.frequencyHz,
            cents = t.cents,
            direction = t.direction,
            inTune = t.inTune,
            confidence = t.confidence,
            centsFine = t.centsFine,
            validity = t.validity,
            ageMs = t.ageMs,
            targetHz = t.targetHz,
        )
    }

    /**
     * Instrument mode: manual string lock. Changes the TARGET (and therefore
     * cents), never the measurement. [note] == null clears the lock and
     * resumes chromatic/AUTO matching.
     */
    fun setManualTarget(note: Note?) {
        tracker.setManualTarget(note)
    }

    /**
     * Instrument-mode AUTO selection: restrict nearest matching to [notes]
     * (the preset's strings). Null restores the full chromatic set.
     */
    fun setTargetSet(notes: List<Note>?) {
        tracker.setTargetSet(notes)
    }

    /**
     * Changes the A4 concert reference (400..480 Hz, validated). Rebuilds the
     * chromatic targets and drops the restricted set — the caller re-applies
     * it right after this call (see AppTuner.applyTarget). Pending identity
     * state is deliberately preserved: a reference change must not renew a
     * note the measurement has left.
     */
    fun setConcertPitch(hz: Double) {
        require(hz in 400.0..480.0) { "concert pitch out of bounds 400..480 Hz: $hz" }
        tracker.setConcertPitch(hz)
    }

    private companion object {
        /**
         * An established note is sustained at a fraction of the acquisition
         * floor: the detector's confidence is the normalized aperiodicity, so
         * a ringing note keeps 0.65-0.86 confidence at the usable horizon — the
         * lower floor is hysteresis against the last few percents of decay, not
         * a global threshold cut.
         */
        const val SUSTAIN_RATIO = 0.7
    }
}