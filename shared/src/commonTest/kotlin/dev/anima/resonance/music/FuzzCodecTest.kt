package dev.anima.resonance.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Fuzz/property tests for the preset codec.
 *
 * Contract under test (TuningPresetJson): imported payloads are
 * UNTRUSTED. The codec must never crash with anything other than
 * [IllegalArgumentException], must enforce every documented limit, must
 * never resurrect a built-in from a file, and must rename — never
 * overwrite — colliding IDs deterministically.
 */
class FuzzCodecTest {

    private val rng = Random(0x0DEADL)

    private val letters = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private fun randomPreset(id: String? = null): TuningPreset {
        val n = 1 + rng.nextInt(12)
        val strings = List(n) { TuningString(id = "s$it", note = Note.fromLabel("${letters[rng.nextInt(letters.size)]}${rng.nextInt(9)}")) }
        return TuningPreset(
            id = id ?: "p-${rng.nextLong(1_000_000).toString(36)}",
            name = List(1 + rng.nextInt(40)) { ('a'.code + rng.nextInt(26)).toChar() }.joinToString(""),
            builtIn = false,
            strings = strings,
        )
    }

    private fun assertValidShape(presets: List<TuningPreset>) {
        val ids = presets.map { it.id }
        assertEquals("imported IDs must be unique", ids.toSet().size, ids.size)
        presets.forEach { p ->
            assertFalse("built-ins must never be imported: ${p.id}", p.builtIn)
            assertTrue("name bounds", p.name.isNotBlank() && p.name.length <= TuningPresetJson.MAX_NAME_LENGTH)
            assertTrue("id bounds", p.id.length in 1..TuningPresetJson.MAX_ID_LENGTH)
            assertTrue("string count bounds", p.strings.size in 1..TuningPresetJson.MAX_STRINGS)
            p.strings.forEach { s ->
                assertTrue("note label round-trips: ${s.note.label}", s.note.label.isNotEmpty())
            }
        }
    }

    /** Only IAE is an acceptable failure mode for any payload. */
    private fun decodeOrIae(payload: String, existing: List<TuningPreset> = emptyList()): List<TuningPreset>? =
        try {
            TuningPresetJson.decode(payload, existing)
        } catch (e: IllegalArgumentException) {
            null
        }

    @Test
    fun roundTripPreservesNameAndStrings() {
        for (i in 0 until 50) {
            val presets = (0 until 1 + rng.nextInt(8)).map { randomPreset() }
            val payload = TuningPresetJson.encode(presets)
            val decoded = TuningPresetJson.decode(payload)
            assertEquals(presets.size, decoded.size)
            decoded.forEachIndexed { i2, d ->
                assertEquals(presets[i2].name, d.name)
                assertEquals(presets[i2].strings.map { it.note.label }, d.strings.map { it.note.label })
                assertFalse(d.builtIn)
            }
            // Second round trip: idempotent.
            val again = TuningPresetJson.decode(TuningPresetJson.encode(decoded))
            assertEquals(decoded.map { it.name to it.strings.map { s -> s.note.label } },
                again.map { it.name to it.strings.map { s -> s.note.label } })
        }
    }

    @Test
    fun idCollisionsRenameDeterministicallyAndNeverOverwrite() {
        val existing = listOf(TuningPreset(id = "x", name = "orig", builtIn = false,
            strings = listOf(TuningString("x-s1", Note.fromLabel("E2")))))
        for (i in 0 until 30) {
            val payload = TuningPresetJson.encode(listOf(randomPreset("x")))
            val decoded = TuningPresetJson.decode(payload, existing)
            assertEquals(1, decoded.size)
            val newId = decoded[0].id
            assertTrue("collision must rename, got $newId", newId != "x")
            assertTrue("deterministic rename", newId == "x-import" || newId.matches(Regex("x-import\\d+")))
            // Importing the SAME payload twice yields distinct IDs.
            val second = TuningPresetJson.decode(payload, existing + decoded)
            assertTrue("second import must not reuse ${second[0].id}", second[0].id != "x" && second[0].id != newId)
        }
    }

    @Test
    fun garbageBytesNeverThrowAnythingButIAE() {
        for (i in 0 until 400) {
            val len = when {
                i % 7 == 0 -> 0
                i % 5 == 0 -> 1 + rng.nextInt(63)
                else -> 1 + rng.nextInt(4095)
            }
            val bytes = ByteArray(len); rng.nextBytes(bytes)
            val payload = String(bytes) // arbitrary code units incl. surrogates
            val result = decodeOrIae(payload)
            if (result != null) assertValidShape(result)
        }
    }

