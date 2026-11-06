package dev.anima.resonance.dsp

import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine

/**
 * Streaming analyzer: the glue between a PCM capture stream and the
 * tuned state machine. The caller feeds fixed-size chunks (typically
 * [frameSize]/2 samples) and receives one [TunerSnapshot] per analyzed frame.
 *
 * Bounded by design: at most [frameSize] samples of ring + one frame of
 * temporaries per call; caller buffers are never mutated (chunks are copied
 * into the ring).
 */
class StreamingAnalyzer(
    val sampleRate: Int,
    private val yin: Yin = Yin(),
    val stateMachine: TunerStateMachine = TunerStateMachine(),
    val frameSize: Int = 2048,
    private val clipThreshold: Double = 0.99,
) {
    init {
        require(frameSize >= 256) { "frameSize too small: $frameSize" }
    }

    private val ring = FloatArray(frameSize)
    private var count = 0
    private var write = 0

    /**
     * Appends [chunk] (contents copied) and, once the ring holds a full frame,
     * analyzes the most recent frame at virtual time [nowMs].
     * Returns null until the first frame is available (bounded startup).
     */
    fun processChunk(chunk: FloatArray, nowMs: Long): TunerSnapshot? {
        if (chunk.isEmpty()) return null
        for (s in chunk) {
            ring[write] = s
            write = (write + 1) % frameSize
            if (count < frameSize) count++
        }
        if (count < frameSize) return null

        // Most recent frame, oldest first.
        val frame = FloatArray(frameSize)
        System.arraycopy(ring, write, frame, 0, frameSize - write)
        System.arraycopy(ring, 0, frame, frameSize - write, write)

        val rms = Conditioning.rmsLevel(frame)
        val clipping = Conditioning.clippingRatio(frame, clipThreshold) > 0.0
        val dcRemoved = Conditioning.removeDc(frame)
        val estimate = yin.detect(dcRemoved, sampleRate)
        val reading = stateMachine.onPitch(estimate, nowMs)
        return TunerSnapshot(reading, rms, clipping, sampleRate)
    }

    /** True once the first frame has been analyzed. */
    fun isReady(): Boolean = count >= frameSize
}
