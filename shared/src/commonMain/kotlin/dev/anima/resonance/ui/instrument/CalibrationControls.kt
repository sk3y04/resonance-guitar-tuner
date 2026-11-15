package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.anima.resonance.music.Spelling

/**
 * Calibration hardware — the A4 reference steppers (coarse ±1 Hz and
 * fine ±0.1 Hz) and the accidental-spelling keys, styled as tactile keys.
 * Tags, spoken descriptions and callbacks are identical to the tested
 * sheet controls.
 */
@Composable
fun CalibrationControls(
    concertLabel: String,
    concertError: String?,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.sheet.concert"),
    ) {
        if (largeText) {
            Text(concertLabel, style = InstrumentType.readoutSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) { ConcertKeys(onConcertStepCoarse, coarse = true) }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = concertLabel,
                    style = InstrumentType.readoutSmall,
                    modifier = Modifier.weight(1f),
                )
                ConcertKeys(onConcertStepCoarse, coarse = true)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) { ConcertKeys(onConcertStep, coarse = false) }
    }
    concertError?.let { error ->
        Text(
            text = error,
            color = p.lampRed,
            style = InstrumentType.readoutSmall,
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag("resonance.sheet.concertError"),
        )
    }
}

@Composable
private fun ConcertKeys(onStep: (Boolean) -> Unit, coarse: Boolean) {
    val step = if (coarse) "1" else "0.1"
    val minusTag = if (coarse) "resonance.sheet.concert.coarseMinus" else "resonance.sheet.concert.minus"
    val plusTag = if (coarse) "resonance.sheet.concert.coarsePlus" else "resonance.sheet.concert.plus"
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InstrumentKey(
            onClick = { onStep(false) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(minusTag)
                .semantics {
                    contentDescription = "Decrease A4 reference by $step hertz"
                },
        ) { Text("−$step", style = InstrumentType.keyLabel) }
        InstrumentKey(
            onClick = { onStep(true) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(plusTag)
                .semantics {
                    contentDescription = "Increase A4 reference by $step hertz"
                },
        ) { Text("+$step", style = InstrumentType.keyLabel) }
    }
}

/** ♯ / ♭ display-preference keys (identity of the pitch never changes). */
@Composable
fun SpellingKeys(
    selected: Spelling,
    onSpellingSelect: (Spelling) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpellingKey("♯", "Sharp note names", Spelling.SHARP, selected, onSpellingSelect, "resonance.sheet.spelling.sharp")
        SpellingKey("♭", "Flat note names", Spelling.FLAT, selected, onSpellingSelect, "resonance.sheet.spelling.flat")
    }
}

@Composable
private fun SpellingKey(
    label: String,
    description: String,
    value: Spelling,
    selected: Spelling,
    onSpellingSelect: (Spelling) -> Unit,
    tag: String,
) {
    val p = InstrumentTheme.palette
    val isSelected = selected == value
    InstrumentKey(
        onClick = { onSpellingSelect(value) },
        selected = isSelected,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = description
                stateDescription = if (isSelected) "Selected" else "Not selected"
                this.selected = isSelected
            },
    ) {
        Text(
            text = label,
            style = InstrumentType.keyLabel.copy(
                color = if (isSelected) p.ivory else p.textMuted,
            ),
        )
    }
}