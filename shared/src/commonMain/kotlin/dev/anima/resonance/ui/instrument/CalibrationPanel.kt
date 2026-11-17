package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.PresetSheetUi
import kotlin.math.roundToLong

/**
 * Calibration as one focused tool.
 *
 * "A4 = 440.0 Hz" is a single value between unmistakable − / + steppers (tap the
 * value for exact entry). The adjustment increment is its own labelled segmented
 * control ("Adjustment step") in the same control language as everything else.
 * Reset appears only when the reference is not 440 Hz. All tested tags and
 * callbacks are unchanged.
 */
@Composable
fun CalibrationControls(
    concertHz: Double,
    concertError: String?,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    onConcertSet: (Double) -> Unit,
    @Suppress("UNUSED_PARAMETER") largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    var coarse by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // TextFieldValue so opening exact entry SELECTS the whole current
    // value — typing replaces it instead of appending (the classic numeric
    // entry contract; appending produced "442.5440.2" on the emulator).
    var editText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var editError by remember { mutableStateOf<String?>(null) }
    // Exact entry opens focused with the numeric keyboard up, applies on
    // IME Done, and dismisses the keyboard on apply/cancel — the confirmation
    // actions are never left hidden behind the IME.
    val editFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun step(up: Boolean) {
        if (coarse) onConcertStepCoarse(up) else onConcertStep(up)
    }

    fun applyEdit() {
        val parsed = PresetSheetUi.parseConcertPitch(editText.text)
        when {
            parsed == null -> editError = "Enter a number like 440.0"
            PresetSheetUi.concertPitchError(parsed) != null ->
                editError = PresetSheetUi.concertPitchError(parsed)
            else -> {
                onConcertSet(parsed)
                editing = false
                editError = null
                keyboard?.hide()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(editing) {
        if (editing) editFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.sheet.concert"),
    ) {
        Text("Reference pitch", style = InstrumentType.label)
        Spacer(Modifier.height(InstrumentTokens.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InstrumentTokens.space2),
        ) {
            StepperKey(
                glyph = "−",
                description = "Lower the reference by ${if (coarse) "1" else "0.1"} hertz",
                onClick = { step(false) },
                tag = "resonance.sheet.concert.minus",
            )
            Surface(
                onClick = {
                    editing = !editing
                    val current = formatHz1(concertHz)
                    editText = androidx.compose.ui.text.input.TextFieldValue(
                        text = current,
                        selection = androidx.compose.ui.text.TextRange(0, current.length),
                    )
                    editError = null
                    if (!editing) keyboard?.hide()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = InstrumentTokens.touchMin)
                    .testTag("resonance.sheet.concert.value")
                    .semantics {
                        contentDescription =
                            "Reference pitch A4 equals ${formatHz1(concertHz)} hertz. Tap to enter an exact value."
                    },
                shape = RoundedCornerShape(InstrumentTokens.cornerMd),
                color = p.segmentCap,
                contentColor = p.textPrimary,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InstrumentTokens.space2, vertical = InstrumentTokens.space2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "A4 = ${formatHz1(concertHz)} Hz",
                        style = InstrumentType.numeric,
                        maxLines = 1,
                    )
                }
            }
            StepperKey(
                glyph = "+",
                description = "Raise the reference by ${if (coarse) "1" else "0.1"} hertz",
                onClick = { step(true) },
                tag = "resonance.sheet.concert.plus",
            )
        }
        if (editing) {
            Spacer(Modifier.height(InstrumentTokens.space3))
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it; editError = null },
                label = { Text("Exact A4 frequency (Hz)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { applyEdit() },
                ),
                isError = editError != null,
                // Warm instrument colours — never the petrol Material fallback.
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = p.textPrimary,
                    unfocusedTextColor = p.textPrimary,
                    focusedContainerColor = p.deckDeep,
                    unfocusedContainerColor = p.deckDeep,
                    cursorColor = p.brassLight,
                    focusedBorderColor = p.brass,
                    unfocusedBorderColor = p.deckEdge,
                    focusedLabelColor = p.textSecondary,
                    unfocusedLabelColor = p.textSecondary,
                    errorBorderColor = p.lampRed,
                    errorLabelColor = p.lampRed,
                    errorCursorColor = p.lampRed,
                    errorSupportingTextColor = p.lampRed,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editFocus)
                    .testTag("resonance.sheet.concert.input"),
            )
            editError?.let {
                Text(
                    text = it,
                    color = p.lampRed,
                    style = InstrumentType.body,
                    modifier = Modifier
                        .padding(top = InstrumentTokens.space1)
                        .testTag("resonance.sheet.concert.editError"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(InstrumentTokens.space2)) {
                TextButton(
                    onClick = { applyEdit() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.sheet.concert.apply"),
                ) { Text("Apply", color = p.accent, style = InstrumentType.label) }
                TextButton(
                    onClick = { editing = false; editError = null; keyboard?.hide() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.sheet.concert.cancel"),
                ) { Text("Cancel", color = p.textSecondary, style = InstrumentType.label) }
            }
        }
        Spacer(Modifier.height(InstrumentTokens.sectionGap))
        Text("Adjustment step", style = InstrumentType.label)
        Spacer(Modifier.height(InstrumentTokens.space2))
        SegmentTrack(modifier = Modifier.fillMaxWidth()) {
            InstrumentSegment(
                label = "1 Hz",
                selected = coarse,
                onClick = { coarse = true },
                tag = "resonance.sheet.concert.step.coarse",
                description = "Adjust by 1 hertz",
                role = Role.RadioButton,
            )
            InstrumentSegment(
                label = "0.1 Hz",
                selected = !coarse,
                onClick = { coarse = false },
                tag = "resonance.sheet.concert.step.fine",
                description = "Adjust by 0.1 hertz",
                role = Role.RadioButton,
            )
        }
        if (concertHz != 440.0) {
            Spacer(Modifier.height(InstrumentTokens.space3))
            TextButton(
                onClick = { onConcertSet(440.0) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("resonance.sheet.concert.reset"),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) { Text("Reset to 440 Hz", color = p.accent, style = InstrumentType.label) }
        }
        concertError?.let { error ->
            Spacer(Modifier.height(InstrumentTokens.space2))
            Text(
                text = error,
                color = p.lampRed,
                style = InstrumentType.body,
                modifier = Modifier.testTag("resonance.sheet.concertError"),
            )
        }
    }
}

/**
 * Accidental display as a labelled three-way segmented choice — Naturals /
 * Sharps ♯ / Flats ♭. All three reflect real engine states, so one option is
 * always selected; the selection is a fill, never colour alone.
 */
@Composable
fun SpellingKeys(
    selected: Spelling,
    onSpellingSelect: (Spelling) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentTrack(modifier = modifier) {
        InstrumentSegment(
            label = "Naturals",
            selected = selected == Spelling.NATURAL,
            onClick = { onSpellingSelect(Spelling.NATURAL) },
            tag = "resonance.sheet.spelling.natural",
            description = "Natural note names",
            role = Role.RadioButton,
        )
        InstrumentSegment(
            label = "Sharps ♯",
            selected = selected == Spelling.SHARP,
            onClick = { onSpellingSelect(Spelling.SHARP) },
            tag = "resonance.sheet.spelling.sharp",
            description = "Sharp note names",
            role = Role.RadioButton,
        )
        InstrumentSegment(
            label = "Flats ♭",
            selected = selected == Spelling.FLAT,
            onClick = { onSpellingSelect(Spelling.FLAT) },
            tag = "resonance.sheet.spelling.flat",
            description = "Flat note names",
            role = Role.RadioButton,
        )
    }
}

/** One-decimal Hz value for the calibration readout/edit field (440.0). */
private fun formatHz1(hz: Double): String {
    val tenths = (hz * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10}"
}