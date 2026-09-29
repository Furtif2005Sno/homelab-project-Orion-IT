package com.homelab.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext

/** Resolved visual variant of the Orion IT theme (the system mode is resolved before this). */
enum class OrionThemeVariant { DARK, LIGHT, OLED }

private fun Color.over(base: Color, alpha: Float): Color = copy(alpha = alpha).compositeOver(base)

private fun orionDarkScheme(p: OrionPalette, oled: Boolean): ColorScheme {
    // Opaque equivalents of the translucent DA borders, so M3 components can use them.
    val borderOpaque = p.border.compositeOver(p.background)
    val inputOpaque = p.input.compositeOver(p.background)
    return darkColorScheme(
        primary = OrionPrimary,
        onPrimary = OrionPrimaryForeground,
        primaryContainer = OrionPrimary.over(p.background, 0.14f),
        onPrimaryContainer = OrionChart1,
        inversePrimary = OrionPrimaryHover,
        // Single accent: secondary is a neutral, used for segmented controls and chips.
        secondary = Color(0xFFD4D4D4),
        onSecondary = p.background,
        secondaryContainer = p.secondary,
        onSecondaryContainer = p.foreground,
        tertiary = p.info,
        onTertiary = p.background,
        tertiaryContainer = p.info.over(p.background, 0.14f),
        onTertiaryContainer = p.info,
        error = p.destructive,
        onError = p.background,
        errorContainer = p.destructive.over(p.background, 0.14f),
        onErrorContainer = p.destructive,
        background = p.background,
        onBackground = p.foreground,
        surface = p.background,
        onSurface = p.foreground,
        surfaceVariant = p.muted,
        onSurfaceVariant = p.mutedForeground,
        surfaceTint = p.background,
        inverseSurface = p.foreground,
        inverseOnSurface = p.background,
        outline = if (oled) Color(0xFF5C5C5C) else Color(0xFF525252),
        outlineVariant = borderOpaque,
        scrim = Color.Black,
        surfaceBright = p.muted,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface.over(p.muted, 0.5f),
        surfaceContainerHigh = p.muted,
        surfaceContainerHighest = inputOpaque.over(p.muted, 0.5f),
    )
}

private fun orionLightScheme(p: OrionPalette): ColorScheme = lightColorScheme(
    primary = OrionPrimary,
    onPrimary = OrionPrimaryForeground,
    primaryContainer = OrionPrimary.over(p.background, 0.14f),
    onPrimaryContainer = OrionChart5,
    inversePrimary = OrionChart1,
    secondary = Color(0xFF404040),
    onSecondary = p.background,
    secondaryContainer = p.secondary,
    onSecondaryContainer = p.foreground,
    tertiary = p.info,
    onTertiary = p.background,
    tertiaryContainer = p.info.over(p.background, 0.10f),
    onTertiaryContainer = p.info,
    error = p.destructive,
    onError = p.background,
    errorContainer = p.destructive.over(p.background, 0.10f),
    onErrorContainer = p.destructive,
    background = p.background,
    onBackground = p.foreground,
    surface = p.background,
    onSurface = p.foreground,
    surfaceVariant = p.muted,
    onSurfaceVariant = p.mutedForeground,
    surfaceTint = p.background,
    inverseSurface = Color(0xFF171717),
    inverseOnSurface = Color(0xFFFAFAFA),
    outline = Color(0xFF8A8A8A),
    outlineVariant = p.border,
    scrim = Color.Black,
    surfaceBright = p.background,
    surfaceDim = p.muted,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.surface,
    surfaceContainer = Color(0xFFF7F5F4),
    surfaceContainerHigh = p.muted,
    surfaceContainerHighest = p.input,
)

@Composable
fun HomelabTheme(
    variant: OrionThemeVariant = OrionThemeVariant.DARK,
    // Material You colors (Android 12+) are opt-in; the Orion IT palette is the default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = when (variant) {
        OrionThemeVariant.DARK -> OrionDarkPalette
        OrionThemeVariant.LIGHT -> OrionLightPalette
        OrionThemeVariant.OLED -> OrionOledPalette
    }
    val context = LocalContext.current
    val colorScheme = remember(variant, dynamicColor) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (variant) {
                OrionThemeVariant.LIGHT -> dynamicLightColorScheme(context)
                OrionThemeVariant.DARK -> dynamicDarkColorScheme(context)
                OrionThemeVariant.OLED -> dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                )
            }
        } else {
            when (variant) {
                OrionThemeVariant.LIGHT -> orionLightScheme(palette)
                OrionThemeVariant.DARK -> orionDarkScheme(palette, oled = false)
                OrionThemeVariant.OLED -> orionDarkScheme(palette, oled = true)
            }
        }
    }

    CompositionLocalProvider(LocalOrionPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
