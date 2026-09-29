package com.homelab.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import com.homelab.app.R
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.util.VpnProvider
import com.homelab.app.util.VpnStatus

/**
 * Shows the detected VPN client (or the one to turn on when services are unreachable) and opens it on tap.
 */
@Composable
fun VpnStatusCard(
    status: VpnStatus,
    hasUnreachableServices: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val provider = status.provider
    val name = when (provider) {
        null, VpnProvider.OTHER -> stringResource(R.string.vpn_generic_name)
        else -> provider.displayName
    }
    val description = when {
        status.isActive -> stringResource(R.string.vpn_desc_active, name)
        hasUnreachableServices && provider != null && provider != VpnProvider.OTHER ->
            stringResource(R.string.vpn_desc_unreachable, name)
        hasUnreachableServices -> stringResource(R.string.vpn_desc_unreachable_generic)
        else -> stringResource(R.string.vpn_desc_inactive, name)
    }
    val statusColor = if (status.isActive) StatusGreen else StatusOrange

    Surface(
        onClick = { openVpnClient(context, provider) },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val fallback = @Composable {
                        Icon(
                            imageVector = Icons.Default.VpnLock,
                            contentDescription = null,
                            tint = if (status.isActive) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    val iconUrl = provider?.iconUrl
                    if (iconUrl == null) {
                        fallback()
                    } else {
                        SubcomposeAsyncImage(
                            model = iconUrl,
                            contentDescription = name,
                            modifier = Modifier.size(26.dp),
                            contentScale = ContentScale.Fit,
                            loading = { fallback() },
                            error = { fallback() }
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Orion IT badge: status color at 12 % fill, 30 % border, full-color label.
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Text(
                        text = stringResource(if (status.isActive) R.string.vpn_status_connected else R.string.vpn_status_disconnected),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

/** Opens the VPN client app, or the system VPN settings for built-in / unidentified VPNs. */
fun openVpnClient(context: Context, provider: VpnProvider?) {
    val pm = context.packageManager
    val launch = provider?.packages?.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    if (launch != null) {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val storePackage = provider?.packages?.firstOrNull()
    if (provider == null || provider == VpnProvider.OTHER || provider == VpnProvider.IPSEC || storePackage == null) {
        try {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$storePackage".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$storePackage".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
