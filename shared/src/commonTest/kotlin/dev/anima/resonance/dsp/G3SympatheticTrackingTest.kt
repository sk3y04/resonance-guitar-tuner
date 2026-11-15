package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine
import dev.anima.resonance.presentation.TunerStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * G3 tracking regression (root cause: sympathetic fourth mix).
 *
 * User report: sustaining G3 detects initially, then loses the note after
 * roughly one second; other notes track normally.
 *
 * Evidence (development probes, see docs/TDD_LOG.md, G3 tracking section): a real G3 pluck
 * excites the open D string — G3's 3rd partial (588 Hz) matches D3's 4th
 * harmonic (587.3 Hz) within ~1 cent. The resulting 4:3 mix is not periodic
 * at any in-band lag, so YIN's strict 0.15 crossing finds nothing and
 * returned "no pitch" even though the 196 Hz dip is deep (d'=0.18 at
 * -14 dB sympathetic level) and overwhelmingly tonal. The state machine
 * then correctly cleared the reading (~1 s to STALE) — the loss was in the
 * detector, not the UI. Unlike the octave-bleed case, a fourth relationship
 * has no state-machine fold.
 *
 * Contract: while a clear G3-centered signal is present, the displayed
 * note must not drop out after locking; true silence must still clear.
 */
class G3SympatheticTrackingTest {

    private companion object {
        const val SR = 48000
        const val CHUNK = 1024
    }

