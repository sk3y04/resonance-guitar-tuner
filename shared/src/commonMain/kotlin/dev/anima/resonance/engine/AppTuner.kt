package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.audio.CaptureOrchestrator
import dev.anima.resonance.audio.CaptureState
import dev.anima.resonance.dsp.StreamingAnalyzer
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.persistence.SettingsRepository
import dev.anima.resonance.presentation.PresetSheetUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStateMachine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

    /**
     * Result of an import attempt. A failed import has
     * [importedCount] = 0 and a non-null [error] describing why.
     */
    data class ImportResult(
        val importedCount: Int,
        val skippedCount: Int,
        val error: String?,
    )

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
 *
 * Thread contract: on Android TWO threads touch this object — the
 * pump thread ([poll]) and the main thread (settings/CRUD/reads). Every
 * access to shared state is serialized on [lock] and is never held across a
 * suspension into the settings repository (disk I/O), so the pump is never
 * blocked on the disk. Single-threaded host tests are unaffected.
 */
class AppTuner(
    private val input: AudioInput,
    private val settingsRepo: SettingsRepository,
    private val presets: PresetStore,
    private val onSnapshot: (TunerSnapshot) -> Unit = {},
    private val onCaptureState: (CaptureState) -> Unit = {},
    /**
     * Durable hook for custom-preset changes. Null = in-memory only
     * (host demo); production passes the repository's saveCustomPresets.
     */
    private val persistCustoms: (suspend (List<TuningPreset>) -> Unit)? = null,
) {
    /** Single lock for all shared-state access (see class doc). */
    private val lock = Any()

    /**
     * Serializes persisted-state reads (startup hydration) with every
     * mutation. The UI can be interactive before the asynchronous restore
     * finishes; without this, a just-created tuning could be overwritten by
     * the restore snapshot. Engine lock order: persistenceMutex -> lock,
     * never the reverse, and never across a suspension while holding [lock].
     */
    private val persistenceMutex = Mutex()

    /** All presets (built-ins + customs) for import dedupe and UI mapping. */
    val presetsView: List<TuningPreset> get() = engineLocked(lock) { presets.all() }
    private val stateMachine = TunerStateMachine()
    private val analyzer = StreamingAnalyzer(input.sampleRate, stateMachine = stateMachine)

    private val orchestrator = CaptureOrchestrator(
        input = input,
        onChunk = { chunk, nowMs ->
            analyzer.processChunk(chunk, nowMs)?.let {
                _lastSnapshot = it
                onSnapshot(it)
            }
        },
        onState = onCaptureState,
    )

    /** Last persisted settings applied to the state machine. */
    private var applied: Settings = Settings.DEFAULTS

    /** Custom/favorite snapshot is restored once per engine instance. */
    @Volatile
    private var hydrated = false

    /**
     * Lifecycle epoch. A start that is still loading settings when the shell
     * stops must not open the microphone on resume; every stop/start bumps
     * the epoch and only the matching start may open capture.
     */
    private var captureEpoch = 0

    /** Read-only view of [applied] (the UI maps from this). */
    val currentSettings: Settings get() = engineLocked(lock) { applied }

    /** Most recent analyzed frame; null before the first frame or after stop. */
    private var _lastSnapshot: TunerSnapshot? = null
    val lastSnapshot: TunerSnapshot?
        get() = engineLocked(lock) { _lastSnapshot }

    /**
     * Locked string index in instrument mode; null = AUTO selection
     * (nearest preset string). Tapping the locked string again unlocks.
     */
    private var _lockedString: Int? = null
    val lockedString: Int?
        get() = engineLocked(lock) { _lockedString }

    /** Live capture state (mirrors the orchestrator). */
    val captureState: CaptureState get() = engineLocked(lock) { orchestrator.state }

    /** Read-only preset lookup for the editor (stable IDs, never positions). */
    fun preset(id: String): TuningPreset? = engineLocked(lock) { presets.byId(id) }

    /**
     * Consistent, lock-protected snapshot for the UI mapper. Preset
     * hydration/CRUD may run on an I/O dispatcher at startup, while the UI
     * ticker reads on Main; both must go through the same lock.
     */
    internal data class UiData(
        val settings: Settings,
        val snapshot: TunerSnapshot?,
        val capturing: Boolean,
        val captureError: Boolean,
        val lockedString: Int?,
        val presets: List<TuningPreset>,
        val favorites: Set<String>,
    )

    internal fun uiData(): UiData = engineLocked(lock) {
        UiData(
            settings = applied,
            snapshot = _lastSnapshot,
            capturing = uiCapturing,
            captureError = uiCaptureError,
            lockedString = _lockedString,
            presets = presets.all(),
            favorites = presets.favorites().toSet(),
        )
    }

    /**
     * UI-facing capture flags, owned by the engine so the main thread
     * reads them through the same lock the pump writes under. (The controller
     * previously kept its own copies, written by the capture-state callback
     * on the pump thread — a cross-thread write with no visibility.)
     */
    private var uiCapturing = false
    private var uiCaptureError = false
    val capturingUi: Boolean get() = engineLocked(lock) { uiCapturing }
    val captureErrorUi: Boolean get() = engineLocked(lock) { uiCaptureError }

    /**
     * Loads persisted settings, custom tunings and favorites, applies the
     * concert pitch / mode / preset to the state machine, and returns the
     * applied settings. Deliberately does NOT open the microphone: settings
     * and the tuning library must be usable even when permission is denied.
     */
    suspend fun load(): Settings = persistenceMutex.withLock {
        var settings = settingsRepo.get() // persistence: outside the lock
        // Restore the user's custom tunings + favorites before any
        // target/mapping read. The store supported hydration but the
        // production path constructed it empty, so a relaunch lost them.
        // Once per engine instance: a foreground restart must not clobber
        // in-memory-only hosts or re-read under a live mutation.
        if (!hydrated) {
            val persistedCustoms = settingsRepo.loadCustomPresets()
            engineLocked(lock) {
                if (!hydrated) {
                    presets.hydrate(persistedCustoms, settings.favorites)
                    hydrated = true
                }
            }
        }
        // A persisted selection can outlive its custom preset. Repair
        // it before applying targets so Instrument mode never restores with
        // an empty target set.
        val selectionExists = engineLocked(lock) {
            presets.byId(settings.selectedPresetId) != null
        }
        if (!selectionExists) {
            settings = settingsRepo.update { it.copy(selectedPresetId = "standard") }
        }
        engineLocked(lock) {
            applied = settings
            stateMachine.setConcertPitch(applied.concertPitchHz)
            applyTarget(applied)
            applied
        }
    }

    /**
     * Loads persisted settings, then opens capture. Returns the applied
     * settings. Permission gating is the controller's job; this method is
     * only called after the mic is allowed.
     */
    suspend fun start(): Settings {
        val epoch = engineLocked(lock) { ++captureEpoch }
        val settings = load()
        return engineLocked(lock) {
            if (captureEpoch != epoch) {
                // A stop happened while the persisted settings were loading.
                // Do not open the mic in the background.
                return@engineLocked settings
            }
            uiCaptureError = false
            uiCapturing = true
            // Never resurrect a pre-error/pre-stop reading on a fresh open.
            _lastSnapshot = null
            orchestrator.start()
            if (orchestrator.state == CaptureState.ERROR) {
                uiCapturing = false
                uiCaptureError = true
            }
            settings
        }
    }

    /** Stops capture and clears the displayed reading. Idempotent. */
    fun stop() {
        engineLocked(lock) {
            captureEpoch++
            orchestrator.stop()
            _lastSnapshot = null
            uiCapturing = false
            uiCaptureError = false
        }
    }

    /** One pump step at virtual time [nowMs] (called from the audio feed). */
    fun poll(nowMs: Long) {
        engineLocked(lock) {
            orchestrator.poll(nowMs)
            if (orchestrator.state == CaptureState.ERROR) {
                // Bounded recovery gave up (3 consecutive device errors).
                uiCapturing = false
                uiCaptureError = true
            }
        }
    }

    /** Persists and applies a new A4 reference (400..480 Hz, validated). */
    suspend fun setConcertPitch(hz: Double): Settings = persistenceMutex.withLock {
        val settings = settingsRepo.update { it.copy(concertPitchHz = hz) }
        engineLocked(lock) {
            applied = settings
            stateMachine.setConcertPitch(hz)
            applyTarget(applied) // re-applies a restricted set under the new scale
            applied
        }
    }

    /**
     * Steps the A4 reference (default ±0.1 Hz, spec §2), clamped to
     * 400..480. A coarse UI step is available so moving 440 → 432 Hz is a
     * few deliberate taps instead of 80.
     */
    suspend fun stepConcertPitch(up: Boolean, stepHz: Double = 0.1) {
        setConcertPitch(
            PresetSheetUi.stepConcertPitch(
                engineLocked(lock) { applied.concertPitchHz },
                up,
                stepHz,
            ),
        )
    }

    /**
     * Sets the enharmonic display preference (spec §2). Display-only: note
     * identity, frequencies and stored tunings never change.
     */
    suspend fun setAccidentalSpelling(spelling: dev.anima.resonance.music.Spelling) {
        persistenceMutex.withLock {
            val settings = settingsRepo.update { it.copy(accidentalSpelling = spelling) }
            engineLocked(lock) { applied = settings }
        }
    }

    /** Persists and applies a new mode; retargets the state machine. */
    suspend fun setMode(mode: TunerMode): Settings = persistenceMutex.withLock {
        val settings = settingsRepo.update { it.copy(mode = mode) }
        engineLocked(lock) {
            applied = settings
            applyTarget(applied)
            applied
        }
    }

    /** Persists and applies a new preset; the instrument returns to AUTO. */
    suspend fun setPreset(id: String): Settings = persistenceMutex.withLock {
        val settings = settingsRepo.update { it.copy(selectedPresetId = id) }
        engineLocked(lock) {
            applied = settings
            _lockedString = null
            applyTarget(applied)
            applied
        }
    }

    /**
     * Locks the [index]-th string (instrument mode). Tapping the already
     * locked string releases the lock and returns to AUTO selection.
     */
    fun selectString(index: Int) {
        engineLocked(lock) {
            _lockedString = if (_lockedString == index) null else index
            applyTarget(applied)
        }
    }

    /** Toggles favorite and persists the favorites list. */
    suspend fun toggleFavorite(id: String): Boolean = persistenceMutex.withLock {
        val (nowFavorite, favorites) = engineLocked(lock) {
            presets.toggleFavorite(id) to presets.favorites()
        }
        settingsRepo.update { it.copy(favorites = favorites) }
        nowFavorite
    }

    /**
     * Creates a custom named tuning (spec §2). String IDs are positional
     * and stable within the preset; the change is persisted durably.
     */
    suspend fun createCustomPreset(name: String, notes: List<Note>): TuningPreset =
        persistenceMutex.withLock {
            val preset = engineLocked(lock) {
                presets.create(name, notes.mapIndexed { i, n -> TuningString("s${i + 1}", n) })
            }
            persistCustoms?.invoke(engineLocked(lock) { presets.customs() })
            preset
        }

    /**
     * Edits a custom tuning (built-ins are read-only). String IDs are
     * preserved positionally when the string count is unchanged.
     */
    suspend fun editCustomPreset(id: String, name: String, notes: List<Note>): TuningPreset =
        persistenceMutex.withLock {
            val updated = engineLocked(lock) {
                val previous = presets.byId(id)
                val strings = notes.mapIndexed { i, n ->
                    val oldId = previous?.strings?.getOrNull(i)?.id
                    TuningString(oldId ?: "s${i + 1}", n)
                }
                val result = presets.edit(id, name, strings)
                // Only the selected tuning feeds the live target set. Editing
                // it makes its target set and any positional string lock
                // stale, so clear the lock and re-apply immediately. Editing
                // another preset must not disturb the active lock.
                if (applied.selectedPresetId == id) {
                    _lockedString = null
                    applyTarget(applied)
                }
                result
            }
            persistCustoms?.invoke(engineLocked(lock) { presets.customs() })
            updated
        }

    /**
     * Deletes a custom tuning. If it was the selected preset, selection
     * falls back to "standard" (persisted) and any string lock is released.
     */
    suspend fun deleteCustomPreset(id: String): Boolean = persistenceMutex.withLock {
        val (removed, wasSelected) = engineLocked(lock) {
            presets.delete(id) to (applied.selectedPresetId == id)
        }
        if (!removed) return@withLock false
        val fallback = if (wasSelected) {
            settingsRepo.update { it.copy(selectedPresetId = "standard") }
        } else null
        engineLocked(lock) {
            _lockedString = null
            fallback?.let { applied = it }
            applyTarget(applied)
        }
        persistCustoms?.invoke(engineLocked(lock) { presets.customs() })
        true
    }

    /**
     * Imports custom tunings from a versioned JSON payload
     * (spec §2). Untrusted by contract: the codec validates size/counts/
     * lengths, built-ins are never imported, duplicate IDs are disambiguated
     * deterministically (never overwriting). A failed import mutates
     * nothing and reports its reason.
     */
    suspend fun importCustomPresets(payload: String): ImportResult = persistenceMutex.withLock {
        val decoded = try {
            dev.anima.resonance.music.TuningPresetJson.decode(payload, engineLocked(lock) { presets.all() })
        } catch (e: IllegalArgumentException) {
            return@withLock ImportResult(0, 0, e.message ?: "Unreadable tuning file")
        }
        engineLocked(lock) {
            for (preset in decoded) {
                presets.addImported(preset)
            }
        }
        persistCustoms?.invoke(engineLocked(lock) { presets.customs() })
        ImportResult(decoded.size, 0, null)
    }

    /** Versioned JSON of all custom tunings (for the document picker). */
    fun exportCustomPresets(): String =
        dev.anima.resonance.music.TuningPresetJson.encode(engineLocked(lock) { presets.customs() })

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
                val lockedNote = _lockedString?.let { preset?.strings?.getOrNull(it)?.note }
                stateMachine.setManualTarget(lockedNote)
            }
        }
    }
}
