package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TargetMatcher
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Whether the pipeline currently has usable pitch evidence.
 *
 * This is the authoritative validity signal for the UI: it is produced from
 * accepted evidence and monotonic timestamps, so the screen never has to infer
 * it from a magic frequency, zero cents or a null combination.
 */
enum class PitchValidity {
    /** No usable pitch and nothing recent to fall back on. */
    NONE,

    /**
     * A fresh, accepted measurement of this frame. [TrackedPitch.ageMs] is 0.
     * Only LIVE readings may drive a needle.
     */
    LIVE,

    /**
     * No live numeric reading this frame: either there is no accepted evidence
     * and the last one is recent ([TrackedPitch.ageMs] ≤ the hold window), or
     * the measurement is not yet supported by the displayed note (pending
     * identity change, [TrackedPitch.ageMs] = age of the supporting evidence).
     * The caller may fade the last live reading; it must never present it as
     * live or move the needle.
     */
    HELD,
}

/**
 * One tracker output: identity, validity, accepted pitch and the smoothed
 * display pitch, all in one consistent object.
 */
data class TrackedPitch(
    val validity: PitchValidity,
    /** Stable note identity. Null whenever no note can be shown as current. */
    val note: Note?,
    /** Reference the cents are measured against (0 when there is no note). */
    val targetHz: Double,
    /** Smoothed display frequency (0 when not LIVE). Drives the numeric readout. */
    val frequencyHz: Double,
    /** Continuous signed deviation from [targetHz] — never quantized. */
    val centsFine: Double,
    val cents: Int,
    val direction: PitchDirection,
    val inTune: Boolean,
    /** Strength of the evidence that produced this output (0..1). */
    val confidence: Double,
    /** Milliseconds since the last accepted evidence (0 for LIVE). */
    val ageMs: Long,
    val status: TunerStatus,
) {
    companion object {
        val idle = TrackedPitch(
            validity = PitchValidity.NONE,
            note = null,
            targetHz = 0.0,
            frequencyHz = 0.0,
            centsFine = 0.0,
            cents = 0,
            direction = PitchDirection.FLAT,
            inTune = false,
            confidence = 0.0,
            ageMs = Long.MAX_VALUE,
            status = TunerStatus.IDLE,
        )
    }
}

/**
 * Temporal tracker. Turns independent per-frame estimates into a stable,
 * honest reading.
 *
 * Why it exists — measured failure modes of the previous design
 * (frame-count hysteresis + a 5-frame median that was cleared on any dropout):
 *  - a single octave/harmonic frame flipped the note label (flicker);
 *  - any weak frame cleared the smoothing ring, so the displayed cents jumped;
 *  - the median added ~105 ms of latency while still leaving 6.4 cents of
 *    jitter on low E in a 20 dB room;
 *  - identity decisions were counted in frames, so they changed with the
 *    analysis cadence.
 *
 * Policies:
 *  - **Evidence, not level.** A frame is evidence when it is finite, in band
 *    and at least [sustainConfidence] strong once a note is established, or
 *    [acquireConfidence] strong when acquiring. The detector's confidence is
 *    the normalized aperiodicity, so it does not fall merely because the
 *    string is getting quieter (verified: 0.65-0.86 at the usable horizon of
 *    a decaying pluck) — amplitude decay is *not* what ends tracking.
 *  - **Time, not frames.** Acquisition and identity changes confirm after
 *    [confirmMs] of sustained evidence; validity expires on elapsed time.
 *  - **Harmonic continuity.** While a string is still ringing ([onsetGapMs]),
 *    a measurement at ×2 or ÷2 of the established pitch is folded onto it:
 *    the waveform genuinely has that period, the player's note did not change.
 *    After a gap, the fold is released so a real octave change is followed.
 *  - **Never display a contradiction.** A measurement outside the note's band
 *    starts a pending identity change; until it confirms, the output carries
 *    the old label but *no numeric reading* (validity HELD). A note whose
 *    measurement left its semitone for longer than the confirm window is
 *    released — no sticky lock, no impossible cent value on screen.
 *  - **Log-domain, time-aware smoothing.** A 3-frame median (single-frame
 *    outlier rejection) followed by a one-pole filter in log-pitch space whose
 *    time constant comes from the elapsed time between accepted frames, and
 *    which snaps when the residual is a real move (≥ [snapCents]). Equal cent
 *    changes produce equal movement, small jitter is attenuated ~3x, a real
 *    step lands in one frame and a slow ramp lags < 2 cents.
 *  - **Cents are never averaged across a note boundary**: the smoother works on
 *    log-frequency, and cents are derived afterwards against the target.
 */
