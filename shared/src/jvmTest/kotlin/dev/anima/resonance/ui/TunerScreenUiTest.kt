package dev.anima.resonance.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.MainScreenUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStatus
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
}
