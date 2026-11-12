package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val JarvisDarkColorScheme = darkColorScheme(
    primary = VoidColor.Violet,
    onPrimary = VoidColor.TextPrimary,
    secondary = VoidColor.Cyan,
    onSecondary = VoidColor.Void950,
    background = VoidColor.Void950,
    onBackground = VoidColor.TextPrimary,
    surface = VoidColor.Void900,
    onSurface = VoidColor.TextPrimary,
    surfaceVariant = VoidColor.Void800,
    onSurfaceVariant = VoidColor.TextSecondary,
    error = VoidColor.Red,
    onError = VoidColor.Void950
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = VoidColor.Violet,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF087F9B),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF7F7FB),
    onBackground = androidx.compose.ui.graphics.Color(0xFF171521),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF171521),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEAE9F1),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF555363),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    onError = androidx.compose.ui.graphics.Color.White
)

internal fun jarvisColorScheme(darkTheme: Boolean) =
    if (darkTheme) JarvisDarkColorScheme else JarvisLightColorScheme

@Composable
fun OpenJarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = jarvisColorScheme(darkTheme),
        content = content
    )
}
