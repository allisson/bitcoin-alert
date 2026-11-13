package io.github.allisson.bitcoinalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.allisson.bitcoinalert.R

/** Rising and falling price colors (24-hour change, candles); Material 3 has no green. */
@Immutable
data class PriceColors(val up: Color, val down: Color)

private val LightPriceColors = PriceColors(up = Color(0xFF0A8754), down = Color(0xFFD1213F))
private val DarkPriceColors = PriceColors(up = Color(0xFF2EBD85), down = Color(0xFFF6465D))

val LocalPriceColors = staticCompositionLocalOf { LightPriceColors }

/** Numbers (prices, OHLC, axes, timeframes): fixed-width digits do not jump as the price updates. */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

// Tertiary is the warning amber (stale data). Text pairs keep a 4.5:1 contrast or more.
private val LightColors = lightColorScheme(
    primary = Color(0xFFB45309),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7931A),
    onPrimaryContainer = Color(0xFF1F1300),
    secondaryContainer = Color(0xFFE7EBEF),
    onSecondaryContainer = Color(0xFF0F1419),
    tertiary = Color(0xFF9A5200),
    tertiaryContainer = Color(0xFFFDF1DC),
    onTertiaryContainer = Color(0xFF9A5200),
    background = Color.White,
    onBackground = Color(0xFF0F1419),
    surface = Color.White,
    onSurface = Color(0xFF0F1419),
    surfaceVariant = Color(0xFFF3F5F7),
    onSurfaceVariant = Color(0xFF5B6672),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF3F5F7),
    surfaceContainerHigh = Color(0xFFE7EBEF),
    surfaceContainerHighest = Color(0xFFDDE2E7),
    outline = Color(0xFFC5CCD4),
    outlineVariant = Color(0xFFE1E5EA),
    error = Color(0xFFD1213F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF7931A),
    onPrimary = Color(0xFF1F1300),
    primaryContainer = Color(0xFFF7931A),
    onPrimaryContainer = Color(0xFF1F1300),
    secondaryContainer = Color(0xFF1D242D),
    onSecondaryContainer = Color(0xFFE8EDF2),
    tertiary = Color(0xFFF5B547),
    tertiaryContainer = Color(0xFF2A2010),
    onTertiaryContainer = Color(0xFFF5B547),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF0B0E11),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF141920),
    onSurfaceVariant = Color(0xFF8A96A3),
    surfaceContainerLowest = Color(0xFF07090B),
    surfaceContainerLow = Color(0xFF101419),
    surfaceContainer = Color(0xFF141920),
    surfaceContainerHigh = Color(0xFF1D242D),
    surfaceContainerHighest = Color(0xFF252D37),
    outline = Color(0xFF3A4552),
    outlineVariant = Color(0xFF222A33),
    error = Color(0xFFF6465D),
)

// Fixed palette, no dynamic color (spec.md §2.1): price colors look the same on every device.
@Composable
fun BitcoinAlertTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPriceColors provides if (dark) DarkPriceColors else LightPriceColors) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
