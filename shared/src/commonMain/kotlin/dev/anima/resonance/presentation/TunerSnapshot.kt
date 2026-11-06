package dev.anima.resonance.presentation

/** Immutable UI-facing snapshot: measurement reading + signal conditions. */
data class TunerSnapshot(
    val reading: TunerReading,
    val rms: Double,
    val clipping: Boolean,
    val sampleRate: Int,
) {
    /** Effective status: clipping takes display priority over the reading. */
    val status: TunerStatus
        get() = if (clipping) TunerStatus.CLIPPING else reading.status
}
