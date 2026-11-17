package dev.anima.resonance.ui.instrument

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * The vintage analog dial: a Peterson/Boss/McIntosh-style moving-coil meter.
 *
 * A wide landscape cream window under recessed glass. The arc's geometric pivot
 * sits BELOW the visible bottom edge, so the needle emerges from a brass
 * escutcheon at the bottom-center seam and its awkward tail stays hidden. 90°
 * sweep (±45° at ±50¢, 0¢ straight up), a 3-tier precision scale (major / medium
 * / fine vernier near center), numerals outside the arc, a parallax mirror
 * hairline, and an illuminated −3..+3¢ target zone.
 *
 * Source of truth is the tested normalized [position] (-1..1 over ±50¢);
 * negative cents deflect LEFT, positive RIGHT. The needle is always on the dial:
 * while a reliable pitch is tracked it follows the measurement through a
 * critically-damped spring (smoothing, not decoration); with no signal it
 * wanders slowly around the flat-side rest, like a real unpowered meter. Pitch
 * math is untouched.
 *
 * TWO RENDER LAYERS, not one canvas. Compose invalidates the NEAREST render
 * layer above a drawing node, so a single canvas made every needle frame
 * re-record the whole dial (face gradient, 11 numerals, 60+ ticks) and, with no
 * layer below the window root, the entire window with it — measured at 8.7 ms of
 * UI thread per frame with 62% of frames missing the 8.35 ms budget at 120 Hz on
 * a 1080x2400 phone. Splitting it costs one extra layer and drops a needle frame
 * to 0.6 ms:
 *  - the STATIC canvas paints the immutable artwork once per size in its own
 *    layer and is never re-recorded while the needle moves;
 *  - the NEEDLE canvas paints the animated zone arc, the needle and the
 *    escutcheon in a second layer, so a frame costs those strokes only.
 * Geometry, brushes, paths and text layouts are built once per size/palette.
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
    val reduced = LocalReducedMotion.current
    val targetAngle = (
        if (active) AnalogMeterGeometry.needleAngle(position.coerceIn(-1.0, 1.0))
        else AnalogMeterGeometry.REST_ANGLE
        ).toFloat()
    // One needle value for both states, so a reading that starts while the needle
    // is mid-wander springs from where the eye last saw it (no teleport).
    val needle = remember { Animatable(targetAngle) }
    LaunchedEffect(active, reduced, targetAngle) {
        if (!active && !reduced) {
            // Idle wander. Driven a frame at a time and written straight into the
            // needle's value, which only the needle layer reads: no recomposition,
            // and the glow of a measurement starting is a spring from here.
            val periodNs = AnalogMeterGeometry.IDLE_SWAY_PERIOD_MS * 1_000_000L
            val startNs = withInfiniteAnimationFrameNanos { it }
            while (isActive) {
                // Infinite-animation frame source: Compose's test/render harness
                // (and any host that pauses infinite animations) can treat this
                // loop as idle instead of waiting for it forever.
                val now = withInfiniteAnimationFrameNanos { it }
                val phase = ((now - startNs) % periodNs).toDouble() / periodNs.toDouble()
                needle.snapTo(AnalogMeterGeometry.idleSwayAngle(phase).toFloat())
            }
        } else {
            needle.animateTo(
                targetValue = if (active) targetAngle else AnalogMeterGeometry.REST_ANGLE.toFloat(),
                animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 280f),
            )
        }
    }
    // The zone colour only moves when the reading enters/leaves the in-tune band,
    // so it stays on the shared content tokens.
    val zoneColor by animateColorAsState(
        targetValue = if (inTune) p.bandGreen else p.zoneOff,
        animationSpec = MotionTokens.content(reduced),
        label = "meter-zone",
    )
    val measurer = rememberTextMeasurer()
    val zoneHalfAngle = AnalogMeterGeometry.inTuneBandHalfAngle(3.0).toFloat()

    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .testTag("resonance.dial"),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val face = remember(maxWidth, maxHeight, density, p, embedded, measurer) {
                buildDialFace(
                    w = with(density) { maxWidth.toPx() },
                    h = with(density) { maxHeight.toPx() },
                    density = density,
                    measurer = measurer,
                    p = p,
                    embedded = embedded,
                )
            }
            // Static glass, frame, scale and numerals: its own layer, recorded
            // once per size — a moving needle never invalidates it.
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                drawDialFace(face, p, measurer)
            }
            // Zone arc + needle + escutcheon: its own layer, re-recorded per
            // frame, so a needle frame costs these strokes only.
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                drawDialNeedle(
                    face = face,
                    p = p,
                    zoneColor = zoneColor,
                    zoneHalfAngle = zoneHalfAngle,
                    angle = needle.value,
                    active = active,
                    clamped = clamped,
                )
            }
        }
    }
}

