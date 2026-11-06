package dev.anima.resonance.persistence

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.TunerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun defaults() {
        val s = Settings()
        assertEquals(440.0, s.concertPitchHz)
        assertEquals(TunerMode.CHROMATIC, s.mode)
        assertEquals("standard", s.selectedPresetId)
        assertTrue(s.favorites.isEmpty())
    }

    @Test
    fun concertPitchValidated() {
        assertEquals(400.0, Settings(concertPitchHz = 400.0).concertPitchHz)
        assertEquals(480.0, Settings(concertPitchHz = 480.0).concertPitchHz)
        assertFailsWith<IllegalArgumentException> { Settings(concertPitchHz = 399.9) }
        assertFailsWith<IllegalArgumentException> { Settings(concertPitchHz = 480.1) }
        assertFailsWith<IllegalArgumentException> { Settings(concertPitchHz = 0.0) }
    }

    @Test
    fun repositoryRoundTripAndValidation() = kotlinx.coroutines.test.runTest {
        val repo = InMemorySettingsRepository()
        assertEquals(Settings.DEFAULTS, repo.get())
        repo.update { it.copy(concertPitchHz = 442.0, mode = TunerMode.INSTRUMENT) }
        assertEquals(442.0, repo.get().concertPitchHz)
        assertEquals(TunerMode.INSTRUMENT, repo.get().mode)
        // invalid update must not be persisted
        assertFailsWith<IllegalArgumentException> {
            repo.update { it.copy(concertPitchHz = 999.0) }
        }
        assertEquals(442.0, repo.get().concertPitchHz, "failed update must leave state intact")
    }

    @Test
    fun favoritesToggleIsStableIdBased() {
        val store = PresetStore()
        val standard = BuiltInTunings.standard.id
        val dropD = BuiltInTunings.dropD.id
        assertFalse(store.isFavorite(standard))
        assertTrue(store.toggleFavorite(standard))
        assertTrue(store.toggleFavorite(dropD))
        assertTrue(store.isFavorite(standard) && store.isFavorite(dropD))
        assertFalse(store.toggleFavorite(standard), "second toggle removes")
        assertEquals(listOf(dropD), store.favorites())
    }
}

class PresetStoreTest {

    private fun strings(vararg labels: String): List<dev.anima.resonance.music.TuningString> =
        labels.mapIndexed { i, l -> dev.anima.resonance.music.TuningString("cs${i + 1}", Note.fromLabel(l)) }

    @Test
    fun builtInsAlwaysPresentAndImmutable() {
        val store = PresetStore()
        assertEquals(BuiltInTunings.all.size, store.all().size)
        assertFailsWith<IllegalArgumentException> {
            store.edit(BuiltInTunings.standard.id, "Hacked", strings("E2", "A2", "D3", "G3", "B3", "E4"))
        }
        assertFalse(store.delete(BuiltInTunings.standard.id))
        assertNotNull(store.byId(BuiltInTunings.standard.id))
    }

    @Test
    fun createAssignsStableUniqueIds() {
        val store = PresetStore()
        val a = store.create("My Open D", strings("D2", "G2", "D3", "G3", "B3", "D4"))
        val b = store.create("My Open D", strings("D2", "G2", "D3", "G3", "B3", "D4"))
        assertTrue(a.builtIn.not())
        assertTrue(a.id.isNotBlank() && b.id.isNotBlank())
        assertTrue(a.id != b.id, "same-name presets must get distinct stable IDs")
        assertEquals(BuiltInTunings.all.size + 2, store.all().size)
        // stable: same object/id persists across calls
        assertEquals(a.id, store.byId(a.id)!!.id)
    }

    @Test
    fun createValidatesStrings() {
        val store = PresetStore()
        assertFailsWith<IllegalArgumentException> { store.create("Empty", emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            store.create("Too many", (0 until 13).map { i -> dev.anima.resonance.music.TuningString("s$i", Note.fromLabel("E2")) })
        }
        assertFailsWith<IllegalArgumentException> { store.create(" ", strings("E2")) }
    }

    @Test
    fun editChangesNameAndStrings() {
        val store = PresetStore()
        val p = store.create("T1", strings("D2", "A2", "D3", "G3", "B3", "E4"))
        val edited = store.edit(p.id, "T1b", strings("D2", "G2", "D3", "G3", "B3", "D4"))
        assertEquals("T1b", edited.name)
        assertEquals("G2", edited.strings[1].note.label, "edit must replace string 2 (A2 -> G2)")
        assertEquals(p.id, store.byId(p.id)!!.id, "edit must keep the stable ID")
        assertFailsWith<IllegalArgumentException> { store.edit("missing", "X", strings("E2")) }
    }

    @Test
    fun deleteRemovesCustomOnly() {
        val store = PresetStore()
        val p = store.create("Tmp", strings("D2", "A2", "D3", "G3", "B3", "E4"))
        assertTrue(store.delete(p.id))
        assertNull(store.byId(p.id))
        assertFalse(store.delete(p.id), "second delete is a no-op")
    }

    @Test
    fun favoritesSurviveDeleteByIdOnly() {
        val store = PresetStore()
        val p = store.create("Tmp", strings("D2", "A2", "D3", "G3", "B3", "E4"))
        store.toggleFavorite(p.id)
        store.delete(p.id)
        assertFalse(store.isFavorite(p.id), "deleting a preset clears its favorite")
    }
}
