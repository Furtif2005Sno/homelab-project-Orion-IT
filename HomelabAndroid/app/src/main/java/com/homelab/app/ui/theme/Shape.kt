package com.homelab.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Orion IT radii (html base 14px): radius-lg = 0.875rem is the base for cards, buttons and fields.
val OrionRadiusSm = 7.dp      // radius-sm: tooltips, small chips
val OrionRadiusMd = 10.dp     // radius-md: small buttons, menu and nav items
val OrionRadiusLg = 12.dp     // radius-lg: cards, buttons, fields
val OrionRadiusXl = 17.dp     // radius-xl: dialogs, sheets

/** Orion IT press feedback: scale(0.97) over 140ms on a strong ease-out curve. */
const val OrionPressScale = 0.97f
const val OrionPressDurationMs = 140
val OrionEaseOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(OrionRadiusSm),
    small = RoundedCornerShape(OrionRadiusMd),
    medium = RoundedCornerShape(OrionRadiusLg),
    large = RoundedCornerShape(OrionRadiusLg),
    extraLarge = RoundedCornerShape(OrionRadiusXl)
)
