package dev.anima.resonance.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.MainScreenUi
import dev.anima.resonance.presentation.PresetSheetUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * JVM-hosted Compose semantics tests. These verify the screen's
 * semantic contract (tags, state visibility) headlessly; they are NOT a
 * visual/pixel review — rendered screenshots remain blocked.
 */
class TunerScreenUiTest {

    @get:Rule
    val rule = createComposeRule()

    private fun state(
        status: TunerStatus,
        note: Note? = null,
        cents: Int = 0,
        permission: MicPermissionState = MicPermissionState.GRANTED,
        mode: TunerMode = TunerMode.CHROMATIC,
        preset: dev.anima.resonance.music.TuningPreset? = null,
        selected: Int? = null,
    ) = MainScreenUi.toUiState(
        snapshot = if (status == TunerStatus.LISTENING || status == TunerStatus.IDLE) null else
            TunerSnapshot(
                reading = dev.anima.resonance.presentation.TunerReading(
                    status, note, if (note == null) 0.0 else 82.4, cents,
                    if (cents < 0) dev.anima.resonance.presentation.PitchDirection.FLAT
                    else dev.anima.resonance.presentation.PitchDirection.SHARP,
                    false, 1.0,
                ),
                rms = 0.3,
                clipping = status == TunerStatus.CLIPPING,
                sampleRate = 48000,
            ),
        permission = permission,
        mode = mode,
        preset = preset,
        selectedStringIndex = selected,
    )