    @Test
    fun adversarialShapesAreRejectedOrSanitized() {
        fun expectIae(name: String, payload: String) {
            try {
                val out = TuningPresetJson.decode(payload)
                // Some shapes legitimately decode to an empty list (skipped built-ins).
                assertTrue("$name: expected IAE, got $out", out.isEmpty())
                assertValidShape(out)
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        expectIae("no version", """{"tunings":[]}""")
        expectIae("bad version type", """{"resonanceVersion":"1","tunings":[]}""")
        expectIae("version 0", """{"resonanceVersion":0,"tunings":[]}""")
        expectIae("version 2", """{"resonanceVersion":2,"tunings":[]}""")
        expectIae("tunings not array", """{"resonanceVersion":1,"tunings":{}}""")
        expectIae("tuning not object", """{"resonanceVersion":1,"tunings":[42]}""")
        expectIae("missing id", """{"resonanceVersion":1,"tunings":[{"name":"a","strings":["E2"]}]}""")
        expectIae("blank name", """{"resonanceVersion":1,"tunings":[{"id":"a","name":"  ","strings":["E2"]}]}""")
        expectIae("bad note label", """{"resonanceVersion":1,"tunings":[{"id":"a","name":"a","strings":["Q9"]}]}""")
        expectIae("string not string", """{"resonanceVersion":1,"tunings":[{"id":"a","name":"a","strings":[7]}]}""")
        expectIae("empty strings", """{"resonanceVersion":1,"tunings":[{"id":"a","name":"a","strings":[]}]}""")
        // builtIn:true in a file must be skipped, not imported.
        val skipped = TuningPresetJson.decode(
            """{"resonanceVersion":1,"tunings":[{"id":"standard","name":"std","builtIn":true,"strings":["E2"]},""" +
                """{"id":"ok","name":"ok","strings":["E2"]}]}""")
        assertEquals(1, skipped.size)
        assertEquals("ok", skipped[0].id)
    }

    @Test
    fun documentedLimitsAreEnforcedAtTheBoundary() {
        fun strings(n: Int): String = List(n) { i -> "\"E${(i % 4) + 2}\"" }.joinToString(",")
        val mk = { tuningCount: Int, stringCount: Int ->
            val body = (0 until tuningCount).joinToString(",") { i ->
                """{"id":"t$i","name":"n","strings":[${strings(stringCount)}]}"""
            }
            """{"resonanceVersion":1,"tunings":[$body]}"""
        }
        val ok = TuningPresetJson.decode(mk(64, 12))
        assertEquals(64, ok.size)
        assertTrue(ok.all { it.strings.size == 12 })
        decodeOrIae(mk(65, 1))?.let { assertTrue("65 tunings must fail", it.isEmpty()) }
        decodeOrIae(mk(1, 13))?.let { assertTrue("13 strings must fail", it.isEmpty()) }

        // name/id length boundaries
        val name41 = "a".repeat(41)
        decodeOrIae("""{"resonanceVersion":1,"tunings":[{"id":"a","name":"$name41","strings":["E2"]}]}""")
            ?.let { assertTrue("41-char name must fail", it.isEmpty()) }
        val id65 = "b".repeat(65)
        decodeOrIae("""{"resonanceVersion":1,"tunings":[{"id":"$id65","name":"a","strings":["E2"]}]}""")
            ?.let { assertTrue("65-char id must fail", it.isEmpty()) }
        val id64 = "b".repeat(64)
        val okId = TuningPresetJson.decode("""{"resonanceVersion":1,"tunings":[{"id":"$id64","name":"a","strings":["E2"]}]}""")
        assertEquals(1, okId.size)

        // payload size boundary (checked before JSON parsing, so a raw
        // oversized string is the right probe — note: max-count/max-length
        // payloads can't reach 16 KB, the size limit is defense in depth)
        val oversized = "x".repeat(TuningPresetJson.MAX_PAYLOAD_BYTES + 1)
        try {
            TuningPresetJson.decode(oversized)
            throw AssertionError("oversized payload must be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("payload too large: ${oversized.length} > ${TuningPresetJson.MAX_PAYLOAD_BYTES}", e.message)
        }
        // and a maxed-out-but-legal payload decodes
        val big = (0 until 64).joinToString(",") { i ->
            "{\"id\":\"${"b".repeat(60)}$i\",\"name\":\"${"a".repeat(40)}\",\"strings\":[${List(12) { "\"E2\"" }.joinToString(",")}]}"
        }
        val bigPayload = """{"resonanceVersion":1,"tunings":[$big]}"""
        assertTrue(bigPayload.length <= TuningPresetJson.MAX_PAYLOAD_BYTES)
        assertEquals(64, TuningPresetJson.decode(bigPayload).size)
    }
}
