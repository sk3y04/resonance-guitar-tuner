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
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.ResonanceType
import kotlin.math.abs

/**
 * The note + measurement readout — one compact recessed instrument
 * strip, not a card. Note + octave on the left; signed cents, frequency
 * and the jewel status on the right. Height is driven by the glyphs with
 * a small floor, so the no-signal state never leaves a dead zone. The
 * semantic contract (tags and spoken descriptions) is unchanged.
 */
@Composable
fun NoteReadout(
    state: MainScreenUiState,
    noteSize: androidx.compose.ui.unit.TextUnit,
    centsSize: androidx.compose.ui.unit.TextUnit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val p = InstrumentTheme.palette
    val centsDescription = when {
        !state.meterActive || state.centsText == null -> "No current reading"
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
            // Detected note + octave (reserved width keeps the strip stable
            // between readings; empty when there is no reliable note).
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
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Signed cents — the primary numeric feedback.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (state.meterActive && state.centsText != null) {
                            state.centsText
                        } else {
                            // A muted dash — never a zero that could read as
                            // a valid "0 cents" measurement.
                            "—"
                        },
                        style = ResonanceType.cents.copy(
                            fontSize = centsSize,
                            color = if (state.meterActive && state.centsText != null) {
                                p.ivory
                            } else {
                                p.textMuted
                            },
                        ),
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
                // Frequency + tuning status share one line normally; at
                // large font scales they stack so nothing clips.
                if (largeText) {
                    state.frequencyText?.let {
                        Text(
                            text = it,
                            style = InstrumentType.readoutSmall,
                            modifier = Modifier.testTag("resonance.frequency"),
                        )
                    }
                    TuningStatusIndicator(state)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.frequencyText?.let {
                            Text(
                                text = it,
                                style = InstrumentType.readoutSmall,
                                modifier = Modifier.testTag("resonance.frequency"),
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        TuningStatusIndicator(state)
                    }
                }
            }
        }
    }
}

/**
 * Jewel lamp + text state. Color is never the only carrier: the lamp is
 * always paired with a word (In tune / Tune up / NO SIGNAL / MIC OFF…).
 * When the state provides a direction, that exact text keeps the tested
 * `resonance.direction` tag and polite live region.
 */
@Composable
fun TuningStatusIndicator(
    state: MainScreenUiState,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val granted = state.permission == MicPermissionState.GRANTED
    val lampColor: androidx.compose.ui.graphics.Color
    val lit: Boolean
    val fallbackWord: String
    when {
        state.captureError -> {
            lampColor = p.lampRed; lit = true; fallbackWord = "MIC ERROR"
        }
        !granted || !state.captureActive -> {
            lampColor = p.lampOff; lit = false; fallbackWord = "MIC OFF"
        }
        !state.meterActive -> {
            lampColor = p.lampAmber; lit = true; fallbackWord = "NO SIGNAL"
        }
        state.inTune -> {
            lampColor = p.lampGreen; lit = true; fallbackWord = "IN TUNE"
        }
        else -> {
            lampColor = p.lampAmber; lit = true; fallbackWord = "OUT OF TUNE"
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JewelLamp(lit = lit, color = lampColor, size = 11.dp)
        Spacer(Modifier.size(7.dp))
        val direction = state.directionText
        if (direction != null) {
            Text(
                text = direction,
                style = InstrumentType.keyLabel.copy(
                    fontSize = 15.sp,
                    color = if (state.inTune) p.lampGreen else p.ivory,
                ),
                modifier = Modifier
                    .testTag("resonance.direction")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Text(
                text = fallbackWord,
                style = InstrumentType.engraved,
            )
        }
    }
}