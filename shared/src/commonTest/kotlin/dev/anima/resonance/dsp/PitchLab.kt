package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine
import dev.anima.resonance.presentation.TunerStatus
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline replay harness for the whole pitch pipeline
 * (PCM chunk → StreamingAnalyzer → detector → tracker → reading).
 *
 * Deterministic: a virtual clock derived from the sample count (exactly what
 * the production pump does with `elapsedRealtime`), seeded signals, no
 * threads. Reproduces the user-visible failures without a microphone and
 * computes the metrics the tuning-quality claims are based on.
 */
object PitchLab {

    const val CHUNK = 1024

    /** One analyzed frame: virtual time + what the pipeline produced. */
    data class Frame(
        val ms: Long,
        val rms: Double,
        val reading: dev.anima.resonance.presentation.TunerReading,
        val status: TunerStatus,
        /** True when the reading carries a live measurement (validity LIVE). */
        val live: Boolean,
        /** Detector's own estimate for this frame (diagnostics). */
        val rawHz: Double = 0.0,
        val rawConfidence: Double = 0.0,
    )

    /**
     * Replays [audio] through the real pipeline exactly as production does:
     * one chunk per pump tick, `nowMs` from the sample clock.
     */
    fun run(
        audio: FloatArray,
        sampleRate: Int = PitchLabSignals.SR,
        machine: TunerStateMachine = TunerStateMachine(),
        frameSize: Int = 4096,
        chunk: Int = CHUNK,
    ): List<Frame> {
        val analyzer = StreamingAnalyzer(sampleRate, stateMachine = machine, frameSize = frameSize)
        val frames = ArrayList<Frame>()
        var nowMs = 0L
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + chunk, audio.size)
            nowMs += (end - i).toLong() * 1000L / sampleRate
            val snap: TunerSnapshot? = analyzer.processChunk(audio.copyOfRange(i, end), nowMs)
            if (snap != null) frames.add(frameOf(nowMs, snap))
            i = end
        }
        return frames
    }

    private fun frameOf(ms: Long, snap: TunerSnapshot): Frame {
        val r = snap.reading
        val live = r.validity == dev.anima.resonance.presentation.PitchValidity.LIVE
        return Frame(ms, snap.rms, r, snap.status, live, snap.rawHz, snap.rawConfidence)
    }

    /**
     * RMS of the *noiseless* signal per analysis frame, used as ground truth
     * for "is pitch recoverable here". Callers pass the clean signal and the
     * noise amplitude they added.
     */
    fun snrHorizonMs(
        cleanRms: (Long) -> Double,
        noiseRms: Double,
        totalMs: Long,
        minSnrDb: Double = 10.0,
    ): Long {
        var last = 0L
        var ms = 0L
        while (ms <= totalMs) {
            val snr = if (noiseRms <= 0.0) 999.0 else 20.0 * kotlin.math.log10(cleanRms(ms) / noiseRms + 1e-12)
            if (snr >= minSnrDb) last = ms
            ms += 10
        }
        return last
    }

    // ── Metrics ─────────────────────────────────────────────────────────────

    data class Report(
        val label: String,
        val firstLiveMs: Long?,
        val firstCorrectMs: Long?,
        val windows: Int,
        val dropouts: Int,
        val wrongNotes: Int,
        val octaveJumps: Int,
        val jitterCents: Double,
        val medianAbsErrCents: Double,
        val p95AbsErrCents: Double,
        val maxAbsErrCents: Double,
        val invalidationMs: Long?,
        /** Note-label changes inside the evaluation window (flicker metric). */
        val noteChanges: Int = 0,
        /** True when an in-tune reading was reached inside the window. */
        val inTuneReached: Boolean = false,
        /** Largest swing of the displayed cents inside the window (breathing). */
        val swingCents: Double = 0.0,
    ) {
        fun line(): String = buildString {
            append(label.padEnd(34))
            append((firstLiveMs?.toString() ?: "-").padStart(6))
            append((firstCorrectMs?.toString() ?: "-").padStart(8))
            append(dropouts.toString().padStart(9))
            append(wrongNotes.toString().padStart(7))
            append(octaveJumps.toString().padStart(8))
            append(fmt(jitterCents).padStart(8))
            append(fmt(medianAbsErrCents).padStart(8))
            append(fmt(p95AbsErrCents).padStart(8))
            append(fmt(maxAbsErrCents).padStart(8))
            append((invalidationMs?.toString() ?: "-").padStart(8))
        }
    }

    private fun fmt(v: Double): String = if (v.isNaN()) "-" else ((v * 10).toInt() / 10.0).toString()

    /**
     * Computes the standard tracking metrics for one run.
     *
     * @param expected the note the player actually plucked
     * @param trueCents the real deviation of the signal from `expected`'s
     *   reference frequency (0.0 for an in-tune pluck)
     * @param fromMs start of the evaluation window (after the attack)
     * @param toMs end of the evaluation window (while the pitch is still
     *   recoverable — the caller derives it from the SNR horizon)
     */
    fun report(
        label: String,
        frames: List<Frame>,
        expected: Note,
        trueCents: Double = 0.0,
        fromMs: Long = 0,
        toMs: Long,
        invalidatedAfterMs: Long? = null,
    ): Report {
        val eval = frames.filter { it.ms in fromMs..toMs }
        val firstCorrect = eval.firstOrNull { it.reading.note == expected }?.ms
        val firstLive = frames.firstOrNull { it.live }?.ms
        var dropouts = 0
        var wrong = 0
        var octave = 0
        if (firstCorrect != null) {
            for (f in eval.filter { it.ms >= firstCorrect }) {
                if (!f.live || f.reading.note != expected) {
                    dropouts++
                    if (f.reading.note != null && f.reading.note != expected) {
                        wrong++
                        val expectedHz = expected.frequency(440.0)
                        val gotHz = f.reading.note!!.frequency(440.0)
                        val ratio = gotHz / expectedHz
                        if (abs(ratio - 2.0) < 0.03 || abs(ratio - 0.5) < 0.015) octave++
                    }
                }
            }
        }
        val centsSeries = eval.filter { it.live && it.reading.note == expected }
            .map { it.reading.centsFine }
        val errs = centsSeries.map { abs(it - trueCents) }
        val mean = if (errs.isEmpty()) 0.0 else errs.average()
        val jitter = if (centsSeries.size < 2) 0.0 else {
            val m = centsSeries.average()
            sqrt(centsSeries.sumOf { (it - m) * (it - m) } / centsSeries.size)
        }
        val invalidated = if (invalidatedAfterMs != null) {
            frames.firstOrNull { it.ms >= invalidatedAfterMs && it.reading.note == null && !it.live }?.ms?.minus(invalidatedAfterMs)
        } else null
        return Report(
            label = label,
            firstLiveMs = firstLive,
            firstCorrectMs = firstCorrect,
            windows = eval.size,
            dropouts = dropouts,
            wrongNotes = wrong,
            octaveJumps = octave,
            jitterCents = jitter,
            medianAbsErrCents = median(errs),
            p95AbsErrCents = percentile(errs, 0.95),
            maxAbsErrCents = if (errs.isEmpty()) 0.0 else errs.max(),
            invalidationMs = invalidated,
            noteChanges = noteChangesOf(eval),
            inTuneReached = eval.any { it.reading.inTune },
            swingCents = if (centsSeries.isEmpty()) 0.0 else centsSeries.max() - centsSeries.min(),
        )
    }

    /** Number of times the displayed note label changes (nulls are not labels). */
    private fun noteChangesOf(frames: List<Frame>): Int {
        var changes = 0
        var previous: dev.anima.resonance.music.Note? = null
        for (f in frames) {
            val n = f.reading.note ?: continue
            if (previous != null && n != previous) changes++
            previous = n
        }
        return changes
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val idx = ((s.size - 1) * p).toInt().coerceIn(0, s.size - 1)
        return s[idx]
    }

    fun header(): String = buildString {
        append("label".padEnd(34))
        append("first".padStart(6))
        append("correct".padStart(8))
        append("dropout".padStart(9))
        append("wrong".padStart(7))
        append("octave".padStart(8))
        append("jitter".padStart(8))
        append("medErr".padStart(8))
        append("p95Err".padStart(8))
        append("maxErr".padStart(8))
        append("invalid".padStart(8))
    }

    /** Cents between two frequencies (log domain — never a plain ratio). */
    fun centsOf(hz: Double, ref: Double): Double = 1200.0 * ln(hz / ref) / ln(2.0)

    /** Ground-truth cents of [measured] against [expected]. */
    fun deviation(measuredHz: Double, expected: Note, concertPitchHz: Double = 440.0): Double =
        centsOf(measuredHz, expected.frequency(concertPitchHz))
}