package com.nofence.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NoFenceDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = ElevatedCharcoal,
    onPrimaryContainer = CyberCyan,
    secondary = ElectricBlue,
    onSecondary = ObsidianBlack,
    tertiary = PrivatePurple,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    error = StatusFailClosedError
)

@Composable
fun NoFenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoFenceDarkColorScheme,
        typography = NoFenceTypography,
        content = content
    )
}
