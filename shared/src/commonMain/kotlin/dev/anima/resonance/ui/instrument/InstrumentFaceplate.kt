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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The shared instrument faceplate — ONE quiet, mostly-flat warm-charcoal
 * surface mounted into the wooden enclosure. The meter, readout and controls
 * sit directly on it (no per-section cards, no nested brown gradients). Wood
 * grain belongs to the outer perimeter ([WoodenInstrumentPanel]), not here.
 *
 * A single restrained directional lip (shadow upper-left, faint catch-light
 * lower-right) marks the real material boundary where the faceplate recesses
 * into the enclosure. One consistent light direction; no decorative scratches.
 */
@Composable
fun InstrumentFaceplate(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    val shape = RoundedCornerShape(InstrumentTokens.cornerLg)
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                // Mostly flat charcoal with a barely-there vertical depth cue.
                val baseBrush = Brush.verticalGradient(
                    0f to p.faceplate,
                    1f to p.faceplateDeep,
                )
                val lipBrush = Brush.linearGradient(
                    0f to p.recessEdge.copy(alpha = 0.85f),
                    0.5f to Color.Transparent,
                    1f to p.faceplateLip.copy(alpha = 0.35f),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                )
                onDrawBehind {
                    drawRect(baseBrush)
                    val inset = 1.dp.toPx()
                    drawRoundRect(
                        brush = lipBrush,
                        topLeft = Offset(inset, inset),
                        size = Size(w - inset * 2, h - inset * 2),
                        cornerRadius = CornerRadius(InstrumentTokens.cornerLg.toPx() - inset),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            },
    ) {
        content()
    }
}