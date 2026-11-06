package dev.anima.resonance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Resonance design tokens (spec §8). Dark instrument theme is PRIMARY;
 * light is a secondary mode. These are design starting points — contrast is
 * to be audited in rendered output when a display is available.
 */
object ResonanceColors {
    // Dark (primary)
    val bg = Color(0xFF0B1118)
    val surface = Color(0xFF121D29)
    val surfaceElevated = Color(0xFF182635)
    val textPrimary = Color(0xFFECF3F7)
    val textSecondary = Color(0xFF98AABD)
    val accent = Color(0xFF54D6C7)
    val inTune = Color(0xFF8AD9A0)
    val caution = Color(0xFFF0B56D)
    val error = Color(0xFFF18F91)

    // Light (secondary)
    val bgLight = Color(0xFFF4F7FA)
    val surfaceLight = Color(0xFFFFFFFF)
    val textPrimaryLight = Color(0xFF131B23)
    val textSecondaryLight = Color(0xFF5A6B7D)
}

/**
 * Tabular numerals for Hz/cents readouts — "tnum" is a standard OpenType
 * feature; platform default fonts carry it. No runtime font downloads.
 */
val TextStyle.tabularNumerals: TextStyle
    get() = this.copy(fontFeatureSettings = "tnum on")

private fun darkScheme() = darkColorScheme(
    background = ResonanceColors.bg,
    surface = ResonanceColors.surface,
    surfaceVariant = ResonanceColors.surfaceElevated,
    onBackground = ResonanceColors.textPrimary,
    onSurface = ResonanceColors.textPrimary,
    onSurfaceVariant = ResonanceColors.textSecondary,
    primary = ResonanceColors.accent,
    onPrimary = ResonanceColors.bg,
    secondary = ResonanceColors.inTune,
    tertiary = ResonanceColors.caution,
    error = ResonanceColors.error,
)

private fun lightScheme() = lightColorScheme(
    background = ResonanceColors.bgLight,
    surface = ResonanceColors.surfaceLight,
    onBackground = ResonanceColors.textPrimaryLight,
    onSurface = ResonanceColors.textPrimaryLight,
    onSurfaceVariant = ResonanceColors.textSecondaryLight,
    primary = ResonanceColors.accent.copy(alpha = 0.75f),
    secondary = ResonanceColors.inTune,
    tertiary = ResonanceColors.caution,
    error = ResonanceColors.error,
)

@Composable
fun ResonanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme() else lightScheme(),
        content = content,
    )
}

/** Shared typography scale for the instrument. */
object ResonanceType {
    val noteGlyph = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 96.sp,
        color = ResonanceColors.textPrimary,
    )
    val octave = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        color = ResonanceColors.textSecondary,
    )
    val frequency = TextStyle(
        fontSize = 24.sp,
        color = ResonanceColors.textSecondary,
    ).tabularNumerals
    val cents = TextStyle(
        fontSize = 44.sp,
        fontWeight = FontWeight.Medium,
        color = ResonanceColors.textPrimary,
    ).tabularNumerals
    val direction = TextStyle(
        fontSize = 18.sp,
        color = ResonanceColors.textSecondary,
    )
    val hint = TextStyle(
        fontSize = 15.sp,
        color = ResonanceColors.textSecondary,
    )
    val labelSmall = TextStyle(
        fontSize = 13.sp,
        color = ResonanceColors.textSecondary,
    )
}
