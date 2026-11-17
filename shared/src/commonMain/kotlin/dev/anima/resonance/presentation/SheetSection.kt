package dev.anima.resonance.presentation

/**
 * The three TASK destinations of the one configuration surface.
 *
 * - [TUNING]: choose the instrument tuning (preset list).
 * - [CALIBRATION]: reference pitch + note names.
 * - [MANAGE]: create / edit / import / export custom tunings.
 *
 * The overlay shows a destination strip so the user always knows where they are
 * and can move between the three without a second navigation model.
 */
enum class SheetSection { TUNING, CALIBRATION, MANAGE }

/** Pure mapping for the overlay's entry points and destination strip. */
object SheetFocus {

    /**
     * The deck's primary configuration entry: in Instrument mode the tuning
     * name opens the preset list; in Chromatic mode there is no preset in play,
     * so the same row is the calibration summary and opens calibration.
     */
    fun forInstrumentPresetEntry(mode: TunerMode = TunerMode.INSTRUMENT): SheetSection =
        if (mode == TunerMode.INSTRUMENT) SheetSection.TUNING else SheetSection.CALIBRATION

    /** The settings key: calibration + note names. */
    fun forSettings(): SheetSection = SheetSection.CALIBRATION

    /** A calibration summary row: its own settings destination. */
    fun forCalibration(): SheetSection = SheetSection.CALIBRATION

    /** Custom-tuning management (create / edit / import / export). */
    fun forManage(): SheetSection = SheetSection.MANAGE

    /**
     * True when [section] already renders first in [mode], so opening the
     * overlay needs no scroll at all (the common case must not shift content).
     */
    fun isDefaultTop(mode: TunerMode, section: SheetSection): Boolean =
        if (mode == TunerMode.INSTRUMENT) section == SheetSection.TUNING
        else section == SheetSection.CALIBRATION
}