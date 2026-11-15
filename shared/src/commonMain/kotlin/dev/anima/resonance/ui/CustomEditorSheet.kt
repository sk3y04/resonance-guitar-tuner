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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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

/**
 * Custom-tuning editor: create or edit a named tuning. Pure form
 * widget — all state lives in the parent (MainScreen); every interaction is
 * a callback, so headless semantics tests drive it deterministically.
 *
 * A row is one string: tap the note button to cycle the note name
 * (C→C#→…→B→C), tap the octave button to cycle the octave (1..6), tap ✕ to
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
    val largeText = LocalDensity.current.fontScale > 1.3f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.editor"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = if (state.isEdit) "Edit tuning" else "New tuning",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            TextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.editor.name"),
            )
            Spacer(Modifier.size(8.dp))
            // No nested scroll: the instrument screen itself scrolls. A clipped
            // 240 dp window made later strings hard to reach and hid most of a
            // six-string tuning behind an inner gesture.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.strings.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onNoteCycle(i, true) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.note")
                                .semantics {
                                    contentDescription =
                                        "String ${i + 1} note ${s.noteName}, tap to change"
                                },
                        ) { Text(s.noteName) }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(
                            onClick = { onOctaveCycle(i, true) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.octave")
                                .semantics {
                                    contentDescription =
                                        "String ${i + 1} octave ${s.octave}, tap to change"
                                },
                        ) { Text(s.octave.toString()) }
                        Spacer(Modifier.size(6.dp))
                        TextButton(
                            onClick = { onRemoveString(i) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.string.$i.remove")
                                .semantics { contentDescription = "Remove string ${i + 1}" },
                        ) { Text("✕") }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onAddString,
                        enabled = state.strings.size < CustomEditorUi.MAX_STRINGS,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.add"),
                    ) { Text("+ String") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.cancel"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Cancel") }
                        Button(
                            onClick = onSave,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("resonance.editor.save"),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text(if (state.isEdit) "Save" else "Create") }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onAddString,
                        enabled = state.strings.size < CustomEditorUi.MAX_STRINGS,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.add"),
                    ) { Text("+ String") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.cancel"),
                    ) { Text("Cancel") }
                    Spacer(Modifier.size(6.dp))
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.editor.save"),
                    ) { Text(if (state.isEdit) "Save" else "Create") }
                }
            }
            state.errors.forEach { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = ResonanceType.hint,
                    modifier = Modifier
                        .testTag("resonance.editor.error")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}
