package dev.anima.resonance.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuningPresetTest {

    private val c = 440.0

    @Test
    fun standardTuningFrequenciesLowToHigh() {
        val freqs = BuiltInTunings.standard.strings.map { it.note.frequency(c) }
        val expected = listOf(82.4069, 110.0000, 146.8324, 195.9977, 246.9417, 329.6276)
        assertEquals(expected.size, freqs.size)
        expected.forEachIndexed { i, f -> assertEquals(f, freqs[i], 1e-3) }
    }

    @Test
    fun standardLabelsAreUnambiguous() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"),
            BuiltInTunings.standard.strings.map { it.note.label })
    }

    @Test
    fun dropDLowersOnlyFirstString() {
        val d = BuiltInTunings.dropD
        assertEquals(Note("D", 2).frequency(c), d.strings[0].note.frequency(c), 1e-9)
        (1 until 6).forEach { i ->
            assertEquals(BuiltInTunings.standard.strings[i].note, d.strings[i].note)
        }
    }

    @Test
    fun dadgadLabels() {
        assertEquals(listOf("D2", "A2", "D3", "G3", "A3", "D4"),
            BuiltInTunings.dadgad.strings.map { it.note.label })
    }

    @Test
    fun openDUsesFSharp3() {
        val o = BuiltInTunings.openD
        assertEquals(listOf("D2", "A2", "D3", "F#3", "A3", "D4"),
            o.strings.map { it.note.label })
        assertEquals(184.9972, o.strings[3].note.frequency(c), 1e-3)
    }

    @Test
    fun openGLabels() {
        assertEquals(listOf("D2", "G2", "D3", "G3", "B3", "D4"),
            BuiltInTunings.openG.strings.map { it.note.label })
    }

    @Test
    fun halfStepDownIsWholeSetTransposedDown() {
        assertEquals(listOf("Eb2", "Ab2", "Db3", "Gb3", "Bb3", "Eb4"),
            BuiltInTunings.halfStepDown.strings.map { it.note.label })
    }

    @Test
    fun allBuiltInsAreSixStringsLowToHigh() {
        assertTrue(BuiltInTunings.all.isNotEmpty())
        BuiltInTunings.all.forEach { preset ->
            assertEquals(6, preset.strings.size, preset.id)
            val f = preset.strings.map { it.note.frequency(c) }
            for (i in 1 until f.size) assertTrue(f[i] > f[i - 1], "${preset.id} not low-to-high at $i")
        }
    }

    @Test
    fun builtInIdsAreDistinctAndStable() {
        val ids = BuiltInTunings.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(setOf("standard", "drop-d", "dadgad", "open-d", "open-g", "half-step-down"), ids.toSet())
        BuiltInTunings.all.forEach { assertTrue(it.builtIn) }
        // string ids stable and unique within a preset
        BuiltInTunings.all.forEach { p ->
            assertEquals(p.strings.size, p.strings.map { it.id }.toSet().size, p.id)
        }
    }

    @Test
    fun presetFrequenciesFollowConcertPitch() {
        val e2 = BuiltInTunings.standard.strings[0].note
        assertEquals(e2.frequency(432.0), BuiltInTunings.standard.strings[0].note.frequency(432.0), 1e-12)
        // derived, not literal: ratio must equal the concert-pitch ratio
        val ratio = BuiltInTunings.standard.strings[0].note.frequency(442.0) /
            BuiltInTunings.standard.strings[0].note.frequency(440.0)
        assertEquals(442.0 / 440.0, ratio, 1e-12)
    }
}
