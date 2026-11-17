package dev.anima.resonance.ui.instrument

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.anima.resonance.ui.instrument.FaceplateGrain as Grain

/**
 * The shared interaction layer. Every custom (indication = null) surface
 * in the instrument gets its press/selection/focus treatment from here, so the
 * whole app speaks one tactile language: warm tonal layers, immediate response,
 * interruptible tweens, one implied light direction (from above).
 */

/**
 * The animated press-state fill for rows and keys that draw their own state
 * layer. Transparent at rest; [pressedColor] while the finger is down. The
 * tween retargets on every press/release — repeated taps never queue, and a
 * drag-cancel releases the layer exactly like a tap-release.
 */
@Composable
fun pressLayerColor(
    pressed: Boolean,
    pressedColor: Color = InstrumentTheme.palette.segmentCap,
): Color {
    val reduced = LocalReducedMotion.current
    val color by animateColorAsState(
        targetValue = if (pressed) pressedColor else Color.Transparent,
        animationSpec = MotionTokens.press(reduced),
        label = "press-layer",
    )
    return color
}

/**
 * The animated selection fill (parchment key / quiet selected row). Separate
 * from [pressLayerColor] so a press on an already-selected key dips to the
 * pressed parchment without waiting for the selection tween.
 */
@Composable
fun selectionFill(selected: Boolean, selectedColor: Color): Color {
    val reduced = LocalReducedMotion.current
    val color by animateColorAsState(
        targetValue = if (selected) selectedColor else Color.Transparent,
        animationSpec = MotionTokens.selection(reduced),
        label = "selection-fill",
    )
    return color
}

/**
 * Keyboard-focus hairline — the only outline a control ever draws. Matches the
 * brass family, inset 1dp so it reads as an edge light, never a coloured box.
 */
fun Modifier.focusHairline(cornerDp: androidx.compose.ui.unit.Dp): Modifier = composed {
    val p = InstrumentTheme.palette
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .drawBehind {
            if (focused) {
                val i = 1.dp.toPx()
                drawRoundRect(
                    color = p.brassLight.copy(alpha = 0.9f),
                    topLeft = Offset(i, i),
                    size = Size(size.width - 2 * i, size.height - 2 * i),
                    cornerRadius = CornerRadius(cornerDp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
}

/**
 * The restrained edge treatment of a SELECTED parchment cap: a catch-light
 * along the top edge and a faint shade along the bottom — the same implied
 * light direction as the faceplate gradient. No bevel stacks, no shadows.
 */
fun DrawScope.selectedCapEdge() {
    val inset = 3.dp.toPx()
    val half = 0.5.dp.toPx()
    drawLine(
        color = Color.White.copy(alpha = 0.30f),
        start = Offset(inset, half),
        end = Offset(size.width - inset, half),
        strokeWidth = 1.dp.toPx(),
    )
    drawLine(
        color = Color(0xFF2A2016).copy(alpha = 0.16f),
        start = Offset(inset, size.height - half),
        end = Offset(size.width - inset, size.height - half),
        strokeWidth = 1.dp.toPx(),
    )
}

/**
 * The matte faceplate finish: a gentle warm vertical gradient (lighter toward
 * the readout, deeper toward the bottom edge), an optional static grain tile,
 * and an optional contact shadow under the seam. Built once per size in
 * drawWithCache; NOTHING here reads measurement state, so pitch updates never
 * rebuild or redraw the surface.
 */
@Composable
fun Modifier.faceplateFinish(
    grain: Boolean = true,
    seamShadow: Boolean = true,
): Modifier {
    val p = InstrumentTheme.palette
    val grainBrush = if (grain) rememberFaceplateGrainBrush() else null
    return this.drawWithCache {
        val gradient = Brush.verticalGradient(
            0f to p.deckTop,
            0.45f to p.deck,
            1f to p.deckBottom,
        )
        val seamDepth = 7.dp.toPx()
        val seam = if (seamShadow) {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.18f),
                1f to Color.Transparent,
                startY = 0f,
                endY = seamDepth,
            )
        } else {
            null
        }
        onDrawBehind {
            drawRect(gradient)
            if (grainBrush != null) drawRect(grainBrush)
            if (seam != null) drawRect(seam, size = Size(size.width, seamDepth))
        }
    }
}

/**
 * Uploads the deterministic grain tile ONCE per composition into a repeating
 * shader. The tile is size-independent, so resizing the window never
 * regenerates it; the pixels come from the tested pure generator.
 */
@Composable
fun rememberFaceplateGrainBrush(): Brush {
    val bitmap = remember {
        val pixels = Grain.argbPixels()
        val size = Grain.TILE_SIZE
        val bmp = ImageBitmap(size, size)
        val canvas = androidx.compose.ui.graphics.Canvas(bmp)
        // Group specks by (colour, alpha) so the whole tile uploads in a
        // handful of draw calls instead of thousands.
        val light = androidx.compose.ui.graphics.Color(0xFFE8D9BC)
        val dark = androidx.compose.ui.graphics.Color(0xFF120B08)
        val groups = HashMap<Long, MutableList<Offset>>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val argb = pixels[y * size + x]
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue
                val rgb = argb and 0xFFFFFF
                val key = (alpha.toLong() shl 32) or rgb.toLong()
                groups.getOrPut(key) { mutableListOf() }
                    .add(Offset(x + 0.5f, y + 0.5f))
            }
        }
        val paint = Paint().apply { strokeWidth = 1f }
        groups.forEach { (key, points) ->
            val alpha = ((key ushr 32) and 0xFF).toInt()
            val rgb = (key and 0xFFFFFF).toInt()
            val base = if (rgb == 0xE8D9BC) light else dark
            paint.color = base.copy(alpha = alpha / 255f)
            canvas.drawPoints(PointMode.Points, points, paint)
        }
        bmp
    }
    return remember(bitmap) {
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * A short fade-in for content that REPLACES other content (mode body, settings
 * destination). The outgoing content is removed immediately — no overlap, no
 * double composition, no queued entrance — and the incoming content settles
 * from transparent. The very first composition renders at full opacity so
 * static renders and first paint are never caught mid-fade. Reduced motion
 * snaps. The alpha lives in a graphicsLayer: only the layer re-draws.
 */
@Composable
fun FadeInOnChange(
    key: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val initialKey = remember { key }
    val anim = remember(key) {
        Animatable(if (key == initialKey || reduced) 1f else 0f)
    }
    LaunchedEffect(key, reduced) {
        if (key != initialKey && !reduced) {
            anim.animateTo(1f, MotionTokens.content(reduced))
        } else {
            anim.snapTo(1f)
        }
    }
    Box(modifier.graphicsLayer { alpha = anim.value }) { content() }
}

/**
 * The status lamp's colour settles between phases instead of snapping; the
 * lit/unlit glow stays immediate (it is paired with text, never decorative).
 */
@Composable
fun animatedLampColor(target: Color): Color {
    val reduced = LocalReducedMotion.current
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = MotionTokens.selection(reduced),
        label = "lamp-color",
    )
    return color
}
