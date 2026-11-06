package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.strings.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onNoteCycle(i, true) },
                            modifier = Modifier.testTag("resonance.editor.string.$i.note"),
                        ) { Text(s.noteName) }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(
                            onClick = { onOctaveCycle(i, true) },
                            modifier = Modifier.testTag("resonance.editor.string.$i.octave"),
                        ) { Text(s.octave.toString()) }
                        Spacer(Modifier.size(6.dp))
                        TextButton(
                            onClick = { onRemoveString(i) },
                            modifier = Modifier.testTag("resonance.editor.string.$i.remove"),
                        ) { Text("✕") }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onAddString,
                    enabled = state.strings.size < CustomEditorUi.MAX_STRINGS,
                    modifier = Modifier.testTag("resonance.editor.add"),
                ) { Text("+ String") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("resonance.editor.cancel"),
                ) { Text("Cancel") }
                Spacer(Modifier.size(6.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.testTag("resonance.editor.save"),
                ) { Text(if (state.isEdit) "Save" else "Create") }
            }
            state.errors.forEach { error ->
                Text(
                    text = error,
                    color = ResonanceColors.error,
                    style = ResonanceType.hint,
                    modifier = Modifier.testTag("resonance.editor.error"),
                )
            }
        }
    }
}
