package dev.anima.resonance.ui.instrument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure geometry for the instrument control block. The block is a stack of
 * rows on one surface; its height comes from its own rows (no mode floor, no
 * reserved blank panel), and the string targets keep 48dp widths by wrapping
 * rather than shrinking.
 */
class ControlDeckLayoutTest {

    private val phone = 336f   // 360dp screen − enclosure inset − body gutter
    private val wide = 480f

    @Test
    fun theBlockIsExactlyAsTallAsItsRows() {
        assertEquals(
            ControlDeckLayout.instrumentContentHeightDp(railRows = 1) + ControlDeckLayout.deckPaddingDp,
            ControlDeckLayout.heightDp(strings = 6, widthDp = phone),
        )
    }

    @Test
    fun theBlockStacksEveryInteractiveRowAtItsTouchFloor() {
        val rows = listOf(
            ControlDeckLayout.MODE_ROW_DP,
            ControlDeckLayout.SETTINGS_ROW_DP,
            ControlDeckLayout.AUTO_ROW_DP,
            ControlDeckLayout.RAIL_ROW_DP,
            ControlDeckLayout.CALIBRATION_ROW_DP,
        )
        rows.forEach { h ->
            assertTrue(h >= ControlDeckLayout.TOUCH_MIN_DP, "row height $h below the 48dp touch floor")
        }
    }

    @Test
    fun sixStringsOnAPhoneFitInOneRow() {
        assertEquals(1, ControlDeckLayout.railRows(widthDp = phone, strings = 6))
        assertEquals(1, ControlDeckLayout.railRows(widthDp = wide, strings = 6))
    }

    @Test
    fun aNarrowSelectorFallsBackToTwoRowsInsteadOfShrinkingTargets() {
        assertEquals(2, ControlDeckLayout.railRows(widthDp = 240f, strings = 6))
        assertTrue(ControlDeckLayout.stringCellWidthDp(widthDp = 240f, strings = 6) > 0f)
    }

    @Test
    fun targetWidthNeverDropsBelowTheTouchFloorWhenItFits() {
        val w = ControlDeckLayout.stringCellWidthDp(widthDp = phone, strings = 6)
        assertTrue(w >= ControlDeckLayout.TOUCH_MIN_DP, "cell width $w below the 48dp touch floor")
    }

    @Test
    fun aTallerSelectorGrowsTheBlockSoTheMeterCannotOverlapIt() {
        val oneRow = ControlDeckLayout.heightDp(strings = 6, widthDp = phone)
        val twoRows = ControlDeckLayout.heightDp(strings = 6, widthDp = 240f)
        assertTrue(twoRows > oneRow)
    }

    @Test
    fun aTuningWithNoStringsReservesNoStringRow() {
        // Chromatic / no-string state: the block keeps mode + tuning rows only.
        assertEquals(
            ControlDeckLayout.instrumentContentHeightDp(railRows = 0) + ControlDeckLayout.deckPaddingDp,
            ControlDeckLayout.heightDp(strings = 0, widthDp = phone),
        )
        assertTrue(
            ControlDeckLayout.heightDp(strings = 0, widthDp = phone) <
                ControlDeckLayout.heightDp(strings = 6, widthDp = phone),
        )
    }

    @Test
    fun aShortScreenDropsOnlyTheQuietReferenceRow() {
        val full = ControlDeckLayout.heightDp(strings = 6, widthDp = phone)
        val compact = ControlDeckLayout.heightDp(strings = 6, widthDp = phone, compact = true)
        assertTrue(compact < full)
        assertEquals(
            ControlDeckLayout.CALIBRATION_ROW_DP + ControlDeckLayout.GAP_TIGHT_DP,
            full - compact,
        )
    }

    @Test
    fun railHeightIsTheRowHeightPlusItsGap() {
        assertEquals(
            ControlDeckLayout.RAIL_ROW_DP * 2 + ControlDeckLayout.RAIL_ROW_GAP_DP,
            ControlDeckLayout.railHeightDp(rows = 2),
        )
    }
}
