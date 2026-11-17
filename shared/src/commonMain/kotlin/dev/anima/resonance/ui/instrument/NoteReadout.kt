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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.ReadoutCopy
import dev.anima.resonance.presentation.SignalPhase
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.tabularNumerals
import kotlin.math.abs

/**
 * The digital readout, re-hierarchised.
 *
 * Reading order on screen: the NOTE (dominant) with its octave; the signed CENTS
 * error in a stable tabular column; the ACTION ("Tune up" / "In tune"); and the
 * measured FREQUENCY as quiet secondary information under the note. A "target"
 * caption appears only when the note is a manual lock (the cents are then
 * relative to that target, which the old screen never said).
 *
 * Signal honesty: values render only while a reading is present — live
 * ([SignalPhase.TRACKING]) or briefly held ([SignalPhase.HOLDING], faded, with a
 * "held" caption). With no usable reading the same grid shows dimmed placeholders
 * and ONE guidance line, and "off scale" marks a reading beyond the dial.
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
    val reduced = LocalReducedMotion.current
    // State colours settle on the shared tokens; the DIGITS themselves are
    // never crossfaded — a measurement must read instantly and truthfully.
    val noteColor by animateColorAsState(
        targetValue = if (live && state.inTune) p.emeraldGlow else p.noteIvory,
        animationSpec = MotionTokens.selection(reduced),
        label = "readout-note-color",
    )
    val ambient by animateColorAsState(
        targetValue = if (live && state.inTune) p.bandGreen.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = MotionTokens.content(reduced),
        label = "readout-ambient",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
            // The readout repaints per pitch tick (text + ambient wash +
            // settled colours). Its own layer keeps that off the window root.
            .animationLayer()
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
            NoteColumn(
                state = state,
                noteSize = noteSize,
                noteColor = noteColor,
                showing = showing,
                live = live,
                alpha = alpha,
                onRetryCapture = onRetryCapture,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            CentsColumn(
                state = state,
                centsSize = centsSize,
                showing = showing,
                holding = holding,
                alpha = alpha,
                centsDescription = centsDescription,
            )
        }
    }
}

/**
 * Note (dominant) + octave, with the measured frequency and the note's role as
 * secondary lines. When there is no reading, the same slot carries the guidance
 * line so nothing jumps.
 */
@Composable
private fun NoteColumn(
    state: MainScreenUiState,
    noteSize: androidx.compose.ui.unit.TextUnit,
    noteColor: Color,
    showing: Boolean,
    live: Boolean,
    alpha: Float,
    onRetryCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Column(
        modifier = modifier,
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
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReadoutCopy.noteRole(state.mode, state.stringLocked)?.let { role ->
                    Text(
                        text = role,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = p.lampAmber),
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Spacer(Modifier.size(8.dp))
                }
                state.frequencyText?.let {
                    Text(
                        text = it,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = p.freqCream.copy(alpha = alpha),
                        ).tabularNumerals,
                        maxLines = 1,
                        modifier = Modifier.testTag("resonance.frequency"),
                    )
                }
            }
        } else {
            // Same grid, no fake glyph, no note tag.
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
            Spacer(Modifier.height(4.dp))
            GuidanceLine(state = state, alpha = alpha)
            if (!state.captureActive) {
                Spacer(Modifier.height(4.dp))
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("resonance.start"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                ) { Text("Start listening", style = InstrumentType.keyLabel.copy(fontSize = 13.sp)) }
            }
        }
    }
}

/** One calm prompt with a subtle pulsing dot (waiting / lost / held). */
@Composable
private fun GuidanceLine(state: MainScreenUiState, alpha: Float) {
    val p = InstrumentTheme.palette
    val reduced = LocalReducedMotion.current
    val pulse by rememberInfiniteTransition(label = "readout-pulse").animateFloat(
        initialValue = if (reduced) 0.7f else 0.35f,
        targetValue = if (reduced) 0.7f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clearAndSetSemantics {}
                // The pulse is an INFINITE per-frame animation. Without its own
                // render layer the alpha change invalidates the nearest layer —
                // the window root — so the whole screen is re-recorded 120x/s
                // (measured: 3.2 ms/frame at 1080x2400, gone once layered). The
                // enclosing readout layer would cover it, but the dot keeps its
                // own so the pulse never re-records the readout's text either.
                .animationLayer()
                .drawBehind {
                    drawCircle(p.lampAmber.copy(alpha = pulse * alpha), radius = size.minDimension / 2f)
                },
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = ReadoutCopy.guidance(state.signalPhase, state.statusHint),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.noteIvory),
            maxLines = 2,
            modifier = Modifier
                .testTag("resonance.hint")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Signed cents (stable tabular column), the action, then the qualifiers. */
@Composable
private fun CentsColumn(
    state: MainScreenUiState,
    centsSize: androidx.compose.ui.unit.TextUnit,
    showing: Boolean,
    holding: Boolean,
    alpha: Float,
    centsDescription: String,
) {
    val p = InstrumentTheme.palette
    val reduced = LocalReducedMotion.current
    val badgeTarget = when {
        state.inTune -> p.emeraldGlow
        state.directionText == "Tune up" -> p.lampAmber
        else -> p.terracotta
    }
    val badgeColor by animateColorAsState(
        targetValue = badgeTarget,
        animationSpec = MotionTokens.selection(reduced),
        label = "readout-badge",
    )
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
                // A stable right-anchored slot sized for sign + 3 digits,
                // so "-3" → "+12" → "0" never shifts the readout layout.
                textAlign = TextAlign.End,
                modifier = Modifier
                    .widthIn(min = (centsSize.value * 2.3f).dp)
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
            val badge: String = when {
                state.inTune -> "✓"
                state.directionText == "Tune up" -> "▲"
                else -> "▼"
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
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = badgeColor.copy(alpha = alpha)),
                    maxLines = 1,
                    // No live region here — direction flaps near the
                    // in-tune boundary and would announce repeatedly. The
                    // cents description carries the same state on focus.
                    modifier = Modifier.testTag("resonance.direction"),
                )
            }
            val qualifier = ReadoutCopy.retentionCaption(state.signalPhase)
                ?: ReadoutCopy.scaleCaption(state.meterClamped)
            qualifier?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    style = TextStyle(fontSize = 11.sp, color = if (holding) p.lampAmber else p.taupe),
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("resonance.readoutQualifier")
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}