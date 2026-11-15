package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared physical-instrument primitives — recessed panels, tactile
 * keys and jewel lamps. These are the "hardware" the screen is built from;
 * they carry no measurement logic. All state is conveyed by text/shape in
 * addition to color (the lamps are decorative; their labels are not).
 */

/** A panel milled into the enclosure: dark recess with an inset shadow. */
@Composable
fun RecessedPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    Box(
        modifier = modifier
            .background(p.recess, shape)
            .drawBehind {
                // Inset shadow: dark at the top edge, faint light at the
                // bottom — reads as milled depth without blur filters.
                val inset = 1.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to p.recessEdge.copy(alpha = 0.85f),
                        0.18f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to p.walnutLight.copy(alpha = 0.22f),
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            .then(
                if (borderColor != null) Modifier.background(Color.Transparent, shape) else Modifier,
            ),
    ) {
        content()
        if (borderColor != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(11.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
            )
        }
    }
}

/**
 * A tactile key: raised horn/wood cap when idle, pressed into the panel
 * when [selected]. Touch targets stay >= 48 dp via the caller's heightIn.
 */
@Composable
fun InstrumentKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(9.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = if (selected) p.recess else p.walnutLight,
        contentColor = p.ivory,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) p.recessEdge else p.brassDark.copy(alpha = 0.65f),
        ),
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .drawBehind {
                    if (!selected) {
                        // Raised cap: faint top bevel highlight.
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to p.walnutLight.copy(alpha = 0.9f),
                                0.35f to Color.Transparent,
                            ),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            content = content,
        )
    }
}

/**
 * A small jewel indicator lamp in a brass bezel. Decorative on its own —
 * every lamp is paired with a text label elsewhere in the component, so
 * color is never the only carrier of state.
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
            .clearAndSetSemantics {},
    ) {
        val c = center
        val r = size.toPx() / 2f
        if (lit) {
            drawCircle(color.copy(alpha = 0.22f), radius = r, center = c)
        }
        drawCircle(p.brassDark, radius = r * 0.82f, center = c)
        val jewel = if (lit) color else p.lampOff
        drawCircle(
            brush = Brush.radialGradient(
                0f to if (lit) jewel.copy(alpha = 1f) else p.lampOff,
                0.55f to jewel,
                1f to jewel.copy(alpha = 0.55f),
                center = Offset(c.x - r * 0.2f, c.y - r * 0.2f),
                radius = r * 0.9f,
            ),
            radius = r * 0.62f,
            center = c,
        )
        // Specular dot
        drawCircle(
            Color.White.copy(alpha = if (lit) 0.55f else 0.18f),
            radius = r * 0.16f,
            center = Offset(c.x - r * 0.22f, c.y - r * 0.26f),
        )
    }
}