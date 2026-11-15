package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The shared instrument faceplate — ONE inset surface mounted into
 * the wooden enclosure, carrying the meter, readout, selector and string
 * keys. Components sit directly on it (no per-section cards). The surface
 * is a deeper walnut tone with a faint horizontal figure and a directional
 * inner lip; grain is generated once per size and cached.
 */
@Composable
fun InstrumentFaceplate(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val streaks = WoodGrain.streaks(seed = 99L, width = w, height = h, count = 18)
                val grainPaths = streaks.map { s ->
                    Path().apply {
                        val step = 28f
                        var x = 0f
                        moveTo(0f, s.startY + WoodGrain.wanderAt(s, 0f))
                        while (x < w) {
                            x = (x + step).coerceAtMost(w)
                            lineTo(x, s.startY + WoodGrain.wanderAt(s, x))
                        }
                    }
                }
                val baseBrush = Brush.verticalGradient(
                    0f to p.walnutDeep,
                    0.5f to p.walnut.copy(alpha = 0.92f),
                    1f to p.walnutDeep,
                )
                val lipBrush = Brush.linearGradient(
                    0f to p.recessEdge.copy(alpha = 0.95f),
                    0.45f to p.recessEdge.copy(alpha = 0.35f),
                    0.75f to Color.Transparent,
                    1f to p.walnutLight.copy(alpha = 0.25f),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                )
                onDrawBehind {
                    drawRect(baseBrush)
                    grainPaths.forEachIndexed { i, path ->
                        drawPath(
                            path = path,
                            color = if (i % 3 == 0) p.grainLight else p.grainDark,
                            alpha = streaks[i].alpha * 0.5f,
                            style = Stroke(width = streaks[i].thickness),
                        )
                    }
                    // Milled inset lip: shadow upper-left, catch-light lower-right.
                    val inset = 1.dp.toPx()
                    drawRoundRect(
                        brush = lipBrush,
                        topLeft = Offset(inset, inset),
                        size = Size(w - inset * 2, h - inset * 2),
                        cornerRadius = CornerRadius(13.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
    ) {
        content()
    }
}