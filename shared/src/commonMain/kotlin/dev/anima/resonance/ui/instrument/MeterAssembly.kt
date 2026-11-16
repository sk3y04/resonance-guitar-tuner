package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState

/**
 * The meter + note readout as ONE purpose-built measurement assembly.
 *
 * A single brass-rimmed recess carries the ivory analog gauge on top and the
 * warm-dark note readout directly beneath it (adjoining recessed surfaces,
 * hairline divider) — they read as one instrument, not two stacked cards.
 *
 * The assembly fills its flexible slot: the gauge keeps a natural arc-filling
 * aspect ([MeterAssemblyLayout]) and the readout absorbs the spare height
 * deliberately (a larger, vertically-centred note), so there are no empty
 * bands above or below the meter. Geometry is calculated from the available
 * bounds, never stretched. Needle mapping, animation and the honest
 * no-signal rest state are unchanged.
 */
@Composable
fun MeterAssembly(
    state: MainScreenUiState,
    largeText: Boolean,
    onRetryCapture: () -> Unit,
    modifier: Modifier = Modifier,
    availableHeightDp: Float = 4096f,
    readoutCapDp: Float = MeterAssemblyLayout.MAX_READOUT_DP,
    minGaugeAspect: Float = MeterAssemblyLayout.MIN_GAUGE_ASPECT,
) {
    val p = InstrumentTheme.palette
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Height-aware: the gauge keeps a natural arc-filling aspect; the
        // readout is compact. Surplus goes to the string keys (instrument) or
        // the readout (chromatic). Fill the slot exactly (minus the divider).
        val targetH = minOf(availableHeightDp, maxHeight.value)
        val spec = MeterAssemblyLayout.compute(maxWidth.value, targetH - 1.5f, readoutCapDp, minGaugeAspect)
        val gaugeH = spec.gaugeHeightDp.dp
        val readoutH = spec.readoutHeightDp.dp
        val noteSp = (readoutH.value * 0.48f).coerceIn(40f, 54f)
        val noteSize = (noteSp / fontScale).coerceAtMost(54f).sp
        val centsSize = (noteSp * 0.52f / fontScale).coerceAtMost(28f).sp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.recess)
                .drawBehind {
                    // Unified beveled bezel: a subtle outer rim highlight
                    // (lit upper-left) + a crisp 1dp dark inner inset border, so
                    // dial + readout read as one module behind instrument glass.
                    val r = 14.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to p.brassLight.copy(alpha = 0.55f),
                            0.4f to p.brass.copy(alpha = 0.30f),
                            1f to p.brassDark.copy(alpha = 0.65f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    val inset = 1.5.dp.toPx()
                    drawRoundRect(
                        color = p.recessEdge.copy(alpha = 0.95f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(r - inset),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
        ) {
            Column(Modifier.fillMaxWidth()) {
                // ── Ivory analog dial (rounded top corners via the bezel) ──
                Box(Modifier.fillMaxWidth().height(gaugeH)) {
                    AnalogTuningMeter(
                        position = state.meterPosition,
                        inTune = state.inTune,
                        clamped = state.meterClamped,
                        active = state.meterActive,
                        embedded = true,
                        aspectRatio = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Recessed divider seam between the dial and the display window.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .drawBehind {
                            drawRect(p.recessEdge)
                            drawLine(
                                color = p.brassDark.copy(alpha = 0.45f),
                                start = Offset(0f, size.height - 0.5.dp.toPx()),
                                end = Offset(size.width, size.height - 0.5.dp.toPx()),
                                strokeWidth = 0.75.dp.toPx(),
                            )
                        },
                )
                // ── Illuminated digital readout (rounded bottom corners) ──
                NoteReadout(
                    state = state,
                    noteSize = noteSize,
                    centsSize = centsSize,
                    largeText = largeText,
                    fillHeight = true,
                    onRetryCapture = onRetryCapture,
                    modifier = Modifier.fillMaxWidth().height(readoutH),
                )
            }
        }
    }
}