package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The explicit "Automatic string selection" affordance maps onto the
 * EXISTING engine semantics — `selectString(i)` locks string i, and selecting
 * the already-locked index releases the lock (AUTO). This pure mapping keeps
 * the visible toggle honest: the UI never invents a selection state the engine
 * does not have.
 */
class StringSelectionUiTest {

    @Test
    fun turningAutomaticOffLocksTheDetectedString() {
        assertEquals(
            2,
            StringSelectionUi.intent(
                currentlyAuto = true,
                lockedIndex = null,
                activeIndex = 2,
                stringCount = 6,
            ),
        )
    }

    @Test
    fun turningAutomaticOffWithoutADetectedStringLocksTheFirstString() {
        assertEquals(
            0,
            StringSelectionUi.intent(
                currentlyAuto = true,
                lockedIndex = null,
                activeIndex = null,
                stringCount = 6,
            ),
        )
    }

    @Test
    fun turningAutomaticOnReleasesTheExistingLock() {
        assertEquals(
            3,
            StringSelectionUi.intent(
                currentlyAuto = false,
                lockedIndex = 3,
                activeIndex = 3,
                stringCount = 6,
            ),
        )
    }

    @Test
    fun turningAutomaticOnWithoutALockIsANoOp() {
        assertNull(
            StringSelectionUi.intent(
                currentlyAuto = false,
                lockedIndex = null,
                activeIndex = 4,
                stringCount = 6,
            ),
        )
    }

    @Test
    fun aTuningWithNoStringsNeverProducesAnIndex() {
        assertNull(
            StringSelectionUi.intent(
                currentlyAuto = true,
                lockedIndex = null,
                activeIndex = null,
                stringCount = 0,
            ),
        )
    }

    @Test
    fun autoIsExactlyTheAbsenceOfAManualLock() {
        assertEquals(true, StringSelectionUi.isAutomatic(lockedIndex = null))
        assertEquals(false, StringSelectionUi.isAutomatic(lockedIndex = 0))
    }

    @Test
    fun toggleStateDescriptionFollowsTheRealSelectionState() {
        assertEquals("On", StringSelectionUi.stateText(lockedIndex = null))
        assertEquals("Off", StringSelectionUi.stateText(lockedIndex = 2))
    }
}