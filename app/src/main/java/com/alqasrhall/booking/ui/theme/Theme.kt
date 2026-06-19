package com.alqasrhall.booking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Forced luxury dark and gold theme (ERP-focused)
private val DarkColorScheme = darkColorScheme(
    primary = GoldenClassic,
    onPrimary = Color.Black,
    secondary = GoldenMuted,
    onSecondary = Color.White,
    tertiary = GoldenBright,
    background = ObsidianBlack,
    onBackground = ChampagneLight,
    surface = DeepCharcoal,
    onSurface = ChampagneLight,
    surfaceVariant = DarkGrayAccent,
    onSurfaceVariant = ChampagneLight,
    error = RedCancel,
    outline = GoldenMuted
)

private val LightColorScheme = lightColorScheme(
    primary = GoldenClassic,
    onPrimary = Color.White,
    secondary = GoldenMuted,
    onSecondary = CharcoalText,
    tertiary = GoldenBronze,
    background = IvoryBackground,
    onBackground = CharcoalText,
    surface = WarmWhiteCard,
    onSurface = CharcoalText,
    surfaceVariant = LightBeigePanel,
    onSurfaceVariant = MutedSlateText,
    error = RedCancel,
    outline = GoldenMuted
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve the luxury black-&-gold color scheme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
