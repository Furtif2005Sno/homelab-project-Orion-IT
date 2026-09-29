package com.homelab.app.ui.truenas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.TrueNasAlert
import com.homelab.app.data.repository.TrueNasDashboardSnapshot
import com.homelab.app.data.repository.TrueNasDisk
import com.homelab.app.data.repository.TrueNasPool
import com.homelab.app.data.repository.TrueNasServiceStatus
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ResourceFormatters
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrueNasDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: TrueNasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val accent = ServiceType.TRUENAS.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_truenas),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchDashboard(forceLoading = false) }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trueNasBrush(accent))
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                UiState.Loading, UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
                is UiState.Error -> ErrorScreen(
                    message = state.message,
                    onRetry = state.retryAction ?: { viewModel.fetchDashboard(forceLoading = true) }
                )
                UiState.Offline -> ErrorScreen(
                    message = stringResource(R.string.error_network),
                    onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                    isOffline = true
                )
                is UiState.Success -> TrueNasContent(
                    data = state.data,
                    instances = instances,
                    selectedInstanceId = viewModel.instanceId,
                    onInstanceSelected = {
                        viewModel.setPreferredInstance(it.id)
                        onNavigateToInstance(it.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrueNasContent(
    data: TrueNasDashboardSnapshot,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    selectedInstanceId: String,
    onInstanceSelected: (com.homelab.app.domain.model.ServiceInstance) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = selectedInstanceId,
                onInstanceSelected = onInstanceSelected,
                label = stringResource(R.string.truenas_instance_label)
            )
        }

        item { TrueNasHero(data) }
        item { TrueNasStorageOverview(data) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TrueNasMetricCard(
                    label = stringResource(R.string.truenas_healthy_pools),
                    value = "${data.healthyPoolCount}/${data.pools.size}",
                    icon = Icons.Default.Storage,
                    modifier = Modifier.weight(1f)
                )
                TrueNasMetricCard(
                    label = stringResource(R.string.truenas_running_services),
                    value = "${data.runningServiceCount}/${data.services.size}",
                    icon = Icons.Default.Dns,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TrueNasMetricCard(
                    label = stringResource(R.string.truenas_disks),
                    value = data.disks.size.toString(),
                    icon = Icons.Default.Memory,
                    modifier = Modifier.weight(1f)
                )
                TrueNasMetricCard(
                    label = stringResource(R.string.truenas_alerts),
                    value = data.alerts.size.toString(),
                    icon = Icons.Default.WarningAmber,
                    modifier = Modifier.weight(1f),
                    tint = if (data.alerts.isEmpty()) StatusGreen else StatusOrange
                )
            }
        }

        item { TrueNasPoolsSection(data.pools) }
        item { TrueNasSharesAndWorkloads(data) }
        item { TrueNasServicesSection(data.services) }
        item { TrueNasDisksSection(data.disks) }
        item { TrueNasAlertsSection(data.alerts) }
    }
}

@Composable
private fun TrueNasHero(data: TrueNasDashboardSnapshot) {
    val context = LocalContext.current
    TrueNasCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.TRUENAS, size = 60.dp, iconSize = 42.dp, cornerRadius = 18.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.system.hostname ?: stringResource(R.string.service_truenas),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = data.system.version ?: stringResource(R.string.truenas_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TrueNasInfoRow(stringResource(R.string.truenas_product), data.system.product ?: "-")
        TrueNasInfoRow(stringResource(R.string.truenas_memory), data.system.physicalMemoryBytes?.let { ResourceFormatters.formatBytes(it, context) } ?: "-")
        TrueNasInfoRow(stringResource(R.string.truenas_uptime), data.system.uptimeSeconds?.let(::formatUptime) ?: "-")
    }
}

