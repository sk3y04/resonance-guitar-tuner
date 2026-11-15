package dev.anima.resonance.ui.instrument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The walnut grain is procedural but DETERMINISTIC — the same seed and
 * panel size must always produce the same streaks (no per-frame randomness,
 * no flicker while the needle moves). Streaks run along the panel's long
 * axis with a gentle sideways wander.
 */
class WoodGrainTest {

    @Test
    fun sameSeedAndSizeProduceIdenticalGrain() {
        val a = WoodGrain.streaks(seed = 7L, width = 400f, height = 900f)
        val b = WoodGrain.streaks(seed = 7L, width = 400f, height = 900f)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsProduceDifferentGrain() {
        val a = WoodGrain.streaks(seed = 7L, width = 400f, height = 900f)
        val b = WoodGrain.streaks(seed = 8L, width = 400f, height = 900f)
        assertTrue(a != b, "a different seed must change the grain pattern")
    }

    @Test
    fun streakCountIsRespected() {
        assertEquals(12, WoodGrain.streaks(3L, 400f, 900f, count = 12).size)
        assertEquals(40, WoodGrain.streaks(3L, 400f, 900f, count = 40).size)
    }

    @Test
    fun streaksStayInsideThePanelAndAreFinite() {
        val streaks = WoodGrain.streaks(11L, 360f, 800f, count = 30)
        for (s in streaks) {
            assertTrue(s.startX in 0f..360f, "startX out of bounds: ${s.startX}")
            assertTrue(s.thickness > 0f, "thickness must be positive")
            assertTrue(s.alpha in 0f..1f, "alpha out of range: ${s.alpha}")
            assertTrue(s.amplitude >= 0f)
            assertTrue(s.wavelength > 0f)
            assertTrue(s.startX.isFinite() && s.amplitude.isFinite() && s.wavelength.isFinite())
            // The wander must not push the streak far outside the panel.
            assertTrue(s.amplitude <= 360f * 0.1f, "wander too wide: ${s.amplitude}")
        }
    }

    @Test
    fun degenerateSizesDoNotCrashOrProduceNaN() {
        val tiny = WoodGrain.streaks(1L, 1f, 1f, count = 5)
        assertEquals(5, tiny.size)
        for (s in tiny) {
            assertTrue(s.startX.isFinite() && s.wavelength.isFinite() && s.thickness.isFinite())
        }
    }
}