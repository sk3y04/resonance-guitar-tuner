package dev.anima.resonance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.presentation.CustomEditorUi
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainScreen: the host composable — a [TunerController] in, a
 * rendered instrument screen out.
 *
 * The ticker re-reads the immutable UI state (the engine is pull-based and
 * the read is cheap); mutations go through the controller and refresh
 * immediately so taps never wait for the next tick. The platform shell
 * (Android Activity) starts/stops capture via the controller lifecycle;
 * [onPermissionCta] is the hook that opens system app settings.
 *
 * The inline settings/preset sheet replaces the old Settings and
 * Tunings buttons — they are hidden here (TunerScreen keeps them, tested,
 * for state-driven rendering).
 */
@Composable
fun MainScreen(
    controller: TunerController,
    modifier: Modifier = Modifier,
    refreshMs: Long = 50,
    onPermissionCta: () -> Unit = {},
    /** Open the document picker for import (Android shell). */
    onImportPicker: () -> Unit = {},
    /** Open the document picker for export (Android shell). */
    onExportPicker: () -> Unit = {},
    /**
     * A tuning-file payload delivered by the shell (the Android
     * document picker reads the file and hands the text over). Null =
     * nothing pending. Handled once, then [onImportConsumed] fires.
     */
    importPayload: String? = null,
    onImportConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var ui by remember(controller) { mutableStateOf(controller.state()) }
    // Last import/export result message (UI-local; the ticker merges
    // it into the mapped state so it survives refreshes).
    var importMessage by remember(controller) { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) {
        while (isActive) {
            ui = controller.state().copy(importResultText = importMessage)
            delay(refreshMs)
        }
    }

    fun refresh() {
        ui = controller.state().copy(importResultText = importMessage)
    }

    /**
     * Handles a tuning file payload (Android's document picker hands
     * it over). Result feedback renders in the sheet.
     */
    fun handleImportPayload(payload: String) {
        scope.launch {
            val result = controller.importCustomPresets(payload)
            importMessage = when {
                result.error != null -> "Import failed: ${result.error}"
                result.importedCount > 0 -> "Imported ${result.importedCount} tuning(s)"
                else -> "Import complete — no new tunings in file"
            }
            refresh()
        }
    }

    // The shell hands over a tuning-file payload (Android document
    // picker → text). Handle it exactly once, then signal consumption so
    // the shell can clear its state.
    LaunchedEffect(importPayload) {
        importPayload?.let { payload ->
            handleImportPayload(payload)
            onImportConsumed()
        }
    }

    // Custom-tuning editor form state (create: editingId = null).
    var editor by remember(controller) { mutableStateOf<CustomEditorUiState?>(null) }

    fun openEditor(id: String? = null) {
        val row = id?.let { uid -> ui.presetRows.firstOrNull { it.id == uid } }
        val strings = row?.stringsPreview
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { label ->
                Regex("([A-G]#?)(\\d)").find(label)?.let { m ->
                    EditorStringUi(m.groupValues[1], m.groupValues[2].toInt())
                }
            }
            ?: listOf(EditorStringUi("E", 2))
        editor = CustomEditorUiState(
            name = row?.name ?: "",
            strings = strings,
            editingId = id,
            isEdit = id != null,
        )
    }

    fun updateEditor(transform: (CustomEditorUiState) -> CustomEditorUiState) {
        editor = editor?.let {
            val next = transform(it)
            next.copy(errors = CustomEditorUi.validate(next.name, next.strings.map { s -> s.label }))
        }
    }

    fun saveEditor() {
        val form = editor ?: return
        // Re-validate on save: the form may be untouched since openEditor,
        // when the errors list was not (re)computed.
        val errors = CustomEditorUi.validate(form.name, form.strings.map { it.label })
        if (errors.isNotEmpty()) {
            editor = form.copy(errors = errors)
            return
        }
        val notes = CustomEditorUi.toNotes(form.strings.map { it.label })
        scope.launch {
            val target = form.editingId
            if (target != null) controller.editCustomPreset(target, form.name, notes)
            else controller.createCustomPreset(form.name, notes)
            editor = null
            refresh()
        }
    }

    TunerScreen(
        state = ui,
        onOpenSettings = {},
        onOpenPresets = {},
        onModeToggle = {
            val next = if (ui.mode == TunerMode.CHROMATIC) TunerMode.INSTRUMENT else TunerMode.CHROMATIC
            scope.launch {
                controller.setMode(next)
                refresh()
            }
        },
        onStringSelect = { index ->
            controller.selectString(index)
            refresh()
        },
        onPermissionCta = onPermissionCta,
        onRetryCapture = {
            scope.launch {
                controller.retryCapture()
                refresh()
            }
        },
        onPresetSelect = { id ->
            scope.launch {
                controller.setPreset(id)
                refresh()
            }
        },
        onConcertStep = { up ->
            scope.launch {
                controller.stepConcertPitch(up)
                refresh()
            }
        },
        onAddTuning = { openEditor() },
        onEditTuning = { openEditor(it) },
        onDeleteTuning = { id ->
            scope.launch {
                controller.deleteCustomPreset(id)
                refresh()
            }
        },
        onSpellingSelect = { spelling ->
            scope.launch {
                controller.setAccidentalSpelling(spelling)
                refresh()
            }
        },
        onImportRequested = onImportPicker,
        onExportRequested = onExportPicker,
        editor = editor,
        onEditorNameChange = { name -> updateEditor { it.copy(name = name) } },
        onEditorNoteCycle = { index, up ->
            updateEditor { form ->
                form.copy(strings = form.strings.mapIndexed { i, s ->
                    if (i == index) s.copy(noteName = CustomEditorUi.nextNoteName(s.noteName, up)) else s
                })
            }
        },
        onEditorOctaveCycle = { index, up ->
            updateEditor { form ->
                form.copy(strings = form.strings.mapIndexed { i, s ->
                    if (i == index) s.copy(octave = CustomEditorUi.nextOctave(s.octave, up)) else s
                })
            }
        },
        onEditorRemoveString = { index ->
            updateEditor { form ->
                form.copy(strings = form.strings.filterIndexed { i, _ -> i != index })
            }
        },
        onEditorAddString = {
            updateEditor { form ->
                if (form.strings.size < CustomEditorUi.MAX_STRINGS) {
                    form.copy(strings = form.strings + EditorStringUi("E", 2))
                } else form
            }
        },
        onEditorSave = { saveEditor() },
        onEditorCancel = { editor = null },
        showSettingsButton = false,
        showPresetsButton = false,
        modifier = modifier,
    )
}