@Composable
private fun TrueNasStorageOverview(data: TrueNasDashboardSnapshot) {
    val context = LocalContext.current
    val total = data.totalStorageBytes
    val used = data.usedStorageBytes.coerceAtLeast(0.0)
    val available = (total - used).coerceAtLeast(0.0)
    val fraction = if (total > 0.0) (used / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent = (fraction * 100).toInt()

    TrueNasCard(title = stringResource(R.string.truenas_storage_used), icon = Icons.Default.Storage) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ResourceFormatters.formatBytes(used, context),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ResourceFormatters.formatBytes(total, context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (fraction < 0.85f) ServiceType.TRUENAS.primaryColor else StatusOrange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (fraction < 0.85f) ServiceType.TRUENAS.primaryColor else StatusOrange,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.truenas_used) + ": " + ResourceFormatters.formatBytes(used, context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.truenas_available) + ": " + ResourceFormatters.formatBytes(available, context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrueNasPoolsSection(pools: List<TrueNasPool>) {
    TrueNasCard(title = stringResource(R.string.truenas_pools), icon = Icons.Default.Storage) {
        if (pools.isEmpty()) {
            Text(stringResource(R.string.truenas_no_pools), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            pools.forEachIndexed { index, pool ->
                if (index > 0) Spacer(modifier = Modifier.height(14.dp))
                TrueNasPoolRow(pool)
            }
        }
    }
}

@Composable
private fun TrueNasPoolRow(pool: TrueNasPool) {
    val context = LocalContext.current
    val statusColor = if (pool.healthy) StatusGreen else StatusOrange
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = pool.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            Text(
                text = ResourceFormatters.formatBytes(pool.freeBytes, context),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { pool.usedFraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (pool.usedFraction < 0.85f) ServiceType.TRUENAS.primaryColor else StatusOrange,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.truenas_used) + ": " + ResourceFormatters.formatBytes(pool.usedBytes, context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.truenas_available) + ": " + ResourceFormatters.formatBytes(pool.freeBytes, context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrueNasSharesAndWorkloads(data: TrueNasDashboardSnapshot) {
    TrueNasCard(title = stringResource(R.string.truenas_shares), icon = Icons.Default.CloudQueue) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric(stringResource(R.string.truenas_smb), data.shares.smb.toString(), Modifier.weight(1f))
            MiniMetric(stringResource(R.string.truenas_nfs), data.shares.nfs.toString(), Modifier.weight(1f))
            MiniMetric(stringResource(R.string.truenas_iscsi), data.shares.iscsi.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric(stringResource(R.string.truenas_apps), "${data.workloads.runningApps}/${data.workloads.apps}", Modifier.weight(1f))
            MiniMetric(stringResource(R.string.truenas_virtual_machines), "${data.workloads.runningVirtualMachines}/${data.workloads.virtualMachines}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrueNasServicesSection(services: List<TrueNasServiceStatus>) {
    TrueNasCard(title = stringResource(R.string.truenas_services), icon = Icons.Default.Dns) {
        if (services.isEmpty()) {
            Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            services.take(8).forEachIndexed { index, service ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (service.isRunning) StatusGreen else MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(service.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (service.isRunning) stringResource(R.string.truenas_running) else service.state.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrueNasDisksSection(disks: List<TrueNasDisk>) {
    TrueNasCard(title = stringResource(R.string.truenas_disks), icon = Icons.Default.Memory) {
        if (disks.isEmpty()) {
            Text(stringResource(R.string.truenas_no_disks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            disks.take(8).forEachIndexed { index, disk ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                TrueNasDiskRow(disk)
            }
        }
    }
}

@Composable
private fun TrueNasDiskRow(disk: TrueNasDisk) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = ServiceType.TRUENAS.primaryColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(disk.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(disk.model, disk.serial).joinToString(" - ").ifBlank { disk.status.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = disk.sizeBytes?.let { ResourceFormatters.formatBytes(it, context) } ?: "-",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrueNasAlertsSection(alerts: List<TrueNasAlert>) {
    TrueNasCard(title = stringResource(R.string.truenas_alerts), icon = Icons.Default.Security) {
        if (alerts.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.truenas_no_alerts), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            itemsPreview(alerts.take(4)) { alert ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = alertColor(alert.level), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alert.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrueNasMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = ServiceType.TRUENAS.primaryColor
) {
    TrueNasCard(modifier = modifier) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrueNasCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = ServiceType.TRUENAS.primaryColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            content()
        }
    }
}

@Composable
private fun TrueNasInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun <T> itemsPreview(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { index, item ->
        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
        row(item)
    }
}

private fun trueNasBrush(accent: Color): Brush {
    return Brush.verticalGradient(
        listOf(
            accent.copy(alpha = 0.10f),
            Color.Transparent,
            Color.Transparent
        )
    )
}

@Composable
private fun alertColor(level: String): Color {
    return when (level.lowercase()) {
        "critical", "error", "alert" -> StatusRed
        "warning", "warn" -> StatusOrange
        else -> StatusGreen
    }
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
