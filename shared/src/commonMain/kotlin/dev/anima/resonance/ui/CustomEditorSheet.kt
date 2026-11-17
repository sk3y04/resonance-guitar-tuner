package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.anima.resonance.presentation.CustomEditorUi
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.RecessedPanel

/**
 * Custom-tuning editor (instrument restyle): create or edit a
 * named tuning. Pure form widget — all state lives in the parent
 * (MainScreen); every interaction is a callback, so headless semantics
 * tests drive it deterministically.
 *
 * A row is one string: tap the note key to cycle the note name
 * (C→C#→…→B→C), tap the octave key to cycle the octave (1..6), tap ✕ to
 * remove the string. "+ String" adds one (max 12, engine contract).
 *
 * Tags: resonance.editor, .name, .string.<i>.note, .string.<i>.octave,
 * .string.<i>.remove, .add, .save, .cancel, .error.
 */
@Composable
fun CustomEditorSheet(
    state: CustomEditorUiState,
    onNameChange: (String) -> Unit,
    onNoteCycle: (Int, Boolean) -> Unit,
    onOctaveCycle: (Int, Boolean) -> Unit,
    onRemoveString: (Int) -> Unit,
    onAddString: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val largeText = LocalDensity.current.fontScale > 1.3f
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    RecessedPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.editor"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = if (state.isEdit) "Edit tuning" else "New tuning",
                style = InstrumentType.engravedStrong,
                modifier = Modifier.semantics { heading() },
            )
            TextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                // Done dismisses the keyboard instead of leaving it over
                // the form's actions; the name field is the only text input.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = p.walnutDeep,
                    unfocusedContainerColor = p.walnutDeep,
                    focusedTextColor = p.ivory,
                    unfocusedTextColor = p.ivory,
                    cursorColor = p.brassLight,
                    focusedLabelColor = p.textMuted,
                    unfocusedLabelColor = p.textMuted,
                    focusedIndicatorColor = p.brass,
                    unfocusedIndicatorColor = p.brassDark,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.editor.name"),
            )
            Spacer(Modifier.size(8.dp))
            // No nested scroll: the instrument screen itself scrolls. A
            // clipped 240 dp window made later strings hard to reach and hid
            // most of a six-string tuning behind an inner gesture.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.strings.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InstrumentKey(
                            onClick = { onNoteCycle(i, true) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.note")
                                .semantics {
                                    contentDescription =
                                        "String ${i + 1} note ${s.noteName}, tap to change"
                                },
                        ) { Text(s.noteName, style = InstrumentType.keyLabel) }
                        Spacer(Modifier.size(6.dp))
                        InstrumentKey(
                            onClick = { onOctaveCycle(i, true) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.octave")
                                .semantics {
                                    contentDescription =
                                        "String ${i + 1} octave ${s.octave}, tap to change"
                                },
                        ) { Text(s.octave.toString(), style = InstrumentType.keyLabel) }
                        Spacer(Modifier.size(6.dp))
                        InstrumentKey(
                            onClick = { onRemoveString(i) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.remove")
                                .semantics { contentDescription = "Remove string ${i + 1}" },
                        ) { Text("✕", style = InstrumentType.keyLabel) }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InstrumentKey(
                        onClick = onAddString,
                        enabled = state.strings.size < CustomEditorUi.MAX_STRINGS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.add"),
                    ) { Text("+ String", style = InstrumentType.keyLabel) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InstrumentKey(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.cancel"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Cancel", style = InstrumentType.keyLabel) }
                        InstrumentKey(
                            onClick = onSave,
                            selected = true,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.save"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                if (state.isEdit) "Save" else "Create",
                                style = InstrumentType.keyLabel.copy(color = p.brassLight),
                            )
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InstrumentKey(
                        onClick = onAddString,
                        enabled = state.strings.size < CustomEditorUi.MAX_STRINGS,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.add"),
                    ) { Text("+ String", style = InstrumentType.keyLabel) }
                    Spacer(Modifier.weight(1f))
                    InstrumentKey(
                        onClick = onCancel,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.cancel"),
                    ) { Text("Cancel", style = InstrumentType.keyLabel) }
                    Spacer(Modifier.size(6.dp))
                    InstrumentKey(
                        onClick = onSave,
                        selected = true,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.save"),
                    ) {
                        Text(
                            if (state.isEdit) "Save" else "Create",
                            style = InstrumentType.keyLabel.copy(color = p.brassLight),
                        )
                    }
                }
            }
            state.errors.forEach { error ->
                Text(
                    text = error,
                    color = p.lampRed,
                    style = InstrumentType.readoutSmall,
                    modifier = Modifier
                        .testTag("resonance.editor.error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}