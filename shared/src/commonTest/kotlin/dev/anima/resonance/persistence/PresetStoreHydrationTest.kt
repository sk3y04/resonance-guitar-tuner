package dev.anima.resonance.persistence

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PresetStore must be hydratable from a persisted snapshot
 * (customs + favorites) so a DataStore-backed app restores user tunings.
 */
class PresetStoreHydrationTest {

    private fun custom(id: String, name: String, vararg labels: String): TuningPreset =
        TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l -> TuningString("$id-s${i + 1}", Note.fromLabel(l)) },
        )

    @Test
    fun hydratesCustomsAndFavoritesFromSnapshot() {
        val c1 = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val c2 = custom("c2", "Half Down", "Eb2", "Ab2", "Db3", "Gb3", "Bb3", "Eb4")
        val store = PresetStore(
            initialCustoms = listOf(c1, c2),
            initialFavorites = listOf("standard", "c1"),
        )
        assertEquals(BuiltInTunings.all.size + 2, store.all().size)
        assertEquals(c1, store.byId("c1"))
        assertTrue(store.isFavorite("c1"))
        assertTrue(store.isFavorite("standard"))
        assertEquals(listOf(c1, c2), store.customs())
    }

    @Test
    fun freshStoreHasNoCustomsOrFavorites() {
        val store = PresetStore()
        assertTrue(store.customs().isEmpty())
        assertTrue(store.favorites().isEmpty())
        assertEquals(BuiltInTunings.all.size, store.all().size)
    }

    @Test
    fun editsAfterHydrationKeepStableIds() {
        val c1 = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val store = PresetStore(initialCustoms = listOf(c1))
        val edited = store.edit("c1", "Open D v2", c1.strings)
        assertEquals("c1", edited.id)
        val created = store.create("New", c1.strings)
        assertTrue(created.id != "c1")
        assertEquals(listOf(edited, created), store.customs(), "hydrated customs come first, stable order")
    }
}
