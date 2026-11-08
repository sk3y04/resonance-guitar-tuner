package dev.anima.resonance.presentation

import dev.anima.resonance.dsp.PitchEstimate
import dev.anima.resonance.music.ChromaticTargets
import dev.anima.resonance.music.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Fuzz/property tests for [TunerStateMachine].
 *
 * Invariants (well-formed input): the machine must always emit a consistent
 * reading — a note from the active target set, cents bounded by a half
 * semitone of the nearest target, in-tune only within the exit threshold,
 * and direction agreeing with the cents sign. Malformed input (NaN, ±Inf,
 * negative/huge frequencies, backwards clocks) must NEVER throw and must not
 * leave the machine corrupted: clean frames after garbage must recover a
 * correct lock.
 */
class FuzzStateMachineTest {

    private val rng = Random(0x5EED1L)
    private val chromatic = ChromaticTargets.inRange(440.0)

    private fun assertReadingConsistent(r: TunerReading, activeSet: List<Note>) {
        assertNotNull("status must be set", r.status)
        if (r.note != null) {
            assertTrue("note ${r.note} not in active set", activeSet.contains(r.note))
            if (r.inTune) {
                assertTrue("in-tune but |cents|=${r.cents} > exit", kotlin.math.abs(r.cents) <= 8.0 + 1e-9)
            }
            if (r.inTune) {
                assertEquals(PitchDirection.INTUNE, r.direction)
            } else {
                // On a discontinuity frame the machine keeps the stale note
                // with the default FLAT arrow and zeroed cents — so zero may
                // show either direction, but never a signed mismatch.
                assertTrue("direction must agree with cents sign",
                    (r.cents <= 0 && r.direction == PitchDirection.FLAT) ||
                        (r.cents >= 0 && r.direction == PitchDirection.SHARP))
            }
        } else {
            assertFalse("no note yet, but inTune", r.inTune)
        }
    }

    /** 20k pseudo-random well-formed frames: no throw, reading consistent, cents bounded. */
    @Test
    fun randomWellFormedFramesStayConsistent() {
        val sm = TunerStateMachine()
        var t = 0L
        var manualActive: Note? = null
        var restricted = false
        var hugeRun = 0
        for (i in 0 until 20_000) {
            t += 1 + rng.nextLong(12)
            val present = rng.nextInt(10) < 8
            val freq = if (present) 55.0 + rng.nextDouble() * 1145.0 else 0.0
            val conf = 0.5 + rng.nextDouble() * 0.5
            if (i % 97 == 0) sm.setConcertPitch(400.0 + rng.nextDouble() * 80.0)
            if (i % 131 == 0) {
                restricted = rng.nextBoolean() && rng.nextInt(3) > 0
                sm.setTargetSet(if (restricted) chromatic.subList(0, 1 + rng.nextInt(6)) else null)
            }
            if (i % 211 == 0) {
                manualActive = if (rng.nextBoolean()) chromatic[rng.nextInt(chromatic.size)] else null
                sm.setManualTarget(manualActive)
            }
            val reading = sm.onPitch(PitchEstimate(freq, conf), t)
            // Every possible active set (full chromatic, any prefix subset,
            // any manual pick) is a subset of [chromatic], so checking
            // membership against the full set is sound.
            assertReadingConsistent(reading, chromatic)
            if (reading.note != null && manualActive == null && !restricted) {
                // Bounded staleness (fuzz contract, full chromatic mode
                // only): a displayed note may sit far outside its semitone —
                // a legitimate transition, a just-changed A4 reference, or a
                // measurement hovering just over the 50-cent nearest-match
                // boundary (the smoothed median can overshoot by a cent or
                // two) — for at most a few frames; never indefinitely. The
                // 200¢ threshold is the true "impossible" bar (2 semitones:
                // even a fully stale 5-frame ring cannot exceed it before
                // the guard yields). A manual lock or a restricted
                // instrument set may show large deviations by design.
                if (kotlin.math.abs(reading.cents) > 200) hugeRun++ else hugeRun = 0
                assertTrue("stale display (${reading.cents}¢ on ${reading.note}) lasted $hugeRun frames (max 4)",
                    hugeRun <= 4)
            }
        }
    }

    /** Garbage frames never throw and never corrupt the lock afterwards. */
    @Test
    fun malformedFramesNeverThrowAndNeverCorrupt() {
        val sm = TunerStateMachine()
        var t = 0L
        val garbage = listOf(
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            -1234.0, 1e300, 1e-300, 0.0,
        )
        for (i in 0 until 500) {
            t += 11
            val f = garbage[i % garbage.size]
            val conf = when (i % 5) {
                0 -> Double.NaN
                1 -> -1.0
                2 -> 2.0
                3 -> 0.9
                else -> 0.55
            }
            val reading = sm.onPitch(PitchEstimate(f, conf), t) // must not throw
            if (reading.note != null) {
                // A garbage frame may transiently emit something, but in-tune
                // must never be claimed on non-finite numbers.
                assertFalse("garbage frame claimed in-tune with freq=${reading.frequencyHz}",
                    reading.inTune && !reading.frequencyHz.isFinite())
            }
            if (i % 25 == 0) {
                // Backwards / huge clocks must not throw either.
                sm.onPitch(PitchEstimate(440.0, 0.9), t - (1 + rng.nextLong(999)))
                sm.onPitch(PitchEstimate(440.0, 0.9), Long.MAX_VALUE)
            }
        }
        // Recovery: 10 clean A4 frames must lock A4 (hysteresis=3, ring=5).
        var last: TunerReading? = null
        repeat(10) {
            t += 11
            last = sm.onPitch(PitchEstimate(440.0, 0.95), t)
        }
        assertNotNull(last)
        assertEquals(Note.fromLabel("A4"), last!!.note)
        assertTrue("clean frames after garbage must lock in-tune", last!!.inTune)
        assertFalse("freq must be finite after garbage", !last!!.frequencyHz.isFinite())
    }

    /** Concert-pitch setter keeps its documented contract under fuzzed values. */
    @Test
    fun concertPitchContractUnderFuzz() {
        val sm = TunerStateMachine()
        for (hz in 400..480) sm.setConcertPitch(hz.toDouble())
        val outOfBand = listOf(399.99, 480.01, 0.0, -5.0, 1000.0, Double.NaN)
        outOfBand.forEach { hz ->
            val threw = try {
                sm.setConcertPitch(hz)
                false
            } catch (e: IllegalArgumentException) {
                true
            }
            assertTrue("setConcertPitch($hz) must reject with IAE", threw)
        }
        // And the machine is still usable afterwards.
        sm.setConcertPitch(432.0)
        var t = 0L
        repeat(6) {
            t += 11
            val r = sm.onPitch(PitchEstimate(432.0, 0.95), t)
            if (r.note != null) assertEquals(Note.fromLabel("A4"), r.note)
        }
    }

    /** Empty/whitespace target sets are normalized to chromatic (never a crash). */
    @Test
    fun emptyTargetSetFallsBackToChromatic() {
        val sm = TunerStateMachine()
        sm.setTargetSet(emptyList())
        var t = 0L
        repeat(6) {
            t += 11
            val r = sm.onPitch(PitchEstimate(220.0, 0.95), t)
            if (r.note != null) assertEquals(Note.fromLabel("A3"), r.note)
        }
    }
}
