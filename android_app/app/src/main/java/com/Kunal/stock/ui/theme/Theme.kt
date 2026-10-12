package com.Kunal.stock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Institutional Dark Trading Palette (Option Quant Edition: Neon Purple / Violet / Cyan)
val BullishGreen = Color(0xFF00E676)
val BullishGreenContainer = Color(0xFF003816)
val BearishRed = Color(0xFFFF5252)
val BearishRedContainer = Color(0xFF4A0007)
val NeutralSlate = Color(0xFF8B949E)
val ElectricBlue = Color(0xFF9D4EDD) // Vibrant Option Violet (Distinguished from Stock Blue)
val NeonCyan = Color(0xFF00DFD8)
val NeonMagenta = Color(0xFFFF007F)
val GoldWarning = Color(0xFFFFC107)

val DarkBackground = Color(0xFF0C0A14) // Deep space violet background
val DarkSurface = Color(0xFF151024)
val DarkCard = Color(0xFF1E1633)
val DarkCardBorder = Color(0xFF352654)
val TextPrimary = Color(0xFFF5F3FF)
val TextSecondary = Color(0xFFA79BC4)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB388FF), // Glowing Violet for Options
    onPrimary = Color(0xFF120038),
    primaryContainer = Color(0xFF381570),
    onPrimaryContainer = Color(0xFFEDDCFF),
    secondary = Color(0xFF00DFD8), // Neon Cyan Accent
    onSecondary = Color.Black,
    error = BearishRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    secondary = Color(0xFF1A7F37),
    error = Color(0xFFCF222E),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE)
)

@Composable
fun StockAnalysisTheme(
    darkTheme: Boolean = true, // Default to Dark Terminal theme for trading
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
