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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
 * The analog tuning meter — the centerpiece of the instrument.
 * A softly aged ivory face in a THIN aged-brass frame, mounted into the
 * walnut body (it casts a short shadow onto the wood). Fine dark-metal
 * needle on a brass pivot; needle, tick arc and pivot share one geometry.
 *
 * Source of truth is the tested normalized [position] (-1..1 over ±50¢).
 * Visual travel is clamped at the mechanical stops by [AnalogMeterGeometry];
 * the underlying pitch math is untouched. With no reliable reading
 * ([active] = false) the needle parks at the flat-side rest stop and the
 * face reads NO SIGNAL — never a centered "in tune" needle.
 *
 * Caching: static artwork lives in a drawWithCache modifier stabilized by
 * remember(active, inTune, clamped, compact) — the 20 Hz state ticker does
 * NOT rebuild it. TextMeasurer caches label measurements. Only the needle
 * reads the animated state. Spring matches the original dial (0.8 / 300).
 */
@Composable
fun AnalogTuningMeter(
    position: Double,
    inTune: Boolean,
    clamped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    aspectRatio: Float? = if (compact) 1.8f else 1.6f,
    embedded: Boolean = false,
) {
    val p = InstrumentTheme.palette
    // With no reliable reading the needle fades out (hub stays) — it is
    // never parked at the flat stop as if it were a measurement. The position
    // still animates to centre while hidden so a new note fades in smoothly.
    val target = (if (active) position.coerceIn(-1.0, 1.0) else 0.0).toFloat()
    val needleState = animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "meter-needle",
    )
    val needleAlpha = animateFloatAsState(
        targetValue = if (AnalogMeterGeometry.needleShown(active)) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "meter-needle-alpha",
    )
    val measurer = rememberTextMeasurer()

    val drawModifier = remember(active, inTune, clamped, compact, embedded, measurer) {
        // The painted band tracks the state machine's EXIT tolerance (±8¢):
        // the needle inside the band is exactly when "In tune" can be claimed.
        val bandHalfAngle = AnalogMeterGeometry.inTuneBandHalfAngle(8.0).toFloat()
        val bandColor = when {
            !active -> p.inkSoft.copy(alpha = 0.28f)
            inTune -> p.bandGreen
            else -> p.bandGreen.copy(alpha = 0.85f)
        }
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            // Embedded into the measurement assembly: full-bleed ivory (the
            // assembly supplies the frame); standalone: framed + cast shadow.
            val margin = if (embedded) 0.dp.toPx() else 2.dp.toPx()
            val faceLeft = margin
            val faceTop = margin
            val faceRight = w - margin
            val faceBottom = h - (if (embedded) 0.dp.toPx() else 6.dp.toPx())
            val frame = if (embedded) 0.dp.toPx() else 3.dp.toPx()
            val pivot = Offset(w / 2f, faceBottom - (if (embedded) 12.dp.toPx() else 14.dp.toPx()))
            val radius = min(
                (faceRight - faceLeft) / 2f - 18.dp.toPx(),
                pivot.y - faceTop - 30.dp.toPx(),
            ).coerceAtLeast(10f)

            fun unit(angleDeg: Float): Offset {
                val rad = angleDeg * PI.toFloat() / 180f
                return Offset(sin(rad), -cos(rad))
            }
            fun at(angleDeg: Float, r: Float): Offset = pivot + unit(angleDeg) * r

            // Thin aged-brass frame: dark muted metal with a catch-light on
            // the upper edge — hardware, not a gold picture frame.
            val frameBrush = Brush.linearGradient(
                0f to p.brass.copy(alpha = 0.85f),
                0.22f to p.brassDark,
                1f to p.brassDark.copy(alpha = 0.92f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val faceBrush = Brush.verticalGradient(
                0f to p.ivory,
                1f to p.ivoryEdge,
                startY = faceTop,
                endY = faceBottom,
            )
            val faceCorner = if (embedded) 0.dp.toPx() else 9.dp.toPx()
            val facePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = faceLeft + frame,
                        top = faceTop + frame,
                        right = faceRight - frame,
                        bottom = faceBottom - frame,
                        cornerRadius = CornerRadius(faceCorner),
                    ),
                )
            }
            // Very subtle age mottling — deterministic, clipped to the face.
            val ageSpots = listOf(
                Offset(w * 0.30f, faceTop + (faceBottom - faceTop) * 0.32f),
                Offset(w * 0.72f, faceTop + (faceBottom - faceTop) * 0.55f),
                Offset(w * 0.48f, faceTop + (faceBottom - faceTop) * 0.78f),
            )
            val spotR = (faceRight - faceLeft) * 0.30f

            // Scale labels: fixed dp keeps geometry stable at any font scale.
            // Larger, crisper numerals (the meter is the centerpiece).
            val labelStyle = TextStyle(
                fontSize = 14.dp.toSp(),
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
            )
            val zeroStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
            val sideLabelStyle = TextStyle(
                fontSize = 12.dp.toSp(),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.dp.toSp(),
                color = p.ink,
            )
            data class Mark(val text: String, val style: TextStyle, val center: Offset, val halfW: Float, val halfH: Float)
            val marks = (-50..50 step 10).map { cents ->
                val text = if (cents > 0) "+$cents" else "$cents"
                val style = if (cents == 0) zeroStyle else labelStyle
                val layout = measurer.measure(text, style)
                Mark(
                    text, style,
                    at(AnalogMeterGeometry.tickAngle(cents).toFloat(), radius + 14.dp.toPx()),
                    layout.size.width / 2f, layout.size.height / 2f,
                )
            }
            val flatSize = measurer.measure("FLAT", sideLabelStyle).size
            val sharpSize = measurer.measure("SHARP", sideLabelStyle).size

            data class Tick(val start: Offset, val end: Offset, val width: Float, val color: Color)
            val ticks = (-50..50 step 5).map { cents ->
                val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
                val len = when {
                    cents == 0 -> 18.dp.toPx()
                    cents % 10 == 0 -> 15.dp.toPx()
                    else -> 9.dp.toPx()
                }
                Tick(
                    start = at(a, radius),
                    end = at(a, radius - len),
                    width = when {
                        cents == 0 -> 2.6.dp.toPx()
                        cents % 10 == 0 -> 2.dp.toPx()
                        else -> 1.1.dp.toPx()
                    },
                    color = if (cents % 10 == 0) p.ink else p.inkSoft,
                )
            }
            val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val sweep = AnalogMeterGeometry.SWEEP_DEGREES.toFloat()

            onDrawBehind {
                if (!embedded) {
                    // Shadow cast onto the wood (light from upper left).
                    drawRoundRect(
                        color = p.recessEdge.copy(alpha = 0.30f),
                        topLeft = Offset(faceLeft + 1.dp.toPx(), faceTop + 3.5.dp.toPx()),
                        size = Size(faceRight - faceLeft, faceBottom - faceTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    drawRoundRect(
                        color = p.recessEdge.copy(alpha = 0.18f),
                        topLeft = Offset(faceLeft + 0.5.dp.toPx(), faceTop + 1.5.dp.toPx()),
                        size = Size(faceRight - faceLeft, faceBottom - faceTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    // Thin metal frame around the standalone face.
                    drawRoundRect(
                        brush = frameBrush,
                        topLeft = Offset(faceLeft, faceTop),
                        size = Size(faceRight - faceLeft, faceBottom - faceTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
                // Ivory face (full-bleed when embedded; the assembly frames it).
                drawRoundRect(
                    brush = faceBrush,
                    topLeft = Offset(faceLeft + frame, faceTop + frame),
                    size = Size(faceRight - faceLeft - frame * 2, faceBottom - faceTop - frame * 2),
                    cornerRadius = CornerRadius(faceCorner),
                )
                clipPath(facePath) {
                    ageSpots.forEach { c ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to p.ivoryEdge.copy(alpha = 0.16f),
                                1f to Color.Transparent,
                                center = c,
                                radius = spotR,
                            ),
                            radius = spotR,
                            center = c,
                        )
                    }
                    // Inset edge: shadow along the top inner wall.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to p.ink.copy(alpha = 0.16f),
                            0.10f to Color.Transparent,
                            1f to Color.Transparent,
                            startY = faceTop + frame,
                            endY = faceBottom - frame,
                        ),
                        topLeft = Offset(faceLeft + frame + 0.5.dp.toPx(), faceTop + frame + 0.5.dp.toPx()),
                        size = Size(
                            faceRight - faceLeft - frame * 2 - 1.dp.toPx(),
                            faceBottom - faceTop - frame * 2 - 1.dp.toPx(),
                        ),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                // Hairline inset edge between frame and face (standalone only).
                if (!embedded) {
                    drawRoundRect(
                        color = p.recessEdge.copy(alpha = 0.5f),
                        topLeft = Offset(faceLeft + frame, faceTop + frame),
                        size = Size(faceRight - faceLeft - frame * 2, faceBottom - faceTop - frame * 2),
                        cornerRadius = CornerRadius(9.dp.toPx()),
                        style = Stroke(width = 0.7.dp.toPx()),
                    )
                }

                // Scale arc + restrained in-tune band.
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
                    style = Stroke(width = 4.dp.toPx()),
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
                    topLeft = Offset(faceLeft + frame + 12.dp.toPx(), pivot.y - flatSize.height / 2f),
                    style = sideLabelStyle,
                )
                drawText(
                    textMeasurer = measurer,
                    text = "SHARP",
                    topLeft = Offset(
                        faceRight - frame - 12.dp.toPx() - sharpSize.width,
                        pivot.y - sharpSize.height / 2f,
                    ),
                    style = sideLabelStyle,
                )
                // ── Needle (the only per-frame state reads) ──────────────
                // Hidden (faded out) when there is no reliable reading;
                // the brass hub below stays visible. Never a flat-stop needle.
                val needleVis = needleAlpha.value
                if (needleVis > 0.02f) {
                    val animated = needleState.value
                    if (active && clamped) {
                        val stopAngle = if (animated >= 0f) sweep / 2f else -sweep / 2f
                        drawCircle(p.inkSoft.copy(alpha = needleVis), radius = 2.5.dp.toPx(), center = at(stopAngle, radius))
                    }
                    val angle = AnalogMeterGeometry.needleAngle(animated.toDouble()).toFloat()
                    val u = unit(angle)
                    val perp = Offset(u.y, -u.x)
                    val len = radius * 0.985f
                    val tail = radius * 0.14f
                    val tip = pivot + u * len
                    val baseL = pivot + perp * 1.7.dp.toPx()
                    val baseR = pivot - perp * 1.7.dp.toPx()
                    val tailL = pivot - u * tail + perp * 2.8.dp.toPx()
                    val tailR = pivot - u * tail - perp * 2.8.dp.toPx()
                    fun needlePath(dx: Float, dy: Float): Path = Path().apply {
                        moveTo(tip.x + dx, tip.y + dy)
                        lineTo(baseL.x + dx, baseL.y + dy)
                        lineTo(tailL.x + dx, tailL.y + dy)
                        lineTo(tailR.x + dx, tailR.y + dy)
                        lineTo(baseR.x + dx, baseR.y + dy)
                        close()
                    }
                    drawPath(needlePath(1.dp.toPx(), 1.8.dp.toPx()), p.ink.copy(alpha = 0.16f * needleVis))
                    drawPath(needlePath(0f, 0f), p.ink.copy(alpha = needleVis))
                    // Fine metal sheen along one edge of the pointer.
                    drawLine(
                        color = p.ivory.copy(alpha = 0.22f * needleVis),
                        start = pivot + u * (radius * 0.12f) + perp * 0.5.dp.toPx(),
                        end = pivot + u * (radius * 0.9f) + perp * 0.5.dp.toPx(),
                        strokeWidth = 0.6.dp.toPx(),
                    )
                }

                // Brass pivot: seat shadow, ring, domed cap, slot, specular.
                drawCircle(
                    p.recessEdge.copy(alpha = 0.30f),
                    radius = 10.5.dp.toPx(),
                    center = Offset(pivot.x + 0.5.dp.toPx(), pivot.y + 1.dp.toPx()),
                )
                drawCircle(p.brassDark, radius = 9.5.dp.toPx(), center = pivot)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to p.brassLight,
                        0.55f to p.brass,
                        1f to p.brassDark,
                        center = Offset(pivot.x - 3.dp.toPx(), pivot.y - 3.dp.toPx()),
                        radius = 9.dp.toPx(),
                    ),
                    radius = 8.dp.toPx(),
                    center = pivot,
                )
                drawLine(
                    color = p.recessEdge.copy(alpha = 0.7f),
                    start = Offset(pivot.x - 3.5.dp.toPx(), pivot.y),
                    end = Offset(pivot.x + 3.5.dp.toPx(), pivot.y),
                    strokeWidth = 1.1.dp.toPx(),
                )
                drawCircle(
                    p.ivory.copy(alpha = 0.5f),
                    radius = 1.2.dp.toPx(),
                    center = Offset(pivot.x - 2.5.dp.toPx(), pivot.y - 2.5.dp.toPx()),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .testTag("resonance.dial"),
    ) {
        Canvas(Modifier.fillMaxSize().then(drawModifier), onDraw = {})
    }
}