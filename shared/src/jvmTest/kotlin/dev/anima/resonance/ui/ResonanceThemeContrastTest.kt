package dev.anima.resonance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deterministic contrast floor for the two shipped themes.
 *
 * This is not a substitute for inspecting rendered screenshots, but it does
 * catch the class of defect the original light theme had: dark-theme pastel
 * accents reused on a white background, and typography with a hardcoded
 * near-white color. Text combinations are held to WCAG AA 4.5:1; graphical
 * indicators to 3:1.
 */
class ResonanceThemeContrastTest {

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
    fun darkThemeMeetsContrastFloors() {
        val s = resonanceDarkScheme()
        assertText("primary text/background", s.onBackground, s.background)
        assertText("primary text/surface", s.onSurface, s.surface)
        assertText("secondary text/background", s.onSurfaceVariant, s.background)
        assertText("secondary text/surface", s.onSurfaceVariant, s.surface)
        assertText("accent text/background", s.primary, s.background)
        assertText("in-tune text/background", s.secondary, s.background)
        assertText("error text/surface", s.error, s.surface)
        assertText("on-primary/primary", s.onPrimary, s.primary)
        assertGraphic("accent meter/background", s.primary, s.background)
        assertGraphic("in-tune zone/background", s.secondary, s.background)
    }

    @Test
    fun lightThemeMeetsContrastFloors() {
        val s = resonanceLightScheme()
        assertText("primary text/background", s.onBackground, s.background)
        assertText("primary text/surface", s.onSurface, s.surface)
        assertText("secondary text/background", s.onSurfaceVariant, s.background)
        assertText("secondary text/surface", s.onSurfaceVariant, s.surface)
        assertText("accent text/background", s.primary, s.background)
        assertText("in-tune text/background", s.secondary, s.background)
        assertText("error text/surface", s.error, s.surface)
        assertText("on-primary/primary", s.onPrimary, s.primary)
        assertGraphic("accent meter/background", s.primary, s.background)
        assertGraphic("in-tune zone/background", s.secondary, s.background)
    }
}