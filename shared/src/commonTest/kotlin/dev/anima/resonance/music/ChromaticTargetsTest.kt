package dev.anima.resonance.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromaticTargetsTest {

    @Test
    fun rangeAt440SpansTuningBand() {
        val notes = ChromaticTargets.inRange(440.0) // default 55..1200 Hz
        assertTrue(notes.size > 40, "expected a full chromatic span, got ${notes.size}")
        assertEquals("A1", notes.first().label)  // 55.0 Hz exactly at A4=440
        assertEquals("D6", notes.last().label)   // 1174.7 Hz; next note exceeds 1200
    }

    @Test
    fun bandEdgesAreRespected() {
        val notes = ChromaticTargets.inRange(440.0)
        notes.forEach { n ->
            val f = n.frequency(440.0)
            assertTrue(f in 55.0..1200.0, "${n.label} = $f Hz outside band")
        }
        // strictly ascending
        for (i in 1 until notes.size) {
            assertTrue(notes[i].frequency(440.0) > notes[i - 1].frequency(440.0))
        }
    }

    @Test
    fun rangeFollowsConcertPitch() {
        // At a lower concert pitch the same MIDI band shifts down in Hz; the
        // number of in-band notes may change, but every result stays in band.
        val notes = ChromaticTargets.inRange(432.0)
        notes.forEach { n ->
            val f = n.frequency(432.0)
            assertTrue(f in 55.0..1200.0, "${n.label} = $f Hz outside band at 432")
        }
    }

    @Test
    fun rejectsNonsenseBand() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { ChromaticTargets.inRange(440.0, 1200.0, 55.0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { ChromaticTargets.inRange(440.0, -1.0, 1200.0) }
    }
}