    private fun show(state: dev.anima.resonance.presentation.MainScreenUiState) {
        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = state,
                    onOpenSettings = {},
                    onOpenPresets = {},
                    onModeToggle = {},
                    onStringSelect = {},
                    onPermissionCta = {},
                    onRetryCapture = {},
                )
            }
        }
    }

    private fun custom(id: String, name: String, vararg labels: String) =
        dev.anima.resonance.music.TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l ->
                dev.anima.resonance.music.TuningString("$id-s${i + 1}", Note.fromLabel(l))
            },
        )

    @Test
    fun listeningStateShowsHintAndDialNotNote() {
        show(state(status = TunerStatus.LISTENING))
        rule.onNodeWithTag("resonance.dial").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
        rule.onNodeWithTag("resonance.note").assertDoesNotExist()
    }

    @Test
    fun flatE2InstrumentModeShowsNoteCentsAndStrings() {
        show(
            state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = 0,
            ),
        )
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.cents").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.0").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.5").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertDoesNotExist()
    }

    @Test
    fun chromaticModeHasNoStringNodes() {
        show(state(status = TunerStatus.MEASURED, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.0").assertDoesNotExist()
    }

    @Test
    fun permanentDenialShowsPermissionCtaOnly() {
        show(state(status = TunerStatus.LISTENING, permission = MicPermissionState.DENIED_PERMANENT))
        rule.onNodeWithTag("resonance.permission").assertIsDisplayed()

        show(state(status = TunerStatus.LISTENING, permission = MicPermissionState.GRANTED))
        rule.onNodeWithTag("resonance.permission").assertDoesNotExist()
    }

    @Test
    fun clippingStateStillShowsDial() {
        show(state(status = TunerStatus.CLIPPING, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.dial").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
    }

    @Test
    fun settingsSheetShowsRowsSelectionAndConcertError() {
        val c1 = custom("custom-1", "My Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val state = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            concertPitchHz = 399.0,
            presets = BuiltInTunings.all + c1,
            favoritePresetIds = setOf("standard"),
            selectedPresetId = "custom-1",
        )
        show(state)
        rule.onNodeWithTag("resonance.sheet").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.concert").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.concertError").assertIsDisplayed()
        // The custom row may sit below the sheet's fold — it must be
        // reachable by scrolling, and displayed once scrolled into view.
        rule.onNodeWithTag("resonance.sheet")
            .performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertIsDisplayed()
    }

    @Test
    fun sheetConcertSteppersInvokeCallback() {
        val calls = mutableListOf<Boolean>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(
                presets = BuiltInTunings.all,
                favorites = setOf("standard"),
                selectedId = "standard",
            ),
            concertLabel = "A4 = 440 Hz",
        )
        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = ui,
                    onOpenSettings = {},
                    onOpenPresets = {},
                    onModeToggle = {},
                    onStringSelect = {},
                    onPermissionCta = {},
                    onRetryCapture = {},
                    onConcertStep = { calls.add(it) },
                )
            }
        }
        rule.onNodeWithTag("resonance.sheet.concert.minus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        assertEquals(listOf(false, true, true), calls, "minus, then plus twice")
    }

    @Test
    fun editorRendersRowsAndControls() {
        val ui = CustomEditorUiState(
            name = "My Open D",
            strings = listOf(
                EditorStringUi("D", 2),
                EditorStringUi("F#", 3),
            ),
        )
        rule.setContent {
            ResonanceTheme {
                CustomEditorSheet(
                    state = ui,
                    onNameChange = {},
                    onNoteCycle = { _, _ -> },
                    onOctaveCycle = { _, _ -> },
                    onRemoveString = {},
                    onAddString = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        rule.onNodeWithTag("resonance.editor").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.name").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.string.0.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.string.1.octave").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.add").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.save").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.cancel").assertIsDisplayed()
    }

    @Test
    fun editorButtonsInvokeFormCallbacks() {
        val events = mutableListOf<String>()
        val ui = CustomEditorUiState(
            name = "X",
            strings = listOf(EditorStringUi("E", 2)),
        )
        rule.setContent {
            ResonanceTheme {
                CustomEditorSheet(
                    state = ui,
                    onNameChange = { events += "name:$it" },
                    onNoteCycle = { i, up -> events += "note:$i:$up" },
                    onOctaveCycle = { i, up -> events += "octave:$i:$up" },
                    onRemoveString = { i -> events += "remove:$i" },
                    onAddString = { events += "add" },
                    onSave = { events += "save" },
                    onCancel = { events += "cancel" },
                )
            }
        }
        rule.onNodeWithTag("resonance.editor.string.0.note").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.octave").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.remove").performClick()
        rule.onNodeWithTag("resonance.editor.add").performClick()
        rule.onNodeWithTag("resonance.editor.save").performClick()
        rule.onNodeWithTag("resonance.editor.cancel").performClick()
        assertEquals(
            listOf("note:0:true", "octave:0:true", "remove:0", "add", "save", "cancel"),
            events,
        )
    }

    @Test
    fun editorShowsValidationErrors() {
        val ui = CustomEditorUiState(name = "", strings = emptyList(), errors = listOf("Name is required"))
        rule.setContent {
            ResonanceTheme {
                CustomEditorSheet(
                    state = ui,
                    onNameChange = {},
                    onNoteCycle = { _, _ -> },
                    onOctaveCycle = { _, _ -> },
                    onRemoveString = {},
                    onAddString = {},
                    onSave = {},
                    onCancel = {},
                )
            }
        }
        rule.onNodeWithTag("resonance.editor.error").assertIsDisplayed()
    }

    @Test
    fun editorVisibleOnlyWhenStateProvided() {
        val ui = state(status = TunerStatus.LISTENING).copy(presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"))
        rule.setContent {
            ResonanceTheme {
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeToggle = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.editor").assertDoesNotExist()

        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = ui,
                    onOpenSettings = {}, onOpenPresets = {}, onModeToggle = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {},
                    editor = CustomEditorUiState(name = "X", strings = listOf(EditorStringUi("E", 2))),
                )
            }
        }
        rule.onNodeWithTag("resonance.editor").assertIsDisplayed()
    }

    @Test
    fun sheetExposesAddTuningAndCustomRowActions() {
        val customPreset = dev.anima.resonance.music.TuningPreset(
            id = "custom-1", name = "My Open D", builtIn = false,
            strings = listOf(
                dev.anima.resonance.music.TuningString("c1-s1", Note.fromLabel("D2")),
                dev.anima.resonance.music.TuningString("c1-s2", Note.fromLabel("A2")),
            ),
        )
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all + customPreset, setOf("standard"), "standard"),
        )
        rule.setContent {
            ResonanceTheme {
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeToggle = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.sheet").assertIsDisplayed()
        // custom rows + add button sit below the sheet's fold: the add button
        // is the sheet's last child — scrolling it into view (a 300dp window)
        // also brings the trailing custom row in, per the preset-sheet pattern.
        rule.onNodeWithTag("resonance.sheet")
            .performScrollToNode(hasTestTag("resonance.sheet.add"))
        rule.onNodeWithTag("resonance.sheet.add").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.edit").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.delete").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard.edit").assertDoesNotExist() // built-ins are read-only
    }

    @Test
    fun sheetSpellingControlInvokesCallback() {
        val calls = mutableListOf<dev.anima.resonance.music.Spelling>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = ui,
                    onOpenSettings = {}, onOpenPresets = {}, onModeToggle = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {},
                    onSpellingSelect = { calls.add(it) },
                )
            }
        }
        rule.onNodeWithTag("resonance.sheet.spelling.sharp").performClick()
        rule.onNodeWithTag("resonance.sheet.spelling.flat").performClick()
        assertEquals(
            listOf(dev.anima.resonance.music.Spelling.SHARP, dev.anima.resonance.music.Spelling.FLAT),
            calls,
        )
    }

    @Test
    fun sheetShowsImportResultText() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            importResultText = "Imported 2 tunings",
        )
        rule.setContent {
            ResonanceTheme {
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeToggle = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.sheet").performScrollToNode(hasTestTag("resonance.sheet.importResult"))
        rule.onNodeWithTag("resonance.sheet.importResult").assertIsDisplayed()
    }

    @Test
    fun presetSheetHiddenWithoutRows() {
        show(state(status = TunerStatus.LISTENING))
        rule.onNodeWithTag("resonance.sheet").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
    }
}
