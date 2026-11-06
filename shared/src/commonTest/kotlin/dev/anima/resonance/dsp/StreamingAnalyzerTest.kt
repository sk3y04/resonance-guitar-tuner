package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.TunerStatus
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamingAnalyzerTest {

    private companion object {
        const val SR = 48000
        const val HOP = 1024
    }

    private fun sineChunk(frequency: Double, amp: Double = 0.5, phase: Int = 0): FloatArray {
        val out = FloatArray(HOP)
        for (i in 0 until HOP) {
            out[i] = (amp * sin(2.0 * PI * frequency * (phase + i) / SR)).toFloat()
        }
        return out
    }

    private fun silence(): FloatArray = FloatArray(HOP)

    private fun clipChunk(): FloatArray = FloatArray(HOP) { if (it % 2 == 0) 1.0f else -1.0f }

    private fun analyzer() = StreamingAnalyzer(SR, frameSize = 2048)

    private fun run(a: StreamingAnalyzer, chunks: List<FloatArray>, startMs: Long = 0L): List<dev.anima.resonance.presentation.TunerSnapshot> {
        val out = ArrayList<dev.anima.resonance.presentation.TunerSnapshot>(chunks.size)
        for ((i, c) in chunks.withIndex()) {
            a.processChunk(c, startMs + i * (HOP * 1000L / SR))?.let { out += it }
        }
        return out
    }

    @Test
    fun nullBeforeFirstFrameThenMeasuredSine() {
        val a = analyzer()
        assertNull(a.processChunk(sineChunk(440.0), 0L), "no frame before 2048 samples")
        val first = a.processChunk(sineChunk(440.0, phase = HOP), 20L)
        assertEquals(TunerStatus.UNSTABLE, first?.status, "2048 samples = one frame, 1 hysteresis frame")
        val snaps = run(a, List(20) { i -> sineChunk(440.0, phase = 2 * HOP + i * HOP) }, startMs = 40L)
        assertTrue(snaps.isNotEmpty())
        val last = snaps.last()
        assertEquals(TunerStatus.MEASURED, last.status)
        assertEquals(Note.fromLabel("A4"), last.reading.note)
        assertEquals(440.0, last.reading.frequencyHz, 1.0)
        assertTrue(last.rms > 0.2, "rms of 0.5-amp sine ~0.35, got ${last.rms}")
    }

    @Test
    fun clippingSurfaceOverridesStatus() {
        val a = analyzer()
        val snaps = run(a, List(12) { clipChunk() })
        val last = snaps.last()
        assertTrue(last.clipping, "full-scale input must be flagged")
        assertEquals(TunerStatus.CLIPPING, last.status)
    }

    @Test
    fun silenceStaysListening() {
        val a = analyzer()
        val snaps = run(a, List(20) { silence() })
        val last = snaps.last()
        assertEquals(TunerStatus.LISTENING, last.status)
        assertNull(last.reading.note)
        assertEquals(0.0, last.reading.frequencyHz, 1e-9)
        assertEquals(0.0, last.rms, 1e-9)
    }

    @Test
    fun callerChunksAreNotMutated() {
        val a = analyzer()
        val chunk = sineChunk(440.0)
        val before = chunk.copyOf()
        repeat(6) { a.processChunk(chunk, it * 20L) }
        assertTrue(before.contentEquals(chunk), "caller chunk must not be mutated")
    }

    @Test
    fun goesStaleAfterSilenceStream() {
        val a = analyzer()
        var phase = 0
        var t = 0L
        repeat(30) {
            a.processChunk(sineChunk(440.0, phase = phase), t); phase += HOP; t += 21L
        }
        assertTrue(a.isReady())
        var snap = a.processChunk(silence(), t); t += 21L
        while (snap != null && snap.status != TunerStatus.STALE) {
            snap = a.processChunk(silence(), t); t += 21L
            assertTrue(t < 20000, "must go stale within the 600ms window, took ${t}ms")
        }
        assertEquals(TunerStatus.STALE, snap!!.status)
    }

    @Test
    fun lowLevelSineIsNotClipping() {
        val a = analyzer()
        val last = run(a, List(12) { sineChunk(440.0, amp = 0.05) }).last()
        assertFalse(last.clipping)
        assertEquals(TunerStatus.MEASURED, last.status)
    }
}
