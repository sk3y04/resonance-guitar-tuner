package dev.anima.resonance.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
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
 */
@Composable
fun CentsDial(
    position: Double, // -1..1 (clamped upstream)
    inTune: Boolean,
    clamped: Boolean,
    modifier: Modifier = Modifier,
) {
    val target = position.toFloat().coerceIn(-1f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "dial-pointer",
    )
    val color = if (inTune) ResonanceColors.inTune else ResonanceColors.accent
    // @Composable reads must happen outside the Canvas draw lambda
    val arcColor = MaterialTheme.colorScheme.surfaceVariant

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
                style = Stroke(width = 8f, cap = StrokeCap.Round),
            )
            // In-tune zone: ±8c around center
            val zone = angleFor(8.0 / 50.0) - angleFor(0.0)
            drawArc(
                color = ResonanceColors.inTune.copy(alpha = 0.55f),
                startAngle = angleFor(0.0) - zone,
                sweepAngle = 2f * zone,
                useCenter = false,
                topLeft = arcTop,
                size = arcSize,
                style = Stroke(width = 8f, cap = StrokeCap.Butt),
            )
            // Ticks every 10c, major at 25c/center
            for (cents in -50..50 step 10) {
                val ang = angleFor(cents / 50.0)
                val isCenter = cents == 0
                val inner = r * (if (isCenter) 0.70f else 0.82f)
                drawLine(
                    color = when {
                        isCenter -> ResonanceColors.textPrimary
                        cents % 25 == 0 -> ResonanceColors.textSecondary
                        else -> ResonanceColors.textSecondary.copy(alpha = 0.45f)
                    },
                    start = pt(ang, inner),
                    end = pt(ang, r),
                    strokeWidth = if (isCenter) 3f else 2f,
                    cap = StrokeCap.Round,
                )
            }
            // Pointer
            val pa = angleFor(animated.toDouble())
            val outer = r * (if (clamped) 1.07f else 0.97f)
            drawLine(
                color = color,
                start = pt(pa, r * 0.78f),
                end = pt(pa, outer),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = color, radius = 10f, center = pt(pa, outer))
        }
    }
}
