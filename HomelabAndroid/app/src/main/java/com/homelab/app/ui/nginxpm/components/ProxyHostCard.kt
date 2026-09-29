package com.homelab.app.ui.nginxpm.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.NpmProxyHost
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed

private fun npmCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.10f else 0.07f
): Color {
    val base = if (isDarkTheme) Color(0xFF271C16) else Color(0xFFFFF0E8)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.22f)) } ?: base
}

private fun npmRaisedCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.08f else 0.05f
): Color {
    val base = if (isDarkTheme) Color(0xFF33251E) else Color(0xFFFFF6F1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

private fun npmBorderTone(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.28f else 0.20f
): Color {
    val base = if (isDarkTheme) Color(0xFF5A4336) else Color(0xFFE2BFA8)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.35f)) } ?: base
}

private fun npmBorderColor(isDarkTheme: Boolean): Color =
    npmBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProxyHostCard(
    proxyHost: NpmProxyHost,
    onClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggle: ((Boolean) -> Unit)? = null
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    var showMenu by remember { mutableStateOf(false) }

    val statusResId = when {
        !proxyHost.isEnabled -> R.string.npm_disabled
        proxyHost.isOnline -> R.string.home_status_online
        else -> R.string.home_status_offline
    }
    val statusColor = when {
        !proxyHost.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        proxyHost.isOnline -> StatusGreen
        else -> StatusRed
    }
    val cardAccent = statusColor

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = npmCardColor(isDarkTheme, accent = cardAccent),
        border = BorderStroke(1.dp, npmBorderTone(isDarkTheme, accent = cardAccent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = proxyHost.primaryDomain,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = npmRaisedCardColor(isDarkTheme, accent = statusColor, tint = 0.10f)
                    ) {
                        Text(
                            text = stringResource(statusResId),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = statusColor
                        )
                    }

                }

                if (proxyHost.domainNames.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = proxyHost.domainNames.drop(1).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = proxyHost.forwardTarget,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (proxyHost.hasSSL) {
                        FeatureChip(icon = Icons.Default.Lock, text = "SSL")
                    }
                    if (proxyHost.http2Support == 1) {
                        FeatureChip(icon = Icons.Default.Speed, text = "HTTP/2")
                    }
                    if (proxyHost.cachingEnabled == 1) {
                        FeatureChip(icon = Icons.Default.Cached, text = stringResource(R.string.npm_cache))
                    }
                    if (proxyHost.blockExploits == 1) {
                        FeatureChip(icon = Icons.Default.Shield, text = stringResource(R.string.npm_block_exploits))
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                if (onToggle != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (proxyHost.isEnabled) stringResource(R.string.npm_disabled)
                                else stringResource(R.string.npm_enabled)
                            )
                        },
                        onClick = { showMenu = false; onToggle(!proxyHost.isEnabled) },
                        leadingIcon = {
                            Icon(
                                if (proxyHost.isEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.npm_delete)) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun FeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val chipColor = featureChipColor(text)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = npmRaisedCardColor(isDarkTheme, accent = chipColor, tint = if (isDarkTheme) 0.12f else 0.08f),
        border = BorderStroke(1.dp, npmBorderTone(isDarkTheme, accent = chipColor).copy(alpha = if (isDarkTheme) 0.62f else 0.52f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = text,
                modifier = Modifier.size(12.dp),
                tint = chipColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = chipColor
            )
        }
    }
}

@Composable
private fun featureChipColor(text: String): Color =
    when (text.lowercase()) {
        "ssl" -> StatusGreen
        "http/2" -> Color(0xFF2196F3)
        "cache" -> Color(0xFFFFB74D)
        "block exploits" -> Color(0xFFF44336)
        else -> Color(0xFF8E8E93)
    }
