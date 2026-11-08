package dev.anima.resonance.dsp

import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine

/**
 * Streaming analyzer: the glue between a PCM capture stream and the
 * tuned state machine. The caller feeds fixed-size chunks (typically
 * [frameSize]/4 samples) and receives one [TunerSnapshot] per analyzed frame.
 *
 * The default 4096-sample window (93 ms @ 44.1 kHz) is the correctness-sweep value: at
 * 2048 the YIN dip for low notes (≤ ~110 Hz) is too shallow against 10 dB
 * broadband noise and the normalized d′ curve's noise trend at long lags
 * wins (measured: 110 Hz sine + 10 dB noise read as 86.3 Hz on 27/27 frames
 * — the global dip at lag 511 was 7x deeper than the true-period dip at
 * lag 400). Doubling the window sharpens the true dip ~2x. The state
 * machine's 300 ms stability window dominates UX latency either way.
 *
 * Bounded by design: at most [frameSize] samples of ring + one frame of
 * temporaries per call; caller buffers are never mutated (chunks are copied
 * into the ring).
 */
class StreamingAnalyzer(
    val sampleRate: Int,
    private val yin: Yin = Yin(),
    val stateMachine: TunerStateMachine = TunerStateMachine(),
    val frameSize: Int = 4096,
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
