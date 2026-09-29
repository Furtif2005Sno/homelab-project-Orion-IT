package com.homelab.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Orion IT Design Authority v2.2 (Arcane layout, Orion IT orange accent).
// Neutrals are chroma-free; a single accent is reserved for primary actions and active states.

val OrionPrimary = Color(0xFFE8650A)
val OrionPrimaryHover = Color(0xFFC75508)
val OrionPrimaryForeground = Color(0xFFFFFFFF)

// Chart ramp, lightest to darkest.
val OrionChart1 = Color(0xFFFFB27E)
val OrionChart2 = Color(0xFFFF8C3F)
val OrionChart3 = OrionPrimary
val OrionChart4 = Color(0xFFB24E07)
val OrionChart5 = Color(0xFF7A3504)

@Immutable
data class OrionPalette(
    val background: Color,
    val foreground: Color,
    val surface: Color,
    val popover: Color,
    val secondary: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val input: Color,
    val success: Color,
    val warning: Color,
    val destructive: Color,
    val info: Color,
)

val OrionDarkPalette = OrionPalette(
    background = Color(0xFF0A0A0A),
    foreground = Color(0xFFFAFAFA),
    surface = Color(0xFF171717),
    popover = Color(0xFF171717),
    secondary = Color(0xFF27272A),
    muted = Color(0xFF262626),
    mutedForeground = Color(0xFFA1A1A1),
    border = Color.White.copy(alpha = 0.10f),
    input = Color.White.copy(alpha = 0.15f),
    success = Color(0xFF00D492),
    warning = Color(0xFFFACC15),
    destructive = Color(0xFFFF6467),
    info = Color(0xFF51A2FF),
)

val OrionOledPalette = OrionDarkPalette.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF050505),
    popover = Color(0xFF0A0A0A),
    secondary = Color(0xFF18181B),
    muted = Color(0xFF141414),
    border = Color.White.copy(alpha = 0.18f),
    input = Color.White.copy(alpha = 0.18f),
)

val OrionLightPalette = OrionPalette(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF0A0A0A),
    surface = Color(0xFFFDF9F7),
    popover = Color(0xFFFFFFFF),
    secondary = Color(0xFFF4F4F5),
    muted = Color(0xFFF5F5F5),
    mutedForeground = Color(0xFF696969),
    border = Color(0xFFE5E5E5),
    input = Color(0xFFEBEBEB),
    success = Color(0xFF009966),
    warning = Color(0xFFCA8A04),
    destructive = Color(0xFFE7000B),
    info = Color(0xFF155DFC),
)

val LocalOrionPalette = staticCompositionLocalOf { OrionDarkPalette }

// Status colors follow the active app theme (not the system one), so a forced
// dark/light/OLED theme always gets readable statuses.
val StatusGreen: Color
    @Composable
    get() = LocalOrionPalette.current.success

val StatusRed: Color
    @Composable
    get() = LocalOrionPalette.current.destructive

// DA "warning" is intentionally yellow so it never competes with the orange accent.
val StatusOrange: Color
    @Composable
    get() = LocalOrionPalette.current.warning

val StatusBlue: Color
    @Composable
    get() = LocalOrionPalette.current.info

val StatusPurple: Color
    @Composable
    get() = if (LocalOrionPalette.current.background.red < 0.5f) Color(0xFFA78BFA) else Color(0xFF7C3AED)
