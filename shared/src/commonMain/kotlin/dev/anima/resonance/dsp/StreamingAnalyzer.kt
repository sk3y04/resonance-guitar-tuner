package dev.anima.resonance.dsp

import dev.anima.resonance.presentation.PitchTrace
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine

/**
 * Streaming analyzer: the glue between a PCM capture stream and the tuned
 * state machine. The caller feeds chunks of any size (typically
 * [frameSize]/4 samples) and receives at most one [TunerSnapshot] per
 * completed analysis hop.
 *
 * The default 4096-sample window (85 ms @ 48 kHz) is the correctness-sweep value: at
 * 2048 the YIN dip for low notes (≤ ~110 Hz) is too shallow against 10 dB
 * broadband noise and the normalized d′ curve's noise trend at long lags
 * wins (measured: 110 Hz sine + 10 dB noise read as 86.3 Hz on 27/27 frames
 * — the global dip at lag 511 was 7x deeper than the true-period dip at
 * lag 400). Doubling the window sharpens the true dip ~2x.
 *
 * Cadence: analysis runs once per [hopSamples] (a quarter window by
 * default), not once per caller chunk. Before this the analyzer analyzed on
 * every chunk, so the detector's cost — 3.1 ms per frame measured on the JVM,
 * more on a phone — scaled with the *chunk size* the platform happened to
 * choose: a 512-sample stream doubled the work for the same audio, and the
 * capture thread could fall behind the microphone, after which the ring holds
 * a spliced window and the detector sees a corrupted frame. The hop is now a
 * property of the analyzer, so the update rate (and the CPU cost) is fixed at
 * [hopMs] regardless of chunking, and stale audio is simply overwritten.
 *
 * Bounded by design: at most [frameSize] samples of ring + one frame of
 * temporaries per analysis; caller buffers are never mutated (chunks are
 * copied into the ring).
 */
class StreamingAnalyzer(
    val sampleRate: Int,
    private val yin: Yin = Yin(),
    val stateMachine: TunerStateMachine = TunerStateMachine(),
    val frameSize: Int = 4096,
    private val clipThreshold: Double = 0.99,
    /** Analysis hop in samples. Defaults to a quarter window. */
    val hopSamples: Int = (frameSize / 4).coerceAtLeast(1),
    /**
     * Optional bounded diagnostics. Null by default: nothing is timed or
     * recorded on the hot path unless a caller explicitly enables a trace.
     */
    private val trace: PitchTrace? = null,
) {
    init {
        require(frameSize >= 256) { "frameSize too small: $frameSize" }
        require(hopSamples in 1..frameSize) { "hopSamples out of bounds: $hopSamples" }
    }

    private val ring = FloatArray(frameSize)
    private var count = 0
    private var write = 0
    private var sinceAnalysis = 0

    /** Frames analyzed so far (diagnostics: cadence and backlog checks). */
    var analyzedFrames: Int = 0
        private set

    /** Analysis hop in milliseconds — the update rate the UI can rely on. */
    val hopMs: Double get() = hopSamples * 1000.0 / sampleRate

    /**
     * Appends [chunk] (contents copied) and, when a full hop has accumulated
     * after the ring filled, analyzes the most recent frame at virtual time
     * [nowMs]. Returns null until the first frame is available and on chunks
     * that do not complete a hop.
     */
    fun processChunk(chunk: FloatArray, nowMs: Long): TunerSnapshot? {
        if (chunk.isEmpty()) return null
        for (s in chunk) {
            ring[write] = s
            write = (write + 1) % frameSize
            if (count < frameSize) count++
        }
        sinceAnalysis += chunk.size
        if (count < frameSize) return null
        // First full frame always analyzes; afterwards only a whole hop counts.
        if (sinceAnalysis < hopSamples && analyzedFrames > 0) return null
        sinceAnalysis = 0

        // Most recent frame, oldest first.
        val frame = FloatArray(frameSize)
        System.arraycopy(ring, write, frame, 0, frameSize - write)
        System.arraycopy(ring, 0, frame, frameSize - write, write)

        val rms = Conditioning.rmsLevel(frame)
        val clipping = Conditioning.clippingRatio(frame, clipThreshold) > 0.0
        val dcRemoved = Conditioning.removeDc(frame)
        val started = trace?.beginFrame()
        val estimate = yin.detect(dcRemoved, sampleRate)
        val reading = stateMachine.onPitch(estimate, nowMs)
        analyzedFrames++
        trace?.record(
            nowMs = nowMs,
            rms = rms,
            rawHz = estimate.frequencyHz,
            rawConfidence = estimate.confidence,
            rawReason = estimate.reason,
            reading = reading,
            started = started,
        )
        return TunerSnapshot(
            reading = reading,
            rms = rms,
            clipping = clipping,
            sampleRate = sampleRate,
            rawHz = estimate.frequencyHz,
            rawConfidence = estimate.confidence,
            rawReason = estimate.reason,
        )
    }

    /** True once the first frame has been analyzed. */
    fun isReady(): Boolean = count >= frameSize
}
