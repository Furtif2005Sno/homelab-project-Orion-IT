package com.homelab.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homelab.app.R

// Orion IT type: Montserrat (sans) for text, Geist Mono (mono) for titles, code and figures.
// Both are variable fonts; each Font entry pins the weight axis.
val Montserrat = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat, FontWeight.Medium),
    Font(R.font.montserrat, FontWeight.SemiBold),
    Font(R.font.montserrat, FontWeight.Bold),
    Font(R.font.montserrat, FontWeight.ExtraBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono, FontWeight.Medium),
    Font(R.font.geist_mono, FontWeight.SemiBold),
    Font(R.font.geist_mono, FontWeight.Bold),
)

private fun mono(size: Int, line: Int, tracking: Double = 0.0, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = GeistMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Montserrat,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

/** Data style (versions, IPs, sizes, durations): Geist Mono, regular weight. */
val OrionCodeStyle = mono(13, 19, weight = FontWeight.Normal)

val Typography = Typography(
    displayLarge = mono(40, 44, -0.035),
    displayMedium = mono(36, 40, -0.035),
    displaySmall = mono(30, 34, -0.03),
    headlineLarge = mono(28, 34, -0.03),
    headlineMedium = mono(24, 30, -0.02),
    headlineSmall = mono(22, 28, -0.02),
    titleLarge = mono(20, 26, -0.02),
    titleMedium = mono(16, 22),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 22),
    bodySmall = sans(12, 17),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(13, 18, FontWeight.Medium),
    labelSmall = sans(11, 16, FontWeight.Medium),
)
