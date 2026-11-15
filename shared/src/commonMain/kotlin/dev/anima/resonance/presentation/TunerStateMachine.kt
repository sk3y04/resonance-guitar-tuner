package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Cents
import dev.anima.resonance.music.ChromaticTargets
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TargetMatcher

/**
 * Tuner state machine. Consumes detector [PitchEstimate]s with a
 * caller-supplied virtual clock ([nowMs]) and produces immutable
 * [TunerReading]s. Pure and side-effect free — deterministic for tests.
 *
 * Policies:
 * - Hysteresis: a note becomes "stable" (displayed) only after
 *   [hysteresisFrames] consecutive agreeing frames; any other candidate or a
 *   discontinuity resets the counter.
 * - Smoothing: median of the most recent 5 raw pitch frequencies; cleared on
 *   any discontinuity (no-pitch / low-confidence frame) so stale data never
 *   blends with new data.
 * - Staleness: no trustworthy pitch for > [staleMs] → STALE.
 * - In-tune hysteresis: enter at |cents| <= [inTuneCents], exit at
 *   |cents| > [inTuneExitCents].
 * - Manual target (Instrument mode): changes the target and therefore the
 *   signed cents — never the measurement — and bypasses chromatic matching.
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
     * G4. If a measurement is within [octaveFoldTolerance] (relative) of
     * half the current target, and there was no playing gap longer than
     * [octaveFoldGapMs], it is folded up to the established note instead of
     * stealing the lock. A real octave change after a gap is still followed.
     */
    val octaveFoldTolerance: Double = 0.02,
    val octaveFoldGapMs: Long = 300,
    private var targets: List<Note> = ChromaticTargets.inRange(440.0),
    /**
     * Instrument-mode AUTO selection: when non-null, nearest matching is
     * restricted to these strings (the selected preset). A manual lock
     * ([setManualTarget]) still overrides the set. Null = chromatic.
     */
    private var targetSet: List<Note>? = null,
    private var concertPitchHz: Double = 440.0,
) {
    private val smoothingWindow = 5

    private var stableNote: Note? = null
    private var candidateNote: Note? = null
    private var candidateCount = 0
    private var lastPitchMs: Long? = null
    private var inTune = false
    private var manualTarget: Note? = null
    private val ring = ArrayList<Double>(smoothingWindow)
    /**
     * Consecutive frames where the measurement is outside the stable note's
     * semitone. Bounded by design: a legitimate note transition
     * stays under the hysteresis window; an unbounded run means the note is
     * stale (re-locking is being blocked by target resets) and it must
     * yield. NOT reset by setting changes — a reference change must not
     * renew a note the measurement has left.
     */
    private var staleFrameCount = 0

    /**
     * Set by a playing gap longer than [octaveFoldGapMs]. While set, a
     * measurement exactly one octave below the established note is treated
     * as a real note change, not as sympathetic octave bleed. Cleared when a
     * fresh measurement agrees with the established note or a new note locks.
     */
    private var octaveFoldSuppressed = false

    /** Processes one detector frame at virtual time [nowMs]. */
    fun onPitch(estimate: PitchEstimate, nowMs: Long): TunerReading {
        val present = estimate.frequencyHz > 0.0
        if (!present || estimate.confidence < minConfidence) {
            // Discontinuity: nothing trustworthy — reset candidate + smoothing.
            candidateNote = null
            candidateCount = 0
            ring.clear()
            inTune = false
            val last = lastPitchMs
            if (last != null && nowMs - last > octaveFoldGapMs) {
                // A real playing gap: the next octave-down measurement is a
                // string change, not sympathetic bleed from the old note.
                octaveFoldSuppressed = true
            }
            val status = when {
                !present && last == null -> TunerStatus.LISTENING
                !present -> if (nowMs - (last ?: nowMs) > staleMs) TunerStatus.STALE else TunerStatus.UNSTABLE
                else -> TunerStatus.INSUFFICIENT_SIGNAL // present but below the confidence floor
            }
            return TunerReading(
                status = status,
                // A frame that is not trustworthy is not a current note.
                // Keeping the previous stable note here invited the UI to
                // present old data as a live measurement.
                note = null,
                frequencyHz = 0.0,
                cents = 0,
                direction = PitchDirection.FLAT,
                inTune = false,
                confidence = estimate.confidence,
            )
        }

        lastPitchMs = nowMs
        val manual = manualTarget
        val rawHz = estimate.frequencyHz
        // Fold continuous octave-down bleed into the established note.
        // The detector can legitimately report the lower octave because the
        // combined waveform is periodic there; the player's intent is the
        // note that was already locked before the bleed appeared.
        val referenceHz = manual?.frequency(concertPitchHz)
            ?: stableNote?.frequency(concertPitchHz)
        val octaveBelow = referenceHz != null && !octaveFoldSuppressed &&
            kotlin.math.abs(rawHz * 2.0 - referenceHz) / referenceHz <= octaveFoldTolerance
        val measuredHz = if (octaveBelow) rawHz * 2.0 else rawHz
        if (!octaveBelow && referenceHz != null &&
            kotlin.math.abs(rawHz - referenceHz) / referenceHz <= octaveFoldTolerance
        ) {
            // The original note is confirmed again; a later octave bleed may
            // be folded normally.
            octaveFoldSuppressed = false
        }
        val note: Note?
        val targetHz: Double

        if (manual != null) {
            // Instrument mode: manual lock overrides matching, not measurement.
            stableNote = manual
            candidateNote = null
            candidateCount = 0
            note = manual
            targetHz = manual.frequency(concertPitchHz)
        } else {
            val match = TargetMatcher.nearest(
                measuredHz,
                targetSet ?: targets,
                concertPitchHz,
            )
            val current = stableNote
            if (current != null && targetSet == null &&
                kotlin.math.abs(Cents.deviation(measuredHz, current.frequency(concertPitchHz))) > 50.0) {
                // The measurement is outside the displayed note's
                // semitone. Nearest matching can never deviate more than
                // half a semitone from the true match, so this is either a
                // legitimate transition (bounded by the hysteresis window)
                // or a stale note kept alive by candidate resets during
                // rapid target/concert changes. The latter must not display
                // forever: after one full re-lock window without progress,
                // the note yields to UNSTABLE. (Instrument AUTO sets are
                // exempt: a large deviation there means the player is on
                // the wrong string — correct display.)
                staleFrameCount++
                if (staleFrameCount > hysteresisFrames) {
                    stableNote = null
                    octaveFoldSuppressed = false
                    candidateNote = match.note
                    candidateCount = 1
                    staleFrameCount = 0
                    ring.clear()
                    inTune = false
                    lastPitchMs = nowMs
                    return TunerReading(
                        status = TunerStatus.UNSTABLE,
                        note = null,
                        frequencyHz = 0.0,
                        cents = 0,
                        direction = PitchDirection.FLAT,
                        inTune = false,
                        confidence = estimate.confidence,
                    )
                }
            } else {
                staleFrameCount = 0
            }
            if (current == null || match.note != current) {
                if (candidateNote == match.note) candidateCount++ else {
                    candidateNote = match.note
                    candidateCount = 1
                }
                if (candidateCount >= hysteresisFrames) {
                    stableNote = candidateNote
                    octaveFoldSuppressed = false
                    candidateNote = null
                    candidateCount = 0
                }
            } else {
                candidateNote = null
                candidateCount = 0
            }
            note = stableNote
            targetHz = note?.frequency(concertPitchHz) ?: match.targetHz
        }

        // Time-aware smoothing: bounded median over recent raw pitches.
        ring.add(measuredHz)
        if (ring.size > smoothingWindow) ring.removeAt(0)
        val smoothed = median(ring)

        val cents = if (note != null) Cents.deviation(smoothed, targetHz) else 0
        if (note != null) {
            if (inTune) {
                if (cents > inTuneExitCents || cents < -inTuneExitCents) inTune = false
            } else {
                if (cents <= inTuneCents && cents >= -inTuneCents) inTune = true
            }
        } else {
            inTune = false
        }

        // Hard backstop: never emit an impossible display. The
        // raw-frame guard above clears a stale note after one full re-lock
        // window, but a frame that happens to sit near the displayed note
        // can reset that counter while the smoothing ring is still dominated
        // by far values — in that case the DISPLAYED deviation is the
        // invariant that must hold: beyond 2 semitones the note is not what
        // the player is hearing, so it yields immediately.
        if (note != null && manual == null && targetSet == null && kotlin.math.abs(cents) > 200) {
            stableNote = null
            octaveFoldSuppressed = false
            candidateNote = null
            candidateCount = 0
            staleFrameCount = 0
            ring.clear()
            inTune = false
            return TunerReading(
                status = TunerStatus.UNSTABLE,
                note = null,
                frequencyHz = 0.0,
                cents = 0,
                direction = PitchDirection.FLAT,
                inTune = false,
                confidence = estimate.confidence,
            )
        }

        val status = if (note == null || candidateNote != null) TunerStatus.UNSTABLE else TunerStatus.MEASURED
        val direction = when {
            inTune -> PitchDirection.INTUNE
            cents < 0 -> PitchDirection.FLAT
            else -> PitchDirection.SHARP
        }
        return TunerReading(
            status = status,
            note = note,
            frequencyHz = smoothed,
            cents = cents,
            direction = direction,
            inTune = inTune,
            confidence = estimate.confidence,
        )
    }

    /**
     * Instrument mode: manual string lock. Changes the TARGET (and therefore
     * cents), never the measurement. [note] == null clears the lock and
     * resumes chromatic matching.
     */
    fun setManualTarget(note: Note?) {
        manualTarget = note
        octaveFoldSuppressed = false
        candidateNote = null
        candidateCount = 0
    }

    /**
     * Instrument-mode AUTO selection: restrict nearest matching to [notes]
     * (the preset's strings). Null restores the full chromatic set. Resets
     * candidate state so no stale target survives the change.
     */
    fun setTargetSet(notes: List<Note>?) {
        targetSet = notes?.takeIf { it.isNotEmpty() }
        octaveFoldSuppressed = false
        candidateNote = null
        candidateCount = 0
    }

    /**
     * Changes the A4 concert reference (400..480 Hz, validated). Rebuilds the
     * chromatic targets, resets candidate state and smoothing so no stale
     * estimate survives the scale change; in-tune is re-evaluated on the next
     * frame (not force-cleared here).
     */
    fun setConcertPitch(hz: Double) {
        require(hz in 400.0..480.0) { "concert pitch out of bounds 400..480 Hz: $hz" }
        concertPitchHz = hz
        targets = ChromaticTargets.inRange(hz, 55.0, 1200.0)
        // A restricted (instrument) set must track the new scale: the caller
        // re-applies it via setTargetSet right after this call.
        targetSet = null
        // No stale estimate may survive a scale change.
        octaveFoldSuppressed = false
        candidateNote = null
        candidateCount = 0
        ring.clear()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
