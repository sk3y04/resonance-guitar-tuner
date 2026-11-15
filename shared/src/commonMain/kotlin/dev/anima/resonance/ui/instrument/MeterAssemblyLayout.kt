package dev.anima.resonance.ui.instrument

/**
 * Pure layout math for the meter+readout measurement assembly.
 *
 * The assembly fills its flexible slot up to a natural maximum — an
 * arc-filling ivory gauge plus a compact readout. Spare height grows the
 * readout (a larger note) up to a cap; any surplus beyond the cap is left
 * for the lower controls, never as empty bands around the meter. The gauge
 * keeps a natural width-driven aspect so the semicircular scale fills it
 * (no second empty ivory area). Calculated from bounds, not stretched.
 *
 * No Compose imports: this is host-testable presentation logic.
 */
object MeterAssemblyLayout {
    const val MIN_READOUT_DP = 76f
    /** Compact readout — sized to its content, not a tall empty panel. */
    const val MAX_READOUT_DP = 104f
    const val MIN_NOTE_SP = 40f
    const val MAX_NOTE_SP = 96f

    /** Natural gauge width:height — the semicircular scale fills this. */
    const val GAUGE_ASPECT = 1.6f
    private const val MIN_GAUGE_ASPECT = 1.4f
    private const val MAX_GAUGE_ASPECT = 1.8f

    data class Spec(
        val gaugeHeightDp: Float,
        val readoutHeightDp: Float,
        val noteGlyphSp: Float,
    )

    /** Tallest the assembly grows for a given width at the compact cap. */
    fun maxHeightDp(widthDp: Float): Float =
        widthDp.coerceAtLeast(1f) / GAUGE_ASPECT + MAX_READOUT_DP

    /**
     * @param readoutCapDp how tall the readout may grow to absorb spare height.
     *   Instrument mode uses the compact cap (surplus goes to the string keys);
     *   chromatic mode (no string grid) lets the readout grow further.
     */
    fun compute(widthDp: Float, heightDp: Float, readoutCapDp: Float = MAX_READOUT_DP): Spec {
        val w = widthDp.coerceAtLeast(1f)
        val h = heightDp.coerceAtLeast(1f)
        val cap = readoutCapDp.coerceAtLeast(MIN_READOUT_DP)
        val naturalGauge = w / GAUGE_ASPECT
        val usedH = minOf(h, naturalGauge + cap)

        // The readout absorbs spare height first, up to its cap.
        var readout = (usedH - naturalGauge).coerceIn(MIN_READOUT_DP, cap)
        var gauge = usedH - readout
        // Keep the gauge at an arc-filling aspect (never a tall empty face).
        gauge = gauge.coerceIn(w / MAX_GAUGE_ASPECT, w / MIN_GAUGE_ASPECT)
        readout = (usedH - gauge).coerceAtLeast(20f)
        gauge = usedH - readout

        val note = (readout * 0.62f).coerceIn(MIN_NOTE_SP, MAX_NOTE_SP)
        return Spec(gaugeHeightDp = gauge, readoutHeightDp = readout, noteGlyphSp = note)
    }
}