package dev.anima.resonance.music

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteMathTest {

    @Test
    fun a4EqualsConcertPitch() {
        assertEquals(440.0, Note("A", 4).frequency(440.0), 1e-9)
    }

    @Test
    fun standardLowEIsE2AtAbout8241Hz() {
        // Scientific pitch notation: E2 ~= 82.407 Hz at A4 = 440.
        assertEquals(82.4069, Note("E", 2).frequency(440.0), 1e-3)
    }

    @Test
    fun frequencyScalesExactlyWithConcertPitch() {
        val a4 = Note("A", 4)
        val ratio = a4.frequency(442.0) / a4.frequency(440.0)
        assertEquals(442.0 / 440.0, ratio, 1e-12)
    }

    @Test
    fun octaveIsOneTwelfthRatio() {
        val e2 = Note("E", 2).frequency(440.0)
        val e3 = Note("E", 3).frequency(440.0)
        assertEquals(2.0, e3 / e2, 1e-12)
    }

    @Test
    fun rejectsOutOfRangeConcertPitch() {
        // Product contract: A4 is only accepted in 400..480 Hz.
        val a4 = Note("A", 4)
        kotlin.test.assertFailsWith<IllegalArgumentException> { a4.frequency(399.9) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { a4.frequency(480.1) }
        // bounds themselves are valid
        a4.frequency(400.0)
        a4.frequency(480.0)
    }
}
