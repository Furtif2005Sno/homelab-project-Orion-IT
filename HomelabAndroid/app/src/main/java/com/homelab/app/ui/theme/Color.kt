package com.homelab.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Orion IT = Arcane's design (getarcaneapp/arcane, frontend/src/routes/layout.css)
// with the violet accent replaced by Orion IT orange. Neutrals are Arcane's chroma-free greys.

val OrionPrimary = Color(0xFFE8650A)
val OrionPrimaryForeground = Color(0xFFFFFFFF)
/** Arcane's `primary-tint`: primary mixed 55 % with white, used for accent text on dark tinted fills. */
val OrionPrimaryTint = Color(0xFFF2AA78)

// Chart ramp, lightest to darkest (Arcane's chart-1..5 re-hued to orange).
val OrionChart1 = Color(0xFFFFB27E)
val OrionChart2 = Color(0xFFFF8C3F)
val OrionChart3 = OrionPrimary
val OrionChart4 = Color(0xFFB24E07)
val OrionChart5 = Color(0xFF7A3504)

@Immutable
data class OrionPalette(
    val isDark: Boolean,
    val background: Color,
    val foreground: Color,
    /** Arcane `--card` / `--surface`: raised surfaces (cards at 40-60 % over the background). */
    val card: Color,
    val popover: Color,
    val secondary: Color,
    val muted: Color,
    val mutedForeground: Color,
    /** Arcane `--border`; cards draw it at 70 %. */
    val border: Color,
    val input: Color,
    val success: Color,
    val warning: Color,
    val destructive: Color,
    val info: Color,
    val purple: Color,
)

// oklch values from Arcane's `.dark` block, converted to sRGB.
val OrionDarkPalette = OrionPalette(
    isDark = true,
    background = Color(0xFF0A0A0A),      // oklch(0.145 0 0)
    foreground = Color(0xFFFAFAFA),      // oklch(0.985 0 0)
    card = Color(0xFF171717),            // oklch(0.205 0 0)
    popover = Color(0xFF171717),
    secondary = Color(0xFF27272A),       // oklch(0.274 0.006 286)
    muted = Color(0xFF262626),           // oklch(0.269 0 0)
    mutedForeground = Color(0xFFA1A1A1), // oklch(0.708 0 0)
    border = Color.White.copy(alpha = 0.10f),
    input = Color.White.copy(alpha = 0.15f),
    success = Color(0xFF00D492),         // oklch(0.765 0.177 163)
    warning = Color(0xFFFFBA00),         // oklch(0.828 0.189 84)
    destructive = Color(0xFFFF6467),     // oklch(0.704 0.191 22)
    info = Color(0xFF51A2FF),            // oklch(0.707 0.165 254)
    purple = Color(0xFFC27AFF),
)

val OrionOledPalette = OrionDarkPalette.copy(
    background = Color(0xFF000000),
    card = Color(0xFF0E0E0E),
    popover = Color(0xFF0E0E0E),
    secondary = Color(0xFF18181B),
    muted = Color(0xFF1A1A1A),
    border = Color.White.copy(alpha = 0.14f),
)

// oklch values from Arcane's `:root` block.
val OrionLightPalette = OrionPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF0A0A0A),
    card = Color(0xFFFFFFFF),
    popover = Color(0xFFFFFFFF),
    secondary = Color(0xFFF4F4F5),
    muted = Color(0xFFF5F5F5),           // oklch(0.97 0 0)
    mutedForeground = Color(0xFF6B6B6B), // oklch(0.52 0 0)
    border = Color(0xFFE2E2E2),          // oklch(0.905 0 0)
    input = Color(0xFFE5E5E5),
    success = Color(0xFF009966),
    warning = Color(0xFFE17100),
    destructive = Color(0xFFE7000B),
    info = Color(0xFF155DFC),
    purple = Color(0xFF9810FA),
)

val LocalOrionPalette = staticCompositionLocalOf { OrionDarkPalette }

// Status colors follow the active app theme (not the system one).
val StatusGreen: Color
    @Composable
    get() = LocalOrionPalette.current.success

val StatusRed: Color
    @Composable
    get() = LocalOrionPalette.current.destructive

val StatusOrange: Color
    @Composable
    get() = LocalOrionPalette.current.warning

val StatusBlue: Color
    @Composable
    get() = LocalOrionPalette.current.info

val StatusPurple: Color
    @Composable
    get() = LocalOrionPalette.current.purple
