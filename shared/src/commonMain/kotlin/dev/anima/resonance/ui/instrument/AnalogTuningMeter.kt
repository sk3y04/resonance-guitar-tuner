package dev.anima.resonance.ui.instrument

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
 * The flagship vintage analog dial — a Peterson/Boss/McIntosh-style
 * moving-coil meter. A wide landscape cream window under recessed glass; the
 * arc's geometric pivot sits BELOW the visible bottom edge so the needle
 * emerges from a brass escutcheon at the bottom-center seam (the awkward tail
 * is hidden). 90° sweep (±45° at ±50¢, 0¢ straight up), a 3-tier precision
 * scale (major / medium / fine vernier near center), numerals outside the arc,
 * a parallax mirror hairline, and an illuminated −3..+3¢ target zone.
 *
 * Source of truth is the tested normalized [position] (-1..1 over ±50¢);
 * negative cents deflect LEFT, positive RIGHT. The needle is ALWAYS drawn:
 * with no reliable reading it rests just past the flat stop (≈ −52¢) via a
 * damped spring, like an unpowered meter. Pitch math is untouched.
 *
 * Caching: static artwork lives in drawWithCache keyed by stable flags + the
 * measurer (NOT the high-frequency position). Only the needle angle and the
 * animated zone color are read inside onDrawBehind.
 */
