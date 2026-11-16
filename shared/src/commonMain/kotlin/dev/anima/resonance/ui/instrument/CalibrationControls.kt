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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.PresetSheetUi
import kotlin.math.roundToLong

/**
 * Calibration as ONE coherent control instead of four equal-priority
 * steppers. A compact row — A4 [−] [440.0 Hz] [+] — whose ± keys use the
 * explicitly selected step (1 Hz or 0.1 Hz). Tapping the value opens direct
 * numeric entry (locale-friendly, validated, never silently coerced). A Reset
 * to 440 Hz appears only when the reference differs. Tags/limits/callbacks of
 * the tested contract are preserved (minus/plus step by the selected amount).
 */
@Composable
fun CalibrationControls(
    concertHz: Double,
    concertError: String?,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    onConcertSet: (Double) -> Unit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    var coarse by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    fun step(up: Boolean) {
        if (coarse) onConcertStepCoarse(up) else onConcertStep(up)
    }

    fun applyEdit() {
        val parsed = PresetSheetUi.parseConcertPitch(editText)
        when {
            parsed == null -> editError = "Enter a number like 440.0"
            PresetSheetUi.concertPitchError(parsed) != null ->
                editError = PresetSheetUi.concertPitchError(parsed)
            else -> {
                onConcertSet(parsed)
                editing = false
                editError = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.sheet.concert"),
    ) {
        Text("Reference pitch", style = InstrumentType.engraved)
        Spacer(Modifier.size(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("A4", style = InstrumentType.engravedStrong)
            InstrumentKey(
                onClick = { step(false) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .testTag("resonance.sheet.concert.minus")
                    .semantics { contentDescription = "Decrease A4 reference by ${if (coarse) "1" else "0.1"} hertz" },
            ) { Text("−", style = InstrumentType.keyLabel.copy(fontSize = 18.sp)) }
            // The value is a button that opens direct entry (whole-area target).
            InstrumentKey(
                onClick = {
                    editing = !editing
                    editText = formatHz1(concertHz)
                    editError = null
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("resonance.sheet.concert.value")
                    .semantics { contentDescription = "Reference pitch ${formatHz1(concertHz)} hertz, tap to edit" },
            ) {
                Text(
                    text = "${formatHz1(concertHz)} Hz",
                    style = InstrumentType.readout.copy(fontSize = 20.sp),
                    maxLines = 1,
                )
            }
            InstrumentKey(
                onClick = { step(true) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .testTag("resonance.sheet.concert.plus")
                    .semantics { contentDescription = "Increase A4 reference by ${if (coarse) "1" else "0.1"} hertz" },
            ) { Text("+", style = InstrumentType.keyLabel.copy(fontSize = 18.sp)) }
        }
        Spacer(Modifier.size(8.dp))
        // Explicit step selection.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Step", style = InstrumentType.engraved)
            StepKey("1 Hz", coarse, { coarse = true }, "resonance.sheet.concert.step.coarse", Modifier.weight(1f))
            StepKey("0.1 Hz", !coarse, { coarse = false }, "resonance.sheet.concert.step.fine", Modifier.weight(1f))
        }
        if (editing) {
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it; editError = null },
                label = { Text("A4 (Hz)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = editError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.sheet.concert.input"),
            )
            editError?.let {
                Text(
                    text = it,
                    color = p.lampRed,
                    style = InstrumentType.readoutSmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("resonance.sheet.concert.editError"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { applyEdit() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.sheet.concert.apply"),
                ) { Text("Apply", color = p.brassLight) }
                TextButton(
                    onClick = { editing = false; editError = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.sheet.concert.cancel"),
                ) { Text("Cancel", color = p.textMuted) }
            }
        }
        // Reset is offered only when the reference differs from 440 Hz.
        if (concertHz != 440.0) {
            Spacer(Modifier.size(4.dp))
            TextButton(
                onClick = { onConcertSet(440.0) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("resonance.sheet.concert.reset"),
            ) { Text("Reset to 440 Hz", color = p.textMuted) }
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

@Composable
private fun StepKey(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    InstrumentKey(
        onClick = onClick,
        selected = selected,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = "Step $label"
                stateDescription = if (selected) "Selected" else "Not selected"
                this.selected = selected
            },
    ) {
        Text(
            text = label,
            style = InstrumentType.keyLabel.copy(color = if (selected) p.ivory else p.textMuted),
            maxLines = 1,
        )
    }
}

/** One-decimal Hz value for the calibration readout/edit field (440.0). */
private fun formatHz1(hz: Double): String {
    val tenths = (hz * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
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