// ── Geometry (built once per size / palette / density) ──────────────────────

private class DialMark(val text: String, val style: TextStyle, val topLeft: Offset)

private class DialTick(val start: Offset, val end: Offset, val width: Float, val color: Color)

/**
 * Every immutable value the dial needs: brushes, paths, tick/numeral positions
 * and text layouts. Rebuilt only when the size, palette or density changes, so
 * neither layer re-measures text or builds a brush while the needle moves.
 */
private class DialFace(
    val embedded: Boolean,
    val w: Float,
    val h: Float,
    val faceLeft: Float,
    val faceTop: Float,
    val faceRight: Float,
    val faceBottom: Float,
    val frame: Float,
    val faceCorner: Float,
    val facePath: Path,
    val glassBrush: Brush,
    val hoodShadow: Brush,
    val frameBrush: Brush,
    val marks: List<DialMark>,
    val ticks: List<DialTick>,
    val triangle: Path,
    val flatText: String,
    val sharpText: String,
    val flatTopLeft: Offset,
    val sharpTopLeft: Offset,
    val sideStyle: TextStyle,
    val pivot: Offset,
    val radius: Float,
    val arcTopLeft: Offset,
    val arcSize: Size,
    val innerTopLeft: Offset,
    val innerSize: Size,
    val needleLen: Float,
    val escR: Float,
    val escCenter: Offset,
    val escPath: Path,
    val escBrush: Brush,
) {
    fun unit(angleDeg: Float): Offset {
        val rad = angleDeg * PI.toFloat() / 180f
        return Offset(sin(rad), -cos(rad))
    }

    fun at(angleDeg: Float, r: Float): Offset = pivot + unit(angleDeg) * r
}

