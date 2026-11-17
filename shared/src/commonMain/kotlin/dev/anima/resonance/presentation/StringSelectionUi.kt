package dev.anima.resonance.presentation

import dev.anima.resonance.music.TuningPreset

/**
 * Presentation mapping for the explicit "Automatic string selection"
 * control.
 *
 * The engine already owns the semantics: `selectString(i)` locks string i, and
 * selecting the already-locked index releases the lock back to AUTO. This
 * object maps the visible On/Off toggle onto exactly those calls, so the control
 * can never show a selection state the engine does not actually have.
 *
 * Compose-free and host-testable (see `StringSelectionUiTest`).
 */
object StringSelectionUi {

    /** Automatic selection is exactly "no manual lock". */
    fun isAutomatic(lockedIndex: Int?): Boolean = lockedIndex == null

    /** Compact state word shown next to the control (never colour alone). */
    fun stateText(lockedIndex: Int?): String = if (lockedIndex == null) "On" else "Off"

    /**
     * The string index the engine must be handed so the desired automatic /
     * manual state becomes true, or null when the expressed intent is a no-op.
     *
     * - turning automatic OFF → lock the detected target (else the first string);
     * - turning automatic ON → hand back the locked index, which releases it.
     */
    fun intent(
        currentlyAuto: Boolean,
        lockedIndex: Int?,
        activeIndex: Int?,
        stringCount: Int,
    ): Int? {
        if (stringCount <= 0) return null
        val valid: (Int?) -> Int? = { idx -> idx?.takeIf { it in 0 until stringCount } }
        if (currentlyAuto) {
            return valid(activeIndex) ?: valid(lockedIndex) ?: 0
        }
        return valid(lockedIndex)
    }

    /** Spoken/visible label for a string target's current role. */
    fun roleText(locked: Boolean, detected: Boolean): String = when {
        locked -> "locked"
        detected -> "detected"
        else -> ""
    }

    /** Accessible description of one string position. */
    fun stringDescription(index: Int, label: String, locked: Boolean, detected: Boolean): String {
        val role = roleText(locked, detected)
        val base = "String ${index + 1}, $label"
        return if (role.isEmpty()) base else "$base, $role"
    }

    /** Accessible description of the string currently used for comparison. */
    fun expectedTargetText(preset: TuningPreset?, activeIndex: Int?): String? {
        if (preset == null || activeIndex == null) return null
        return preset.strings.getOrNull(activeIndex)?.note?.label
    }
}