package dev.anima.resonance.music

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TargetMatcherTest {

    private val c = 440.0
    private val up = { cents: Double -> 2.0.pow(cents / 1200.0) }

    @Test
    fun picksSmallestCentsAmongTargets() {
        val measured = 110.0 * up(15.0) // 15 cents above A2
        val m = TargetMatcher.nearest(measured, listOf(Note("A", 2), Note("A#", 2), Note("B", 2)), c)
        assertEquals("A2", m.note.label)
        assertEquals(15, m.cents)
        assertEquals(110.0, m.targetHz, 1e-9)
    }

    @Test
    fun exactMatchIsZeroCents() {
        val m = TargetMatcher.nearest(246.9417, BuiltInTunings.standard.strings.map { it.note }, c)
        assertEquals("B3", m.note.label)
        assertEquals(0, m.cents)
    }

    @Test
    fun exactTieBreaksTowardLowerFrequency() {
        val measured = 110.0 * up(50.0) // exactly 50c above A2 and 50c below A#2
        val m = TargetMatcher.nearest(measured, listOf(Note("A#", 2), Note("A", 2)), c)
        assertEquals("A2", m.note.label)
        assertEquals(50, m.cents)
    }

    @Test
    fun manualLockPreservesSignedCentsBeyondMeterRange() {
        // Locked to E2 while the string sits two octaves high: target stays E2,
        // cents stay fully signed (±2400), never clamped or swapped.
        val m = TargetMatcher.nearest(Note("E", 4).frequency(c), listOf(Note("E", 2)), c)
        assertEquals("E2", m.note.label)
        assertEquals(2400, m.cents)
    }

    @Test
    fun lockStillReportsFlatDirection() {
        val measured = Note("E", 2).frequency(c) * up(-35.0)
        val m = TargetMatcher.nearest(measured, listOf(Note("E", 2)), c)
        assertEquals(-35, m.cents)
    }

    @Test
    fun rejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { TargetMatcher.nearest(-1.0, listOf(Note("A", 2)), c) }
        assertFailsWith<IllegalArgumentException> { TargetMatcher.nearest(440.0, emptyList(), c) }
    }
}
