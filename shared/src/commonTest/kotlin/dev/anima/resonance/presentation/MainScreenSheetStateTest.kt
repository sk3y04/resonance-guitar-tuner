package dev.anima.resonance.presentation

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sheet inputs (preset list, favorites, selection, concert pitch)
 * must flow into MainScreenUiState through the same pure mapper.
 */
class MainScreenSheetStateTest {

    private fun custom(id: String, name: String, vararg labels: String): TuningPreset =
        TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l -> TuningString("$id-s${i + 1}", Note.fromLabel(l)) },
        )

    @Test
    fun sheetInputsProduceRowsAndConcertLabel() {
        val c1 = custom("custom-1", "My Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val state = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            concertPitchHz = 442.0,
            presets = BuiltInTunings.all + c1,
            favoritePresetIds = setOf("standard", "custom-1"),
            selectedPresetId = "custom-1",
        )
        assertEquals(BuiltInTunings.all.size + 1, state.presetRows.size)
        val selected = state.presetRows.first { it.id == "custom-1" }
        assertTrue(selected.selected && selected.favorite && !selected.builtIn)
        val standard = state.presetRows.first { it.id == "standard" }
        assertTrue(standard.favorite && standard.builtIn && !standard.selected)
        assertEquals("A4 = 442 Hz", state.concertLabel)
        assertNull(state.concertError)
    }

    @Test
    fun outOfRangeConcertSurfacesError() {
        val state = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            concertPitchHz = 399.0,
        )
        assertEquals(399.0, state.concertPitchHz)
        assertTrue(state.concertError != null)
        assertEquals("A4 = 399 Hz", state.concertLabel)
    }

    @Test
    fun defaultsWithoutSheetInputs() {
        val state = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertTrue(state.presetRows.isEmpty())
        assertEquals("A4 = 440 Hz", state.concertLabel)
        assertNull(state.concertError)
        assertFalse(state.permissionCtaVisible)
    }
}
