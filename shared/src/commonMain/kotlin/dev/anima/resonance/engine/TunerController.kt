package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.audio.CaptureState
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.SettingsRepository
import dev.anima.resonance.presentation.MainScreenUi
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode

/**
 * TunerController: the common, Compose-free bridge between the
 * [AppTuner] engine and the screen's [MainScreenUiState].
 *
 * Owns the engine (so the capture-state callback can never be forgotten),
 * gates capture on the microphone permission, and surfaces capture errors.
 * The AndroidX ViewModel is a thin shell around this type; everything here
 * is host-testable with fake audio and a virtual clock.
 */
class TunerController(
    input: AudioInput,
    settingsRepo: SettingsRepository,
    private val presets: PresetStore,
) {
    private var capturing = false
    private var captureError = false
    private var permission: MicPermissionState = MicPermissionState.NOT_REQUESTED

    private val engine = AppTuner(
        input = input,
        settingsRepo = settingsRepo,
        presets = presets,
        onCaptureState = { s ->
            if (s == CaptureState.ERROR) captureError = true
            if (s == CaptureState.IDLE) capturing = false
        },
    )

    /** Live capture state (mirrors the engine; the Android pump thread uses it). */
    val captureState: CaptureState get() = engine.captureState

    /** Records the permission result from the platform layer. */
    fun onPermissionResult(state: MicPermissionState) {
        permission = state
    }

    /** Opens capture. Denied permission never touches the mic. */
    suspend fun startCapture() {
        if (permission != MicPermissionState.GRANTED) return
        capturing = true
        captureError = false
        engine.start()
        if (engine.captureState == CaptureState.ERROR) {
            capturing = false
            captureError = true
        }
    }

    /** Stops capture and clears the screen reading. Idempotent. */
    suspend fun stopCapture() {
        engine.stop()
        capturing = false
        captureError = false
    }

    /** User-driven retry after a capture error: fresh stop + start. */
    suspend fun retryCapture() {
        engine.stop()
        startCapture()
    }

    /** One pump step (called from the platform audio feed). */
    fun pump(nowMs: Long) {
        engine.poll(nowMs)
    }

    /** The complete, immutable screen state, mapped through [MainScreenUi]. */
    fun state(): MainScreenUiState {
        val settings = engine.currentSettings
        val instrument = settings.mode == TunerMode.INSTRUMENT
        return MainScreenUi.toUiState(
            snapshot = if (capturing) engine.lastSnapshot else null,
            permission = permission,
            mode = settings.mode,
            preset = if (instrument) presets.byId(settings.selectedPresetId) else null,
            selectedStringIndex = if (instrument) engine.lockedString else null,
            concertPitchHz = settings.concertPitchHz,
            captureError = captureError,
            presets = presets.all(),
            favoritePresetIds = presets.favorites().toSet(),
            selectedPresetId = settings.selectedPresetId,
        )
    }

    suspend fun setMode(mode: TunerMode) {
        engine.setMode(mode)
    }

    suspend fun setPreset(id: String) {
        engine.setPreset(id)
    }

    fun selectString(index: Int) {
        engine.selectString(index)
    }

    suspend fun setConcertPitch(hz: Double) {
        engine.setConcertPitch(hz)
    }

    suspend fun toggleFavorite(id: String): Boolean = engine.toggleFavorite(id)
}