private fun buildDialFace(
    w: Float,
    h: Float,
    density: Density,
    measurer: TextMeasurer,
    p: InstrumentPalette,
    embedded: Boolean,
): DialFace {
    val margin = with(density) { (if (embedded) 0.dp else 2.dp).toPx() }
    val faceLeft = margin
    val faceTop = margin
    val faceRight = w - margin
    val faceBottom = h - with(density) { (if (embedded) 0.dp else 6.dp).toPx() }
    val frame = with(density) { (if (embedded) 0.dp else 3.dp).toPx() }
    // The pivot sits just below the bottom edge: the needle emerges from the
    // bottom-center seam and its tail hides behind the escutcheon. Raised far
    // enough down that the width-limited arc still fills the face height.
    val pivot = Offset(w / 2f, faceBottom + with(density) { 12.dp.toPx() })

    fun unit(angleDeg: Float): Offset {
        val rad = angleDeg * PI.toFloat() / 180f
        return Offset(sin(rad), -cos(rad))
    }
    fun at(angleDeg: Float, r: Float): Offset = pivot + unit(angleDeg) * r

    // Radius from the bounds: numerals sit OUTSIDE the arc, so the extreme
    // numeral (at ±45°) must stay inside the face width, and the top numeral
    // must clear the top edge.
    val halfSweepRad = (AnalogMeterGeometry.SWEEP_DEGREES / 2.0 * PI / 180.0).toFloat()
    val sinHalf = sin(halfSweepRad)
    val numeralOffset = with(density) { 15.dp.toPx() }
    val numeralHalf = with(density) { 13.dp.toPx() }
    val edgePad = with(density) { 5.dp.toPx() }
    val widthLimit =
        ((faceRight - faceLeft) / 2f - edgePad - numeralHalf) / sinHalf - numeralOffset
    val heightLimit = pivot.y - faceTop - numeralOffset - with(density) { 6.dp.toPx() }
    val radius = min(widthLimit, heightLimit).coerceAtLeast(10f)

    val faceCorner = with(density) { (if (embedded) 0.dp else 14.dp).toPx() }
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
    // Recessed glass: warm radial ivory (bright upper-center → parchment edges)
    // plus a soft top-edge inner bezel shadow under the hood.
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
        endY = faceTop + frame + with(density) { 8.dp.toPx() },
    )
    val frameBrush = Brush.linearGradient(
        0f to p.brass.copy(alpha = 0.85f),
        0.22f to p.brassDark,
        1f to p.brassDark.copy(alpha = 0.92f),
        start = Offset(0f, 0f),
        end = Offset(w, h),
    )

    // Scale numerals OUTSIDE (above) the arc — the needle sweeps the inner ticks
    // cleanly without overlapping text. Fixed dp keeps geometry stable at any
    // system font scale.
    val labelStyle = TextStyle(
        fontSize = with(density) { 14.dp.toSp() },
        fontWeight = FontWeight.SemiBold,
        color = p.ink,
    )
    val zeroStyle = labelStyle.copy(fontWeight = FontWeight.Bold)
    val marks = (-50..50 step 10).map { cents ->
        val text = if (cents > 0) "+$cents" else "$cents"
        val style = if (cents == 0) zeroStyle else labelStyle
        val layout = measurer.measure(text, style)
        val center = at(AnalogMeterGeometry.tickAngle(cents).toFloat(), radius + numeralOffset)
        DialMark(
            text = text,
            style = style,
            topLeft = Offset(
                center.x - layout.size.width / 2f,
                center.y - layout.size.height / 2f,
            ),
        )
    }
    val sideStyle = TextStyle(
        fontSize = with(density) { 13.dp.toSp() },
        fontWeight = FontWeight.SemiBold,
        letterSpacing = with(density) { 0.8.dp.toSp() },
        // The FLAT/SHARP markers are orientation information, so they clear the
        // secondary-ink contrast floor instead of being drawn as faint hints.
        color = p.inkSoft,
    )
    val flatText = "FLAT ♭"
    val sharpText = "♯ SHARP"
    val flatSize = measurer.measure(flatText, sideStyle).size
    val sharpSize = measurer.measure(sharpText, sideStyle).size

    // 3-tier tick hierarchy pointing inward toward the pivot.
    val ticks = buildList {
        for (cents in -50..50 step 5) {
            val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
            val major = cents % 10 == 0
            val len = with(density) { (if (major) 14.dp else 9.dp).toPx() }
            val sw = with(density) { (if (major) 2.dp else 1.25.dp).toPx() }
            add(
                DialTick(
                    start = at(a, radius),
                    end = at(a, radius - len),
                    width = sw,
                    color = if (major) p.ink else p.inkSoft,
                ),
            )
        }
        // Fine vernier every 2¢ inside the critical −10..+10 zone.
        for (cents in -10..10 step 2) {
            if (cents % 5 == 0) continue
            val a = AnalogMeterGeometry.tickAngle(cents).toFloat()
            add(
                DialTick(
                    start = at(a, radius),
                    end = at(a, radius - with(density) { 5.dp.toPx() }),
                    width = with(density) { 0.8.dp.toPx() },
                    color = p.inkSoft,
                ),
            )
        }
    }
    // Small downward triangle index (▼) centered directly above 0.
    val zeroTip = at(0f, radius + with(density) { 2.dp.toPx() })
    val triHalf = with(density) { 3.5.dp.toPx() }
    val triHeight = with(density) { 5.dp.toPx() }
    val triangle = Path().apply {
        moveTo(zeroTip.x, zeroTip.y)
        lineTo(zeroTip.x - triHalf, zeroTip.y - triHeight)
        lineTo(zeroTip.x + triHalf, zeroTip.y - triHeight)
        close()
    }
    val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val innerRadius = radius - with(density) { 18.dp.toPx() }
    val innerTopLeft = Offset(pivot.x - innerRadius, pivot.y - innerRadius)
    val innerSize = Size(innerRadius * 2f, innerRadius * 2f)
    // The needle reaches just past the inner tips of the major ticks.
    val needleLen = radius - with(density) { 10.dp.toPx() }
    val escR = with(density) { 13.dp.toPx() }
    val escCenter = Offset(w / 2f, faceBottom - frame)
    val escPath = Path().apply {
        addArc(
            oval = Rect(
                escCenter.x - escR, escCenter.y - escR,
                escCenter.x + escR, escCenter.y + escR,
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
        )
        close()
    }
    val escBrush = Brush.radialGradient(
        0f to p.brassLight,
        0.6f to p.brass,
        1f to p.brassDark,
        center = Offset(escCenter.x - escR * 0.3f, escCenter.y - escR * 0.4f),
        radius = escR,
    )

    return DialFace(
        embedded = embedded,
        w = w,
        h = h,
        faceLeft = faceLeft,
        faceTop = faceTop,
        faceRight = faceRight,
        faceBottom = faceBottom,
        frame = frame,
        faceCorner = faceCorner,
        facePath = facePath,
        glassBrush = glassBrush,
        hoodShadow = hoodShadow,
        frameBrush = frameBrush,
        marks = marks,
        ticks = ticks,
        triangle = triangle,
        flatText = flatText,
        sharpText = sharpText,
        flatTopLeft = Offset(
            faceLeft + frame + with(density) { 12.dp.toPx() },
            faceBottom - frame - flatSize.height - with(density) { 10.dp.toPx() },
        ),
        sharpTopLeft = Offset(
            faceRight - frame - with(density) { 12.dp.toPx() } - sharpSize.width,
            faceBottom - frame - sharpSize.height - with(density) { 10.dp.toPx() },
        ),
        sideStyle = sideStyle,
        pivot = pivot,
        radius = radius,
        arcTopLeft = arcTopLeft,
        arcSize = arcSize,
        innerTopLeft = innerTopLeft,
        innerSize = innerSize,
        needleLen = needleLen,
        escR = escR,
        escCenter = escCenter,
        escPath = escPath,
        escBrush = escBrush,
    )
}

