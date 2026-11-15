package dev.anima.resonance.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Partial-arc cents dial with tick marks, a centered in-tune zone, and a
 * smoothly moving pointer (spec §8). Geometry is cheap trig recomputed per
 * draw — deliberately NOT wrapped in drawWithCache (the spec warns against
 * blind caching); the only per-frame state read is the animated pointer.
 *
 * [active] is false when there is no trustworthy measurement (silence, stale,
 * weak signal, clipping). The pointer is then hidden rather than parked at
 * the center, which would visually claim "in tune". The surrounding
 * composable exposes the spoken reading; this graphic is decorative.
 */
@Composable
fun CentsDial(
    position: Double, // -1..1 (clamped upstream)
    inTune: Boolean,
    clamped: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val target = position.toFloat().coerceIn(-1f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "dial-pointer",
    )
    // Theme tokens must be read in composition, not inside DrawScope.
    val scheme = MaterialTheme.colorScheme
    val pointerColor = when {
        !active -> scheme.onSurfaceVariant
        inTune -> scheme.secondary
        else -> scheme.primary
    }
    val zoneColor = scheme.secondary
    val arcColor = scheme.surfaceVariant
    val tickStrong = scheme.onSurface
    val tickSoft = scheme.onSurfaceVariant

    Box(
        modifier = modifier
            .aspectRatio(1.4f)
            .testTag("resonance.dial"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = min(cx, cy) * 0.92f
            val arcSize = Size(r * 2f, r * 2f)
            val arcTop = Offset(cx - r, cy - r)
            val arcStroke = 8.dp.toPx()

            // -50c -> 135°, 0c -> 270° (bottom), +50c -> 405°; gap at bottom center
            fun angleFor(pos: Double): Float =
                (135.0 + (pos + 1.0) / 2.0 * 270.0).toFloat()

            fun pt(angleDeg: Float, radius: Float): Offset {
                val rad = angleDeg.toDouble() * PI / 180.0
                return Offset(cx + (radius * cos(rad)).toFloat(), cy + (radius * sin(rad)).toFloat())
            }

            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTop,
                size = arcSize,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round),
            )
            // In-tune zone: ±8c around center. Dimmed when there is no reading
            // so the target remains visible without implying a live result.
            val zone = angleFor(8.0 / 50.0) - angleFor(0.0)
            drawArc(
                // A green target band is a success-shaped cue; keep it
                // neutral when there is no live reading.
                color = if (active) zoneColor.copy(alpha = 0.55f) else tickSoft.copy(alpha = 0.30f),
                startAngle = angleFor(0.0) - zone,
                sweepAngle = 2f * zone,
                useCenter = false,
                topLeft = arcTop,
                size = arcSize,
                style = Stroke(width = arcStroke, cap = StrokeCap.Butt),
            )
            // Ticks every 10c, major at 25c/center.
            for (cents in -50..50 step 10) {
                val ang = angleFor(cents / 50.0)
                val isCenter = cents == 0
                val isMajor = cents % 25 == 0
                val inner = r * (if (isCenter) 0.70f else 0.82f)
                drawLine(
                    color = when {
                        // With no live pointer, a bright center tick reads
                        // like a centered "in tune" needle. Dim it.
                        isCenter -> if (active) tickStrong else tickSoft
                        isMajor -> tickSoft
                        else -> tickSoft.copy(alpha = 0.45f)
                    },
                    start = pt(ang, inner),
                    end = pt(ang, r),
                    strokeWidth = if (isCenter) 3.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (active) {
                val pa = angleFor(animated.toDouble())
                val outer = r * (if (clamped) 1.07f else 0.97f)
                drawLine(
                    color = pointerColor,
                    start = pt(pa, r * 0.78f),
                    end = pt(pa, outer),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = pointerColor,
                    radius = 5.dp.toPx(),
                    center = pt(pa, outer),
                )
            }
        }
    }
}