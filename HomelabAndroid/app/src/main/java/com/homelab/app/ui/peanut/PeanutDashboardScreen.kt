package com.homelab.app.ui.peanut

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.PeanutDashboardData
import com.homelab.app.data.repository.PeanutDevice
import com.homelab.app.data.repository.PeanutPowerState
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ArcaneBadge
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
import com.homelab.app.util.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeanutDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: PeanutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val accent = ServiceType.PEANUT.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_peanut),
                        style = MaterialTheme.typography.headlineSmall,
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                is UiState.Success -> PeanutContent(
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
private fun PeanutContent(
    data: PeanutDashboardData,
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
                    label = stringResource(R.string.peanut_instance_label)
                )
            }
        }

        item { PeanutOverviewCard(data) }

        if (data.devices.isEmpty()) {
            item {
                PeanutCard {
                    Text(
                        text = stringResource(R.string.peanut_no_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(data.devices, key = { it.id }) { device -> PeanutDeviceCard(device) }
        }
    }
}

/** Arcane card: translucent fill, hairline border, rounded-xl. */
@Composable
private fun PeanutCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun PeanutOverviewCard(data: PeanutDashboardData) {
    PeanutCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.PEANUT, size = 48.dp, iconSize = 30.dp, cornerRadius = 12.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.service_peanut), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.peanut_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            data.version?.let {
                Text("v$it", style = OrionCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PeanutStat(stringResource(R.string.peanut_stat_devices), "${data.devices.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            PeanutStat(stringResource(R.string.peanut_stat_online), "${data.onlineCount}", StatusGreen, Modifier.weight(1f))
            PeanutStat(
                stringResource(R.string.peanut_stat_on_battery),
                "${data.onBatteryCount}",
                if (data.onBatteryCount > 0) StatusOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PeanutStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label.uppercase(), style = OrionOverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        }
    }
}

@Composable
private fun PeanutDeviceCard(device: PeanutDevice) {
    val (stateLabel, stateColor) = when (device.powerState) {
        PeanutPowerState.ONLINE -> stringResource(R.string.peanut_state_online) to StatusGreen
        PeanutPowerState.ON_BATTERY -> stringResource(R.string.peanut_state_on_battery) to StatusOrange
        PeanutPowerState.LOW_BATTERY -> stringResource(R.string.peanut_state_low_battery) to StatusRed
        PeanutPowerState.OFFLINE -> stringResource(R.string.peanut_state_offline) to StatusRed
        PeanutPowerState.UNKNOWN -> stringResource(R.string.peanut_state_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    PeanutCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(device.manufacturer, device.model.takeIf { it != device.name }).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            ArcaneBadge(text = stateLabel, color = stateColor)
        }

        device.batteryCharge?.let { charge ->
            PeanutGauge(
                label = stringResource(R.string.peanut_battery),
                valueText = "${charge.roundToInt()} %",
                fraction = (charge / 100.0).toFloat(),
                color = when {
                    charge <= 20 -> StatusRed
                    charge <= 50 -> StatusOrange
                    else -> StatusGreen
                }
            )
        }
        device.load?.let { load ->
            PeanutGauge(
                label = stringResource(R.string.peanut_load),
                valueText = "${load.roundToInt()} %",
                fraction = (load / 100.0).toFloat(),
                color = when {
                    load >= 90 -> StatusRed
                    load >= 70 -> StatusOrange
                    else -> StatusBlue
                }
            )
        }

        val facts = buildList {
            device.runtimeSeconds?.let { add(stringResource(R.string.peanut_runtime) to formatRuntime(it)) }
            device.estimatedWatts?.let { add(stringResource(R.string.peanut_power) to "${it.roundToInt()} W") }
            device.inputVoltage?.let { add(stringResource(R.string.peanut_input_voltage) to "${it.roundToInt()} V") }
            device.outputVoltage?.let { add(stringResource(R.string.peanut_output_voltage) to "${it.roundToInt()} V") }
            device.batteryVoltage?.let { add(stringResource(R.string.peanut_battery_voltage) to String.format("%.1f V", it)) }
        }
        if (facts.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                facts.forEach { (label, value) -> PeanutFact(label, value) }
            }
        }

        val flags = buildList {
            if (device.isCharging) add(stringResource(R.string.peanut_flag_charging) to StatusBlue)
            if (device.needsBatteryReplacement) add(stringResource(R.string.peanut_flag_replace_battery) to StatusRed)
            if (device.isOverloaded) add(stringResource(R.string.peanut_flag_overload) to StatusRed)
        }
        if (flags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                flags.forEach { (label, color) -> ArcaneBadge(text = label, color = color) }
            }
        }

        if (device.statusFlags.isNotEmpty()) {
            Text(
                text = "ups.status: ${device.statusFlags.joinToString(" ")}",
                style = OrionCodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeanutGauge(label: String, valueText: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = color)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
private fun PeanutFact(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = OrionOverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun formatRuntime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours} h ${minutes.toString().padStart(2, '0')}" else "$minutes min"
}
