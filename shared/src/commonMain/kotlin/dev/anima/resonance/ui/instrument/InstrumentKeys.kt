package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared physical-instrument primitives — recessed panels, tactile
 * keys and jewel lamps. One hardware language for the whole body:
 * engaged keys stand proud of the surface with a short shadow; idle keys
 * sit flat in the recess; edges are hairlines, never gold outlines.
 * Lighting is consistent: highlights upper-left, shadows lower-right.
 * State is always conveyed by text/shape in addition to color.
 */

/**
 * A panel milled into the enclosure: warm near-black recess with a
 * directional inner lip (shadow at the upper-left wall, faint light on
 * the lower-right lip). No outer border — the edge IS the material
 * transition.
 *
 * [faceplate] switches the fill to the shared top-lit deck gradient (the
 * settings sheet's quieter variant — no grain, no seam shadow) while keeping
 * the same lip language. The lip radius follows the panel's own shape.
 */
@Composable
fun RecessedPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color? = null,
    faceplate: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    val fillModifier = if (faceplate) {
        Modifier
            .clip(shape)
            .faceplateFinish(grain = false, seamShadow = false)
    } else {
        Modifier.background(color ?: p.recess, shape)
    }
    Box(
        modifier = modifier
            .then(fillModifier)
            .drawBehind {
                val inset = 0.75.dp.toPx()
                val r = if (shape is RoundedCornerShape) {
                    shape.topStart.toPx(size, this)
                } else {
                    6.dp.toPx()
                }
                // Inner lip lit from the upper left: dark shadow along the
                // top/left walls, a faint catch-light on the bottom/right.
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to p.recessEdge.copy(alpha = 1f),
                        0.45f to p.recessEdge.copy(alpha = 0.35f),
                        0.75f to Color.Transparent,
                        1f to p.walnutLight.copy(alpha = 0.22f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(width = 2.dp.toPx()),
                )
            },
    ) {
        content()
    }
}

/**
 * A tactile key. [selected] = engaged: the cap stands proud with a short
 * shadow and a top bevel. [highlighted] = a secondary active hint (e.g.
 * AUTO-detected string) without full engagement. Finger presses dip the
 * cap; disabled keys dim. Touch targets stay >= 48 dp via the caller.
 */
@Composable
fun InstrumentKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    accentBorder: Boolean = false,
    bevel: Boolean = false,
    shape: Shape = RoundedCornerShape(6.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val raised = (selected || accentBorder) && !pressed
    val capTarget = when {
        !enabled -> p.walnut.copy(alpha = 0.55f)
        pressed -> p.walnutDeep
        accentBorder -> p.walnutLight
        raised -> p.walnutLight
        highlighted -> p.walnut
        else -> p.recess
    }
    // The cap tone settles at press speed instead of snapping.
    val capColor by androidx.compose.animation.animateColorAsState(
        targetValue = capTarget,
        animationSpec = MotionTokens.press(LocalReducedMotion.current),
        label = "instrument-key-cap",
    )
    Surface(
        onClick = onClick,
        // The cap tone animates on press/release; Surface paints its
        // background OUTSIDE its clip, so without a layer of our own every
        // frame of that 100 ms dip re-records the entire window.
        modifier = modifier.animationLayer(),
        enabled = enabled,
        shape = shape,
        color = capColor,
        contentColor = p.ivory,
        border = BorderStroke(
            width = if (accentBorder) 1.5.dp else 1.dp,
            color = when {
                accentBorder -> p.brass
                raised -> p.walnutLight.copy(alpha = 0.9f)
                pressed -> p.recessEdge
                else -> p.recessEdge.copy(alpha = 0.85f)
            },
        ),
        shadowElevation = if (accentBorder) 2.dp else if (raised) 3.dp else 0.dp,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .drawBehind {
                    val r = 5.dp.toPx()
                    if (raised || bevel) {
                        // Proud cap / push-button: bevel catch-light along the top edge.
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to (if (bevel) p.brassLight.copy(alpha = 0.30f) else p.ivory.copy(alpha = 0.14f)),
                                0.4f to Color.Transparent,
                            ),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    } else if (!pressed) {
                        // Flat key seated in the recess: faint inner shadow.
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to p.recessEdge.copy(alpha = 0.6f),
                                0.6f to Color.Transparent,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                            ),
                            cornerRadius = CornerRadius(r),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * A small jewel indicator lamp in a dark brass bezel. Decorative on its
 * own — every lamp is paired with a text label elsewhere in the component,
 * so color is never the only carrier of state.
 */
@Composable
fun JewelLamp(
    lit: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val p = InstrumentTheme.palette
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(size)
            // The jewel colour animates between phases — keep that redraw
            // inside its own layer instead of re-recording the window.
            .animationLayer()
            .clearAndSetSemantics {},
    ) {
        val c = center
        val r = size.toPx() / 2f
        if (lit) {
            drawCircle(color.copy(alpha = 0.20f), radius = r, center = c)
        }
        drawCircle(p.recessEdge, radius = r * 0.86f, center = c)
        drawCircle(p.brassDark, radius = r * 0.78f, center = c)
        val jewel = if (lit) color else p.lampOff
        drawCircle(
            brush = Brush.radialGradient(
                0f to jewel,
                0.6f to jewel,
                1f to jewel.copy(alpha = 0.5f),
                center = Offset(c.x - r * 0.2f, c.y - r * 0.2f),
                radius = r * 0.9f,
            ),
            radius = r * 0.58f,
            center = c,
        )
        drawCircle(
            Color.White.copy(alpha = if (lit) 0.5f else 0.15f),
            radius = r * 0.14f,
            center = Offset(c.x - r * 0.2f, c.y - r * 0.24f),
        )
    }
}

/**
 * A small antique-brass gear/tuning icon for the settings push-button.
 * Decorative (paired with the uppercase label); deterministic Canvas drawing.
 */
@Composable
fun GearIcon(
    modifier: Modifier = Modifier,
    color: Color = InstrumentTheme.palette.brass,
) {
    val p = InstrumentTheme.palette
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(16.dp)
            .clearAndSetSemantics {},
    ) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(color, radius = r * 0.70f, center = c)
        val teeth = 8
        for (i in 0 until teeth) {
            val ang = i * 2.0 * kotlin.math.PI / teeth
            val tx = c.x + (kotlin.math.cos(ang) * r * 0.80f).toFloat()
            val ty = c.y + (kotlin.math.sin(ang) * r * 0.80f).toFloat()
            drawCircle(color, radius = r * 0.24f, center = Offset(tx, ty))
        }
        drawCircle(p.recess, radius = r * 0.30f, center = c)
        drawCircle(color.copy(alpha = 0.5f), radius = r * 0.30f, center = c, style = Stroke(width = r * 0.08f))
    }
}