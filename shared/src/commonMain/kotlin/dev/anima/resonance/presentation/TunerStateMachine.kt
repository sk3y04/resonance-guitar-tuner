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
            val status = when {
                !present && last == null -> TunerStatus.LISTENING
                !present -> if (nowMs - (last ?: nowMs) > staleMs) TunerStatus.STALE else TunerStatus.UNSTABLE
                else -> TunerStatus.UNSTABLE // signal present but below confidence floor
            }
            return TunerReading(
                status = status,
                note = stableNote,
                frequencyHz = 0.0,
                cents = 0,
                direction = PitchDirection.FLAT,
                inTune = false,
                confidence = estimate.confidence,
            )
        }

        lastPitchMs = nowMs
        val manual = manualTarget
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
                estimate.frequencyHz,
                targetSet ?: targets,
                concertPitchHz,
            )
            val current = stableNote
            if (current == null || match.note != current) {
                if (candidateNote == match.note) candidateCount++ else {
                    candidateNote = match.note
                    candidateCount = 1
                }
                if (candidateCount >= hysteresisFrames) {
                    stableNote = candidateNote
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
        ring.add(estimate.frequencyHz)
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
        candidateNote = null
        candidateCount = 0
        ring.clear()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
