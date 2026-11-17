package dev.anima.resonance.ui.instrument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The faceplate's matte grain is a DETERMINISTIC static texture — generated
 * once from a fixed seed, never per frame and never from the pitch state. These
 * tests pin the generator's contract so the rendered surface cannot drift into
 * visible noise, tiling artifacts, or text-destroying contrast.
 */
class FaceplateGrainTest {

    @Test
    fun theSameSeedAlwaysProducesTheSameTile() {
        val a = FaceplateGrain.argbPixels(seed = FaceplateGrain.SEED)
        val b = FaceplateGrain.argbPixels(seed = FaceplateGrain.SEED)
        assertTrue(a.contentEquals(b), "grain must be deterministic (fixed seed)")
    }

    @Test
    fun aDifferentSeedProducesADifferentTile() {
        val a = FaceplateGrain.argbPixels(seed = FaceplateGrain.SEED)
        val b = FaceplateGrain.argbPixels(seed = FaceplateGrain.SEED + 1)
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun theTileIsSquareAndFullyPopulated() {
        val px = FaceplateGrain.argbPixels()
        assertEquals(FaceplateGrain.TILE_SIZE * FaceplateGrain.TILE_SIZE, px.size)
    }

    @Test
    fun grainIsBarelyPerceptibleAndNeverOpaque() {
        val px = FaceplateGrain.argbPixels()
        var specks = 0
        for (argb in px) {
            val alpha = (argb ushr 24) and 0xFF
            assertTrue(alpha <= FaceplateGrain.MAX_ALPHA, "grain alpha $alpha exceeds the cap")
            if (alpha > 0) specks++
        }
        val density = specks.toDouble() / px.size
        // Sparse enough to read as matte finish, dense enough to break banding.
        assertTrue(density in 0.04..0.25, "grain density was $density")
        assertTrue(specks > 0, "grain must not be empty")
    }

    @Test
    fun grainSpecksAreWarmNotGray() {
        val px = FaceplateGrain.argbPixels()
        var warm = 0
        var specks = 0
        for (argb in px) {
            val alpha = (argb ushr 24) and 0xFF
            if (alpha == 0) continue
            specks++
            val r = (argb ushr 16) and 0xFF
            val b = argb and 0xFF
            if (r >= b) warm++
        }
        // Every speck belongs to the warm material family (light ivory or deep
        // espresso); a neutral gray noise would read as TV static on brown.
        assertEquals(specks, warm, "grain must stay in the warm family")
    }
}
