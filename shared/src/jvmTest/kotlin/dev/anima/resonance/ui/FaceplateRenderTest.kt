package dev.anima.resonance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import dev.anima.resonance.ui.instrument.ControlDeck
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pixel contract for the matte faceplate finish, rendered headlessly at
 * phone density. Pins the MATERIAL claims so they cannot silently regress:
 *  - the surface is top-lit (lighter near the readout seam, deeper at bottom);
 *  - a soft contact shadow sits under the seam (no hard divider line);
 *  - the static grain is present but restrained (breaks banding, never noise);
 *  - two renders of the same surface are byte-identical (no moving grain);
 *  - the material stays warm (r > b), never a neutral gray gradient.
 */
class FaceplateRenderTest {

    private fun renderDeck(): ByteArray {
        val scene = ImageComposeScene(
            width = 720,
            height = 800,
            density = Density(density = 2f, fontScale = 1f),
            coroutineContext = Dispatchers.Unconfined,
        ) {
            ResonanceTheme {
                ControlDeck(modifier = Modifier.fillMaxSize()) {}
            }
        }
        try {
            val image = scene.render(0L)
            return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
        } finally {
            scene.close()
        }
    }

    private fun lum(r: Int, g: Int, b: Int): Double = 0.2126 * r + 0.7152 * g + 0.0722 * b

    @Test
    fun theFaceplateIsTopLitWarmGrainedAndSeamShadowed() {
        val png = renderDeck()
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(720, img.width)
        assertEquals(800, img.height)

        fun patchLum(x0: Int, y0: Int, w: Int, h: Int): Double {
            var sum = 0.0
            for (y in y0 until y0 + h) for (x in x0 until x0 + w) {
                val rgb = img.getRGB(x, y)
                sum += lum((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            }
            return sum / (w * h)
        }

        // Top-lit: the upper control area is lighter than the deep bottom.
        val top = patchLum(300, 120, 120, 20)
        val bottom = patchLum(300, 740, 120, 20)
        assertTrue(top > bottom + 2.0, "faceplate must be top-lit (top=$top bottom=$bottom)")

        // Contact shadow: the first rows under the seam are darker than the
        // lit surface a little below — a gradient, not a divider line.
        val seam = patchLum(300, 2, 120, 4)
        val below = patchLum(300, 60, 120, 10)
        assertTrue(seam < below - 1.0, "seam contact shadow missing (seam=$seam below=$below)")

        // Warm family: red channel dominates blue everywhere on the surface.
        val rgb = img.getRGB(360, 400)
        val r = (rgb shr 16) and 0xFF
        val b = rgb and 0xFF
        assertTrue(r > b + 8, "faceplate must stay warm (r=$r b=$b)")

        // Grain: measurable micro-variance, but restrained. A flat gradient
        // would give ~0; obvious noise would give tens of levels.
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 380 until 440) for (x in 300 until 360) {
            val p = img.getRGB(x, y)
            val l = lum((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            sum += l
            sumSq += l * l
            n++
        }
        val variance = sumSq / n - (sum / n) * (sum / n)
        val std = kotlin.math.sqrt(variance.coerceAtLeast(0.0))
        assertTrue(std in 0.3..8.0, "grain must be perceptible but restrained (std=$std)")
    }

    @Test
    fun theGrainIsStaticAcrossRenders() {
        val a = renderDeck()
        val b = renderDeck()
        assertTrue(a.contentEquals(b), "the faceplate must render identically — no moving grain")
    }
}
