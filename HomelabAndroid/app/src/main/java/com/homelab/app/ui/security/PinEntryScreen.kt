package com.homelab.app.ui.security

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.homelab.app.R
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Lock

internal data class SecurityScreenPalette(
    val backgroundBrush: Brush,
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val iconFill: Color,
    val iconStroke: Color,
    val dotEmpty: Color,
    val keypadFill: Color,
    val keypadAltFill: Color,
    val keypadStroke: Color,
    val keypadText: Color
)

@Composable
internal fun rememberSecurityScreenPalette(): SecurityScreenPalette {
    // Arcane surfaces: flat background, translucent cards with a hairline border, orange only for state.
    val scheme = MaterialTheme.colorScheme
    val palette = com.homelab.app.ui.theme.LocalOrionPalette.current
    return SecurityScreenPalette(
        backgroundBrush = androidx.compose.ui.graphics.SolidColor(scheme.background),
        accent = scheme.primary,
        primaryText = scheme.onBackground,
        secondaryText = scheme.onSurfaceVariant,
        iconFill = scheme.surfaceContainerLow,
        iconStroke = scheme.outlineVariant,
        dotEmpty = palette.muted,
        keypadFill = scheme.surfaceContainerLow,
        keypadAltFill = scheme.background,
        keypadStroke = scheme.outlineVariant,
        keypadText = scheme.onBackground
    )
}

// MARK: - Reusable PIN Entry

@Composable
fun PinEntryScreen(
    title: String,
    subtitle: String,
    errorMessage: String? = null,
    showBiometric: Boolean = false,
    onBiometricTap: (() -> Unit)? = null,
    onPinComplete: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }
    val palette = rememberSecurityScreenPalette()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            repeat(2) {
                shakeOffset.animateTo(8f, spring(stiffness = 800f, dampingRatio = 0.7f))
                shakeOffset.animateTo(-8f, spring(stiffness = 800f, dampingRatio = 0.7f))
            }
            shakeOffset.animateTo(0f, spring(stiffness = 500f, dampingRatio = 0.8f))
            pin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.35f))

            // Header Icon
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialTheme.shapes.large,
                color = palette.iconFill,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.iconStroke),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Lucide.Lock,
                        contentDescription = title,
                        modifier = Modifier.size(30.dp),
                        tint = palette.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.primaryText
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // PIN Dots with spring animation
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                repeat(6) { index ->
                    val isFilled = index < pin.length
                    val dotScale by animateFloatAsState(
                        targetValue = if (isFilled) 1.25f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "dotScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) palette.accent else palette.dotEmpty
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error with animation
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Number Pad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { digit ->
                            NumberButton(digit) {
                                if (pin.length < 6) {
                                    pin += digit
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (pin.length == 6) {
                                        scope.launch {
                                            delay(100)
                                            onPinComplete(pin)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Last row: biometric, 0, delete
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric Button
                    if (showBiometric && onBiometricTap != null) {
                        PadButton(onClick = onBiometricTap) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = stringResource(R.string.security_enable_biometric),
                                tint = palette.keypadText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(76.dp))
                    }

                    NumberButton("0") {
                        if (pin.length < 6) {
                            pin += "0"
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (pin.length == 6) {
                                scope.launch {
                                    delay(100)
                                    onPinComplete(pin)
                                }
                            }
                        }
                    }

                    // Delete Button
                    PadButton(
                        onClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.delete),
                            tint = palette.keypadText
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NumberButton(digit: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val palette = rememberSecurityScreenPalette()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "buttonScale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(76.dp)
            .scale(scale),
        shape = CircleShape,
        color = palette.keypadFill,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.keypadStroke),
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = palette.keypadText
            )
        }
    }
}

@Composable
private fun PadButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val palette = rememberSecurityScreenPalette()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "padScale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(76.dp)
            .scale(scale),
        shape = CircleShape,
        color = palette.keypadAltFill,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.keypadStroke),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
