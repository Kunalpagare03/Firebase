package com.Kunal.stock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Institutional Dark Trading Palette
val BullishGreen = Color(0xFF00E676)
val BullishGreenContainer = Color(0xFF003816)
val BearishRed = Color(0xFFFF5252)
val BearishRedContainer = Color(0xFF4A0007)
val NeutralSlate = Color(0xFF8B949E)
val ElectricBlue = Color(0xFF2F81F7)
val GoldWarning = Color(0xFFFFC107)

val DarkBackground = Color(0xFF0E1318)
val DarkSurface = Color(0xFF161C24)
val DarkCard = Color(0xFF1F2633)
val DarkCardBorder = Color(0xFF2C3545)
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF9BA7B6)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF183D70),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = BullishGreen,
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
