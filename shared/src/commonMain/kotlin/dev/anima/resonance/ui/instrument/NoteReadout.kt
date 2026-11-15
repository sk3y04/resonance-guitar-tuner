package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.ResonanceType
import kotlin.math.abs

/**
 * The note + measurement readout — one compact recessed strip adjoining
 * the meter. While tracking: large note + octave, signed cents, frequency and
 * the jewel status. With no reliable reading: ONE short primary message
 * ("Play a note" / "Signal lost — play again" / …) plus intentional dash
 * placeholders — never a fake zero, never a "NO SIGNAL" repeated over the
 * meter face. The semantic contract (tags + spoken descriptions) is unchanged.
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
    val tracking = state.meterActive
    val centsDescription = when {
        !tracking || state.centsText == null -> "No current reading"
        state.inTune -> "In tune"
        state.directionText == "Tune up" ->
            "${abs(state.centsText.toIntOrNull() ?: 0)} cents flat, tune up"
        else ->
            "${abs(state.centsText.toIntOrNull() ?: 0)} cents sharp, tune down"
    }

    RecessedPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Detected note + octave. A muted dash placeholder keeps the strip
            // stable when there is no reliable note (never a fake glyph).
            Box(
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.noteLabel != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = state.noteLabel,
                            style = ResonanceType.noteGlyph.copy(
                                fontSize = noteSize,
                                color = p.ivory,
                            ),
                            maxLines = 1,
                            modifier = Modifier
                                .testTag("resonance.note")
                                .semantics {
                                    contentDescription =
                                        (if (state.mode == TunerMode.INSTRUMENT) {
                                            "Target note"
                                        } else {
                                            "Detected note"
                                        }) + " ${state.noteLabel}${state.octave ?: ""}"
                                    liveRegion = LiveRegionMode.Polite
                                },
                        )
                        state.octave?.let {
                            Text(
                                text = it,
                                style = ResonanceType.octave.copy(
                                    fontSize = (noteSize.value * 0.38f).sp,
                                    color = p.textMuted,
                                ),
                                modifier = Modifier
                                    .padding(start = 3.dp, bottom = (noteSize.value * 0.10f).dp)
                                    .clearAndSetSemantics {},
                            )
                        }
                    }
                } else {
                    Text(
                        text = "—",
                        style = ResonanceType.noteGlyph.copy(
                            fontSize = noteSize,
                            color = p.textMuted.copy(alpha = 0.55f),
                        ),
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tracking) {
                    // Signed cents — the primary numeric feedback.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = state.centsText ?: "—",
                            style = ResonanceType.cents.copy(fontSize = centsSize, color = p.ivory),
                            modifier = Modifier
                                .testTag("resonance.cents")
                                .semantics { contentDescription = centsDescription },
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "cents",
                            style = InstrumentType.engraved,
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .clearAndSetSemantics {},
                        )
                    }
                    if (largeText) {
                        state.frequencyText?.let {
                            Text(it, style = InstrumentType.readoutSmall, modifier = Modifier.testTag("resonance.frequency"))
                        }
                        TuningStatusIndicator(state)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.frequencyText?.let {
                                Text(it, style = InstrumentType.readoutSmall, modifier = Modifier.testTag("resonance.frequency"))
                                Spacer(Modifier.size(10.dp))
                            }
                            TuningStatusIndicator(state)
                        }
                    }
                    // A secondary coaching hint only when tracking needs it
                    // (e.g. "Hold the note steady", far-from-target).
                    state.statusHint?.let { hint ->
                        Text(
                            text = hint,
                            style = InstrumentType.readoutSmall,
                            modifier = Modifier
                                .testTag("resonance.hint")
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                } else {
                    // No reliable reading: ONE short primary message, then a
                    // muted dash placeholder for cents. No fake measurement.
                    Text(
                        text = state.statusHint ?: "Play a note",
                        style = InstrumentType.keyLabel.copy(fontSize = 16.sp, color = p.ivory),
                        maxLines = 2,
                        modifier = Modifier
                            .testTag("resonance.hint")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        text = "—",
                        style = ResonanceType.cents.copy(
                            fontSize = centsSize,
                            color = p.textMuted.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier
                            .testTag("resonance.cents")
                            .semantics { contentDescription = centsDescription },
                    )
                    if (!state.captureActive) {
                        InstrumentKey(
                            onClick = onRetryCapture,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.start"),
                        ) { Text("Start listening", style = InstrumentType.keyLabel) }
                    }
                }
            }
        }
    }
}

/**
 * Jewel lamp + direction text while tracking. Color is never the only
 * carrier: the lamp is paired with a word (In tune / Tune up / Tune down).
 * The exact direction text keeps the tested `resonance.direction` tag and
 * polite live region.
 */
@Composable
fun TuningStatusIndicator(
    state: MainScreenUiState,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val lampColor = if (state.inTune) p.lampGreen else p.lampAmber
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JewelLamp(lit = true, color = lampColor, size = 11.dp)
        Spacer(Modifier.size(7.dp))
        Text(
            text = state.directionText ?: "—",
            style = InstrumentType.keyLabel.copy(
                fontSize = 15.sp,
                color = if (state.inTune) p.lampGreen else p.ivory,
            ),
            modifier = Modifier
                .testTag("resonance.direction")
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}