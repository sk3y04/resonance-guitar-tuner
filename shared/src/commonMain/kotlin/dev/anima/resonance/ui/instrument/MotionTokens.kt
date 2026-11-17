package dev.anima.resonance.ui.instrument

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The instrument's shared motion system.
 *
 * Four durations, two easings, one rule: feedback starts on the first frame
 * after input, every animation is an interruptible tween (repeated taps retarget
 * the running animation instead of queueing), and nothing bounces. Call sites
 * pick a ROLE (press / selection / content / sheet), never an arbitrary number.
 *
 * Reduced motion: when the platform reports that animations are disabled, every
 * spec collapses to an immediate snap — state still changes, it just does not
 * travel. The needle's critically-damped spring is measurement smoothing, not
 * decoration; it snaps too under reduced motion (the tracker already limits its
 * lag, so a snapped needle stays truthful).
 */
object MotionTokens {
    /** Press-state tonal change: visible almost immediately (80–140 ms band). */
    const val PRESS_MS = 100

    /** Small selection/color transitions (140–220 ms band). */
    const val SELECTION_MS = 170

    /** Content transitions: mode body, settings destination (180–280 ms band). */
    const val CONTENT_MS = 220

    /** Sheet/dialog transitions — the only large motion (220–350 ms band). */
    const val SHEET_MS = 300

    /** Controls settle crisply; no overshoot. */
    val controlEasing: Easing = FastOutSlowInEasing

    /** The sheet decelerates into place like a damped physical panel. */
    val sheetEasing: Easing = EaseOutCubic

    fun <T> press(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(PRESS_MS, easing = controlEasing)

    fun <T> selection(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(SELECTION_MS, easing = controlEasing)

    fun <T> content(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(CONTENT_MS, easing = controlEasing)

    fun <T> sheet(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(SHEET_MS, easing = sheetEasing)
}

/**
 * True when the user/platform asked for reduced motion. Provided once by
 * [dev.anima.resonance.ui.ResonanceTheme] from the platform seam below;
 * defaults to false so headless renders and tests see full motion.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Platform seam: does the system ask for reduced motion?
 * Android reads the global animator duration scale (0 = animations off, the
 * accessibility/developer setting); the JVM host has no such preference.
 */
@Composable
expect fun systemReducedMotion(): Boolean
