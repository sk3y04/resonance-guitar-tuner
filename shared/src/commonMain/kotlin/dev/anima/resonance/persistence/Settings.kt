package dev.anima.resonance.persistence

import dev.anima.resonance.music.Note
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import dev.anima.resonance.presentation.TunerMode

/**
 * Persisted user settings. Concert pitch is validated to the
 * product range 400..480 Hz — persisted and imported values must pass.
 */
data class Settings(
    val concertPitchHz: Double = 440.0,
    val mode: TunerMode = TunerMode.CHROMATIC,
    val selectedPresetId: String = "standard",
    val favorites: List<String> = emptyList(),
    val accidentalSpelling: Spelling = Spelling.NATURAL,
) {
    init {
        require(concertPitchHz in 400.0..480.0) { "concert pitch out of bounds 400..480: $concertPitchHz" }
        require(selectedPresetId.isNotBlank()) { "selectedPresetId must not be blank" }
    }

    companion object {
        val DEFAULTS = Settings()
    }
}

/**
 * Synchronous settings repository contract (host-testable; the DataStore
 * implementation backs it on Android and JVM).
 */
interface SettingsRepository {
    suspend fun get(): Settings

    /** Applies [transform] atomically; the returned settings are persisted. */
    suspend fun update(transform: (Settings) -> Settings): Settings

    /**
     * Persisted custom presets. Default pass-through keeps engines
     * that never mutate customs on in-memory-only hosts working; real
     * backends override this.
     */
    suspend fun saveCustomPresets(customs: List<TuningPreset>): List<TuningPreset> = customs

    /**
     * Restores the persisted custom tunings on process start.
     * Without this the store was hydratable but the production engine never
     * asked for the snapshot — custom tunings vanished on relaunch.
     */
    suspend fun loadCustomPresets(): List<TuningPreset> = emptyList()
}

/** In-memory implementation (tests, debug, and future platforms). */
class InMemorySettingsRepository(initial: Settings = Settings.DEFAULTS) : SettingsRepository {
    private var current: Settings = initial
    private var customs: List<TuningPreset> = emptyList()

    /** Current customs snapshot (tests and UI mapping). */
    val customsSnapshot: List<TuningPreset> get() = customs

    override suspend fun get(): Settings = current

    override suspend fun update(transform: (Settings) -> Settings): Settings {
        current = transform(current)
        return current
    }

    override suspend fun saveCustomPresets(customs: List<TuningPreset>): List<TuningPreset> {
        this.customs = customs
        return customs
    }

    override suspend fun loadCustomPresets(): List<TuningPreset> = customs
}

/**
 * Custom-preset store. Built-ins are immutable and always present;
 * customs get stable string IDs (never list positions). Favorites reference
 * IDs of either kind.
 */
class PresetStore(
    private val builtIns: List<dev.anima.resonance.music.TuningPreset> =
        dev.anima.resonance.music.BuiltInTunings.all,
    /** Persisted customs snapshot — restored before any mutation. */
    initialCustoms: List<dev.anima.resonance.music.TuningPreset> = emptyList(),
    /** Persisted favorites snapshot. */
    initialFavorites: List<String> = emptyList(),
) {
    private var customs: MutableList<dev.anima.resonance.music.TuningPreset> =
        initialCustoms.toMutableList()
    private var favorites: MutableList<String> = initialFavorites.toMutableList()
    private var nextCustomIndex = 0
    private var idCounter = initialCustoms
        .mapNotNull { it.id.removePrefix("custom-").toIntOrNull() }
        .maxOrNull() ?: 0

    /** All presets, built-ins first (stable order), then customs. */
    fun all(): List<dev.anima.resonance.music.TuningPreset> = builtIns + customs

    fun byId(id: String): dev.anima.resonance.music.TuningPreset? = all().firstOrNull { it.id == id }

    fun favorites(): List<String> = favorites.toList()

    /** Only user-created presets, in stable creation order. */
    fun customs(): List<dev.anima.resonance.music.TuningPreset> = customs.toList()

    fun isFavorite(id: String): Boolean = id in favorites

    /**
     * Replaces the in-memory snapshot with the persisted one (process start).
     * Favorites that point at a deleted/unknown preset are dropped; the ID
     * counter advances past every restored "custom-N" so future creations
     * cannot collide. Only call this before user mutations.
     */
    fun hydrate(
        customs: List<dev.anima.resonance.music.TuningPreset>,
        favorites: List<String>,
    ) {
        val builtInIds = builtIns.map { it.id }.toSet()
        this.customs = customs
            .filter { it.id !in builtInIds }
            .distinctBy { it.id }
            .toMutableList()
        val known = all().map { it.id }.toSet()
        this.favorites = favorites.filter { it in known }.toMutableList()
        idCounter = this.customs
            .mapNotNull { it.id.removePrefix("custom-").toIntOrNull() }
            .maxOrNull() ?: 0
    }

    private fun newCustomId(): String {
        // Imported presets may legitimately carry a "custom-N" id that the
        // constructor-time counter never saw. Advance until unique so a new
        // tuning can never share an id with an imported one.
        do {
            idCounter += 1
        } while (customs.any { it.id == "custom-$idCounter" })
        return "custom-$idCounter"
    }

    private fun requireValid(name: String, strings: List<TuningString>) {
        require(name.isNotBlank()) { "preset name must not be blank" }
        require(strings.isNotEmpty()) { "preset must have at least one string" }
        require(strings.size <= 12) { "preset has more than 12 strings" }
    }

    /** Creates a custom preset; returns it. Names may repeat (IDs stay unique). */
    fun create(name: String, strings: List<TuningString>): TuningPreset {
        requireValid(name, strings)
        val preset = TuningPreset(
            id = newCustomId(),
            name = name,
            strings = strings,
            builtIn = false,
        )
        customs.add(preset)
        return preset
    }

    /** Edits a custom preset (built-ins are read-only). Returns the new value. */
    fun edit(id: String, name: String, strings: List<TuningString>): TuningPreset {
        requireValid(name, strings)
        val index = customs.indexOfFirst { it.id == id }
        require(index >= 0) { "unknown custom preset id: $id" }
        val updated = customs[index].copy(name = name, strings = strings)
        customs[index] = updated
        return updated
    }

    /** Deletes a custom preset (built-ins cannot be deleted). */
    fun delete(id: String): Boolean {
        val removed = customs.removeAll { it.id == id }
        if (removed) favorites.removeAll { it == id }
        return removed
    }

    fun toggleFavorite(id: String): Boolean {
        if (id !in all().map { it.id }) throw IllegalArgumentException("unknown preset id: $id")
        return if (id in favorites) {
            favorites.remove(id)
            false
        } else {
            favorites.add(id)
            true
        }
    }

    /**
     * Adds an imported custom preset. The codec has already
     * disambiguated its ID against the existing set; this is defense in
     * depth — an ID collision here is a bug, never an overwrite.
     */
    fun addImported(preset: TuningPreset): TuningPreset {
        require(!preset.builtIn) { "built-ins are never imported: ${preset.id}" }
        require(customs.none { it.id == preset.id }) { "import ID collision: ${preset.id}" }
        customs.add(preset)
        return preset
    }
}
