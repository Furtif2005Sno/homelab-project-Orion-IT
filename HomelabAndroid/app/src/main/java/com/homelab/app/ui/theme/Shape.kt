package com.homelab.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Arcane radii: --radius = 0.875rem on a 14px root, scaled by its multipliers.
val OrionRadiusSm = 7.dp      // radius-sm  (x0.6): tooltips, small chips
val OrionRadiusMd = 10.dp     // radius-md  (x0.8): small buttons, menu items
val OrionRadiusLg = 12.dp     // radius-lg  (x1.0): badges, inputs, icon tiles
val OrionRadiusXl = 17.dp     // radius-xl  (x1.4): cards and buttons
val OrionRadius2xl = 22.dp    // radius-2xl (x1.8): mobile nav items, dialogs
val OrionRadius3xl = 27.dp    // radius-3xl (x2.2): floating mobile nav bar

/** Arcane press feedback: scale(0.97) over ~110-140ms on a strong ease-out curve. */
const val OrionPressScale = 0.97f
const val OrionPressDurationMs = 140
val OrionEaseOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(OrionRadiusSm),
    small = RoundedCornerShape(OrionRadiusMd),
    medium = RoundedCornerShape(OrionRadiusXl),
    large = RoundedCornerShape(OrionRadiusXl),
    extraLarge = RoundedCornerShape(OrionRadius2xl)
)
