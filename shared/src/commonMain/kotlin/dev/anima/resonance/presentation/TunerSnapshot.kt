package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchReason

/**
 * Immutable UI-facing snapshot: measurement reading + signal conditions.
 *
 * [rawHz] / [rawConfidence] / [rawReason] are the *detector's* own per-frame
 * answer, before any temporal decision. They are diagnostics (trace output,
 * on-device QA) and must never drive the display: the tracker can reject or
 * fold a raw estimate, and a UI that reads [rawHz] would resurrect exactly the
 * flicker the tracker exists to remove.
 */
data class TunerSnapshot(
    val reading: TunerReading,
    val rms: Double,
    val clipping: Boolean,
    val sampleRate: Int,
    val rawHz: Double = reading.frequencyHz,
    val rawConfidence: Double = reading.confidence,
    val rawReason: PitchReason = PitchReason.FIRST_CROSSING,
) {
    /** Effective status: clipping takes display priority over the reading. */
    val status: TunerStatus
        get() = if (clipping) TunerStatus.CLIPPING else reading.status
}