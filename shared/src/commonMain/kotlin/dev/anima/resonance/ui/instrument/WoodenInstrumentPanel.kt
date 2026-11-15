package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The walnut enclosure. Procedural grain is generated ONCE per panel
 * size from a fixed seed inside drawWithCache — it never regenerates while
 * the needle moves or the state ticker refreshes. Bevel, inset shadow and
 * four brass corner screws complete the tabletop-instrument feel.
 */
@Composable
fun WoodenEnclosure(modifier: Modifier = Modifier) {
    val p = InstrumentTheme.palette
    Canvas(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val streaks = WoodGrain.streaks(seed = 20261115L, width = w, height = h)
            val grainPaths = streaks.map { s ->
                Path().apply {
                    val step = 24f
                    var y = 0f
                    moveTo(s.startX + WoodGrain.wanderAt(s, 0f), 0f)
                    while (y < h) {
                        y = (y + step).coerceAtMost(h)
                        lineTo(s.startX + WoodGrain.wanderAt(s, y), y)
                    }
                }
            }
            val baseBrush = Brush.verticalGradient(
                0f to p.walnutLight,
                0.45f to p.walnut,
                1f to p.walnutDeep,
            )
            val bevelBrush = Brush.verticalGradient(
                0f to p.walnutLight.copy(alpha = 0.85f),
                1f to p.walnutDeep.copy(alpha = 0.9f),
            )
            val screwInset = 12.dp.toPx()
            val screwR = 3.2.dp.toPx()
            val screws = listOf(
                Offset(screwInset, screwInset),
                Offset(w - screwInset, screwInset),
                Offset(screwInset, h - screwInset),
                Offset(w - screwInset, h - screwInset),
            )
            onDrawBehind {
                drawRect(baseBrush)
                grainPaths.forEachIndexed { i, path ->
                    drawPath(
                        path = path,
                        color = if (i % 3 == 0) p.grainLight else p.grainDark,
                        alpha = streaks[i].alpha,
                        style = Stroke(width = streaks[i].thickness),
                    )
                }
                // Beveled edge + restrained inset shadow.
                val edge = 1.dp.toPx()
                drawRoundRect(
                    brush = bevelBrush,
                    topLeft = Offset(edge, edge),
                    size = Size(w - edge * 2, h - edge * 2),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawRoundRect(
                    color = p.recessEdge.copy(alpha = 0.30f),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(w - 6.dp.toPx(), h - 6.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx()),
                )
                // Corner screws with deterministic slot angles.
                screws.forEachIndexed { i, c ->
                    drawCircle(p.brassDark, radius = screwR, center = c)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to p.brassLight,
                            1f to p.brass,
                            center = Offset(c.x - screwR * 0.3f, c.y - screwR * 0.3f),
                            radius = screwR,
                        ),
                        radius = screwR * 0.74f,
                        center = c,
                    )
                    val ang = (i * 47.0 + 20.0) * PI / 180.0
                    val dx = (cos(ang) * screwR * 0.55).toFloat()
                    val dy = (sin(ang) * screwR * 0.55).toFloat()
                    drawLine(
                        color = p.recessEdge.copy(alpha = 0.75f),
                        start = Offset(c.x - dx, c.y - dy),
                        end = Offset(c.x + dx, c.y + dy),
                        strokeWidth = 0.9.dp.toPx(),
                    )
                }
            }
        },
        onDraw = {},
    )
}