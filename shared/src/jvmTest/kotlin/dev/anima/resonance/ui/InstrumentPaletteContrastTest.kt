package dev.anima.resonance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.anima.resonance.ui.instrument.InstrumentPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contrast floors for the physical-instrument palette. The walnut
 * enclosure, ivory meter face and recessed control panels carry real
 * information (labels, readouts, status lamps), so the same WCAG discipline
 * as [ResonanceThemeContrastTest] applies: 4.5:1 for text, 3:1 for graphics.
 * Decorative shading pairs (ivory-on-ivory gradients, brass bevels) are
 * intentionally not information carriers and are not floored here.
 */
class InstrumentPaletteContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertText(label: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$label contrast must be >= 4.5:1, was ${"%.2f".format(r)}")
    }

    private fun assertGraphic(label: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 3.0, "$label contrast must be >= 3:1, was ${"%.2f".format(r)}")
    }

    @Test
    fun meterFaceMarkingsAreLegibleOnIvory() {
        val p = InstrumentPalette
        assertText("charcoal ink/ivory", p.ink, p.ivory)
        assertText("secondary ink/ivory", p.inkSoft, p.ivory)
        assertGraphic("needle/ivory", p.ink, p.ivory)
        assertText("engraved brass label/ivory", p.brassDark, p.ivory)
        assertGraphic("in-tune band/ivory", p.bandGreen, p.ivory)
    }

    @Test
    fun readoutsAreLegibleOnWoodAndRecessedPanels() {
        val p = InstrumentPalette
        assertText("ivory text/walnut", p.ivory, p.walnut)
        assertText("ivory text/recess", p.ivory, p.recess)
        assertText("muted text/recess", p.textMuted, p.recess)
        assertText("muted text/walnut", p.textMuted, p.walnut)
    }

    @Test
    fun hardwareTrimIsVisibleAgainstTheEnclosure() {
        val p = InstrumentPalette
        assertGraphic("brass/walnut", p.brass, p.walnut)
        assertGraphic("brass highlight/walnut", p.brassLight, p.walnut)
    }

    @Test
    fun theWoodenControlDeckCarriesItsControlsAndLabels() {
        val p = InstrumentPalette
        // The deck is wood, not a dark card — its information still has to
        // clear the same floors as the enclosure.
        assertText("ivory text/control deck", p.ivory, p.deck)
        assertText("muted text/control deck", p.textMuted, p.deck)
        assertText("ivory text/deck shadow side", p.ivory, p.deckDeep)
        assertText("muted text/deck shadow side", p.textMuted, p.deckDeep)
        assertGraphic("brass underline/control deck", p.brass, p.deck)
    }

    @Test
    fun theStringRailPositionsAndTheirSelectedStatesAreLegible() {
        val p = InstrumentPalette
        assertText("ivory position/rail", p.ivory, p.rail)
        assertText("muted octave/rail", p.textMuted, p.rail)
        // A locked position is the one ivory control surface: dark ink on ivory.
        assertText("locked ink/ivory position", p.ink, p.ivory)
        assertText("locked octave ink/ivory position", p.inkSoft, p.ivory)
        assertGraphic("brass detected marker/rail", p.brass, p.rail)
    }

    @Test
    fun statusLampsAreVisibleOnTheRecessedPanel() {
        val p = InstrumentPalette
        assertGraphic("in-tune lamp/recess", p.lampGreen, p.recess)
        assertGraphic("caution lamp/recess", p.lampAmber, p.recess)
        assertGraphic("error lamp/recess", p.lampRed, p.recess)
    }

    @Test
    fun theFaceplateGradientKeepsEveryInformationPairLegible() {
        val p = InstrumentPalette
        // The deck is no longer one flat hex — it shades from a slightly
        // lighter top to a deeper bottom. BOTH ends (and the pressed layer)
        // must carry the same text/graphics floors as the anchor colour.
        assertText("primary text/deck top light", p.textPrimary, p.deckTop)
        assertText("secondary text/deck top light", p.textSecondary, p.deckTop)
        assertText("primary text/deck bottom shade", p.textPrimary, p.deckBottom)
        assertText("secondary text/deck bottom shade", p.textSecondary, p.deckBottom)
        assertText("primary text/pressed layer", p.textPrimary, p.segmentCap)
        assertText("secondary text/pressed layer", p.textSecondary, p.segmentCap)
        assertGraphic("brass marker/deck top light", p.brass, p.deckTop)
        assertGraphic("brass marker/deck bottom shade", p.brass, p.deckBottom)
        // The selected parchment key carries dark ink — never colour alone.
        assertText("selected ink/parchment key", p.accentInk, p.accent)
        assertText("selected ink/pressed parchment key", p.accentInk, p.accentPressed)
    }
}