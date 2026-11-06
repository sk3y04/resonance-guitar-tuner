package dev.anima.resonance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.TunerMode
import kotlin.math.roundToInt

/**
 * Main instrument screen. Renders the tested [MainScreenUiState]
 * Original precision-instrument aesthetic: large note
 * glyph, partial-arc dial, signed cents, FLAT/SHARP context, string row,
 * mode toggle, and real permission/settings controls — no placeholders.
 *
 * Semantic/test tags: resonance.note, resonance.cents, resonance.dial,
 * resonance.hint, resonance.mode, resonance.string.<i>, resonance.settings,
 * resonance.concert, resonance.permission, resonance.preset.
 */
@Composable
fun TunerScreen(
    state: MainScreenUiState,
    onOpenSettings: () -> Unit,
    onOpenPresets: () -> Unit,
    onModeToggle: () -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onConcertStep: (Boolean) -> Unit = {},
    onPresetSelect: (String) -> Unit = {},
    onAddTuning: () -> Unit = {},
    onEditTuning: (String) -> Unit = {},
    onDeleteTuning: (String) -> Unit = {},
    onSpellingSelect: (dev.anima.resonance.music.Spelling) -> Unit = {},
    editor: CustomEditorUiState? = null,
    onEditorNameChange: (String) -> Unit = {},
    onEditorNoteCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorOctaveCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorRemoveString: (Int) -> Unit = {},
    onEditorAddString: () -> Unit = {},
    onEditorSave: () -> Unit = {},
    onEditorCancel: () -> Unit = {},
    showSettingsButton: Boolean = true,
    showPresetsButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: preset / mode name + settings + concert pitch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.presetName ?: "Chromatic",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("resonance.preset"),
            )
            if (showSettingsButton) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("resonance.settings"),
                    contentPadding = PaddingValues(12.dp),
                ) { Text("Settings") }
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = "A4 ${state.concertPitchHz.toIntIfWholeOrOneDecimal()} Hz",
                style = ResonanceType.labelSmall,
                modifier = Modifier.testTag("resonance.concert"),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Built-in microphone",
            style = ResonanceType.hint,
            modifier = Modifier.align(Alignment.Start),
        )

        Spacer(Modifier.height(24.dp))

        // Note + octave + frequency
        if (state.noteLabel != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.noteLabel,
                    style = ResonanceType.noteGlyph,
                    modifier = Modifier
                        .testTag("resonance.note")
                        .semantics {
                            contentDescription = "Detected note ${state.noteLabel}${state.octave ?: ""}"
                        },
                )
                state.octave?.let {
                    Text(
                        text = it,
                        style = ResonanceType.octave,
                        modifier = Modifier.padding(bottom = 28.dp),
                    )
                }
            }
            Text(
                text = state.frequencyText ?: "",
                style = ResonanceType.frequency,
                modifier = Modifier.testTag("resonance.frequency"),
            )
        } else {
            Spacer(Modifier.size(96.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Dial with FLAT / SHARP context
        CentsDial(
            position = state.meterPosition,
            inTune = state.inTune,
            clamped = state.meterClamped,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FLAT", style = ResonanceType.labelSmall)
            Text(
                text = state.centsText?.let { "$it cents" } ?: "—",
                style = ResonanceType.cents,
                modifier = Modifier
                    .testTag("resonance.cents")
                    .semantics {
                        contentDescription = "${state.centsText ?: "no"} cents ${state.directionText?.lowercase() ?: "measuring"}"
                    },
            )
            Text("SHARP", style = ResonanceType.labelSmall)
        }
        Text(
            text = state.directionText ?: "",
            style = ResonanceType.direction,
        )

        // Status hint (listening / clipping / stale / error)
        if (state.statusHint != null) {
            Text(
                text = state.statusHint,
                style = ResonanceType.hint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("resonance.hint"),
            )
        }
        if (state.captureError) {
            Button(
                onClick = onRetryCapture,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("resonance.retry"),
            ) { Text("Retry microphone") }
        }

        Spacer(Modifier.height(16.dp))

        // String row (instrument mode) or nothing (chromatic)
        if (state.targetLabels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.targetLabels.forEachIndexed { i, label ->
                    val selected = state.selectedStringIndex == i
                    val color = if (selected) ResonanceColors.accent
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resonance.string.$i")
                            .semantics {
                                contentDescription = "String ${i + 1}: $label" +
                                    (if (selected) ", selected" else "")
                            }
                            .let { m -> if (selected) m.background(ResonanceColors.accent.copy(alpha = 0.18f)) else m },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = ResonanceType.labelSmall.copy(
                                    color = color,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mode toggle
        Row(
            modifier = Modifier.testTag("resonance.mode"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onModeToggle) {
                Text(if (state.mode == TunerMode.CHROMATIC) "Instrument" else "Chromatic")
            }
        }

        // Permission CTA — only on permanent denial (never re-prompts)
        if (state.permissionCtaVisible) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPermissionCta,
                modifier = Modifier.testTag("resonance.permission"),
            ) { Text(state.permissionCtaText) }
        }

        Spacer(Modifier.height(8.dp))

        // Settings/preset sheet — rows + concert pitch (tested state)
        // Custom-tuning editor: rendered above the sheet while open.
        editor?.let { form ->
            CustomEditorSheet(
                state = form,
                onNameChange = onEditorNameChange,
                onNoteCycle = onEditorNoteCycle,
                onOctaveCycle = onEditorOctaveCycle,
                onRemoveString = onEditorRemoveString,
                onAddString = onEditorAddString,
                onSave = onEditorSave,
                onCancel = onEditorCancel,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (state.presetRows.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("resonance.sheet"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resonance.sheet.concert"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.concertLabel,
                            style = ResonanceType.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onConcertStep(false) },
                            modifier = Modifier.testTag("resonance.sheet.concert.minus"),
                        ) { Text("−") }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(
                            onClick = { onConcertStep(true) },
                            modifier = Modifier.testTag("resonance.sheet.concert.plus"),
                        ) { Text("+") }
                        state.concertError?.let { error ->
                            Text(
                                text = error,
                                color = ResonanceColors.error,
                                style = ResonanceType.hint,
                                modifier = Modifier.testTag("resonance.sheet.concertError"),
                            )
                        }
                    }
                    // Enharmonic display preference (♯ / ♭)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("resonance.sheet.spelling"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Accidentals",
                            style = ResonanceType.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.accidentalSpelling == dev.anima.resonance.music.Spelling.SHARP) {
                            Button(
                                onClick = { onSpellingSelect(dev.anima.resonance.music.Spelling.SHARP) },
                                modifier = Modifier.testTag("resonance.sheet.spelling.sharp"),
                            ) { Text("♯") }
                        } else {
                            OutlinedButton(
                                onClick = { onSpellingSelect(dev.anima.resonance.music.Spelling.SHARP) },
                                modifier = Modifier.testTag("resonance.sheet.spelling.sharp"),
                            ) { Text("♯") }
                        }
                        Spacer(Modifier.size(6.dp))
                        if (state.accidentalSpelling == dev.anima.resonance.music.Spelling.FLAT) {
                            Button(
                                onClick = { onSpellingSelect(dev.anima.resonance.music.Spelling.FLAT) },
                                modifier = Modifier.testTag("resonance.sheet.spelling.flat"),
                            ) { Text("♭") }
                        } else {
                            OutlinedButton(
                                onClick = { onSpellingSelect(dev.anima.resonance.music.Spelling.FLAT) },
                                modifier = Modifier.testTag("resonance.sheet.spelling.flat"),
                            ) { Text("♭") }
                        }
                    }
                    state.presetRows.forEach { row ->
                        Surface(
                            onClick = { onPresetSelect(row.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .testTag("resonance.sheet.row.${row.id}")
                                .semantics {
                                    contentDescription = "Tuning ${row.name}" +
                                        (if (row.selected) ", selected" else "") +
                                        (if (row.favorite) ", favorite" else "")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (row.selected) ResonanceColors.accent.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = row.name,
                                        style = ResonanceType.labelSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (row.favorite) {
                                        Text(
                                            text = "★",
                                            color = ResonanceColors.accent,
                                            style = ResonanceType.labelSmall,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    if (row.selected) {
                                        Text(
                                            text = "●",
                                            color = ResonanceColors.accent,
                                            style = ResonanceType.labelSmall,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = row.stringsPreview,
                                        style = ResonanceType.hint,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!row.builtIn) {
                                        TextButton(
                                            onClick = { onEditTuning(row.id) },
                                            modifier = Modifier.testTag("resonance.sheet.row.${row.id}.edit"),
                                            contentPadding = PaddingValues(6.dp),
                                        ) { Text("✎") }
                                        TextButton(
                                            onClick = { onDeleteTuning(row.id) },
                                            modifier = Modifier.testTag("resonance.sheet.row.${row.id}.delete"),
                                            contentPadding = PaddingValues(6.dp),
                                        ) { Text("✕") }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onAddTuning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("resonance.sheet.add"),
                    ) { Text("＋ New tuning") }
                }
            }
        }

        if (showPresetsButton) {
            OutlinedButton(
                onClick = onOpenPresets,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.presets"),
            ) { Text("Tunings") }
        }
    }
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