@Composable
fun AnalogTuningMeter(
    position: Double,
    inTune: Boolean,
    clamped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    aspectRatio: Float? = if (compact) 1.8f else 1.5f,
    embedded: Boolean = false,
) {
    val p = InstrumentTheme.palette
    // Needle always rendered. Active → mapped cents angle; no signal →
    // rest just past the flat stop. Damped spring gives a physical settle.
    val targetAngle = (
        if (active) AnalogMeterGeometry.needleAngle(position.coerceIn(-1.0, 1.0))
        else AnalogMeterGeometry.REST_ANGLE
        ).toFloat()
    val needleAngle = animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 210f),
        label = "meter-needle",
    )
    val zoneColor = animateColorAsState(
        targetValue = if (inTune) p.bandGreen else p.zoneOff,
        animationSpec = tween(durationMillis = 260),
        label = "meter-zone",
    )
    val measurer = rememberTextMeasurer()

    val drawModifier = remember(active, inTune, clamped, compact, embedded, measurer) {
        val zoneHalfAngle = AnalogMeterGeometry.inTuneBandHalfAngle(3.0).toFloat()
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val margin = if (embedded) 0.dp.toPx() else 2.dp.toPx()
            val faceLeft = margin
            val faceTop = margin
            val faceRight = w - margin
            val faceBottom = h - (if (embedded) 0.dp.toPx() else 6.dp.toPx())
            val frame = if (embedded) 0.dp.toPx() else 3.dp.toPx()
            // Pivot BELOW the visible bottom edge — the needle emerges from
            // the bottom-center seam; its tail is hidden behind the escutcheon.
            val pivot = Offset(w / 2f, faceBottom + 18.dp.toPx())

            fun unit(angleDeg: Float): Offset {
                val rad = angleDeg * PI.toFloat() / 180f
                return Offset(sin(rad), -cos(rad))
            }
            fun at(angleDeg: Float, r: Float): Offset = pivot + unit(angleDeg) * r

            // Radius from the bounds: numerals sit OUTSIDE the arc, so the
            // extreme numeral (at ±45°) must stay inside the face width, and
            // the top numeral must clear the top edge.
            val halfSweepRad = (AnalogMeterGeometry.SWEEP_DEGREES / 2.0 * PI / 180.0).toFloat()
            val sinHalf = sin(halfSweepRad)
            val numeralOffset = 15.dp.toPx()
            val numeralHalf = 13.dp.toPx()
            val edgePad = 5.dp.toPx()
            val widthLimit =
                ((faceRight - faceLeft) / 2f - edgePad - numeralHalf) / sinHalf - numeralOffset
            val heightLimit = pivot.y - faceTop - numeralOffset - 12.dp.toPx()
            val radius = min(widthLimit, heightLimit).coerceAtLeast(10f)

            val faceCorner = if (embedded) 0.dp.toPx() else 14.dp.toPx()
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
            // Recessed glass: warm radial ivory (bright upper-center → parchment
            // edges) + a soft top-edge inner bezel shadow under the hood.
            val glassBrush = Brush.radialGradient(
                0f to p.ivory,
                0.7f to p.ivory,
                1f to p.ivoryEdge,
                center = Offset(w / 2f, faceTop + (faceBottom - faceTop) * 0.30f),
                radius = (faceRight - faceLeft) * 0.78f,
            )
            val hoodShadow = Brush.verticalGradient(
                0f to p.ink.copy(alpha = 0.20f),
                1f to Color.Transparent,
                startY = faceTop + frame,
                endY = faceTop + frame + 8.dp.toPx(),
            )
            val frameBrush = Brush.linearGradient(
                0f to p.brass.copy(alpha = 0.85f),
                0.22f to p.brassDark,
                1f to p.brassDark.copy(alpha = 0.92f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )

            // Scale numerals OUTSIDE (above) the arc — needle sweeps the inner
            // ticks cleanly without overlapping text. Fixed dp keeps geometry
            // stable at any font scale.
            val labelStyle = TextStyle(
                fontSize = 13.dp.toSp(),
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
            )
            val zeroStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
            data class Mark(val text: String, val style: TextStyle, val center: Offset, val halfW: Float, val halfH: Float)
            val marks = (-50..50 step 10).map { cents ->
                val text = if (cents > 0) "+$cents" else "$cents"
                val style = if (cents == 0) zeroStyle else labelStyle
                val layout = measurer.measure(text, style)
                Mark(
                    text, style,
                    at(AnalogMeterGeometry.tickAngle(cents).toFloat(), radius + numeralOffset),
                    layout.size.width / 2f, layout.size.height / 2f,
                )
            }
            val sideStyle = TextStyle(
                fontSize = 12.dp.toSp(),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.dp.toSp(),
                color = p.ink.copy(alpha = 0.30f),
            )
            val flatText = "FLAT ♭"
            val sharpText = "♯ SHARP"
            val flatSize = measurer.measure(flatText, sideStyle).size
            val sharpSize = measurer.measure(sharpText, sideStyle).size
            val stampStyle = TextStyle(
                fontSize = 8.5.dp.toSp(),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.dp.toSp(),
                color = p.taupe.copy(alpha = 0.38f),
            )
            val stampText = "CENTS • 440Hz CALIBRATED"
            val stampSize = measurer.measure(stampText, stampStyle).size

            // 3-tier tick hierarchy pointing inward toward the pivot.
            data class Tick(val start: Offset, val end: Offset, val width: Float, val color: Color)
            val ticks = buildList {
                for (cents in -50..50 step 5) {
                    val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
                    val major = cents % 10 == 0
                    val len = if (major) 14.dp.toPx() else 9.dp.toPx()
                    val sw = if (major) 2.dp.toPx() else 1.25.dp.toPx()
                    add(Tick(at(a, radius), at(a, radius - len), sw, if (major) p.ink else p.inkSoft))
                }
                // Fine vernier every 2¢ inside the critical −10..+10 zone.
                for (cents in -10..10 step 2) {
                    if (cents % 5 == 0) continue
                    val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
                    add(Tick(at(a, radius), at(a, radius - 5.dp.toPx()), 0.8.dp.toPx(), p.inkSoft))
                }
            }
            // Small downward triangle index (▼) centered directly above 0.
            val zeroTip = at(0f, radius + 2.dp.toPx())
            val triHalf = 3.5.dp.toPx()
            val triHeight = 5.dp.toPx()
            val triangle = Path().apply {
                moveTo(zeroTip.x, zeroTip.y)
                lineTo(zeroTip.x - triHalf, zeroTip.y - triHeight)
                lineTo(zeroTip.x + triHalf, zeroTip.y - triHeight)
                close()
            }
            val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val sweep = AnalogMeterGeometry.SWEEP_DEGREES.toFloat()
            val innerRadius = radius - 18.dp.toPx()
            val innerTopLeft = Offset(pivot.x - innerRadius, pivot.y - innerRadius)
            val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
            // Needle reaches just past the inner tips of the major ticks.
            val needleLen = radius - 10.dp.toPx()
            val escR = 13.dp.toPx()

            onDrawBehind {
                if (!embedded) {
                    drawRoundRect(
                        color = p.recessEdge.copy(alpha = 0.30f),
                        topLeft = Offset(faceLeft + 1.dp.toPx(), faceTop + 3.5.dp.toPx()),
                        size = Size(faceRight - faceLeft, faceBottom - faceTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    drawRoundRect(
                        brush = frameBrush,
                        topLeft = Offset(faceLeft, faceTop),
                        size = Size(faceRight - faceLeft, faceBottom - faceTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
                // Cream glass face.
                drawRoundRect(
                    brush = glassBrush,
                    topLeft = Offset(faceLeft + frame, faceTop + frame),
                    size = Size(faceRight - faceLeft - frame * 2, faceBottom - faceTop - frame * 2),
                    cornerRadius = CornerRadius(faceCorner),
                )
                clipPath(facePath) {
                    // Top-edge inner bezel shadow (recessed under the hood).
                    drawRect(
                        brush = hoodShadow,
                        topLeft = Offset(faceLeft + frame, faceTop + frame),
                        size = Size(faceRight - faceLeft - frame * 2, 8.dp.toPx()),
                    )
                    // Parallax mirror hairline (concentric, 18dp inside the arc).
                    drawArc(
                        color = p.ink.copy(alpha = 0.18f),
                        startAngle = 270f - sweep / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Main scale arc.
                    drawArc(
                        color = p.ink,
                        startAngle = 270f - sweep / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                    // Illuminated −3..+3¢ target zone (animated sage → emerald).
                    drawArc(
                        color = zoneColor.value,
                        startAngle = 270f - zoneHalfAngle,
                        sweepAngle = zoneHalfAngle * 2f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 4.5.dp.toPx()),
                    )
                    ticks.forEach { t -> drawLine(t.color, t.start, t.end, strokeWidth = t.width) }
                    drawPath(triangle, p.ink)
                    marks.forEach { m ->
                        drawText(
                            textMeasurer = measurer,
                            text = m.text,
                            topLeft = Offset(m.center.x - m.halfW, m.center.y - m.halfH),
                            style = m.style,
                        )
                    }
                    // Corner labels + vintage dial stamp (low contrast).
                    drawText(
                        textMeasurer = measurer,
                        text = flatText,
                        topLeft = Offset(faceLeft + frame + 12.dp.toPx(), faceBottom - frame - flatSize.height - 10.dp.toPx()),
                        style = sideStyle,
                    )
                    drawText(
                        textMeasurer = measurer,
                        text = sharpText,
                        topLeft = Offset(faceRight - frame - 12.dp.toPx() - sharpSize.width, faceBottom - frame - sharpSize.height - 10.dp.toPx()),
                        style = sideStyle,
                    )
                    drawText(
                        textMeasurer = measurer,
                        text = stampText,
                        topLeft = Offset(w / 2f - stampSize.width / 2f, faceBottom - frame - stampSize.height - 12.dp.toPx()),
                        style = stampStyle,
                    )

                    // ── Needle (always rendered; the only per-frame angle read) ──
                    val angle = needleAngle.value
                    val u = unit(angle)
                    val perp = Offset(u.y, -u.x)
                    val tip = pivot + u * needleLen
                    val baseHalf = 1.75.dp.toPx()
                    val tipHalf = 0.5.dp.toPx()
                    fun needlePath(dx: Float, dy: Float): Path = Path().apply {
                        moveTo(tip.x + perp.x * tipHalf + dx, tip.y + perp.y * tipHalf + dy)
                        lineTo(pivot.x + perp.x * baseHalf + dx, pivot.y + perp.y * baseHalf + dy)
                        lineTo(pivot.x - perp.x * baseHalf + dx, pivot.y - perp.y * baseHalf + dy)
                        lineTo(tip.x - perp.x * tipHalf + dx, tip.y - perp.y * tipHalf + dy)
                        close()
                    }
                    // Soft drop shadow cast onto the cream dial.
                    drawPath(needlePath(2.5.dp.toPx(), 2.dp.toPx()), Color.Black.copy(alpha = 0.14f))
                    drawPath(needlePath(0f, 0f), p.ink)
                    // Fine metal sheen along one edge.
                    drawLine(
                        color = p.ivory.copy(alpha = 0.22f),
                        start = pivot + u * (needleLen * 0.18f) + perp * 0.5.dp.toPx(),
                        end = pivot + u * (needleLen * 0.9f) + perp * 0.5.dp.toPx(),
                        strokeWidth = 0.6.dp.toPx(),
                    )
                    if (active && clamped) {
                        val stopAngle = if (angle >= 0f) sweep / 2f else -sweep / 2f
                        drawCircle(p.inkSoft, radius = 2.5.dp.toPx(), center = at(stopAngle, radius))
                    }
                }
                // Brass escutcheon: a semicircular cap at the bottom-center seam
                // hiding the needle tail (drawn over the needle exit).
                val escCenter = Offset(w / 2f, faceBottom - frame)
                val escPath = Path().apply {
                    addArc(
                        oval = androidx.compose.ui.geometry.Rect(
                            escCenter.x - escR, escCenter.y - escR,
                            escCenter.x + escR, escCenter.y + escR,
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f,
                    )
                    close()
                }
                drawPath(escPath, p.brassDark)
                drawPath(
                    escPath,
                    brush = Brush.radialGradient(
                        0f to p.brassLight,
                        0.6f to p.brass,
                        1f to p.brassDark,
                        center = Offset(escCenter.x - escR * 0.3f, escCenter.y - escR * 0.4f),
                        radius = escR,
                    ),
                )
                drawArc(
                    color = p.recessEdge.copy(alpha = 0.6f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(escCenter.x - escR, escCenter.y - escR),
                    size = Size(escR * 2f, escR * 2f),
                    style = Stroke(width = 0.8.dp.toPx()),
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