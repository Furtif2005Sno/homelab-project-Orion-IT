package com.homelab.app.ui.safeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.SafelineAttackEvent
import com.homelab.app.data.repository.SafelineDashboardData
import com.homelab.app.data.repository.SafelineSite
import com.homelab.app.data.repository.SafelineSiteMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ArcaneBadge
import com.homelab.app.ui.components.ArcaneCard
import com.homelab.app.ui.components.ArcaneServiceDashboard
import com.homelab.app.ui.components.ArcaneStatTile
import com.homelab.app.ui.components.LocalNavBarInset
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.OrionCodeStyle
import com.homelab.app.ui.theme.OrionOverlineStyle
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SafelineDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: SafelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    ArcaneServiceDashboard(
        title = stringResource(R.string.service_safeline),
        accent = ServiceType.SAFELINE.primaryColor,
        uiState = uiState,
        isRefreshing = isRefreshing,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.fetchDashboard(forceLoading = false) },
        onRetry = { viewModel.fetchDashboard(forceLoading = true) }
    ) { data ->
        SafelineContent(
            data = data,
            instances = instances,
            selectedInstanceId = viewModel.instanceId,
            onInstanceSelected = {
                viewModel.setPreferredInstance(it.id)
                onNavigateToInstance(it.id)
            }
        )
    }
}

@Composable
private fun SafelineContent(
    data: SafelineDashboardData,
    instances: List<ServiceInstance>,
    selectedInstanceId: String,
    onInstanceSelected: (ServiceInstance) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp + LocalNavBarInset.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (instances.size > 1) {
            item {
                ServiceInstancePicker(
                    instances = instances,
                    selectedInstanceId = selectedInstanceId,
                    onInstanceSelected = onInstanceSelected,
                    label = stringResource(R.string.safeline_instance_label)
                )
            }
        }
        item { SafelineOverviewCard(data) }

        item { SectionTitle(stringResource(R.string.safeline_sites_title)) }
        if (data.sites.isEmpty()) {
            item { EmptyCard(stringResource(R.string.safeline_no_sites)) }
        } else {
            items(data.sites, key = { "site-${it.id}" }) { site -> SafelineSiteRow(site) }
        }

        item { SectionTitle(stringResource(R.string.safeline_attacks_title)) }
        if (data.events.isEmpty()) {
            item { EmptyCard(stringResource(R.string.safeline_no_attacks)) }
        } else {
            items(data.events, key = { "event-${it.id}" }) { event -> SafelineAttackRow(event) }
        }
    }
}

private fun formatCount(value: Long?): String = value?.let { NumberFormat.getInstance().format(it) } ?: "-"

@Composable
private fun SafelineOverviewCard(data: SafelineDashboardData) {
    ArcaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.SAFELINE, size = 48.dp, iconSize = 30.dp, cornerRadius = 12.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.service_safeline), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.safeline_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data.version?.let { Text(it, style = OrionCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ArcaneStatTile(stringResource(R.string.safeline_stat_requests), formatCount(data.todayRequests), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            ArcaneStatTile(stringResource(R.string.safeline_stat_visitors), formatCount(data.todayVisitors), StatusBlue, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ArcaneStatTile(
                stringResource(R.string.safeline_stat_blocked),
                formatCount(data.blocked),
                if ((data.blocked ?: 0) > 0) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
            ArcaneStatTile(stringResource(R.string.safeline_stat_attack_ips), formatCount(data.attackIps), StatusOrange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = OrionOverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun EmptyCard(text: String) {
    ArcaneCard {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SafelineSiteRow(site: SafelineSite) {
    val (modeLabel, modeColor) = when {
        !site.enabled -> stringResource(R.string.safeline_mode_disabled) to MaterialTheme.colorScheme.onSurfaceVariant
        site.mode == SafelineSiteMode.PROTECT -> stringResource(R.string.safeline_mode_protect) to StatusGreen
        site.mode == SafelineSiteMode.OBSERVE -> stringResource(R.string.safeline_mode_observe) to StatusOrange
        site.mode == SafelineSiteMode.MAINTENANCE -> stringResource(R.string.safeline_mode_maintenance) to StatusBlue
        else -> stringResource(R.string.peanut_state_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ArcaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(site.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val hosts = site.serverNames.joinToString(", ")
                if (hosts.isNotBlank() && hosts != site.title) {
                    Text(hosts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val route = listOfNotNull(
                    site.ports.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = ":"),
                    site.upstreams.firstOrNull()?.let { "→ $it" }
                ).joinToString("  ")
                if (route.isNotBlank()) {
                    Text(route, style = OrionCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ArcaneBadge(text = modeLabel, color = modeColor)
        }
    }
}

@Composable
private fun SafelineAttackRow(event: SafelineAttackEvent) {
    ArcaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.ip, style = OrionCodeStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), maxLines = 1)
                val target = listOfNotNull(event.host, event.port?.let { ":$it" }).joinToString("")
                val subtitle = listOfNotNull(target.ifBlank { null }, event.location).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                event.startAtEpochSeconds?.let { seconds ->
                    Text(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(seconds)),
                        style = OrionCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ArcaneBadge(text = stringResource(R.string.safeline_blocked_count, event.denyCount), color = StatusRed)
                if (event.passCount > 0) {
                    ArcaneBadge(text = stringResource(R.string.safeline_passed_count, event.passCount), color = StatusOrange)
                }
            }
        }
    }
}
