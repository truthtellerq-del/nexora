package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NexoraLightColorScheme = lightColorScheme(
    primary = Navy900,
    onPrimary = Color.White,
    primaryContainer = Navy800,
    onPrimaryContainer = Color.White,
    secondary = Emerald600,
    onSecondary = Color.White,
    secondaryContainer = Emerald50,
    onSecondaryContainer = Emerald600,
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber50,
    onTertiaryContainer = Amber600,
    error = Crimson600,
    onError = Color.White,
    errorContainer = Crimson50,
    onErrorContainer = Crimson600,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate200
)

private val NexoraDarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Navy950,
    primaryContainer = Emerald600,
    onPrimaryContainer = Color.White,
    secondary = Emerald500,
    onSecondary = Navy950,
    secondaryContainer = Navy800,
    onSecondaryContainer = Emerald100,
    tertiary = Amber600,
    error = Crimson600,
    background = Navy950,
    onBackground = Color.White,
    surface = Navy900,
    onSurface = Color.White,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate200,
    outline = Navy800
)

val NexoraShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun NexoraTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) NexoraDarkColorScheme else NexoraLightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = NexoraTypography,
        shapes = NexoraShapes,
        content = content
    )
}
