package dev.anima.resonance.ui.instrument

import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure geometry for the instrument's control block.
 *
 * The block is a stack of rows on ONE surface (no card): mode -> active tuning ->
 * auto-select -> string targets -> reference summary. Its height is derived from
 * its rows only, so the meter reservation is honest in both modes; in Chromatic
 * mode the block is simply shorter instead of reserving a blank instrument panel.
 *
 * No Compose imports: host-testable (see `ControlDeckLayoutTest`).
 */
object ControlDeckLayout {

    /** Touch floor for every interactive row/position (Android 48dp). */
    const val TOUCH_MIN_DP = 48f

    /** Mode row: 48dp keys inside a 3dp milled track inset. */
    const val MODE_ROW_DP = 54f
    /** Active tuning: label + name on two lines. */
    const val SETTINGS_ROW_DP = 56f
    const val AUTO_ROW_DP = 48f
    const val RAIL_ROW_DP = 48f
    /** Quiet reference-pitch row. */
    const val CALIBRATION_ROW_DP = 48f

    /** Spacing inside a related group. */
    const val GAP_TIGHT_DP = 12f
    /** Spacing between major groups (selection behaviour -> string targets). */
    const val GAP_GROUP_DP = 16f
    /** Gap inside the string row before the first/last position. */
    const val RAIL_INSET_DP = 0f
    /** Gap between two string-target rows in the narrow-screen grid. */
    const val RAIL_ROW_GAP_DP = 6f
    /** Gap between equal-width string targets inside one row. */
    const val STRING_GAP_DP = 4f

    const val PAD_TOP_DP = 16f
    const val PAD_BOTTOM_DP = 16f

    private const val MAX_RAIL_SLOTS = 8

    /**
     * Rail positions reserved when neither the visible targets nor the preset's
     * string count is known (a guitar tuning). Keeps the meter reservation
     * stable instead of collapsing on incomplete state.
     */
    const val DEFAULT_RESERVED_STRINGS = 6

    /** Vertical padding inside the block (top + bottom). */
    const val deckPaddingDp: Float = PAD_TOP_DP + PAD_BOTTOM_DP

    /** Height of a rail carrying [rows] rows of positions. */
    fun railHeightDp(rows: Int): Float {
        if (rows <= 0) return 0f
        return RAIL_ROW_DP * rows + RAIL_ROW_GAP_DP * (rows - 1)
    }

    /** Usable width inside the string row. */
    fun railInnerWidthDp(widthDp: Float): Float = (widthDp - 2f * RAIL_INSET_DP).coerceAtLeast(1f)

    /** Positions placed side by side for a given panel [widthDp]. */
    fun perRowSlots(widthDp: Float, strings: Int): Int {
        if (strings <= 0) return 0
        val n = strings.coerceAtMost(MAX_RAIL_SLOTS)
        for (slots in n downTo 1) {
            if (cellWidthDp(widthDp, slots) >= TOUCH_MIN_DP) return slots
        }
        return 1
    }

    /** Width of one position when [slots] share the string row [widthDp]. */
    fun cellWidthDp(widthDp: Float, slots: Int): Float {
        if (slots <= 0) return 0f
        val inner = railInnerWidthDp(widthDp)
        val gaps = STRING_GAP_DP * (slots - 1)
        return ((inner - gaps) / slots).coerceAtLeast(0f)
    }

    /** Width of one position when the tuning's [strings] share the row. */
    fun stringCellWidthDp(widthDp: Float, strings: Int): Float =
        cellWidthDp(widthDp, perRowSlots(widthDp, strings))

    /** Rows the string selector needs: one when every position keeps a 48dp target. */
    fun railRows(widthDp: Float, strings: Int): Int {
        if (strings <= 0) return 0
        val slots = perRowSlots(widthDp, strings)
        if (slots <= 0) return 0
        return ceil(strings.toFloat() / slots.toFloat()).toInt().coerceAtLeast(1)
    }

    /** Content height of the instrument block for a string row of [railRows]. */
    fun instrumentContentHeightDp(railRows: Int, includeCalibration: Boolean = true): Float =
        MODE_ROW_DP + GAP_TIGHT_DP +
            SETTINGS_ROW_DP + GAP_TIGHT_DP +
            AUTO_ROW_DP + GAP_GROUP_DP +
            railHeightDp(railRows) +
            (if (includeCalibration) GAP_TIGHT_DP + CALIBRATION_ROW_DP else 0f)

    /**
     * Total block height for a tuning of [strings] positions. `compact` drops
     * only the quiet reference row, which keeps the meter intact on short
     * screens instead of compressing every row.
     */
    fun heightDp(strings: Int, widthDp: Float, compact: Boolean = false): Float {
        val rows = max(railRows(widthDp, strings), 0)
        return instrumentContentHeightDp(rows, includeCalibration = !compact) + deckPaddingDp
    }
}
