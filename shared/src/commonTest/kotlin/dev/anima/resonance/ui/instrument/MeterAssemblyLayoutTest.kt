package dev.anima.resonance.ui.instrument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The meter+readout assembly fills its flexible slot up to a natural
 * maximum (arc-filling gauge + a compact readout); any surplus height goes
 * to the lower controls, never to empty bands around the meter. The ivory
 * gauge keeps a natural aspect so the arc fills it (no second empty ivory
 * area). These invariants pin the layout math: calculated from bounds, not
 * stretched.
 */
class MeterAssemblyLayoutTest {

    private val w = 316f

    @Test
    fun fillsTheSlotExactlyWithinCapacity() {
        for (h in listOf(240f, 270f, 300f)) {
            val s = MeterAssemblyLayout.compute(w, h)
            assertEquals(h, s.gaugeHeightDp + s.readoutHeightDp, 0.01f, "must fill slot at h=$h")
        }
    }

    @Test
    fun surplusBeyondCapacityIsCappedNotStretched() {
        val cap = MeterAssemblyLayout.maxHeightDp(w)
        for (h in listOf(cap + 40f, cap + 100f, cap + 200f, cap + 340f)) {
            val s = MeterAssemblyLayout.compute(w, h)
            assertEquals(cap, s.gaugeHeightDp + s.readoutHeightDp, 0.01f, "assembly caps at h=$h")
            assertTrue(s.gaugeHeightDp + s.readoutHeightDp < h, "surplus left for the controls at h=$h")
        }
    }

    @Test
    fun gaugeStaysAtAnArcFillingAspect() {
        for (h in listOf(260f, 300f, 360f, 410f, 560f)) {
            val s = MeterAssemblyLayout.compute(w, h)
            val aspect = w / s.gaugeHeightDp
            // Wide landscape dial (1.5) deepening to 1.2 on tall slots.
            assertTrue(aspect in 1.2f..1.85f, "gauge aspect $aspect out of range at h=$h")
        }
    }

    @Test
    fun readoutStaysCompactAndComfortable() {
        for (h in listOf(260f, 300f, 360f, 410f, 560f, 700f)) {
            val s = MeterAssemblyLayout.compute(w, h)
            assertTrue(s.readoutHeightDp >= MeterAssemblyLayout.MIN_READOUT_DP - 0.01f, "readout too small at h=$h")
            assertTrue(s.readoutHeightDp <= MeterAssemblyLayout.MAX_READOUT_DP + 0.01f, "readout too tall at h=$h")
        }
    }

    @Test
    fun spareHeightGrowsTheReadoutUpToItsCap() {
        val a = MeterAssemblyLayout.compute(w, 290f)
        val b = MeterAssemblyLayout.compute(w, 300f)
        assertTrue(b.readoutHeightDp > a.readoutHeightDp, "taller slot grows the readout")
        val capped = MeterAssemblyLayout.compute(w, 560f)
        assertEquals(MeterAssemblyLayout.MAX_READOUT_DP, capped.readoutHeightDp, 0.01f)
    }

    @Test
    fun noteGlyphScalesWithReadoutButIsCapped() {
        val short = MeterAssemblyLayout.compute(w, 300f)
        val tall = MeterAssemblyLayout.compute(w, 320f)
        assertTrue(tall.noteGlyphSp >= short.noteGlyphSp, "bigger readout => bigger note")
        assertTrue(tall.noteGlyphSp <= MeterAssemblyLayout.MAX_NOTE_SP, "note glyph capped")
        assertTrue(short.noteGlyphSp >= MeterAssemblyLayout.MIN_NOTE_SP, "note glyph floor")
    }

    @Test
    fun normalPhoneGivesADominantGauge() {
        val s = MeterAssemblyLayout.compute(w, 410f)
        assertTrue(s.gaugeHeightDp > 180f, "gauge should dominate the upper portion")
        assertTrue(s.readoutHeightDp >= MeterAssemblyLayout.MIN_READOUT_DP, "readout comfortable")
    }

    @Test
    fun degenerateBoundsAreClampedNotNegative() {
        val s = MeterAssemblyLayout.compute(200f, 120f)
        assertTrue(s.gaugeHeightDp > 0f)
        assertTrue(s.readoutHeightDp > 0f)
        assertEquals(120f, s.gaugeHeightDp + s.readoutHeightDp, 0.01f)
    }
}