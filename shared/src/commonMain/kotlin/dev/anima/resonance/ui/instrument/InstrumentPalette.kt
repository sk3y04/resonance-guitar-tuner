package dev.anima.resonance.ui.instrument

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.anima.resonance.ui.tabularNumerals

/**
 * The physical-instrument palette — dark walnut enclosure, warm ivory
 * meter face, charcoal markings, muted brass hardware, restrained jewel
 * lamps. Deliberately IDENTICAL in system light and dark mode: a wooden
 * instrument does not change its materials with the room lighting. The
 * Material scheme (which still adapts) drives dialogs and text fields.
 *
 * Every information-carrying combination is floored by
 * `InstrumentPaletteContrastTest` (WCAG AA: 4.5:1 text, 3:1 graphics).
 */
object InstrumentPalette {
    // Enclosure — dark walnut
    val walnut = Color(0xFF33221A)
    val walnutDeep = Color(0xFF241610)
    val walnutLight = Color(0xFF4A3323)
    val grainDark = Color(0xFF1A0F09)
    val grainLight = Color(0xFF5E4530)

    // Recessed panels (readout, control slots) — near-black warm brown
    val recess = Color(0xFF1D130C)
    val recessEdge = Color(0xFF0F0906)

    // Meter face — warm ivory, charcoal ink
    val ivory = Color(0xFFF1E8D4)
    val ivoryEdge = Color(0xFFDDD0B4)
    val ink = Color(0xFF2A241C)
    val inkSoft = Color(0xFF6B6152)

    // Hardware — muted brass
    val brass = Color(0xFFB08D4F)
    val brassLight = Color(0xFFD6B672)
    val brassDark = Color(0xFF6E5527)

    // Status jewels (always paired with text/shape, never color alone)
    val lampGreen = Color(0xFF9FCF8B)
    val lampAmber = Color(0xFFE3B45C)
    val lampRed = Color(0xFFE2795B)
    val lampOff = Color(0xFF4A3B2E)

    // In-tune band painted on the ivory face (darker than the jewel so it
    // keeps graphic contrast on the light surface).
    val bandGreen = Color(0xFF4F7A4A)

    // Text on wood/recess
    val textMuted = Color(0xFFC9B99B)
}

/** Instrument materials for the current composition (see [InstrumentPalette]). */
val LocalInstrumentPalette = staticCompositionLocalOf { InstrumentPalette }

/** Convenience accessor mirroring MaterialTheme.colorScheme usage. */
object InstrumentTheme {
    val palette: InstrumentPalette
        @Composable get() = LocalInstrumentPalette.current
}

/**
 * Instrument typography: engraved small-caps-style labels and tabular
 * readouts. sp-based so system font scaling applies; colors come from the
 * palette (never from the light/dark Material scheme).
 */
object InstrumentType {
    val engraved: TextStyle
        @Composable get() = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.6.sp,
            color = InstrumentTheme.palette.textMuted,
        )
    val engravedStrong: TextStyle
        @Composable get() = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = InstrumentTheme.palette.ivory,
        )
    val readout: TextStyle
        @Composable get() = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = InstrumentTheme.palette.ivory,
        ).tabularNumerals
    val readoutSmall: TextStyle
        @Composable get() = TextStyle(
            fontSize = 14.sp,
            color = InstrumentTheme.palette.textMuted,
        ).tabularNumerals
    val keyLabel: TextStyle
        @Composable get() = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = InstrumentTheme.palette.ivory,
        )
}