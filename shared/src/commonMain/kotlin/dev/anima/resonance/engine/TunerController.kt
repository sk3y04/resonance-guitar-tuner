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
    private var permission: MicPermissionState = MicPermissionState.NOT_REQUESTED

    /**
     * The engine owns the UI capture flags (capturingUi /
     * captureErrorUi), written under its lock by the pump side, so the main
     * thread reads them in [state] with a happens-before guarantee.
     */
    private val engine = AppTuner(
        input = input,
        settingsRepo = settingsRepo,
        presets = presets,
        persistCustoms = { customs -> settingsRepo.saveCustomPresets(customs) },
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
        // The engine sets/clears the UI capture flags under its own lock.
        engine.start()
    }

    /** Stops capture and clears the screen reading. Idempotent. */
    suspend fun stopCapture() {
        engine.stop()
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
            snapshot = if (engine.capturingUi) engine.lastSnapshot else null,
            permission = permission,
            mode = settings.mode,
            preset = if (instrument) presets.byId(settings.selectedPresetId) else null,
            selectedStringIndex = if (instrument) engine.lockedString else null,
            concertPitchHz = settings.concertPitchHz,
            captureError = engine.captureErrorUi,
            presets = presets.all(),
            favoritePresetIds = presets.favorites().toSet(),
            selectedPresetId = settings.selectedPresetId,
            accidentalSpelling = settings.accidentalSpelling,
        )
    }

    suspend fun setMode(mode: TunerMode) {
        engine.setMode(mode)
    }

    suspend fun setPreset(id: String) {
        engine.setPreset(id)
    }

    /** Creates a custom named tuning (spec §2); persists durably. */
    suspend fun createCustomPreset(name: String, notes: List<dev.anima.resonance.music.Note>): dev.anima.resonance.music.TuningPreset =
        engine.createCustomPreset(name, notes)

    /** Edits a custom tuning (built-ins are read-only). */
    suspend fun editCustomPreset(id: String, name: String, notes: List<dev.anima.resonance.music.Note>) {
        engine.editCustomPreset(id, name, notes)
    }

    /** Deletes a custom tuning; selection falls back to "standard". */
    suspend fun deleteCustomPreset(id: String): Boolean = engine.deleteCustomPreset(id)

    fun selectString(index: Int) {
        engine.selectString(index)
    }

    suspend fun setConcertPitch(hz: Double) {
        engine.setConcertPitch(hz)
    }

    /** Steps the A4 reference by ±0.1 Hz (spec §2); clamped at 400..480. */
    suspend fun stepConcertPitch(up: Boolean) {
        engine.stepConcertPitch(up)
    }

    /** Sets the enharmonic display preference (display-only, persisted). */
    suspend fun setAccidentalSpelling(spelling: dev.anima.resonance.music.Spelling) {
        engine.setAccidentalSpelling(spelling)
    }

    /**
     * Imports tunings from a versioned JSON payload (the Android
     * document picker hands the controller the file contents).
     */
    suspend fun importCustomPresets(payload: String): ImportResult =
        engine.importCustomPresets(payload)

    /** Versioned JSON of all custom tunings (for the export picker). */
    fun exportCustomPresets(): String = engine.exportCustomPresets()

    suspend fun toggleFavorite(id: String): Boolean = engine.toggleFavorite(id)
}
