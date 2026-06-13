package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    secondary = EmeraldDarkSecondary,
    tertiary = EmeraldDarkTertiary,
    background = EmeraldDarkBackground,
    surface = EmeraldDarkSurface,
    onPrimary = EmeraldDarkOnPrimary,
    onSecondary = EmeraldDarkOnSecondary,
    onBackground = EmeraldDarkOnBackground,
    onSurface = EmeraldDarkOnSurface,
    primaryContainer = EmeraldDarkPrimaryContainer,
    onPrimaryContainer = EmeraldDarkOnPrimaryContainer,
    secondaryContainer = EmeraldDarkSecondaryContainer,
    onSecondaryContainer = EmeraldDarkOnSecondaryContainer,
    tertiaryContainer = EmeraldDarkTertiaryContainer,
    onTertiaryContainer = EmeraldDarkOnTertiaryContainer,
    surfaceVariant = EmeraldDarkSurfaceVariant,
    onSurfaceVariant = EmeraldDarkOnSurfaceVariant,
    outline = EmeraldDarkOutline,
    outlineVariant = EmeraldDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    secondary = EmeraldSecondary,
    tertiary = EmeraldTertiary,
    background = EmeraldBackground,
    surface = EmeraldSurface,
    onPrimary = EmeraldOnPrimary,
    onSecondary = EmeraldOnSecondary,
    onBackground = EmeraldOnBackground,
    onSurface = EmeraldOnSurface,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = EmeraldOnSecondaryContainer,
    tertiaryContainer = EmeraldTertiaryContainer,
    onTertiaryContainer = EmeraldOnTertiaryContainer,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldOnSurfaceVariant,
    outline = EmeraldOutline,
    outlineVariant = EmeraldOutlineVariant
)

@Composable
fun BalanceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color disable-able to enforce our polished Evergreen theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Retain compatibility mapping so standard template calls can run without issues
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    BalanceTrackerTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
