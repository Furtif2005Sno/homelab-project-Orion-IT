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

/**
 * Maps Arcane's tokens onto Material 3 roles:
 * - surfaceContainerLow  = Arcane card at 60 % over the background (the default card fill)
 * - surfaceContainer     = Arcane card
 * - surfaceContainerHigh = between card and muted (pressed / nested)
 * - surfaceContainerHighest / surfaceVariant = Arcane muted (active nav item, tracks)
 * - outlineVariant = Arcane border at 70 % (card borders), outline = Arcane input
 * - secondary* = Arcane neutral secondary; the only accent is primary.
 */
private fun orionScheme(p: OrionPalette): ColorScheme {
    val cardFill = p.card.over(p.background, 0.6f)
    val borderOpaque = p.border.copy(alpha = p.border.alpha * 0.7f).compositeOver(p.background)
    val inputOpaque = p.input.compositeOver(p.background)
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = OrionPrimary,
        onPrimary = OrionPrimaryForeground,
        primaryContainer = OrionPrimary.over(p.background, if (p.isDark) 0.15f else 0.10f),
        onPrimaryContainer = if (p.isDark) OrionPrimaryTint else OrionChart4,
        inversePrimary = OrionChart2,
        secondary = p.mutedForeground,
        onSecondary = p.background,
        secondaryContainer = p.muted,
        onSecondaryContainer = p.foreground,
        tertiary = p.info,
        onTertiary = p.background,
        tertiaryContainer = p.info.over(p.background, 0.10f),
        onTertiaryContainer = p.info,
        error = p.destructive,
        onError = Color.White,
        errorContainer = p.destructive.over(p.background, 0.10f),
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
        outline = inputOpaque,
        outlineVariant = borderOpaque,
        scrim = Color.Black,
        surfaceBright = p.card,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = cardFill,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card.over(p.muted, 0.5f),
        surfaceContainerHighest = p.muted,
    )
}

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
            orionScheme(palette)
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
