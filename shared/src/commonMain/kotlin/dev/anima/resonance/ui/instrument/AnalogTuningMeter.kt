package dev.anima.resonance.ui.instrument

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The analog tuning meter — ivory face in a brass bezel, charcoal
 * scale, physical needle pivoting from a visible hub.
 *
 * Source of truth is the tested normalized [position] (-1..1 over ±50¢).
 * Visual travel is clamped at the mechanical stops by [AnalogMeterGeometry];
 * the underlying pitch math is untouched. With no reliable reading
 * ([active] = false) the needle parks at the flat-side rest stop and the
 * face reads NO SIGNAL — never a centered "in tune" needle.
 *
 * Caching: the static artwork (face, scale, label positions, bezel) lives
 * in a drawWithCache modifier stabilized by remember(active, inTune,
 * clamped) — the 20 Hz state ticker does NOT rebuild it. Label painting
 * goes through TextMeasurer, which caches measurements internally. Only
 * the needle reads the animated state, so per-frame work is one small
 * path. The needle spring matches the previous dial's motion spec.
 */
@Composable
fun AnalogTuningMeter(
    position: Double,
    inTune: Boolean,
    clamped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val p = InstrumentTheme.palette
    val target = (if (active) position.coerceIn(-1.0, 1.0) else -1.0).toFloat()
    val needleState = animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "meter-needle",
    )
    val measurer = rememberTextMeasurer()

    val drawModifier = remember(active, inTune, clamped, compact, measurer) {
        // The painted band tracks the state machine's EXIT tolerance (±8¢):
        // the needle inside the band is exactly when "In tune" can be claimed.
        val bandHalfAngle = AnalogMeterGeometry.inTuneBandHalfAngle(8.0).toFloat()
        val bandColor = when {
            !active -> p.inkSoft.copy(alpha = 0.30f)
            inTune -> p.bandGreen
            else -> p.bandGreen.copy(alpha = 0.8f)
        }
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bezel = 6.dp.toPx()
            val faceLeft = bezel
            val faceTop = bezel
            val faceRight = w - bezel
            val faceBottom = h - bezel
            val pivot = Offset(w / 2f, faceBottom - 16.dp.toPx())
            val radius = min(
                (faceRight - faceLeft) / 2f - 26.dp.toPx(),
                pivot.y - faceTop - 34.dp.toPx(),
            ).coerceAtLeast(10f)

            fun unit(angleDeg: Float): Offset {
                val rad = angleDeg * PI.toFloat() / 180f
                return Offset(sin(rad), -cos(rad))
            }
            fun at(angleDeg: Float, r: Float): Offset = pivot + unit(angleDeg) * r

            val bezelBrush = Brush.linearGradient(
                0f to p.brassLight,
                0.5f to p.brass,
                1f to p.brassDark,
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val faceBrush = Brush.verticalGradient(
                0f to p.ivory,
                1f to p.ivoryEdge,
                startY = faceTop,
                endY = faceBottom,
            )

            // Scale labels are instrument markings: fixed dp size keeps the
            // geometry stable at any system font scale.
            val labelStyle = TextStyle(
                fontSize = 10.dp.toSp(),
                fontWeight = FontWeight.Medium,
                color = p.ink,
            )
            val zeroStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
            val engravingStyle = TextStyle(
                fontSize = 9.dp.toSp(),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.dp.toSp(),
                color = p.inkSoft,
            )
            val makerStyle = engravingStyle.copy(letterSpacing = 2.4.dp.toSp())
            // FLAT/SHARP are primary scale labels: full ink for clarity.
            val sideLabelStyle = engravingStyle.copy(color = p.ink)
            // Pre-measure once for positions/sizes; TextMeasurer caches the
            // results, so painting in onDrawBehind does not re-measure.
            data class Mark(val text: String, val style: TextStyle, val center: Offset, val halfW: Float, val halfH: Float)
            val marks = (-50..50 step 10).map { cents ->
                val text = if (cents > 0) "+$cents" else "$cents"
                val style = if (cents == 0) zeroStyle else labelStyle
                val layout = measurer.measure(text, style)
                Mark(
                    text, style,
                    at(AnalogMeterGeometry.tickAngle(cents).toFloat(), radius + 15.dp.toPx()),
                    layout.size.width / 2f, layout.size.height / 2f,
                )
            }
            val flatSize = measurer.measure("FLAT", sideLabelStyle).size
            val sharpSize = measurer.measure("SHARP", sideLabelStyle).size
            val makerSize = measurer.measure("RESONANCE", makerStyle).size
            val noSignalSize = measurer.measure("NO SIGNAL", engravingStyle).size

            data class Tick(val start: Offset, val end: Offset, val width: Float, val color: Color)
            val ticks = (-50..50 step 5).map { cents ->
                val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
                val len = when {
                    cents == 0 -> 13.dp.toPx()
                    cents % 10 == 0 -> 10.dp.toPx()
                    else -> 6.dp.toPx()
                }
                Tick(
                    start = at(a, radius),
                    end = at(a, radius - len),
                    width = when {
                        cents == 0 -> 2.4.dp.toPx()
                        cents % 10 == 0 -> 1.7.dp.toPx()
                        else -> 1.dp.toPx()
                    },
                    color = if (cents % 10 == 0) p.ink else p.inkSoft,
                )
            }
            val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val sweep = AnalogMeterGeometry.SWEEP_DEGREES.toFloat()

            onDrawBehind {
                // Bezel + face
                drawRoundRect(brush = bezelBrush, cornerRadius = CornerRadius(16.dp.toPx()))
                drawRoundRect(
                    brush = faceBrush,
                    topLeft = Offset(faceLeft, faceTop),
                    size = Size(faceRight - faceLeft, faceBottom - faceTop),
                    cornerRadius = CornerRadius(11.dp.toPx()),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to p.ink.copy(alpha = 0.14f),
                        0.12f to Color.Transparent,
                        1f to Color.Transparent,
                        startY = faceTop,
                        endY = faceBottom,
                    ),
                    topLeft = Offset(faceLeft + 1.dp.toPx(), faceTop + 1.dp.toPx()),
                    size = Size(faceRight - faceLeft - 2.dp.toPx(), faceBottom - faceTop - 2.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // Maker's mark only when the face is tall enough that the
                // top scale label cannot collide with it (decorative).
                if (faceBottom - faceTop >= 210.dp.toPx()) {
                    drawText(
                        textMeasurer = measurer,
                        text = "RESONANCE",
                        topLeft = Offset(w / 2f - makerSize.width / 2f, faceTop + 9.dp.toPx()),
                        style = makerStyle,
                    )
                }

                // Scale arc + in-tune band
                drawArc(
                    color = p.ink,
                    startAngle = 270f - sweep / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
                drawArc(
                    color = bandColor,
                    startAngle = 270f - bandHalfAngle,
                    sweepAngle = bandHalfAngle * 2f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 5.dp.toPx()),
                )
                ticks.forEach { t -> drawLine(t.color, t.start, t.end, strokeWidth = t.width) }
                marks.forEach { m ->
                    drawText(
                        textMeasurer = measurer,
                        text = m.text,
                        topLeft = Offset(m.center.x - m.halfW, m.center.y - m.halfH),
                        style = m.style,
                    )
                }
                drawText(
                    textMeasurer = measurer,
                    text = "FLAT",
                    topLeft = Offset(faceLeft + 13.dp.toPx(), pivot.y - flatSize.height / 2f),
                    style = sideLabelStyle,
                )
                drawText(
                    textMeasurer = measurer,
                    text = "SHARP",
                    topLeft = Offset(faceRight - 13.dp.toPx() - sharpSize.width, pivot.y - sharpSize.height / 2f),
                    style = sideLabelStyle,
                )
                if (!active) {
                    drawText(
                        textMeasurer = measurer,
                        text = "NO SIGNAL",
                        topLeft = Offset(
                            w / 2f - noSignalSize.width / 2f,
                            pivot.y - radius * 0.5f - noSignalSize.height / 2f,
                        ),
                        style = engravingStyle,
                    )
                }

                // ── Needle (the only per-frame state read) ───────────────
                val animated = needleState.value
                // Pinned-at-stop marker when the deviation exceeds full scale.
                if (active && clamped) {
                    val stopAngle = if (animated >= 0f) sweep / 2f else -sweep / 2f
                    drawCircle(p.inkSoft, radius = 2.5.dp.toPx(), center = at(stopAngle, radius))
                }
                val angle = AnalogMeterGeometry.needleAngle(animated.toDouble()).toFloat()
                val u = unit(angle)
                val perp = Offset(u.y, -u.x)
                val len = radius * 0.96f
                val tail = radius * 0.16f
                val tip = pivot + u * len
                val baseL = pivot + perp * 2.3.dp.toPx()
                val baseR = pivot - perp * 2.3.dp.toPx()
                val tailL = pivot - u * tail + perp * 3.4.dp.toPx()
                val tailR = pivot - u * tail - perp * 3.4.dp.toPx()
                fun needlePath(dx: Float, dy: Float): Path = Path().apply {
                    moveTo(tip.x + dx, tip.y + dy)
                    lineTo(baseL.x + dx, baseL.y + dy)
                    lineTo(tailL.x + dx, tailL.y + dy)
                    lineTo(tailR.x + dx, tailR.y + dy)
                    lineTo(baseR.x + dx, baseR.y + dy)
                    close()
                }
                drawPath(needlePath(1.5.dp.toPx(), 2.5.dp.toPx()), p.ink.copy(alpha = 0.18f))
                drawPath(needlePath(0f, 0f), p.ink)

                // Hub cap over the needle pivot
                drawCircle(p.brassDark, radius = 10.dp.toPx(), center = pivot)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to p.brassLight,
                        0.7f to p.brass,
                        1f to p.brassDark,
                        center = Offset(pivot.x - 3.dp.toPx(), pivot.y - 3.dp.toPx()),
                        radius = 10.dp.toPx(),
                    ),
                    radius = 8.dp.toPx(),
                    center = pivot,
                )
                drawLine(
                    color = p.recessEdge.copy(alpha = 0.7f),
                    start = Offset(pivot.x - 4.dp.toPx(), pivot.y),
                    end = Offset(pivot.x + 4.dp.toPx(), pivot.y),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(if (compact) 1.8f else 1.6f)
            .testTag("resonance.dial"),
    ) {
        Canvas(Modifier.fillMaxSize().then(drawModifier), onDraw = {})
    }
}