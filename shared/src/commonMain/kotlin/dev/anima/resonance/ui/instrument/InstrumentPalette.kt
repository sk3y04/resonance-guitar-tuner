package dev.anima.resonance.ui.instrument

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.ui.tabularNumerals

/**
 * The vintage instrument palette — warm espresso surfaces, beige type,
 * parchment/ivory keys, brass accents. Same design system as the control deck (tokens, one
 * mint-free accent role, one type scale), but back on the warm material identity:
 * no petrol, no blue, no wooden wallpaper.
 *
 * Material roles:
 *  - Espresso brown: the app background and the control deck (flat, no grain).
 *  - Deep brown:       recesses (the readout well, pressed states).
 *  - Parchment/ivory:  the meter face and the SELECTED key/string position.
 *  - Warm cream:       primary type.
 *  - Beige:            secondary type and note sequences.
 *  - Brass:            small indicators only (switch track, detected marker,
 *                      meter hardware).
 *
 * Deliberately identical in light and dark system themes (a physical instrument
 * does not repaint itself). Contrast pairs are floored by
 * `InstrumentPaletteContrastTest` (WCAG AA: 4.5:1 text, 3:1 graphics).
 */
object InstrumentPalette {

    // ── Enclosure family (kept for meter hardware; the app no longer draws wood) ──
    val walnut = Color(0xFF3A2A1E)
    val walnutDeep = Color(0xFF241A12)
    val walnutLight = Color(0xFF4A3728)
    val grainDark = Color(0xFF2A1D13)
    val grainLight = Color(0xFF56402C)

    // ── App + control surfaces: flat warm espresso ───────────────────────────
    /** The app background behind the status row. */
    val background = Color(0xFF1B1310)
    /** The control deck anchor (one surface, matte finish). */
    val deck = Color(0xFF2B1F18)
    /** Faceplate: the deck's gently lit upper edge (light from above). */
    val deckTop = Color(0xFF36281C)
    /** Faceplate: the deck's deeper lower edge (same material, in shade). */
    val deckBottom = Color(0xFF221812)
    /** Sunken / pressed surface. */
    val deckDeep = Color(0xFF1F1611)
    /** Raised interactive surface (tonal buttons, pressed keys). */
    val segmentCap = Color(0xFF3B2A20)
    /** Hairline border on espresso. */
    val deckEdge = Color(0xFF5B4433)

    // ── Recesses: readout well ───────────────────────────────────────────────
    val recess = Color(0xFF170F0C)
    val recessEdge = Color(0xFF0E0907)
    val rail = Color(0xFF170F0C)
    val railEdge = Color(0xFF0E0907)

    // ── Meter: warm ivory face, warm dark ink ────────────────────────────────
    val ivory = Color(0xFFF1E7CF)
    val ivoryEdge = Color(0xFFDFD1B4)
    val ink = Color(0xFF2C2318)
    val inkSoft = Color(0xFF5F5343)

    // ── Text on espresso ─────────────────────────────────────────────────────
    val textPrimary = Color(0xFFF4EAD6)
    val textSecondary = Color(0xFFC6B49B)
    /** Legacy alias: secondary text. */
    val textMuted = textSecondary

    // ── Selection: a parchment key, never a colour wash ──────────────────────
    val accent = Color(0xFFE8D9BC)
    /** The pressed dip of a selected parchment key (same family, darker). */
    val accentPressed = Color(0xFFDCCBAA)
    /** Text/icon colour on a selected parchment key. */
    val accentInk = Color(0xFF2A2016)

    // ── Hardware: brass, small indicators only ───────────────────────────────
    val brass = Color(0xFFAD925B)
    val brassLight = Color(0xFFC8AC76)
    val brassDark = Color(0xFF6C5A38)

    // ── Semantic state lamps (always paired with text/shape) ─────────────────
    val lampGreen = Color(0xFFA9B77E)
    val lampAmber = Color(0xFFE4B96B)
    val lampRed = Color(0xFFD2704F)
    val lampOff = Color(0xFF574634)

    // ── Dial markings ────────────────────────────────────────────────────────
    /** In-tune zone printed on the ivory face. */
    val bandGreen = Color(0xFF6E8A5B)
    /** Off-pitch zone — neutral warm grey on ivory. */
    val zoneOff = Color(0xFFA79C88)

    // ── Readout ──────────────────────────────────────────────────────────────
    val noteIvory = Color(0xFFF4EAD6)
    val freqCream = Color(0xFFC6B49B)
    val taupe = Color(0xFF9C8A72)
    val emeraldGlow = Color(0xFFA9B77E)
    val terracotta = Color(0xFFD2704F)
    val backlightCenter = Color(0xFF241A13)
    val backlightEdge = Color(0xFF17100C)
}

/**
 * The spacing, shape and border scale. One main gutter; local padding only
 * inside genuinely contained surfaces — never screen + card + section + row.
 */
object InstrumentTokens {
    // Spacing scale (4 · 8 · 12 · 16 · 24 · 32)
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 24.dp
    val space6 = 32.dp

    /** The one main content gutter. */
    val gutter = 16.dp
    /** Internal padding of a contained surface. */
    val surfacePad = 16.dp
    /** Between closely related elements. */
    val rowGap = 8.dp
    /** Between different tasks / sections. */
    val sectionGap = 24.dp

    // Corner family (one family, three sizes)
    val cornerSm = 8.dp
    val cornerMd = 12.dp
    val cornerLg = 20.dp
    val cornerSheet = 24.dp

    /** Minimum interactive touch target (accessibility floor). */
    val touchMin = 48.dp
    val hairline = 1.dp

    // Legacy aliases used by the meter/dial code.
    val gapXs = space1
    val gapSm = space2
    val gapMd = space3
    val gapLg = space4
    val deckCorner = cornerLg
}

/** Design tokens for the current composition (see [InstrumentPalette]). */
val LocalInstrumentPalette = staticCompositionLocalOf { InstrumentPalette }

/** Convenience accessor mirroring MaterialTheme.colorScheme usage. */
object InstrumentTheme {
    val palette: InstrumentPalette
        @Composable get() = LocalInstrumentPalette.current
}

/**
 * Type scale. Deliberate and small: one display size for the live readout, one
 * title, one label, one body, one caption. No decorative letter spacing and no
 * engraved low-contrast treatment for essential labels.
 */
object InstrumentType {
    /** Settings screen title. */
    val display: TextStyle
        @Composable get() = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textPrimary,
        )

    /** Destination title (used only where it adds information). */
    val title: TextStyle
        @Composable get() = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textPrimary,
        )

    /** Row label / control label. */
    val label: TextStyle
        @Composable get() = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textPrimary,
        )

    /** Supporting text. */
    val body: TextStyle
        @Composable get() = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textSecondary,
        )

    /** Small supporting text (metadata, qualifiers). */
    val caption: TextStyle
        @Composable get() = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textSecondary,
        )

    /** Stable numeric value (readouts, calibration). */
    val numeric: TextStyle
        @Composable get() = TextStyle(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            color = InstrumentTheme.palette.textPrimary,
        ).tabularNumerals

    // ── Legacy aliases still referenced by dial / companion code ─────────
    val engraved: TextStyle
        @Composable get() = caption

    val engravedStrong: TextStyle
        @Composable get() = title

    val readout: TextStyle
        @Composable get() = numeric

    val readoutSmall: TextStyle
        @Composable get() = TextStyle(
            fontSize = 14.sp,
            color = InstrumentTheme.palette.textSecondary,
        ).tabularNumerals

    val keyLabel: TextStyle
        @Composable get() = label
}