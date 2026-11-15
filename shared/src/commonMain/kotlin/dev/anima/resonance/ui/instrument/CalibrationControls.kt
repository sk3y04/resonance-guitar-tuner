package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling

/**
 * Calibration hardware redesigned as one coherent control. A clear
 * "Reference pitch" label and the live value ("A4 · 440 Hz"), with the coarse
 * (±1 Hz) and fine (±0.1 Hz) steppers grouped and explicitly labelled so their
 * purpose is obvious. Tags, spoken descriptions, limits and callbacks are
 * identical to the tested contract.
 */
@Composable
fun CalibrationControls(
    concertValue: String,
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
        Text("Reference pitch", style = InstrumentType.engraved)
        Spacer(Modifier.size(4.dp))
        Text(
            text = concertValue,
            style = InstrumentType.readout.copy(fontSize = 22.sp),
            modifier = Modifier.semantics {
                contentDescription = "Reference pitch $concertValue"
            },
        )
        Spacer(Modifier.size(10.dp))
        if (largeText) {
            StepperGroup("±1 Hz", coarse = true, onStep = onConcertStepCoarse, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            StepperGroup("±0.1 Hz", coarse = false, onStep = onConcertStep, modifier = Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepperGroup("±1 Hz", coarse = true, onStep = onConcertStepCoarse, modifier = Modifier.weight(1f))
                StepperGroup("±0.1 Hz", coarse = false, onStep = onConcertStep, modifier = Modifier.weight(1f))
            }
        }
        concertError?.let { error ->
            Text(
                text = error,
                color = p.lampRed,
                style = InstrumentType.readoutSmall,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag("resonance.sheet.concertError"),
            )
        }
    }
}

/** A labelled pair of stepper keys (−step / +step) with comfortable targets. */
@Composable
private fun StepperGroup(
    label: String,
    coarse: Boolean,
    onStep: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = if (coarse) "1" else "0.1"
    val minusTag = if (coarse) "resonance.sheet.concert.coarseMinus" else "resonance.sheet.concert.minus"
    val plusTag = if (coarse) "resonance.sheet.concert.coarsePlus" else "resonance.sheet.concert.plus"
    Column(modifier = modifier) {
        Text(
            text = label,
            style = InstrumentType.engraved,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InstrumentKey(
                onClick = { onStep(false) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(minusTag)
                    .semantics { contentDescription = "Decrease A4 reference by $step hertz" },
            ) { Text("−$step", style = InstrumentType.keyLabel) }
            InstrumentKey(
                onClick = { onStep(true) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(plusTag)
                    .semantics { contentDescription = "Increase A4 reference by $step hertz" },
            ) { Text("+$step", style = InstrumentType.keyLabel) }
        }
    }
}

/**
 * Accidental display preference as a clearly labelled segmented choice
 * ("Sharps ♯" / "Flats ♭") in one recessed track. The selected option stands
 * proud with ivory text; selection is exposed semantically (never colour
 * alone). Identity of the pitch never changes — only the spelling.
 */
@Composable
fun SpellingKeys(
    selected: Spelling,
    onSpellingSelect: (Spelling) -> Unit,
    modifier: Modifier = Modifier,
) {
    RecessedPanel(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SpellingKey("Sharps ♯", "Sharp note names", Spelling.SHARP, selected, onSpellingSelect, "resonance.sheet.spelling.sharp", Modifier.weight(1f))
            SpellingKey("Flats ♭", "Flat note names", Spelling.FLAT, selected, onSpellingSelect, "resonance.sheet.spelling.flat", Modifier.weight(1f))
        }
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
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val isSelected = selected == value
    InstrumentKey(
        onClick = { onSpellingSelect(value) },
        selected = isSelected,
        modifier = modifier
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
            maxLines = 1,
        )
    }
}