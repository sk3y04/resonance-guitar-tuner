package dev.anima.resonance.music

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Versioned JSON codec for tuning presets (local import/export).
 *
 * Format (v1):
 * ```
 * { "resonanceVersion": 1,
 *   "tunings": [ { "id": "…", "name": "…", "strings": ["E2", "A#", …] } ] }
 * ```
 *
 * Contract: imported payloads are UNTRUSTED. Hard limits: payload size,
 * tuning count, strings per tuning, name/id length, schema version.
 * Decoding never overwrites [existing] presets: id collisions rename the
 * IMPORT deterministically ("x" -> "x-import", "x-import2", …). Payload
 * entries carrying `builtIn: true` are skipped — built-ins always come from
 * code, never from user files. Imported presets are never marked built-in.
 */
object TuningPresetJson {

    const val FORMAT_VERSION = 1
    const val MAX_PAYLOAD_BYTES = 16_384
    const val MAX_TUNINGS = 64
    const val MAX_STRINGS = 12
    const val MAX_NAME_LENGTH = 40
    const val MAX_ID_LENGTH = 64

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(tunings: List<TuningPreset>): String {
        val root = JsonObject(
            buildMap {
                put("resonanceVersion", JsonPrimitive(FORMAT_VERSION))
                put(
                    "tunings",
                    JsonArray(
                        tunings.map { p ->
                            JsonObject(
                                buildMap {
                                    put("id", JsonPrimitive(p.id))
                                    put("name", JsonPrimitive(p.name))
                                    if (p.builtIn) put("builtIn", JsonPrimitive(true))
                                    put("strings", JsonArray(p.strings.map { JsonPrimitive(it.note.label) }))
                                },
                            )
                        },
                    ),
                )
            },
        )
        return Json.encodeToString(JsonObject.serializer(), root)
    }

    fun decode(payload: String, existing: List<TuningPreset> = emptyList()): List<TuningPreset> {
        require(payload.length <= MAX_PAYLOAD_BYTES) {
            "payload too large: ${payload.length} > $MAX_PAYLOAD_BYTES"
        }
        val root = try {
            json.parseToJsonElement(payload).jsonObject
        } catch (e: JsonException) {
            throw IllegalArgumentException("malformed JSON payload: ${e.message}", e)
        }

        val version = try {
            (root["resonanceVersion"] as? JsonPrimitive)?.int
        } catch (e: JsonException) {
            throw IllegalArgumentException("resonanceVersion must be an integer", e)
        }
        ?: throw IllegalArgumentException("missing resonanceVersion")
        require(version == FORMAT_VERSION) {
            "unsupported resonanceVersion $version (supported: $FORMAT_VERSION)"
        }

        val tunings = root["tunings"]?.jsonArray
            ?: throw IllegalArgumentException("missing 'tunings' array")
        require(tunings.size <= MAX_TUNINGS) {
            "too many tunings: ${tunings.size} > $MAX_TUNINGS"
        }

        val taken = existing.map { it.id }.toMutableSet()
        return tunings.map { el ->
            val o = el.jsonObject

            // Built-ins are never imported from files.
            if (o["builtIn"] is JsonPrimitive && (o["builtIn"] as JsonPrimitive).booleanOrNull == true) {
                return@map null
            }

            val rawId = (o["id"] as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("tuning missing string 'id'")
            require(rawId.length in 1..MAX_ID_LENGTH) { "id length out of bounds (1..$MAX_ID_LENGTH): ${rawId.length}" }

            val name = (o["name"] as? JsonPrimitive)?.contentOrNull
                ?: throw IllegalArgumentException("tuning '$rawId' missing string 'name'")
            require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
                "name out of bounds for '$rawId' (non-blank, <= $MAX_NAME_LENGTH)"
            }

            val stringsEl = o["strings"]?.jsonArray
                ?: throw IllegalArgumentException("tuning '$rawId' missing 'strings' array")
            require(stringsEl.size in 1..MAX_STRINGS) {
                "string count out of bounds for '$rawId' (1..$MAX_STRINGS): ${stringsEl.size}"
            }

            val id = uniqueId(rawId, taken)
            TuningPreset(
                id = id,
                name = name,
                builtIn = false,
                strings = stringsEl.mapIndexed { i, s ->
                    val label = (s as? JsonPrimitive)?.contentOrNull
                        ?: throw IllegalArgumentException("tuning '$rawId' strings[$i] is not a string")
                    TuningString(id = "$id-s${i + 1}", note = Note.fromLabel(label))
                },
            )
        }.filterNotNull()
    }

    internal fun uniqueId(wanted: String, taken: MutableSet<String>): String {
        if (wanted !in taken) {
            taken += wanted
            return wanted
        }
        var n = 1
        var candidate = "${wanted}-import"
        while (candidate in taken) {
            n++
            candidate = "${wanted}-import$n"
        }
        taken += candidate
        return candidate
    }
}
