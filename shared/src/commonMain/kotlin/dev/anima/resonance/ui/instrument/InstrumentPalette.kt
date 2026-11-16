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
    // Enclosure — deep espresso/wood chassis (warm vintage palette)
    val walnut = Color(0xFF2B2019)
    val walnutDeep = Color(0xFF1E1611)
    val walnutLight = Color(0xFF3A2C20)
    val grainDark = Color(0xFF1A0F09)
    val grainLight = Color(0xFF5E4530)

    // Recessed panels (readout, control slots) — near-black warm brown
    val recess = Color(0xFF160F0B)
    val recessEdge = Color(0xFF0F0906)

    // Meter face — warm ivory/cream dial, charcoal ink
    val ivory = Color(0xFFF3EBD8)
    val ivoryEdge = Color(0xFFE4D7BE)
    val ink = Color(0xFF2A241C)
    val inkSoft = Color(0xFF6B6152)

    // Hardware — antique brass
    val brass = Color(0xFFB89758)
    val brassLight = Color(0xFFD6B672)
    val brassDark = Color(0xFF6E5527)

    // Status jewels (always paired with text/shape, never color alone)
    val lampGreen = Color(0xFF58C48A)
    val lampAmber = Color(0xFFE3B45C)
    val lampRed = Color(0xFFE2795B)
    val lampOff = Color(0xFF4A3B2E)

    // In-tune target zone painted on the ivory face (emerald when in tune).
    val bandGreen = Color(0xFF3D8B5E)
    // Off-pitch target zone — muted sage/taupe (decorative shading on ivory).
    val zoneOff = Color(0xFF8C8474)

    // Text on wood/recess
    val textMuted = Color(0xFFC9B99B)

    // Illuminated digital readout
    val noteIvory = Color(0xFFF5ECD8)     // detected note letter
    val freqCream = Color(0xFFB8AA96)     // frequency readout
    val taupe = Color(0xFF8C7F6A)         // micro-labels / dial stamp
    val emeraldGlow = Color(0xFF58C48A)   // in-tune note glow
    val terracotta = Color(0xFFC8795B)    // "tune down" badge
    val backlightCenter = Color(0xFF291D15) // display backlight center
    val backlightEdge = Color(0xFF160F0B)   // display backlight edge
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
            fontSize = 15.sp,
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