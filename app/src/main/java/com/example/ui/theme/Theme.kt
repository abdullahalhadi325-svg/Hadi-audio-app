package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = OffWhiteText,
    primaryContainer = ElectricPurpleContainer,
    onPrimaryContainer = ElectricPurpleGlow,
    secondary = ElectricPurpleLight,
    onSecondary = OffWhiteText,
    secondaryContainer = ElectricPurpleContainer,
    onSecondaryContainer = ElectricPurpleGlow,
    tertiary = SubduedText,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = OffWhiteText,
    surface = DarkSurface,
    onSurface = OffWhiteText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SubduedText,
    surfaceContainer = DarkCardSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    error = SubduedRed,
    onError = OffWhiteText,
    errorContainer = SubduedRedContainer,
    onErrorContainer = SubduedRedGlow,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

@Composable
fun SmartVoiceWriterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SmartVoiceWriterTheme(content = content)
}
