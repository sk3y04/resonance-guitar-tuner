package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.audio.CaptureOrchestrator
import dev.anima.resonance.audio.CaptureState
import dev.anima.resonance.dsp.StreamingAnalyzer
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.persistence.SettingsRepository
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine

/**
 * AppTuner: the single deterministic engine behind the UI.
 *
 * Owns the [CaptureOrchestrator] (device lifecycle + bounded recovery),
 * the [StreamingAnalyzer] (frames → YIN → state machine), the [PresetStore]
 * (targets) and the [SettingsRepository] (persisted user choices). Time and
 * pumping are caller-driven ([poll]) — no threads, fully host-testable.
 * The production Android feed calls [poll] from the AudioRecord loop.
 *
 * All persisted mutations go through [settingsRepo] and re-apply the target
 * immediately, so the UI and the state machine never disagree about
 * mode/preset/concert pitch.
 */
class AppTuner(
    private val input: AudioInput,
    private val settingsRepo: SettingsRepository,
    private val presets: PresetStore,
    private val onSnapshot: (TunerSnapshot) -> Unit = {},
    private val onCaptureState: (CaptureState) -> Unit = {},
) {
    private val stateMachine = TunerStateMachine()
    private val analyzer = StreamingAnalyzer(input.sampleRate, stateMachine = stateMachine)

    private val orchestrator = CaptureOrchestrator(
        input = input,
        onChunk = { chunk, nowMs ->
            analyzer.processChunk(chunk, nowMs)?.let {
                lastSnapshot = it
                onSnapshot(it)
            }
        },
        onState = onCaptureState,
    )

    /** Last persisted settings applied to the state machine. */
    private var applied: Settings = Settings.DEFAULTS

    /** Most recent analyzed frame; null before the first frame or after stop. */
    var lastSnapshot: TunerSnapshot? = null
        private set

    /** Currently selected string index within the instrument preset. */
    var selectedStringIndex: Int = 0
        private set

    /** Live capture state (mirrors the orchestrator). */
    val captureState: CaptureState get() = orchestrator.state

    /**
     * Loads persisted settings, applies concert pitch / mode / preset to the
     * state machine, and opens capture. Returns the applied settings.
     */
    suspend fun start(): Settings {
        applied = settingsRepo.get()
        stateMachine.setConcertPitch(applied.concertPitchHz)
        applyTarget(applied)
        orchestrator.start()
        return applied
    }

    /** Stops capture and clears the displayed reading. Idempotent. */
    fun stop() {
        orchestrator.stop()
        lastSnapshot = null
    }

    /** One pump step at virtual time [nowMs] (called from the audio feed). */
    fun poll(nowMs: Long) {
        orchestrator.poll(nowMs)
    }

    /** Persists and applies a new A4 reference (400..480 Hz, validated). */
    suspend fun setConcertPitch(hz: Double): Settings {
        applied = settingsRepo.update { it.copy(concertPitchHz = hz) }
        stateMachine.setConcertPitch(hz)
        applyTarget(applied)
        return applied
    }

    /** Persists and applies a new mode; retargets the state machine. */
    suspend fun setMode(mode: TunerMode): Settings {
        applied = settingsRepo.update { it.copy(mode = mode) }
        applyTarget(applied)
        return applied
    }

    /** Persists and applies a new preset; resets the selected string. */
    suspend fun setPreset(id: String): Settings {
        applied = settingsRepo.update { it.copy(selectedPresetId = id) }
        selectedStringIndex = 0
        applyTarget(applied)
        return applied
    }

    /** Moves the selected string (instrument mode retargets immediately). */
    fun selectString(index: Int) {
        selectedStringIndex = index
        applyTarget(applied)
    }

    /** Toggles favorite and persists the favorites list. */
    suspend fun toggleFavorite(id: String): Boolean {
        val nowFavorite = presets.toggleFavorite(id)
        settingsRepo.update { it.copy(favorites = presets.favorites()) }
        return nowFavorite
    }

    /**
     * Applies mode + preset to the state machine: chromatic clears the manual
     * target (free matching); instrument locks the selected string of the
     * selected preset (unknown preset → no lock, chromatic behavior).
     */
    private fun applyTarget(settings: Settings) {
        val target = when (settings.mode) {
            TunerMode.CHROMATIC -> null
            TunerMode.INSTRUMENT -> presets
                .byId(settings.selectedPresetId)
                ?.strings
                ?.getOrNull(selectedStringIndex)
                ?.note
        }
        stateMachine.setManualTarget(target)
    }
}
