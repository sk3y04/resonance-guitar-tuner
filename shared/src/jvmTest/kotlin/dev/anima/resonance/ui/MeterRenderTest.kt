package dev.anima.resonance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import dev.anima.resonance.ui.instrument.AnalogTuningMeter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pixel contract for the analog dial, which paints its static face and its
 * moving needle in two separate render layers.
 *
 * The split is a performance change, so the ANALOG INSTRUMENT must render the
 * same way it did: an ivory glass face, an ink scale arc, a brass escutcheon at
 * the bottom seam, and a needle that points at the measured position (negative
 * cents left of vertical, positive right). Pinning it in pixels is what stops a
 * "cached artwork" refactor from silently blanking the face or dropping the
 * needle; the frame cost itself is verified on device.
 */
class MeterRenderTest {

    private val w = 720
    private val h = 480

    private fun render(position: Double, active: Boolean = true): BufferedImage {
        val scene = ImageComposeScene(
            width = w,
            height = h,
            density = Density(density = 2f, fontScale = 1f),
            coroutineContext = Dispatchers.Unconfined,
        ) {
            ResonanceTheme {
                AnalogTuningMeter(
                    position = position,
                    inTune = false,
                    clamped = false,
                    active = active,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        try {
            val image = scene.render(0L)
            val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
            java.io.File("build/render").mkdirs()
            java.io.File("build/render/meter-${position}.png").writeBytes(png)
            return ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    private fun lum(img: BufferedImage, x: Int, y: Int): Double {
        val rgb = img.getRGB(x, y)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * The needle is the only dark stroke that spans many radii from the pivot;
     * ticks are short rim marks. Score each angle by the number of distinct
     * radial bins it occupies and return the best one in degrees (0 = vertical).
     */
    private fun needleAngleDegrees(img: BufferedImage): Double {
        val density = 2f
        val pivotX = w / 2.0
        val pivotY = (h - 6 * density) + 12 * density
        var best = 0.0
        var bestScore = -1
        var deg = -50.0
        while (deg <= 50.0) {
            val rad = deg * PI / 180.0
            val ux = sin(rad)
            val uy = -cos(rad)
            val bins = HashSet<Int>()
            var r = 20.0
            while (r < 300.0) {
                val x = (pivotX + ux * r).toInt()
                val y = (pivotY + uy * r).toInt()
                if (x in 1 until w - 1 && y in 1 until h - 1 && lum(img, x, y) < 90.0) {
                    bins.add((r / 4).toInt())
                }
                r += 1.0
            }
            if (bins.size > bestScore) {
                bestScore = bins.size
                best = deg
            }
            deg += 0.5
        }
        return best
    }

    @Test
    fun theDialRendersFaceScaleEscutcheonAndNeedle() {
        val img = render(position = -0.6)

        // Ivory glass face: bright and warm (r > b) in the middle of the dial.
        val face = img.getRGB(w / 2, 150)
        val fr = (face shr 16) and 0xFF
        val fb = face and 0xFF
        assertTrue(
            lum(img, w / 2, 150) > 170.0,
            "the glass face must be lit ivory (lum=${lum(img, w / 2, 150)} rgb=${Integer.toHexString(face)})",
        )
        assertTrue(fr > fb, "the face must stay warm (r=$fr b=$fb)")

        // Brass escutcheon at the bottom seam (the needle's hidden tail).
        val esc = img.getRGB(w / 2, 450)
        assertTrue(lum(img, w / 2, 450) < 175.0, "brass escutcheon missing at the seam")
        assertTrue(((esc shr 16) and 0xFF) > (esc and 0xFF), "the escutcheon must read brass")

        // The scale exists: dark ink (numerals + arc + ticks) in the top band.
        var rimDark = 0
        for (y in 50 until 95) for (x in 40 until w - 40) if (lum(img, x, y) < 120.0) rimDark++
        assertTrue(rimDark > 200, "the ink scale must be drawn (found $rimDark dark scale px)")
    }

    @Test
    fun theNeedlePointsAtTheMeasuredPosition() {
        val left = needleAngleDegrees(render(position = -0.6))
        val right = needleAngleDegrees(render(position = 0.6))
        assertTrue(left < -12.0, "flat reading must deflect left (got $left°)")
        assertTrue(right > 12.0, "sharp reading must deflect right (got $right°)")
        assertEquals(abs(left), abs(right), 2.0, "deflection must be symmetric ($left vs $right)")
        // ±0.6 of full scale on a 90° sweep = ±27°.
        assertEquals(27.0, abs(left), 4.0, "needle angle must follow the linear cents map")
    }

    @Test
    fun theNeedleIsOnTheDialWithoutSignal() {
        // No signal never means "no needle": it idles on the flat side of the
        // scale (well left of 0¢), so the dial reads as an instrument that is
        // not measuring rather than as a blank face or a fake centered reading.
        val idleAngle = needleAngleDegrees(render(position = 0.0, active = false))
        assertTrue(idleAngle < -15.0, "the idle needle must rest on the flat side (got $idleAngle°)")
        assertTrue(idleAngle > -70.0, "the idle needle must stay on the scale (got $idleAngle°)")
    }

    @Test
    fun theDialRendersIdenticallyAcrossRuns() {
        val a = render(position = 0.25)
        val b = render(position = 0.25)
        for (y in 0 until h step 7) for (x in 0 until w step 7) {
            assertEquals(a.getRGB(x, y), b.getRGB(x, y), "render must be deterministic at ($x,$y)")
        }
    }
}