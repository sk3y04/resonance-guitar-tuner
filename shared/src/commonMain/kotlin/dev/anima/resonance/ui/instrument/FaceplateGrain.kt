package dev.anima.resonance.ui.instrument

import kotlin.random.Random

/**
 * The faceplate's matte grain — a DETERMINISTIC static speckle tile.
 *
 * Pure pixel generation (no Compose, host-testable): a fixed seed produces the
 * same tile forever, so the surface never shimmers, regenerates on pitch ticks,
 * or differs between frames. The composable layer uploads this tile ONCE into
 * an [androidx.compose.ui.graphics.ImageBitmap] and repeats it with a shader;
 * nothing here runs during drawing.
 *
 * Restraint contract (pinned by FaceplateGrainTest):
 *  - specks cover ~14% of the tile, never more than a quarter;
 *  - alpha never exceeds [MAX_ALPHA] (~4.7%) — the grain breaks gradient banding
 *    and reads as satin finish, but cannot reduce text clarity;
 *  - every speck is warm (ivory light or espresso dark), never neutral gray.
 */
object FaceplateGrain {
    const val TILE_SIZE = 192
    const val SEED = 0x5EEDL

    /** Hard alpha cap for any speck (0..255). */
    const val MAX_ALPHA = 12

    /** Warm ivory speck (the palette's parchment family). */
    private const val LIGHT = 0xE8D9BC

    /** Deep espresso speck (the recess family). */
    private const val DARK = 0x120B08

    fun argbPixels(seed: Long = SEED, size: Int = TILE_SIZE): IntArray {
        val rnd = Random(seed)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val roll = rnd.nextInt(1000)
            when {
                roll < 70 -> pixels[i] = ((4 + rnd.nextInt(7)) shl 24) or LIGHT
                roll < 140 -> pixels[i] = ((4 + rnd.nextInt(7)) shl 24) or DARK
                // else: transparent — the surface gradient shows through.
            }
        }
        return pixels
    }
}
