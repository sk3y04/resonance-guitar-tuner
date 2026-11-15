package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The walnut enclosure — one continuous wooden body. Horizontal
 * flat-sawn figure with broad tonal bands, lit consistently from the upper
 * left (highlights) toward the lower right (shadows). Grain and bands are
 * generated ONCE per panel size from a fixed seed inside drawWithCache —
 * never regenerated while the needle moves or the state ticker refreshes.
 * Corner hardware is four small, dark brass screws — subtle, not jewelry.
 */
@Composable
fun WoodenEnclosure(modifier: Modifier = Modifier) {
    val p = InstrumentTheme.palette
    Canvas(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val streaks = WoodGrain.streaks(seed = 20261115L, width = w, height = h, count = 42)
            val grainPaths = streaks.map { s ->
                Path().apply {
                    val step = 24f
                    var x = 0f
                    moveTo(0f, s.startY + WoodGrain.wanderAt(s, 0f))
                    while (x < w) {
                        x = (x + step).coerceAtMost(w)
                        lineTo(x, s.startY + WoodGrain.wanderAt(s, x))
                    }
                }
            }
            val bands = WoodGrain.bands(seed = 20261115L, height = h)
            val baseBrush = Brush.verticalGradient(
                0f to p.walnutLight,
                0.5f to p.walnut,
                1f to p.walnutDeep,
            )
            // Consistent room lighting: soft highlight upper-left, shade
            // lower-right, laid over the whole body.
            val lightBrush = Brush.linearGradient(
                0f to p.walnutLight.copy(alpha = 0.16f),
                0.45f to Color.Transparent,
                1f to p.walnutDeep.copy(alpha = 0.22f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val bevelBrush = Brush.linearGradient(
                0f to p.walnutLight.copy(alpha = 0.9f),
                0.5f to p.walnut.copy(alpha = 0.5f),
                1f to p.walnutDeep.copy(alpha = 0.95f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val recessBrush = Brush.linearGradient(
                0f to p.recessEdge.copy(alpha = 0.42f),
                0.6f to p.recessEdge.copy(alpha = 0.16f),
                1f to p.walnutLight.copy(alpha = 0.14f),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val screwInset = 11.dp.toPx()
            val screwR = 2.6.dp.toPx()
            val screws = listOf(
                Offset(screwInset, screwInset),
                Offset(w - screwInset, screwInset),
                Offset(screwInset, h - screwInset),
                Offset(w - screwInset, h - screwInset),
            )
            onDrawBehind {
                drawRect(baseBrush)
                // Broad heartwood tonal bands (soft-edged, very low alpha).
                bands.forEach { b ->
                    val tone = if (b.lighten) p.grainLight else p.grainDark
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to tone.copy(alpha = (b.alpha * 1.3f).coerceAtMost(0.18f)),
                            1f to Color.Transparent,
                            startY = b.centerY - b.halfHeight,
                            endY = b.centerY + b.halfHeight,
                        ),
                        topLeft = Offset(0f, b.centerY - b.halfHeight),
                        size = Size(w, b.halfHeight * 2f),
                    )
                }
                // Fine horizontal figure — a touch stronger on the exposed
                // rails so the body reads as walnut, not a flat brown fill.
                grainPaths.forEachIndexed { i, path ->
                    drawPath(
                        path = path,
                        color = if (i % 3 == 0) p.grainLight else p.grainDark,
                        alpha = (streaks[i].alpha * 1.5f).coerceAtMost(0.34f),
                        style = Stroke(width = streaks[i].thickness * 1.15f),
                    )
                }
                drawRect(lightBrush)
                // Carefully shaded outer bevel + darker inner recess lip.
                val edge = 0.75.dp.toPx()
                drawRoundRect(
                    brush = bevelBrush,
                    topLeft = Offset(edge, edge),
                    size = Size(w - edge * 2, h - edge * 2),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                drawRoundRect(
                    brush = recessBrush,
                    topLeft = Offset(2.5.dp.toPx(), 2.5.dp.toPx()),
                    size = Size(w - 5.dp.toPx(), h - 5.dp.toPx()),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx()),
                )
                // Small dark brass corner screws, deterministic slot angles.
                screws.forEachIndexed { i, c ->
                    drawCircle(p.recessEdge.copy(alpha = 0.55f), radius = screwR * 1.25f, center = c)
                    drawCircle(p.brassDark, radius = screwR, center = c)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to p.brass,
                            1f to p.brassDark,
                            center = Offset(c.x - screwR * 0.35f, c.y - screwR * 0.35f),
                            radius = screwR,
                        ),
                        radius = screwR * 0.72f,
                        center = c,
                    )
                    val ang = (i * 47.0 + 20.0) * PI / 180.0
                    val dx = (cos(ang) * screwR * 0.5).toFloat()
                    val dy = (sin(ang) * screwR * 0.5).toFloat()
                    drawLine(
                        color = p.recessEdge.copy(alpha = 0.8f),
                        start = Offset(c.x - dx, c.y - dy),
                        end = Offset(c.x + dx, c.y + dy),
                        strokeWidth = 0.8.dp.toPx(),
                    )
                }
            }
        },
        onDraw = {},
    )
}