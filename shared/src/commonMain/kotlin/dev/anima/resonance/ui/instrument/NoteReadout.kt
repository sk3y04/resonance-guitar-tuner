package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.ResonanceType
import kotlin.math.abs

/**
 * The note + measurement readout on a recessed panel, and the jewel
 * status indicator. The detected note is the strongest textual element;
 * frequency, signed cents and direction appear only when the tested state
 * actually provides them (never fabricated). Semantic contract (tags and
 * spoken descriptions) is identical to the pre-redesign screen.
 */
@Composable
fun NoteReadout(
    state: MainScreenUiState,
    noteSize: androidx.compose.ui.unit.TextUnit,
    centsSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 380.dp
            val noteBlock: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.heightIn(min = 96.dp),
                    contentAlignment = if (narrow) Alignment.Center else Alignment.CenterStart,
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
                                    style = ResonanceType.octave.copy(color = p.textMuted),
                                    modifier = Modifier
                                        .padding(start = 4.dp, bottom = 12.dp)
                                        .clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                }
            }
            val freqBlock: @Composable () -> Unit = {
                state.frequencyText?.let {
                    Text(
                        text = it,
                        style = InstrumentType.readoutSmall,
                        modifier = Modifier.testTag("resonance.frequency"),
                    )
                }
            }
            val centsBlock: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.meterActive && state.centsText != null) {
                        Text(
                            text = state.centsText,
                            style = ResonanceType.cents.copy(
                                fontSize = centsSize,
                                color = p.ivory,
                            ),
                            modifier = Modifier
                                .testTag("resonance.cents")
                                .semantics { contentDescription = centsDescription },
                        )
                    } else {
                        // A muted dash — never a giant zero that could read
                        // as a valid "0 cents" measurement.
                        Text(
                            text = "—",
                            style = ResonanceType.cents.copy(
                                fontSize = centsSize,
                                color = p.textMuted,
                            ),
                            modifier = Modifier
                                .testTag("resonance.cents")
                                .semantics { contentDescription = centsDescription },
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "cents",
                        style = InstrumentType.engraved,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            val statusBlock: @Composable () -> Unit = {
                TuningStatusIndicator(state)
            }

            if (narrow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    noteBlock()
                    freqBlock()
                    centsBlock()
                    statusBlock()
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        noteBlock()
                        freqBlock()
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        centsBlock()
                        statusBlock()
                    }
                }
            }
        }
    }
}

/**
 * Jewel lamp + text state. Color is never the only carrier: the lamp is
 * always paired with a word (IN TUNE / Tune up / NO SIGNAL / MIC OFF…).
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
        JewelLamp(lit = lit, color = lampColor, size = 12.dp)
        Spacer(Modifier.size(8.dp))
        val direction = state.directionText
        if (direction != null) {
            Text(
                text = direction,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (state.inTune) p.lampGreen else p.ivory,
                ),
                textAlign = TextAlign.Center,
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