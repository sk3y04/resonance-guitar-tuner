package dev.anima.resonance.ui.instrument

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The walnut grain is procedural but DETERMINISTIC — the same seed and
 * panel size must always produce the same streaks (no per-frame randomness,
 * no flicker while the needle moves). Grain runs HORIZONTALLY along the
 * enclosure (predominantly horizontal walnut figure) with a gentle vertical
 * wander, plus broad tonal bands for natural color variation.
 */
class WoodGrainTest {

    @Test
    fun sameSeedAndSizeProduceIdenticalGrain() {
        val a = WoodGrain.streaks(seed = 7L, width = 400f, height = 900f)
        val b = WoodGrain.streaks(seed = 7L, width = 400f, height = 900f)
        assertEquals(a, b)
        assertEquals(WoodGrain.bands(7L, 900f), WoodGrain.bands(7L, 900f))
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
    fun horizontalStreaksStayInsideThePanelAndAreFinite() {
        val streaks = WoodGrain.streaks(11L, 360f, 800f, count = 30)
        for (s in streaks) {
            assertTrue(s.startY in 0f..800f, "startY out of bounds: ${s.startY}")
            assertTrue(s.thickness > 0f, "thickness must be positive")
            assertTrue(s.alpha in 0f..1f, "alpha out of range: ${s.alpha}")
            assertTrue(s.amplitude >= 0f)
            assertTrue(s.wavelength > 0f)
            assertTrue(s.startY.isFinite() && s.amplitude.isFinite() && s.wavelength.isFinite())
            // The wander must stay gentle relative to the panel height.
            assertTrue(s.amplitude <= 800f * 0.06f, "wander too wide: ${s.amplitude}")
        }
    }

    @Test
    fun wanderIsBoundedByAmplitude() {
        val streaks = WoodGrain.streaks(5L, 360f, 800f, count = 10)
        for (s in streaks) {
            for (x in listOf(0f, 90f, 180f, 270f, 360f)) {
                assertTrue(
                    abs(WoodGrain.wanderAt(s, x)) <= s.amplitude + 1e-3f,
                    "wander exceeded amplitude at x=$x",
                )
            }
        }
    }

    @Test
    fun tonalBandsAreBoundedAndSubtle() {
        val bands = WoodGrain.bands(9L, 800f, count = 5)
        assertEquals(5, bands.size)
        for (b in bands) {
            assertTrue(b.centerY in 0f..800f, "band center out of bounds")
            assertTrue(b.halfHeight > 0f)
            assertTrue(b.alpha in 0f..0.2f, "tonal bands must stay subtle: ${b.alpha}")
            assertTrue(b.centerY.isFinite() && b.halfHeight.isFinite())
        }
    }

    @Test
    fun degenerateSizesDoNotCrashOrProduceNaN() {
        val tiny = WoodGrain.streaks(1L, 1f, 1f, count = 5)
        assertEquals(5, tiny.size)
        for (s in tiny) {
            assertTrue(s.startY.isFinite() && s.wavelength.isFinite() && s.thickness.isFinite())
        }
        assertEquals(3, WoodGrain.bands(1L, 1f, count = 3).size)
    }
}