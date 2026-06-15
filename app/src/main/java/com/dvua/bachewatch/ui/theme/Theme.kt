package com.dvua.bachewatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SafetyAmber,
    secondary = AlertYellow,
    tertiary = SuccessGreen,
    background = SlateDark,
    surface = SlateCard,
    error = DangerRed,
    onPrimary = Color(0xFF381E72),
    onSecondary = SlateDark,
    onBackground = LightBackground,
    onSurface = LightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = SafetyAmber,
    secondary = AlertYellow,
    tertiary = SuccessGreen,
    background = SlateDark,
    surface = SlateCard,
    error = DangerRed,
    onPrimary = Color(0xFF381E72),
    onSecondary = SlateDark,
    onBackground = LightBackground,
    onSurface = LightBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
