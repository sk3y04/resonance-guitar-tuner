package dev.anima.resonance.ui.instrument

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The shared motion system. One small set of durations/easings for the
 * whole instrument instead of arbitrary tweens at each call site, and every
 * spec collapses to an immediate snap when the user asks for reduced motion.
 *
 * The durations are tuned for a phone: press feedback must be visible within
 * ~100 ms, selection changes settle quickly, content transitions stay short,
 * and the settings sheet is the only "large" motion. Nothing bounces.
 */
class MotionTokensTest {

    @Test
    fun pressFeedbackIsImmediateButVisible() {
        assertTrue(MotionTokens.PRESS_MS in 80..140, "press was ${MotionTokens.PRESS_MS}")
    }

    @Test
    fun selectionTransitionsAreShort() {
        assertTrue(MotionTokens.SELECTION_MS in 140..220, "selection was ${MotionTokens.SELECTION_MS}")
    }

    @Test
    fun contentTransitionsAreShort() {
        assertTrue(MotionTokens.CONTENT_MS in 180..280, "content was ${MotionTokens.CONTENT_MS}")
    }

    @Test
    fun sheetTransitionsAreTheOnlyLargeMotion() {
        assertTrue(MotionTokens.SHEET_MS in 220..350, "sheet was ${MotionTokens.SHEET_MS}")
        assertTrue(MotionTokens.SHEET_MS > MotionTokens.CONTENT_MS)
        assertTrue(MotionTokens.CONTENT_MS > MotionTokens.SELECTION_MS)
        assertTrue(MotionTokens.SELECTION_MS > MotionTokens.PRESS_MS)
    }

    @Test
    fun normalMotionUsesInterruptibleTweensWithTheTokenDurations() {
        val press = MotionTokens.press<Float>(reducedMotion = false)
        val selection = MotionTokens.selection<Float>(reducedMotion = false)
        val content = MotionTokens.content<Float>(reducedMotion = false)
        val sheet = MotionTokens.sheet<Float>(reducedMotion = false)
        assertIs<TweenSpec<Float>>(press)
        assertIs<TweenSpec<Float>>(selection)
        assertIs<TweenSpec<Float>>(content)
        assertIs<TweenSpec<Float>>(sheet)
        assertEquals(MotionTokens.PRESS_MS, press.durationMillis)
        assertEquals(MotionTokens.SELECTION_MS, selection.durationMillis)
        assertEquals(MotionTokens.CONTENT_MS, content.durationMillis)
        assertEquals(MotionTokens.SHEET_MS, sheet.durationMillis)
        // No delay: feedback starts on the very first frame after input.
        assertEquals(0, press.delay)
        assertEquals(0, selection.delay)
        assertEquals(0, content.delay)
        assertEquals(0, sheet.delay)
    }

    @Test
    fun reducedMotionSnapsEverySpecImmediately() {
        val specs: List<FiniteAnimationSpec<Float>> = listOf(
            MotionTokens.press(reducedMotion = true),
            MotionTokens.selection(reducedMotion = true),
            MotionTokens.content(reducedMotion = true),
            MotionTokens.sheet(reducedMotion = true),
        )
        specs.forEach { spec ->
            assertIs<SnapSpec<Float>>(spec, "reduced motion must snap, was $spec")
            assertEquals(0, spec.delay)
        }
    }

    @Test
    fun specsAreTypedForColorAndFloatAlike() {
        // The same tokens drive color transitions (selection fills, lamps) and
        // float transitions (alpha, offsets) — one system, not per-type values.
        val color = MotionTokens.selection<androidx.compose.ui.graphics.Color>(reducedMotion = false)
        assertIs<TweenSpec<androidx.compose.ui.graphics.Color>>(color)
        assertEquals(MotionTokens.SELECTION_MS, color.durationMillis)
    }
}
