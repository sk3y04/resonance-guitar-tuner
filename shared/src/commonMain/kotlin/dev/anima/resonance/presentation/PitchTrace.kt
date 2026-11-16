package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchReason
import kotlin.time.TimeSource

/**
 * One analyzed frame, as seen by the pipeline (diagnostics).
 *
 * Everything here is a metric the pipeline actually computes — no invented
 * numbers. [nowMs] is the caller's monotonic clock (the audio clock in
 * production), [processingMicros] is how long detection+tracking took.
 */
data class PitchFrameTrace(
    val nowMs: Long,
    val rms: Double,
    val rawHz: Double,
    val rawConfidence: Double,
    val rawReason: PitchReason,
    val validity: PitchValidity,
    val noteLabel: String?,
    val targetHz: Double,
    val frequencyHz: Double,
    val centsFine: Double,
    val ageMs: Long,
    val status: TunerStatus,
    val processingMicros: Long,
)

/**
 * Bounded, explicitly-enabled trace of the pitch pipeline.
 *
 * Off by default and never enabled from production code: nothing is recorded
 * (not even a timestamp) unless [enabled] is set, and the ring allocates once
 * at construction. It stores metrics only — never microphone audio.
 *
 * Usage on a device or in a harness:
 * ```
 * val trace = PitchTrace(capacity = 256).apply { enabled = true }
 * val analyzer = StreamingAnalyzer(sampleRate, trace = trace)
 * ...
 * trace.snapshot().forEach { println(it) }
 * ```
 */
class PitchTrace(val capacity: Int = 256) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    /** Recording switch. Default off. */
    var enabled: Boolean = false

    private val ring = arrayOfNulls<PitchFrameTrace>(capacity)
    private var write = 0
    private var count = 0

    /** Frames dropped because the ring was full since the last [snapshot]. */
    var overwritten: Long = 0
        private set

    val size: Int get() = count

    /** Monotonic start mark for the current frame (null when tracing is off). */
    fun beginFrame(): TimeSource.Monotonic.ValueTimeMark? =
        if (enabled) TimeSource.Monotonic.markNow() else null

    /** Records one frame. A no-op unless [enabled]. */
    fun record(
        nowMs: Long,
        rms: Double,
        rawHz: Double,
        rawConfidence: Double,
        rawReason: PitchReason,
        reading: TunerReading,
        started: TimeSource.Monotonic.ValueTimeMark?,
    ) {
        if (!enabled) return
        val elapsed = started?.elapsedNow()?.inWholeMicroseconds ?: -1L
        if (ring[write] != null) overwritten++
        ring[write] = PitchFrameTrace(
            nowMs = nowMs,
            rms = rms,
            rawHz = rawHz,
            rawConfidence = rawConfidence,
            rawReason = rawReason,
            validity = reading.validity,
            noteLabel = reading.note?.label,
            targetHz = reading.targetHz,
            frequencyHz = reading.frequencyHz,
            centsFine = reading.centsFine,
            ageMs = reading.ageMs,
            status = reading.status,
            processingMicros = elapsed,
        )
        write = (write + 1) % capacity
        if (count < capacity) count++
    }

    /** Recorded frames, oldest first. */
    fun snapshot(): List<PitchFrameTrace> {
        val out = ArrayList<PitchFrameTrace>(count)
        val start = if (count == capacity) write else 0
        for (i in 0 until count) out += ring[(start + i) % capacity]!!
        return out
    }

    fun clear() {
        ring.fill(null)
        write = 0
        count = 0
        overwritten = 0
    }
}