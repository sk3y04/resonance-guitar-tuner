package dev.anima.resonance.ui.instrument

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.SignalPhase
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.tabularNumerals
import kotlin.math.abs

/**
 * The digital note readout — a compact display mounted behind the same
 * instrument glass as the dial. One coherent type hierarchy (no tiny tracked
 * micro-labels), tabular numerals for changing values, and stable positions
 * across signal states.
 *
 * Three columns: detected/target note (+ accidental + octave) | frequency |
 * signed cents + a single direction/in-tune badge. The direction badge is the
 * ONE direction indicator (the needle also shows it); a redundant reticle was
 * removed.
 *
 * Signal honesty: values render only while a reading is present — live
 * ([SignalPhase.TRACKING]) or briefly held after an interruption
 * ([SignalPhase.HOLDING], visibly faded, never announced as live). With no
 * usable pitch the SAME grid shows dimmed placeholders + one calm prompt, so
 * the container height never jumps. The semantic tag contract is preserved.
 */
@Composable
fun NoteReadout(
    state: MainScreenUiState,
    noteSize: androidx.compose.ui.unit.TextUnit,
    centsSize: androidx.compose.ui.unit.TextUnit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    onRetryCapture: () -> Unit = {},
) {
    val p = InstrumentTheme.palette
    val live = state.meterActive
    val holding = state.signalPhase == SignalPhase.HOLDING
    val showing = live || holding
    val alpha = if (live) 1f else state.readingAlpha
    val centsDescription = when {
        !showing || state.centsText == null -> "No current reading"
        holding -> "Last reading ${state.centsText} cents, signal interrupted"
        state.inTune -> "In tune"
        state.directionText == "Tune up" ->
            "${abs(state.centsText.toIntOrNull() ?: 0)} cents flat, tune up"
        else ->
            "${abs(state.centsText.toIntOrNull() ?: 0)} cents sharp, tune down"
    }
    val noteColor by animateColorAsState(
        targetValue = if (live && state.inTune) p.emeraldGlow else p.noteIvory,
        animationSpec = tween(280),
        label = "readout-note-color",
    )
    val ambient by animateColorAsState(
        targetValue = if (live && state.inTune) p.bandGreen.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(320),
        label = "readout-ambient",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
            .drawBehind {
                // Warm radial backlight (illuminated display depth).
                drawRect(
                    brush = Brush.radialGradient(
                        0f to p.backlightCenter,
                        1f to p.backlightEdge,
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(ambient)
                // 1dp recessed inner border.
                drawRect(
                    color = p.recessEdge.copy(alpha = 0.9f),
                    topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - 1.dp.toPx(),
                        size.height - 1.dp.toPx(),
                    ),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteColumn(state, noteSize, noteColor, showing, live, alpha)
            Spacer(Modifier.size(10.dp))
            CenterColumn(state, showing, alpha, onRetryCapture, Modifier.weight(1f))
            Spacer(Modifier.size(10.dp))
            CentsColumn(state, centsSize, showing, live, alpha, centsDescription)
        }
    }
}

@Composable
private fun NoteColumn(
    state: MainScreenUiState,
    noteSize: androidx.compose.ui.unit.TextUnit,
    noteColor: Color,
    showing: Boolean,
    live: Boolean,
    alpha: Float,
) {
    val p = InstrumentTheme.palette
    Column(
        modifier = Modifier.widthIn(min = 72.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showing && state.noteLabel != null) {
            val label = state.noteLabel!!
            val root = label.first().toString()
            val accidental = label.drop(1)
                .replace("#", "♯")
                .replace("b", "♭")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = root,
                    style = TextStyle(
                        fontSize = noteSize,
                        fontWeight = FontWeight.SemiBold,
                        color = noteColor.copy(alpha = alpha),
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("resonance.note")
                        .semantics {
                            contentDescription =
                                (if (state.mode == TunerMode.INSTRUMENT) "Target note" else "Detected note") +
                                    " ${label}${state.octave ?: ""}" +
                                    (if (live) "" else ", signal interrupted")
                            if (live) liveRegion = LiveRegionMode.Polite
                        },
                )
                if (accidental.isNotEmpty() || state.octave != null) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.height(noteSize.value.dp * 0.92f),
                    ) {
                        Text(
                            text = accidental,
                            style = TextStyle(
                                fontSize = (noteSize.value * 0.42f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = noteColor.copy(alpha = alpha),
                            ),
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Text(
                            text = state.octave ?: "",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = p.brass.copy(alpha = alpha),
                            ).tabularNumerals,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        } else {
            // Ghosted note placeholder — same grid, no fake glyph, no note tag.
            Text(
                text = "–",
                style = TextStyle(
                    fontSize = noteSize,
                    fontWeight = FontWeight.SemiBold,
                    color = p.noteIvory.copy(alpha = 0.35f),
                ),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun CenterColumn(
    state: MainScreenUiState,
    showing: Boolean,
    alpha: Float,
    onRetryCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showing) {
            state.frequencyText?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = p.freqCream.copy(alpha = alpha),
                    ).tabularNumerals,
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("resonance.frequency"),
                )
            }
        } else {
            // One calm prompt with a subtle pulsing dot (waiting / no signal).
            val pulse by rememberInfiniteTransition(label = "readout-pulse").animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "pulse-alpha",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clearAndSetSemantics {}
                        .drawBehind {
                            drawCircle(p.lampAmber.copy(alpha = pulse), radius = size.minDimension / 2f)
                        },
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = state.statusHint ?: "Play a string",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.noteIvory),
                    maxLines = 2,
                    modifier = Modifier
                        .testTag("resonance.hint")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (!state.captureActive) {
                Spacer(Modifier.height(6.dp))
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("resonance.start"),
                ) { Text("Start listening", style = InstrumentType.keyLabel) }
            }
        }
    }
}

@Composable
private fun CentsColumn(
    state: MainScreenUiState,
    centsSize: androidx.compose.ui.unit.TextUnit,
    showing: Boolean,
    live: Boolean,
    alpha: Float,
    centsDescription: String,
) {
    val p = InstrumentTheme.palette
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (showing) (state.centsText ?: "–") else "–",
                style = TextStyle(
                    fontSize = centsSize,
                    fontWeight = FontWeight.Bold,
                    color = if (showing) p.noteIvory.copy(alpha = alpha) else p.noteIvory.copy(alpha = 0.35f),
                ).tabularNumerals,
                maxLines = 1,
                modifier = Modifier
                    .testTag("resonance.cents")
                    .semantics { contentDescription = centsDescription },
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "cents",
                style = TextStyle(fontSize = 12.sp, color = p.taupe),
                maxLines = 1,
                modifier = Modifier
                    .padding(bottom = 3.dp)
                    .clearAndSetSemantics {},
            )
        }
        if (showing) {
            Spacer(Modifier.height(5.dp))
            val badge: String
            val badgeColor: Color
            when {
                state.inTune -> { badge = "✓"; badgeColor = p.emeraldGlow }
                state.directionText == "Tune up" -> { badge = "▲"; badgeColor = p.lampAmber }
                else -> { badge = "▼"; badgeColor = p.terracotta }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = badge,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = badgeColor.copy(alpha = alpha)),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = state.directionText ?: "—",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = badgeColor.copy(alpha = alpha)),
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("resonance.direction")
                        .semantics { if (live) liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}