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
 * light is a secondary mode. Both schemes are guarded by
 * `ResonanceThemeContrastTest` (WCAG AA text/graphic floors) and rendered
 * headlessly by `TunerScreenRenderSmokeTest`.
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

    // Light (secondary). Accent/secondary are darkened from the dark-theme
    // pastels so text and graphic indicators keep WCAG AA contrast on white.
    val bgLight = Color(0xFFF4F7FA)
    val surfaceLight = Color(0xFFFFFFFF)
    val textPrimaryLight = Color(0xFF131B23)
    val textSecondaryLight = Color(0xFF4A5A6A)
    val accentLight = Color(0xFF0B6B61)
    val inTuneLight = Color(0xFF2E6B3F)
    val cautionLight = Color(0xFF7A4E00)
    val errorLight = Color(0xFFA4262C)
}

/**
 * Tabular numerals for Hz/cents readouts — "tnum" is a standard OpenType
 * feature; platform default fonts carry it. No runtime font downloads.
 */
val TextStyle.tabularNumerals: TextStyle
    get() = this.copy(fontFeatureSettings = "tnum on")

internal fun resonanceDarkScheme() = darkColorScheme(
    background = ResonanceColors.bg,
    surface = ResonanceColors.surface,
    surfaceVariant = ResonanceColors.surfaceElevated,
    onBackground = ResonanceColors.textPrimary,
    onSurface = ResonanceColors.textPrimary,
    onSurfaceVariant = ResonanceColors.textSecondary,
    primary = ResonanceColors.accent,
    onPrimary = ResonanceColors.bg,
    secondary = ResonanceColors.inTune,
    onSecondary = ResonanceColors.bg,
    tertiary = ResonanceColors.caution,
    onTertiary = ResonanceColors.bg,
    error = ResonanceColors.error,
    onError = ResonanceColors.bg,
)

internal fun resonanceLightScheme() = lightColorScheme(
    background = ResonanceColors.bgLight,
    surface = ResonanceColors.surfaceLight,
    surfaceVariant = Color(0xFFE3EAF0),
    onBackground = ResonanceColors.textPrimaryLight,
    onSurface = ResonanceColors.textPrimaryLight,
    onSurfaceVariant = ResonanceColors.textSecondaryLight,
    primary = ResonanceColors.accentLight,
    onPrimary = Color.White,
    secondary = ResonanceColors.inTuneLight,
    onSecondary = Color.White,
    tertiary = ResonanceColors.cautionLight,
    onTertiary = Color.White,
    error = ResonanceColors.errorLight,
    onError = Color.White,
)

@Composable
fun ResonanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) resonanceDarkScheme() else resonanceLightScheme(),
        content = content,
    )
}

/**
 * Shared typography scale for the instrument. Colors come from the active
 * Material color scheme (never hardcoded light/dark values) so the light
 * theme stays legible; sizes remain sp-based for system font scaling.
 */
object ResonanceType {
    val noteGlyph: TextStyle
        @Composable get() = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 96.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    val octave: TextStyle
        @Composable get() = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val frequency: TextStyle
        @Composable get() = TextStyle(
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ).tabularNumerals
    val cents: TextStyle
        @Composable get() = TextStyle(
            fontSize = 44.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        ).tabularNumerals
    val direction: TextStyle
        @Composable get() = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    val hint: TextStyle
        @Composable get() = TextStyle(
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val labelSmall: TextStyle
        @Composable get() = TextStyle(
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}