// ── Static layer: glass, frame, scale, numerals ─────────────────────────────

private fun DrawScope.drawDialFace(face: DialFace, p: InstrumentPalette, measurer: TextMeasurer) {
    val sweep = AnalogMeterGeometry.SWEEP_DEGREES.toFloat()
    if (!face.embedded) {
        drawRoundRect(
            color = p.recessEdge.copy(alpha = 0.30f),
            topLeft = Offset(face.faceLeft + 1.dp.toPx(), face.faceTop + 3.5.dp.toPx()),
            size = Size(face.faceRight - face.faceLeft, face.faceBottom - face.faceTop),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
        drawRoundRect(
            brush = face.frameBrush,
            topLeft = Offset(face.faceLeft, face.faceTop),
            size = Size(face.faceRight - face.faceLeft, face.faceBottom - face.faceTop),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
    }
    // Cream glass face.
    drawRoundRect(
        brush = face.glassBrush,
        topLeft = Offset(face.faceLeft + face.frame, face.faceTop + face.frame),
        size = Size(
            face.faceRight - face.faceLeft - face.frame * 2,
            face.faceBottom - face.faceTop - face.frame * 2,
        ),
        cornerRadius = CornerRadius(face.faceCorner),
    )
    clipPath(face.facePath) {
        // Top-edge inner bezel shadow (recessed under the hood).
        drawRect(
            brush = face.hoodShadow,
            topLeft = Offset(face.faceLeft + face.frame, face.faceTop + face.frame),
            size = Size(face.faceRight - face.faceLeft - face.frame * 2, 8.dp.toPx()),
        )
        // Parallax mirror hairline (concentric, 18dp inside the arc).
        drawArc(
            color = p.ink.copy(alpha = 0.18f),
            startAngle = 270f - sweep / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = face.innerTopLeft,
            size = face.innerSize,
            style = Stroke(width = 1.dp.toPx()),
        )
        // Main scale arc.
        drawArc(
            color = p.ink,
            startAngle = 270f - sweep / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = face.arcTopLeft,
            size = face.arcSize,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        face.ticks.forEach { t -> drawLine(t.color, t.start, t.end, strokeWidth = t.width) }
        drawPath(face.triangle, p.ink)
        face.marks.forEach { m ->
            drawText(
                textMeasurer = measurer,
                text = m.text,
                topLeft = m.topLeft,
                style = m.style,
            )
        }
        // Corner labels + vintage dial stamp (low contrast).
        drawText(
            textMeasurer = measurer,
            text = face.flatText,
            topLeft = face.flatTopLeft,
            style = face.sideStyle,
        )
        drawText(
            textMeasurer = measurer,
            text = face.sharpText,
            topLeft = face.sharpTopLeft,
            style = face.sideStyle,
        )
    }
}

// ── Live layer: target zone, needle, escutcheon ─────────────────────────────

private fun DrawScope.drawDialNeedle(
    face: DialFace,
    p: InstrumentPalette,
    zoneColor: Color,
    zoneHalfAngle: Float,
    angle: Float,
    active: Boolean,
    clamped: Boolean,
) {
    val sweep = AnalogMeterGeometry.SWEEP_DEGREES.toFloat()
    clipPath(face.facePath) {
        // Illuminated −3..+3¢ target zone (animated sage → emerald).
        drawArc(
            color = zoneColor,
            startAngle = 270f - zoneHalfAngle,
            sweepAngle = zoneHalfAngle * 2f,
            useCenter = false,
            topLeft = face.arcTopLeft,
            size = face.arcSize,
            style = Stroke(width = 4.5.dp.toPx()),
        )
        val u = face.unit(angle)
        val perp = Offset(u.y, -u.x)
        val pivot = face.pivot
        val tip = pivot + u * face.needleLen
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
            start = pivot + u * (face.needleLen * 0.18f) + perp * 0.5.dp.toPx(),
            end = pivot + u * (face.needleLen * 0.9f) + perp * 0.5.dp.toPx(),
            strokeWidth = 0.6.dp.toPx(),
        )
        if (active && clamped) {
            // Off-scale: the needle is pinned at a stop.
            val stopAngle = if (angle >= 0f) sweep / 2f else -sweep / 2f
            drawCircle(
                p.inkSoft,
                radius = 2.5.dp.toPx(),
                center = face.at(stopAngle, face.radius),
            )
        }
    }
    // Brass escutcheon: a semicircular cap at the bottom-center seam hiding the
    // needle tail (drawn over the needle exit).
    drawPath(face.escPath, p.brassDark)
    drawPath(face.escPath, face.escBrush)
    drawArc(
        color = p.recessEdge.copy(alpha = 0.6f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(face.escCenter.x - face.escR, face.escCenter.y - face.escR),
        size = Size(face.escR * 2f, face.escR * 2f),
        style = Stroke(width = 0.8.dp.toPx()),
    )
}