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

/** Rising and falling price colors (24-hour change, candles); Material 3 has no green. */
@Immutable
data class PriceColors(val up: Color, val down: Color)

private val LightPriceColors = PriceColors(up = Color(0xFF1B873F), down = Color(0xFFC62828))
private val DarkPriceColors = PriceColors(up = Color(0xFF4CAF50), down = Color(0xFFEF5350))

val LocalPriceColors = staticCompositionLocalOf { LightPriceColors }

// Fixed palette, no dynamic color (spec.md §2.1): price colors look the same on every device.
@Composable
fun BitcoinAlertTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPriceColors provides if (dark) DarkPriceColors else LightPriceColors) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