    /** Sustained harmonic stack with slow exponential decay (ringing string). */
    private fun sustain(f0: Double, seconds: Double, decayTau: Double = 2.5, harmonics: Int = 8): FloatArray {
        val n = (seconds * SR).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / SR
            val env = 0.35 * exp(-t / decayTau)
            var v = 0.0
            for (h in 1..harmonics) v += sin(2.0 * PI * f0 * h * t) / h
            (env * v).toFloat()
        }
    }

    private fun sine(f0: Double, seconds: Double, amp: Double = 0.3): FloatArray {
        val n = (seconds * SR).toInt()
        return FloatArray(n) { (amp * sin(2.0 * PI * f0 * it / SR)).toFloat() }
    }

    /** Karplus-Strong pluck (same physical model as PluckedStringRealismTest). */
    private fun pluck(f0: Double, seconds: Double): FloatArray {
        val n = (seconds * SR).toInt()
        val period = SR / f0
        val M = period.toInt().coerceAtLeast(2)
        val alpha = period - M
        val L = M + 1
        val pluckPos = (L * 0.3).toInt().coerceAtLeast(1)
        val buf = DoubleArray(L) { i ->
            if (i < pluckPos) i.toDouble() / pluckPos
            else (L - i).toDouble() / (L - pluckPos)
        }
        val out = FloatArray(n)
        for (i in 0 until n) {
            val a = buf[(i - M + L) % L]
            val b = buf[(i - M - 1 + L) % L]
            out[i] = a.toFloat()
            buf[i % L] = 0.996 * ((1 - alpha) * a + alpha * b)
        }
        val peak = out.maxOf { abs(it.toDouble()) }.coerceAtLeast(1e-9)
        for (i in out.indices) out[i] = (out[i] / peak * 0.5).toFloat()
        return out
    }

    /** Sympathetic string building up over [rampMs] (energy transfer takes time). */
    private fun withDelayedSympathetic(main: FloatArray, fSymp: Double, relDb: Double, rampMs: Long): FloatArray {
        val amp = Math.pow(10.0, relDb / 20.0)
        return FloatArray(main.size) { i ->
            val t = i.toDouble() / SR
            val buildUp = ((t * 1000.0) / rampMs).coerceAtMost(1.0)
            val symp = amp * buildUp * exp(-t / 3.0) * sin(2.0 * PI * fSymp * t)
            (main[i] + symp).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private data class Frame(val ms: Long, val snap: TunerSnapshot)

    private fun run(audio: FloatArray, machine: TunerStateMachine = TunerStateMachine()): List<Frame> {
        val analyzer = StreamingAnalyzer(SR, stateMachine = machine)
        val frames = mutableListOf<Frame>()
        var nowMs = 0L
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + CHUNK, audio.size)
            nowMs += (end - i).toLong() * 1000 / SR
            analyzer.processChunk(audio.copyOfRange(i, end), nowMs)?.let { frames.add(Frame(nowMs, it)) }
            i = end
        }
        return frames
    }

    /**
     * Continuity contract: after the first lock of [expected], while the
     * signal is clearly present ([presentMs]), gaps without the note must
     * stay short (<= 300 ms) and the display must not wander to other notes.
     */
    private fun assertContinuity(label: String, frames: List<Frame>, expected: Note, presentMs: Long) {
        val present = frames.filter { it.ms <= presentMs }
        val firstLock = present.firstOrNull { it.snap.reading.note == expected }
        assertTrue(firstLock != null, "$label: never locked $expected")
        val after = present.filter { it.ms >= firstLock!!.ms }
        var gapStart: Long? = null
        var worstGap = 0L
        var wrongNoteFrames = 0
        for (f in after) {
            val n = f.snap.reading.note
            if (n != expected) {
                if (n != null) wrongNoteFrames++
                if (gapStart == null) gapStart = f.ms
            } else {
                gapStart?.let { worstGap = maxOf(worstGap, f.ms - it) }
                gapStart = null
            }
        }
        gapStart?.let { worstGap = maxOf(worstGap, present.last().ms - it) }
        assertTrue(worstGap <= 300, "$label: note dropped out for ${worstGap}ms while signal present")
        assertTrue(wrongNoteFrames <= after.size / 20, "$label: wandered to other notes ($wrongNoteFrames frames)")
    }

    // ── The bug regression: sympathetic D3 builds up under a sustained G3 ──

    @Test
    fun g3WithDelayedSympatheticD3KeepsTracking() {
        for (db in listOf(-10.0, -14.0)) {
            val audio = withDelayedSympathetic(sustain(196.0, 3.5), 146.83, db, rampMs = 700)
            val frames = run(audio)
            assertContinuity("G3+delayedD3 ${db}dB", frames, Note("G", 3), presentMs = 3000)
        }
    }

    @Test
    fun g3WithSympatheticD3InInstrumentAutoAndManualLock() {
        val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4").map { Note.fromLabel(it) }
        val audio = withDelayedSympathetic(sustain(196.0, 3.5), 146.83, -10.0, rampMs = 700)
        val auto = TunerStateMachine().apply { setTargetSet(strings) }
        assertContinuity("G3+D3 AUTO", run(audio, auto), Note("G", 3), presentMs = 3000)
        val manual = TunerStateMachine().apply { setTargetSet(strings); setManualTarget(Note("G", 3)) }
        assertContinuity("G3+D3 manual", run(audio, manual), Note("G", 3), presentMs = 3000)
    }

    /**
     * Detector-level contract: a tonal sub-threshold dip at the played
     * fundamental must be recovered (d' < 0.35 + strong spectral presence),
     * while the noise-trend global minimum (~65 Hz, no tonal energy) must
     * never win.
     */
    @Test
    fun yinRecoversTheTonalSubThresholdDipOfTheMix() {
        val yin = Yin()
        for (db in listOf(-10.0, -14.0)) {
            val audio = withDelayedSympathetic(sustain(196.0, 1.2), 146.83, db, rampMs = 300)
            val start = (0.8 * SR).toInt()
            val frame = Conditioning.removeDc(audio.copyOfRange(start, start + 4096))
            val est = yin.detect(frame, SR)
            assertTrue(
                est.frequencyHz > 0.0 && abs(est.frequencyHz - 196.0) / 196.0 < 0.01,
                "mix ${db}dB: expected ~196 Hz, got ${est.frequencyHz}",
            )
            assertTrue(est.confidence >= 0.5, "mix ${db}dB: confidence ${est.confidence} below the state-machine floor")
        }
    }

    /**
     * Physical limit (documented, not hidden): when the sympathetic string
     * is LOUDER than the played note (-6 dB), the mix carries no recoverable
     * periodicity at 196 Hz (d' ~ 0.53). The tuner must then be honest —
     * no false lock on another note, and the reading clears.
     */
    @Test
    fun overwhelminglySympatheticMixStaysHonest() {
        val audio = withDelayedSympathetic(sustain(196.0, 3.0), 146.83, -6.0, rampMs = 700)
        val frames = run(audio)
        val late = frames.filter { it.ms in 1500..2800 }
        assertTrue(late.isNotEmpty(), "no late frames")
        assertTrue(
            late.all { it.snap.reading.note == null || it.snap.reading.note == Note("G", 3) },
            "must not lock a wrong note on the dominated mix: ${late.map { it.snap.reading.note }.distinct()}",
        )
    }

    // ── Controls: clean signals and adjacent notes keep working ──────────

    @Test
    fun sustainedG3HarmonicStackTracksContinuously() {
        assertContinuity("G3 sustain", run(sustain(196.0, 3.5)), Note("G", 3), presentMs = 3000)
    }

    @Test
    fun sustainedG3SineTracksContinuously() {
        assertContinuity("G3 sine", run(sine(196.0, 3.5)), Note("G", 3), presentMs = 3000)
    }

    @Test
    fun g3PluckTracksThroughDecay() {
        assertContinuity("G3 pluck", run(pluck(196.0, 3.0)), Note("G", 3), presentMs = 2000)
    }

    @Test
    fun adjacentStringsTrackContinuouslyAsControls() {
        for ((f0, note) in listOf(146.83 to Note("D", 3), 246.94 to Note("B", 3), 82.41 to Note("E", 2), 329.63 to Note("E", 4))) {
            assertContinuity("${note.label} control", run(sustain(f0, 3.5)), note, presentMs = 3000)
        }
    }

    @Test
    fun g3WithInharmonicPartialsAndBeatingAndSettling() {
        val n = (3.5 * SR).toInt()
        // Stiff-string inharmonicity.
        val inharmonic = FloatArray(n) { i ->
            val t = i.toDouble() / SR
            val env = 0.35 * exp(-t / 2.5)
            var v = 0.0
            for (h in 1..8) v += sin(2.0 * PI * 196.0 * h * kotlin.math.sqrt(1.0 + 0.0002 * h * h) * t) / h
            (env * v).toFloat()
        }
        assertContinuity("G3 inharmonic", run(inharmonic), Note("G", 3), presentMs = 3000)
        // Slow amplitude beating (body resonance).
        val beating = FloatArray(n) { i ->
            val t = i.toDouble() / SR
            val env = 0.35 * exp(-t / 2.5) * (1.0 + 0.4 * sin(2.0 * PI * 2.3 * t))
            var v = 0.0
            for (h in 1..8) v += sin(2.0 * PI * 196.0 * h * t) / h
            (env * v).toFloat()
        }
        assertContinuity("G3 beating", run(beating), Note("G", 3), presentMs = 3000)
        // Pluck starts sharp and settles.
        var phase = 0.0
        val settling = FloatArray(n) { i ->
            val t = i.toDouble() / SR
            val f = 196.0 * Math.pow(2.0, 18.0 * exp(-t / 0.4) / 1200.0)
            phase += 2.0 * PI * f / SR
            val env = 0.35 * exp(-t / 2.5)
            var v = 0.0
            for (h in 1..8) v += sin(phase * h) / h
            (env * v).toFloat()
        }
        assertContinuity("G3 settling", run(settling), Note("G", 3), presentMs = 3000)
    }

    @Test
    fun silenceAfterSustainClearsTheReading() {
        val audio = sustain(196.0, 1.5) + FloatArray((1.5 * SR).toInt())
        val frames = run(audio)
        val tail = frames.filter { it.ms > 2400 }
        assertTrue(tail.isNotEmpty(), "no tail frames")
        assertTrue(tail.all { it.snap.reading.note == null }, "true silence must clear the reading")
        assertTrue(
            tail.last().snap.reading.status == TunerStatus.STALE || tail.last().snap.reading.status == TunerStatus.LISTENING,
            "silence must end STALE/LISTENING, got ${tail.last().snap.reading.status}",
        )
    }

    /** The recovery path must not turn noise into a pitch. */
    @Test
    fun noiseDoesNotRecoverAPitch() {
        val yin = Yin()
        val rng = Random(42)
        repeat(20) {
            val noise = FloatArray(4096) { (rng.nextDouble() * 2.0 - 1.0).toFloat() * 0.3f }
            val est = yin.detect(Conditioning.removeDc(noise), SR)
            assertTrue(est.frequencyHz == 0.0, "noise frame produced ${est.frequencyHz} Hz")
        }
    }
}