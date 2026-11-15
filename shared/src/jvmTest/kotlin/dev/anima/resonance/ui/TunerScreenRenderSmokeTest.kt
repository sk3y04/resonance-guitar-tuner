package dev.anima.resonance.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.MainScreenUi
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.PresetSheetUi
import dev.anima.resonance.presentation.TunerMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Render smoke test + manual visual-audit artifact generator.
 *
 * CMP's desktop test jar has no `captureToImage`, but the public
 * `ImageComposeScene` renders real Skia frames headlessly. This test asserts
 * that representative states of the FIXED instrument and its configuration
 * overlay produce non-blank PNGs and writes them to `build/render/` for
 * inspection at realistic phone sizes.
 *
 * It is not a pixel-diff test: fonts and anti-aliasing may differ across
 * machines. Assertions are limited to dimensions and non-uniform output.
 */
class TunerScreenRenderSmokeTest {

    @Test
    fun representativeStatesRenderToNonBlankPngs() {
        val out = File("build/render").apply { mkdirs() }
        val states = listOf(
            // Main instrument — chromatic and instrument, fixed faceplate.
            RenderCase("dark-chromatic-main", 1080, 2400, 3f, dark = true, state = listening()),
            RenderCase(
                "dark-instrument-main", 1080, 2400, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT),
            ),
            RenderCase(
                "dark-in-tune", 1080, 2400, 3f, dark = true,
                state = measured("E2", 0, TunerMode.INSTRUMENT, inTune = true),
            ),
            RenderCase(
                "dark-clamped-sharp", 1080, 2400, 3f, dark = true,
                state = measured("E2", 120, TunerMode.INSTRUMENT),
            ),
            RenderCase(
                "dark-no-signal", 1080, 2400, 3f, dark = true,
                state = MainScreenUi.toUiState(
                    snapshot = dev.anima.resonance.presentation.TunerSnapshot(
                        reading = dev.anima.resonance.presentation.TunerReading(
                            dev.anima.resonance.presentation.TunerStatus.INSUFFICIENT_SIGNAL,
                            Note.fromLabel("E2"), 0.0, 0,
                            dev.anima.resonance.presentation.PitchDirection.FLAT,
                            false, 0.2,
                        ),
                        rms = 0.001,
                        clipping = false,
                        sampleRate = 48000,
                    ),
                    permission = MicPermissionState.GRANTED,
                    mode = TunerMode.INSTRUMENT,
                    preset = BuiltInTunings.standard,
                    selectedStringIndex = null,
                    captureActive = true,
                ),
            ),
            RenderCase(
                "light-instrument-main", 1080, 2400, 3f, dark = false,
                state = measured("E2", -3, TunerMode.INSTRUMENT),
            ),
            RenderCase(
                "dark-permission-denied", 1080, 2400, 3f, dark = true,
                state = listening(permission = MicPermissionState.DENIED_PERMANENT),
            ),
            // Small supported phone (360x640dp) — must stay fixed, unclipped.
            RenderCase(
                "dark-small-phone", 1080, 1920, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT),
            ),
            // Large accessibility font — explicit scrollable fallback.
            RenderCase(
                "dark-large-text-fallback", 960, 1440, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT),
                fontScale = 2f,
            ),
            // Configuration overlay over the instrument.
            RenderCase(
                "dark-overlay", 1080, 2400, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT, withSheet = true),
                overlay = true,
            ),
            RenderCase(
                "light-overlay", 1080, 2400, 3f, dark = false,
                state = measured("E2", -3, TunerMode.INSTRUMENT, withSheet = true),
                overlay = true,
            ),
            RenderCase(
                "dark-overlay-custom-tuning", 1080, 2400, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT, withSheet = true).copy(
                    presetRows = PresetSheetUi.toRows(
                        BuiltInTunings.all + customPreset(),
                        setOf("custom-1"),
                        "custom-1",
                    ),
                ),
                overlay = true,
            ),
            RenderCase(
                "dark-overlay-editor", 1080, 2400, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT, withSheet = true),
                overlay = true,
                editor = CustomEditorUiState(
                    name = "My Open D",
                    strings = listOf("D2", "A2", "D3", "F#3", "A3", "D4").map {
                        val note = Note.fromLabel(it)
                        EditorStringUi(it.dropLast(1), note.octave)
                    },
                ),
            ),
            RenderCase(
                "dark-overlay-large-text", 960, 2400, 3f, dark = true,
                state = measured("E2", -3, TunerMode.INSTRUMENT, withSheet = true),
                overlay = true,
                fontScale = 2f,
            ),
        )
        for (case in states) {
            val file = File(out, "${case.name}.png")
            render(case, file)
            val data = file.readBytes()
            assertTrue(data.size > 5_000, "${case.name}: suspiciously small PNG (${data.size} bytes)")
            assertTrue(
                data.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
                "${case.name}: not a PNG",
            )
        }
    }

    private fun render(case: RenderCase, file: File) {
        val scene = ImageComposeScene(
            width = case.widthPx,
            height = case.heightPx,
            density = Density(density = case.density, fontScale = case.fontScale),
            coroutineContext = Dispatchers.Unconfined,
        ) {
            ResonanceTheme(darkTheme = case.dark) {
                TunerScreen(
                    state = case.state,
                    onModeSelect = {},
                    onStringSelect = {},
                    onPermissionCta = {},
                    onRetryCapture = {},
                )
                if (case.overlay) {
                    TuningsOverlay(
                        state = case.state,
                        onClose = {},
                        editor = case.editor,
                    )
                }
            }
        }
        try {
            val image = scene.render(0L)
            val data = image.encodeToData(EncodedImageFormat.PNG)
            requireNotNull(data) { "${case.name}: PNG encoding failed" }
            file.writeBytes(data.bytes)
        } finally {
            scene.close()
        }
    }

    private fun listening(
        permission: MicPermissionState = MicPermissionState.GRANTED,
        withSheet: Boolean = false,
    ) = MainScreenUi.toUiState(
        snapshot = null,
        permission = permission,
        mode = TunerMode.CHROMATIC,
        preset = null,
        selectedStringIndex = null,
        presets = if (withSheet) BuiltInTunings.all else emptyList(),
        favoritePresetIds = if (withSheet) setOf("standard") else emptySet(),
        selectedPresetId = "standard",
        captureActive = permission == MicPermissionState.GRANTED,
    )

    private fun measured(
        note: String,
        cents: Int,
        mode: TunerMode,
        inTune: Boolean = false,
        withSheet: Boolean = false,
    ) =
        MainScreenUi.toUiState(
            snapshot = dev.anima.resonance.presentation.TunerSnapshot(
                reading = dev.anima.resonance.presentation.TunerReading(
                    status = dev.anima.resonance.presentation.TunerStatus.MEASURED,
                    note = Note.fromLabel(note),
                    frequencyHz = Note.fromLabel(note).frequency(440.0),
                    cents = cents,
                    direction = if (inTune) dev.anima.resonance.presentation.PitchDirection.INTUNE
                    else if (cents < 0) dev.anima.resonance.presentation.PitchDirection.FLAT
                    else dev.anima.resonance.presentation.PitchDirection.SHARP,
                    inTune = inTune,
                    confidence = 1.0,
                ),
                rms = 0.2,
                clipping = false,
                sampleRate = 48000,
            ),
            permission = MicPermissionState.GRANTED,
            mode = mode,
            preset = if (mode == TunerMode.INSTRUMENT) BuiltInTunings.standard else null,
            selectedStringIndex = null,
            presets = if (withSheet) BuiltInTunings.all else emptyList(),
            favoritePresetIds = if (withSheet) setOf("standard") else emptySet(),
            selectedPresetId = "standard",
            captureActive = true,
        )

    private data class RenderCase(
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val dark: Boolean,
        val state: MainScreenUiState,
        val fontScale: Float = 1f,
        val overlay: Boolean = false,
        val editor: CustomEditorUiState? = null,
    )

    private fun customPreset() = dev.anima.resonance.music.TuningPreset(
        id = "custom-1",
        name = "My Open D",
        builtIn = false,
        strings = listOf("D2", "A2", "D3", "F#3", "A3", "D4").mapIndexed { i, label ->
            dev.anima.resonance.music.TuningString("c1-s$i", Note.fromLabel(label))
        },
    )
}