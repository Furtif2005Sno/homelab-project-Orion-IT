package com.homelab.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homelab.app.R

// Arcane type: Montserrat for all UI text, Geist Mono only for data (versions, IPs, sizes, code).
// Static instances generated from the variable fonts: Android ignores the weight axis of
// resource variable fonts and would render every weight as the file's default (Thin).
val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_bold, FontWeight.ExtraBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_medium, FontWeight.SemiBold),
)

private fun sans(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Montserrat,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Data values (versions, IPs, sizes, durations): Geist Mono, ligatures off. */
val OrionCodeStyle = TextStyle(
    fontFamily = GeistMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    fontFeatureSettings = "liga 0, clig 0",
)

/** Arcane section / stat labels: small uppercase with wide tracking (apply uppercase to the text). */
val OrionOverlineStyle = sans(11, 14, FontWeight.Medium, tracking = 0.1)

val Typography = Typography(
    displayLarge = sans(40, 46, FontWeight.SemiBold, -0.025),
    displayMedium = sans(34, 40, FontWeight.SemiBold, -0.025),
    displaySmall = sans(30, 36, FontWeight.SemiBold, -0.025),
    headlineLarge = sans(28, 34, FontWeight.SemiBold, -0.025),
    headlineMedium = sans(24, 30, FontWeight.SemiBold, -0.02),
    headlineSmall = sans(21, 28, FontWeight.SemiBold, -0.015),
    titleLarge = sans(19, 26, FontWeight.SemiBold, -0.01),
    titleMedium = sans(16, 22, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(15, 23, FontWeight.Normal),
    bodyMedium = sans(14, 21, FontWeight.Normal),
    bodySmall = sans(12, 17, FontWeight.Normal),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(11, 14, FontWeight.Medium),
)