class PitchTracker(
    /** Confidence needed to acquire (or re-acquire) a note. */
    val acquireConfidence: Double = 0.5,
    /** Confidence needed to keep an established note — decay-tolerant. */
    val sustainConfidence: Double = 0.35,
    /** No accepted evidence for longer than this → STALE. */
    val staleMs: Long = 600,
    /** Window in which a lost reading stays HELD (fadeable) before NONE. */
    val holdMs: Long = 400,
    /** Sustained evidence needed to acquire a note or change identity. */
    val confirmMs: Long = 40,
    /** A note is supported while the measurement stays within this band. */
    val noteBandCents: Double = 50.0,
    /** Relative tolerance for the octave fold (0.02 = 34 cents). */
    val octaveFoldTolerance: Double = 0.02,
    /** A gap longer than this ends the current ring and releases the fold. */
    val onsetGapMs: Long = 250,
    val inTuneCents: Double = 5.0,
    val inTuneExitCents: Double = 8.0,
    /** Time constant of the display smoother at rest. */
    val smoothingTauMs: Double = 55.0,
    /** Residual at which the smoother follows immediately (a real move). */
    val snapCents: Double = 3.0,
    targets: List<Note> = dev.anima.resonance.music.ChromaticTargets.inRange(440.0),
    targetSet: List<Note>? = null,
    concertPitchHz: Double = 440.0,
) {
    private var targets: List<Note> = targets
    private var targetSet: List<Note>? = targetSet?.takeIf { it.isNotEmpty() }
    private var concertPitchHz: Double = concertPitchHz
    private var manualTarget: Note? = null

    private var stableNote: Note? = null
    private var pendingNote: Note? = null
    private var pendingSinceMs: Long = 0
    private var lastEvidenceMs: Long? = null
    private var lastAcceptMs: Long = 0
    private var inTune = false

    /** Log-domain display pitch (cents above 1 Hz); [smoothValid] gates reuse. */
    private var smoothLog: Double = 0.0
    private var smoothValid = false
    private val rawRing = DoubleArray(3)
    private var rawCount = 0
    private var rawWrite = 0

    /** Processes one detector frame at monotonic time [nowMs]. */
    fun onPitch(estimate: PitchEstimate, nowMs: Long): TrackedPitch {
        val hz = estimate.frequencyHz
        val conf = estimate.confidence
        val finite = hz.isFinite() && conf.isFinite() && hz > 0.0
        val established = stableNote != null || manualTarget != null
        val floor = if (established) sustainConfidence else acquireConfidence
        val present = finite && conf >= floor

        val last = lastEvidenceMs
        val age = when {
            last == null -> Long.MAX_VALUE
            nowMs <= last -> 0L
            else -> nowMs - last
        }

        if (!present) {
            // No accepted evidence: nothing here may refresh validity.
            clearSmoothing()
            pendingNote = null
            inTune = false
            val status = when {
                last == null -> if (finite) TunerStatus.INSUFFICIENT_SIGNAL else TunerStatus.LISTENING
                age > staleMs -> TunerStatus.STALE
                finite -> TunerStatus.INSUFFICIENT_SIGNAL
                else -> TunerStatus.UNSTABLE
            }
            val validity = if (last != null && age <= holdMs && stableNote != null) {
                PitchValidity.HELD
            } else {
                PitchValidity.NONE
            }
            return TrackedPitch(
                validity = validity,
                note = null,
                targetHz = 0.0,
                frequencyHz = 0.0,
                centsFine = 0.0,
                cents = 0,
                direction = PitchDirection.FLAT,
                inTune = false,
                confidence = conf,
                ageMs = age,
                status = status,
            )
        }

        // ── Accepted evidence from here on ────────────────────────────────
        val measured = foldHarmonic(hz, nowMs)
        val manual = manualTarget
        val targetList = targetSet ?: targets
        val match = TargetMatcher.nearest(measured, targetList, concertPitchHz)

        var note: Note?
        var numeric = false
        if (manual != null) {
            // Instrument mode: the lock overrides matching, never the measurement.
            stableNote = manual
            pendingNote = null
            note = manual
            numeric = true
        } else {
            val current = stableNote
            if (current == null) {
                if (pendingNote == match.note) {
                    if (nowMs - pendingSinceMs >= confirmMs) {
                        stableNote = match.note
                        pendingNote = null
                        note = stableNote
                        numeric = true
                    } else {
                        note = null
                    }
                } else {
                    pendingNote = match.note
                    pendingSinceMs = nowMs
                    note = null
                }
            } else if (abs(centsBetween(measured, current.frequency(concertPitchHz))) <= noteBandCents ||
                match.note == current
            ) {
                // The measurement supports the displayed note (it is within the
                // note's band, or the active target set says it is still the
                // nearest string — a badly out-of-tune string shows its real
                // deviation rather than flickering to a neighbour).
                pendingNote = null
                note = current
                numeric = true
            } else if (pendingNote == match.note && nowMs - pendingSinceMs >= confirmMs) {
                // Sustained, confirmed conflicting evidence: the note yields.
                stableNote = match.note
                pendingNote = null
                note = stableNote
                numeric = true
                clearSmoothing()
                inTune = false
            } else {
                if (pendingNote != match.note) {
                    pendingNote = match.note
                    pendingSinceMs = nowMs
                }
                // Label stability without a numeric reading: never show the old
                // note with a cent value the waveform contradicts. The age is
                // the age of the evidence that supports the label, not 0 — a
                // caller fading by age must not be told this is fresh.
                return TrackedPitch(
                    validity = PitchValidity.HELD,
                    note = current,
                    targetHz = current.frequency(concertPitchHz),
                    frequencyHz = 0.0,
                    centsFine = 0.0,
                    cents = 0,
                    direction = PitchDirection.FLAT,
                    inTune = false,
                    confidence = conf,
                    ageMs = age,
                    status = TunerStatus.UNSTABLE,
                )
            }
        }

        // ── Numeric reading for a supported identity ──────────────────────
        if (!numeric || note == null) {
            // Acquiring: evidence accepted, but no note to report yet.
            lastEvidenceMs = nowMs
            return TrackedPitch(
                validity = PitchValidity.HELD,
                note = null,
                targetHz = 0.0,
                frequencyHz = 0.0,
                centsFine = 0.0,
                cents = 0,
                direction = PitchDirection.FLAT,
                inTune = false,
                confidence = conf,
                ageMs = 0,
                status = TunerStatus.UNSTABLE,
            )
        }

        val targetHz = note.frequency(concertPitchHz)
        val displayHz = smooth(measured, nowMs)
        val centsFine = centsBetween(displayHz, targetHz)
        val cents = centsFine.roundToInt()
        if (inTune) {
            if (centsFine > inTuneExitCents || centsFine < -inTuneExitCents) inTune = false
        } else if (centsFine <= inTuneCents && centsFine >= -inTuneCents) {
            inTune = true
        }
        val direction = when {
            inTune -> PitchDirection.INTUNE
            centsFine < 0.0 -> PitchDirection.FLAT
            else -> PitchDirection.SHARP
        }
        lastEvidenceMs = if (nowMs > lastAcceptMs) nowMs else lastAcceptMs
        lastAcceptMs = lastEvidenceMs!!
        return TrackedPitch(
            validity = PitchValidity.LIVE,
            note = note,
            targetHz = targetHz,
            frequencyHz = displayHz,
            centsFine = centsFine,
            cents = cents,
            direction = direction,
            inTune = inTune,
            confidence = conf,
            ageMs = 0,
            status = TunerStatus.MEASURED,
        )
    }

    /**
     * Octave folding: while the established string is still ringing, a
     * measurement at ×2 or ÷2 of its pitch is the same note (the waveform
     * genuinely has that period — a plucked string's 2nd partial or a
     * sympathetic octave). After a playing gap the fold is released so a real
     * octave change is followed.
     */
    private fun foldHarmonic(hz: Double, nowMs: Long): Double {
        val reference = (manualTarget ?: stableNote)?.frequency(concertPitchHz) ?: return hz
        val last = lastEvidenceMs ?: return hz
        if (nowMs - last > onsetGapMs) return hz
        // Exactly one octave: folding across two octaves would collide with a
        // genuinely different string (E2 and E4 are two octaves apart — the
        // most common string change on a guitar).
        if (abs(hz / reference - 2.0) / 2.0 <= octaveFoldTolerance) return hz / 2.0
        if (abs(hz / reference - 0.5) * 2.0 <= octaveFoldTolerance) return hz * 2.0
        return hz
    }

    /**
     * Display smoothing in log-pitch space: a 3-frame median (single-frame
     * outliers) then a time-aware one-pole. The gain rises with the residual so
     * a real move is followed immediately while small jitter is attenuated.
     */
    private fun smooth(measuredHz: Double, nowMs: Long): Double {
        val logPitch = 1200.0 * ln(measuredHz) / LN2
        // Unreachable by construction (the caller validates the estimate), but
        // never emit a nonsense value if it were: keep the last valid display.
        if (!logPitch.isFinite()) return if (smoothValid) 2.0.pow(smoothLog / 1200.0) else measuredHz
        rawRing[rawWrite] = logPitch
        rawWrite = (rawWrite + 1) % rawRing.size
        if (rawCount < rawRing.size) rawCount++
        val median = when (rawCount) {
            1 -> rawRing[0]
            2 -> (rawRing[0] + rawRing[1]) / 2.0
            else -> medianOf3(rawRing)
        }
        if (!smoothValid) {
            smoothLog = logPitch
            smoothValid = true
            lastAcceptMs = nowMs
            return measuredHz
        }
        val residual = median - smoothLog
        val dt = (nowMs - lastAcceptMs).let { if (it <= 0L) 1L else it }.coerceAtMost(200L)
        val alpha = if (abs(residual) >= snapCents) {
            1.0
        } else {
            val base = 1.0 - exp(-dt.toDouble() / smoothingTauMs)
            (base * (0.85 + 0.10 * abs(residual))).coerceIn(0.05, 0.9)
        }
        smoothLog += alpha * residual
        lastAcceptMs = if (nowMs > lastAcceptMs) nowMs else lastAcceptMs
        return 2.0.pow(smoothLog / 1200.0)
    }

    private fun medianOf3(values: DoubleArray): Double {
        val a = values[0]
        val b = values[1]
        val c = values[2]
        return when {
            (a <= b && b <= c) || (c <= b && b <= a) -> b
            (b <= a && a <= c) || (c <= a && a <= b) -> a
            else -> c
        }
    }

    private fun clearSmoothing() {
        rawCount = 0
        rawWrite = 0
        smoothValid = false
    }

    private fun centsBetween(a: Double, b: Double): Double =
        if (a <= 0.0 || b <= 0.0) 0.0 else 1200.0 * ln(a / b) / LN2

    // ── Configuration ────────────────────────────────────────────────────

    /**
     * Manual string lock (instrument mode). Changes the TARGET — and therefore
     * the cents — never the measurement. Does not reset a pending identity
     * change: a reference change must not renew a note the measurement left.
     */
    fun setManualTarget(note: Note?) {
        manualTarget = note
        if (note != null) stableNote = note
        pendingNote = null
    }

    /** Instrument AUTO selection: restrict matching to [notes]. Null = chromatic. */
    fun setTargetSet(notes: List<Note>?) {
        targetSet = notes?.takeIf { it.isNotEmpty() }
        pendingNote = null
        if (targetSet != null && stableNote != null && targetSet!!.none { it == stableNote }) {
            stableNote = null
        }
    }

    /** Changes the A4 reference; rebuilds chromatic targets. */
    fun setConcertPitch(hz: Double) {
        require(hz in 400.0..480.0) { "concert pitch out of bounds 400..480 Hz: $hz" }
        concertPitchHz = hz
        targets = dev.anima.resonance.music.ChromaticTargets.inRange(hz, 55.0, 1200.0)
        // A restricted (instrument) set must be re-applied by the caller.
        targetSet = null
        // Deliberately keeps pending identity state: a scale change must not
        // let a stale note survive by restarting its confirmation window.
    }


    private companion object {
        val LN2 = ln(2.0)
    }
}