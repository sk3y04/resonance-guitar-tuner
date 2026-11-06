package dev.anima.resonance.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TuningPresetJsonTest {

    private fun custom(id: String = "my-dadgad", name: String = "My DADGAD") =
        TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = listOf("D2", "A2", "D3", "G3", "A3", "D4").mapIndexed { i, l ->
                TuningString(id = "$id-s${i + 1}", note = Note.fromLabel(l))
            },
        )

    @Test
    fun roundTripPreservesIdentityAndOrder() {
        val original = listOf(custom("custom-a", "First"), custom("custom-b", "Second"))
        val decoded = TuningPresetJson.decode(TuningPresetJson.encode(original))
        assertEquals(original.map { it.id }, decoded.map { it.id })
        original.forEachIndexed { i, p ->
            assertEquals(p.name, decoded[i].name)
            assertEquals(p.builtIn, decoded[i].builtIn)
            assertEquals(p.strings.map { it.note.label }, decoded[i].strings.map { it.note.label })
        }
    }

    @Test
    fun rejectsUnsupportedOrMissingVersion() {
        val v99 = """{"resonanceVersion": 99, "tunings": []}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(v99) }
        val missing = """{"tunings": []}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(missing) }
    }

    @Test
    fun rejectsInvalidNoteLabels() {
        val bad = """
            {"resonanceVersion": 1, "tunings": [{"id": "t1", "name": "Bad",
              "strings": ["E2", "X9", "D3", "G3", "B3", "E4"]}]}
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(bad) }
    }

    @Test
    fun rejectsTooManyStrings() {
        val strings = (0 until 13).map { "\"E${it % 5 + 1}\"" }.joinToString(", ")
        val bad = """{"resonanceVersion": 1, "tunings": [{"id": "t1", "name": "Long", "strings": [$strings]}]}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(bad) }
    }

    @Test
    fun rejectsMissingRequiredFields() {
        val noName = """{"resonanceVersion": 1, "tunings": [{"id": "t1", "strings": ["E2"]}]}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(noName) }
        val noStrings = """{"resonanceVersion": 1, "tunings": [{"id": "t1", "name": "N"}]}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(noStrings) }
    }

    @Test
    fun rejectsOversizedNameAndId() {
        val longName = "n".repeat(41)
        val badName = """{"resonanceVersion": 1, "tunings": [{"id": "t1", "name": "$longName", "strings": ["E2"]}]}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(badName) }
        val longId = "i".repeat(65)
        val badId = """{"resonanceVersion": 1, "tunings": [{"id": "$longId", "name": "N", "strings": ["E2"]}]}"""
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(badId) }
    }

    @Test
    fun rejectsOversizedPayload() {
        // Many valid tiny tunings pushed past the hard payload limit.
        val many = (1..500).joinToString(", ") { """{"id": "t$it", "name": "N", "strings": ["E2"]}""" }
        val big = """{"resonanceVersion": 1, "tunings": [$many]}"""
        assertTrue(big.length > TuningPresetJson.MAX_PAYLOAD_BYTES, "test payload must exceed limit")
        assertFailsWith<IllegalArgumentException> { TuningPresetJson.decode(big) }
    }

    @Test
    fun importedTuningsAreNeverMarkedBuiltIn() {
        val out = TuningPresetJson.decode(TuningPresetJson.encode(listOf(BuiltInTunings.standard)))
        assertTrue(out.none { it.builtIn }, "imported presets must not carry the built-in flag")
    }

    @Test
    fun duplicateIdsNeverOverwriteExisting() {
        val colliding = TuningPreset(
            id = "standard",
            name = "Not really standard",
            builtIn = false,
            strings = BuiltInTunings.standard.strings,
        )
        val out = TuningPresetJson.decode(TuningPresetJson.encode(listOf(colliding)), existing = BuiltInTunings.all)
        assertEquals(1, out.size)
        assertTrue(out[0].id != "standard", "imported id must not shadow an existing one")
        assertTrue(out[0].id !in BuiltInTunings.all.map { it.id })
        // deterministic: same input -> same output
        val again = TuningPresetJson.decode(TuningPresetJson.encode(listOf(colliding)), existing = BuiltInTunings.all)
        assertEquals(out[0].id, again[0].id)
    }

    @Test
    fun duplicateIdsWithinOneImportAreDisambiguated() {
        val a = TuningPreset("dup", "A", listOf(TuningString("dup-1", Note.fromLabel("E2"))))
        val b = TuningPreset("dup", "B", listOf(TuningString("dup-2", Note.fromLabel("A2"))))
        val out = TuningPresetJson.decode(TuningPresetJson.encode(listOf(a, b)))
        assertEquals(2, out.size)
        assertEquals(2, out.map { it.id }.toSet().size)
    }
}

class NoteLabelTest {

    @Test
    fun parsesLabels() {
        assertEquals(Note("E", 2), Note.fromLabel("E2"))
        assertEquals(Note("F", Spelling.SHARP, 3), Note.fromLabel("F#3"))
        assertEquals(Note("E", Spelling.FLAT, 2), Note.fromLabel("Eb2"))
        assertEquals(Note("C", 0), Note.fromLabel("C0"))
    }

    @Test
    fun rejectsMalformedLabels() {
        assertFailsWith<IllegalArgumentException> { Note.fromLabel("E") }      // no octave
        assertFailsWith<IllegalArgumentException> { Note.fromLabel("X2") }     // bad letter
        assertFailsWith<IllegalArgumentException> { Note.fromLabel("E23") }    // octave too large
        assertFailsWith<IllegalArgumentException> { Note.fromLabel("E#b2") }   // nonsense
        assertFailsWith<IllegalArgumentException> { Note.fromLabel("") }
    }
}
