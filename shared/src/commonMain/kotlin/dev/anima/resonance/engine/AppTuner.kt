package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.audio.CaptureOrchestrator
import dev.anima.resonance.audio.CaptureState
import dev.anima.resonance.dsp.StreamingAnalyzer
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.persistence.SettingsRepository
import dev.anima.resonance.presentation.PresetSheetUi
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

    /** Read-only view of [applied] (the UI maps from this). */
    val currentSettings: Settings get() = applied

    /** Most recent analyzed frame; null before the first frame or after stop. */
    var lastSnapshot: TunerSnapshot? = null
        private set

    /**
     * Locked string index in instrument mode; null = AUTO selection
     * (nearest preset string). Tapping the locked string again unlocks.
     */
    var lockedString: Int? = null
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
        applyTarget(applied) // re-applies a restricted set under the new scale
        return applied
    }

    /** Steps the A4 reference by ±0.1 Hz (spec §2); clamped at 400..480. */
    suspend fun stepConcertPitch(up: Boolean) {
        setConcertPitch(PresetSheetUi.stepConcertPitch(applied.concertPitchHz, up))
    }

    /** Persists and applies a new mode; retargets the state machine. */
    suspend fun setMode(mode: TunerMode): Settings {
        applied = settingsRepo.update { it.copy(mode = mode) }
        applyTarget(applied)
        return applied
    }

    /** Persists and applies a new preset; the instrument returns to AUTO. */
    suspend fun setPreset(id: String): Settings {
        applied = settingsRepo.update { it.copy(selectedPresetId = id) }
        lockedString = null
        applyTarget(applied)
        return applied
    }

    /**
     * Locks the [index]-th string (instrument mode). Tapping the already
     * locked string releases the lock and returns to AUTO selection.
     */
    fun selectString(index: Int) {
        lockedString = if (lockedString == index) null else index
        applyTarget(applied)
    }

    /** Toggles favorite and persists the favorites list. */
    suspend fun toggleFavorite(id: String): Boolean {
        val nowFavorite = presets.toggleFavorite(id)
        settingsRepo.update { it.copy(favorites = presets.favorites()) }
        return nowFavorite
    }

    /**
     * Applies mode + preset to the state machine:
     *  - chromatic: full chromatic set, no lock;
     *  - instrument: AUTO selection over the preset's strings, overridden by
     *    the manual lock ([lockedString]) when active.
     */
    private fun applyTarget(settings: Settings) {
        val preset = presets.byId(settings.selectedPresetId)
        when (settings.mode) {
            TunerMode.CHROMATIC -> {
                stateMachine.setTargetSet(null)
                stateMachine.setManualTarget(null)
            }
            TunerMode.INSTRUMENT -> {
                // setConcertPitch may have cleared the set on a scale change;
                // apply the set first, then the lock (lock overrides the set).
                stateMachine.setTargetSet(preset?.strings?.map { it.note })
                val lock = lockedString?.let { preset?.strings?.getOrNull(it)?.note }
                stateMachine.setManualTarget(lock)
            }
        }
    }
}
