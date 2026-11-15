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
) {
    val p = InstrumentTheme.palette
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Height-aware: the gauge shrinks to fit short screens and stays at
        // its natural arc-filling aspect on tall ones; the readout is compact.
        // The host gives any surplus height to the lower controls.
        val spec = MeterAssemblyLayout.compute(maxWidth.value, availableHeightDp)
        val gaugeH = spec.gaugeHeightDp.dp
        val readoutH = spec.readoutHeightDp.dp
        val noteSp = (readoutH.value * 0.55f).coerceIn(40f, 96f)
        val noteSize = (noteSp / fontScale).coerceAtMost(96f).sp
        val centsSize = (noteSp * 0.45f / fontScale).coerceAtMost(34f).sp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.recess)
                .drawBehind {
                    // One shared mounting rim: muted brass hairline with a
                    // directional inner lip (light upper-left).
                    val inset = 0.75.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to p.brass.copy(alpha = 0.85f),
                            0.5f to p.brassDark,
                            1f to p.brassDark.copy(alpha = 0.9f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(11.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                },
        ) {
            Column(Modifier.fillMaxWidth()) {
                // ── Ivory gauge (scale + needle only; messages live below) ──
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
                // Hairline brass divider between the two recessed surfaces.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(p.brassDark.copy(alpha = 0.7f)),
                )
                // ── Note readout (adjoining recess; carries the message) ──
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