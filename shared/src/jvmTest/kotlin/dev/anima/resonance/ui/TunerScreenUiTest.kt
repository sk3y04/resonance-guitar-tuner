package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    private fun show(
        state: dev.anima.resonance.presentation.MainScreenUiState,
        onModeSelect: (TunerMode) -> Unit = {},
        onStringSelect: (Int) -> Unit = {},
        onPermissionCta: () -> Unit = {},
        onRequestPermission: () -> Unit = {},
        onToggleFavorite: (String) -> Unit = {},
        onPresetSelect: (String) -> Unit = {},
        onConcertStep: (Boolean) -> Unit = {},
        onConcertStepCoarse: (Boolean) -> Unit = {},
        onRetryCapture: () -> Unit = {},
    ) {
        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = state,
                    onOpenSettings = {},
                    onOpenPresets = {},
                    onModeSelect = onModeSelect,
                    onStringSelect = onStringSelect,
                    onPermissionCta = onPermissionCta,
                    onRequestPermission = onRequestPermission,
                    onToggleFavorite = onToggleFavorite,
                    onPresetSelect = onPresetSelect,
                    onConcertStep = onConcertStep,
                    onConcertStepCoarse = onConcertStepCoarse,
                    onRetryCapture = onRetryCapture,
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
        // The instrument surface scrolls as one page; settings live below
        // the primary reading. Scroll the root (not a nested sheet).
        val scroll = rule.onNodeWithTag("resonance.scroll")
        scroll.performScrollToNode(hasTestTag("resonance.sheet.concertError"))
        rule.onNodeWithTag("resonance.sheet.concertError").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.sheet.row.standard"))
        rule.onNodeWithTag("resonance.sheet.row.standard").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.title").assertExists()
    }

    @Test
    fun tuningsShortcutJumpsToTheBelowFoldSheet() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertLabel = "A4 = 440 Hz",
        )
        show(ui)
        rule.onNodeWithTag("resonance.openTunings").assertIsDisplayed()
        rule.onNodeWithTag("resonance.openTunings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("resonance.sheet.title").assertIsDisplayed()
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
                    onModeSelect = {},
                    onStringSelect = {},
                    onPermissionCta = {},
                    onRetryCapture = {},
                    onConcertStep = { calls.add(it) },
                )
            }
        }
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.concert.plus"))
        rule.onNodeWithTag("resonance.sheet.concert.minus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        assertEquals(listOf(false, true, true), calls, "minus, then plus twice")
    }

    @Test
    fun coarseConcertSteppersInvokeTheOneHertzCallback() {
        val calls = mutableListOf<Boolean>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertLabel = "A4 = 440 Hz",
        )
        show(ui, onConcertStepCoarse = { calls += it })
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.concert.coarsePlus"))
        rule.onNodeWithTag("resonance.sheet.concert.coarseMinus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.coarsePlus").performClick()
        assertEquals(listOf(false, true), calls)
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
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeSelect = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.editor").assertDoesNotExist()

        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = ui,
                    onOpenSettings = {}, onOpenPresets = {}, onModeSelect = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {},
                    editor = CustomEditorUiState(name = "X", strings = listOf(EditorStringUi("E", 2))),
                )
            }
        }
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.editor"))
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
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeSelect = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.scroll")
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
                    onOpenSettings = {}, onOpenPresets = {}, onModeSelect = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {},
                    onSpellingSelect = { calls.add(it) },
                )
            }
        }
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.spelling.sharp"))
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
                TunerScreen(state = ui, onOpenSettings = {}, onOpenPresets = {}, onModeSelect = {}, onStringSelect = {}, onPermissionCta = {}, onRetryCapture = {})
            }
        }
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.importResult"))
        rule.onNodeWithTag("resonance.sheet.importResult").assertIsDisplayed()
    }

    @Test
    fun presetSheetHiddenWithoutRows() {
        show(state(status = TunerStatus.LISTENING))
        rule.onNodeWithTag("resonance.sheet").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
    }

    @Test
    fun stringTargetsAreClickableAndExposeSelectionState() {
        val clicks = mutableListOf<Int>()
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = 0,
            ),
            onStringSelect = { clicks += it },
        )
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.string.2"))
        rule.onNodeWithTag("resonance.string.0").assertIsSelected()
        rule.onNodeWithTag("resonance.string.2").performClick()
        assertEquals(listOf(2), clicks, "tapping the third string must lock index 2")
        rule.onNodeWithTag("resonance.string.2")
            .assertContentDescriptionEquals("String 3, D3")
    }

    @Test
    fun autoDetectedStringIsVisibleAndSemanticallyDifferentFromALock() {
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = null,
            ),
        )
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.string.0"))
        rule.onNodeWithTag("resonance.note")
            .assertContentDescriptionEquals("Target note E2")
        rule.onNodeWithTag("resonance.string.0")
            .assertContentDescriptionEquals("String 1, E2, detected")
        // AUTO is an observation, not a user selection.
        rule.onNodeWithTag("resonance.string.0").assertIsNotSelected()
    }

    @Test
    fun favoriteToggleIsARealNamedControl() {
        val clicks = mutableListOf<String>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, emptySet(), "standard"),
        )
        show(ui, onToggleFavorite = { clicks += it })
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.row.standard.favorite"))
        val favorite = rule.onNodeWithTag("resonance.sheet.row.standard.favorite")
        favorite.assertContentDescriptionEquals("Add Standard to favorites")
        favorite.performClick()
        assertEquals(listOf("standard"), clicks)
    }

    @Test
    fun softDenialCtaRequestsPermissionWhilePermanentDenialOpensSettings() {
        val requests = mutableListOf<String>()
        show(
            state = state(status = TunerStatus.LISTENING, permission = MicPermissionState.DENIED),
            onPermissionCta = { requests += "settings" },
            onRequestPermission = { requests += "request" },
        )
        rule.onNodeWithTag("resonance.permission").performClick()
        assertEquals(listOf("request"), requests)

        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = state(
                        status = TunerStatus.LISTENING,
                        permission = MicPermissionState.DENIED_PERMANENT,
                    ),
                    onOpenSettings = {},
                    onOpenPresets = {},
                    onModeSelect = {},
                    onStringSelect = {},
                    onPermissionCta = { requests += "settings" },
                    onRequestPermission = { requests += "request" },
                    onRetryCapture = {},
                )
            }
        }
        rule.onNodeWithTag("resonance.permission").performClick()
        assertEquals(listOf("request", "settings"), requests)
    }

    @Test
    fun modeOptionsExposeAndInvokeExplicitChoices() {
        val selected = mutableListOf<TunerMode>()
        show(
            state = state(status = TunerStatus.LISTENING, mode = TunerMode.CHROMATIC),
            onModeSelect = { selected += it },
        )
        rule.onNodeWithTag("resonance.scroll")
            .performScrollToNode(hasTestTag("resonance.mode.instrument"))
        rule.onNodeWithTag("resonance.mode.chromatic").assertIsSelected()
        rule.onNodeWithTag("resonance.mode.instrument").performClick()
        assertEquals(listOf(TunerMode.INSTRUMENT), selected)
    }

    @Test
    fun spokenReadingCombinesNoteAndOctaveAndKeepsSignMeaning() {
        show(state(status = TunerStatus.MEASURED, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.note")
            .assertContentDescriptionEquals("Detected note E2")
        rule.onNodeWithTag("resonance.cents")
            .assertContentDescriptionEquals("3 cents flat, tune up")

        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("A4"),
                cents = 0,
            ).copy(inTune = true, directionText = "In tune"),
        )
        rule.onNodeWithTag("resonance.cents")
            .assertContentDescriptionEquals("In tune")
    }

    @Test
    fun smallScreenLargeTextKeepsEveryPrimaryControlReachable() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                ResonanceTheme {
                    Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                        TunerScreen(
                            state = state(
                                status = TunerStatus.MEASURED,
                                note = Note.fromLabel("E2"),
                                cents = -3,
                                mode = TunerMode.INSTRUMENT,
                                preset = BuiltInTunings.standard,
                                selected = 0,
                            ).copy(
                                presetRows = PresetSheetUi.toRows(
                                    BuiltInTunings.all,
                                    setOf("standard"),
                                    "standard",
                                ),
                            ),
                            onOpenSettings = {},
                            onOpenPresets = {},
                            onModeSelect = {},
                            onStringSelect = {},
                            onPermissionCta = {},
                            onRetryCapture = {},
                        )
                    }
                }
            }
        }
        val scroll = rule.onNodeWithTag("resonance.scroll")
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.mode.instrument"))
        rule.onNodeWithTag("resonance.mode.instrument").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.string.5"))
        rule.onNodeWithTag("resonance.string.5").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.sheet.add"))
        rule.onNodeWithTag("resonance.sheet.add").assertIsDisplayed()
    }

    @Test
    fun sheetToggleCollapsesAndReExpandsSecondaryControls() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertLabel = "A4 = 440 Hz",
        )
        show(ui)
        val scroll = rule.onNodeWithTag("resonance.scroll")
        scroll.performScrollToNode(hasTestTag("resonance.sheet.toggle"))
        rule.onNodeWithTag("resonance.sheet.toggle").performClick()
        rule.waitForIdle()
        // Collapsed: secondary configuration leaves the tree entirely.
        rule.onNodeWithTag("resonance.sheet.concert").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.toggle").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.toggle").performClick()
        rule.waitForIdle()
        scroll.performScrollToNode(hasTestTag("resonance.sheet.concert"))
        rule.onNodeWithTag("resonance.sheet.concert").assertExists()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertExists()
    }

    @Test
    fun readingSurfaceNeverShowsAZeroHzOrCenteredZeroForWeakSignal() {
        val weak = MainScreenUi.toUiState(
            snapshot = TunerSnapshot(
                reading = dev.anima.resonance.presentation.TunerReading(
                    TunerStatus.INSUFFICIENT_SIGNAL,
                    Note.fromLabel("E2"),
                    0.0,
                    0,
                    dev.anima.resonance.presentation.PitchDirection.FLAT,
                    false,
                    0.2,
                ),
                rms = 0.001,
                clipping = false,
                sampleRate = 48000,
            ),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            captureActive = true,
        )
        show(weak)
        rule.onNodeWithTag("resonance.note").assertDoesNotExist()
        rule.onNodeWithTag("resonance.frequency").assertDoesNotExist()
        rule.onNodeWithTag("resonance.cents").assertContentDescriptionEquals("No current reading")
        rule.onNodeWithTag("resonance.direction").assertDoesNotExist()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
    }
}
