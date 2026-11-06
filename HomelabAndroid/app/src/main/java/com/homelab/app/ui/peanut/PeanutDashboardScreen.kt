package com.homelab.app.ui.peanut

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.homelab.app.data.repository.PeanutDashboardData
import com.homelab.app.data.repository.PeanutDevice
import com.homelab.app.data.repository.PeanutPowerState
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ArcaneBadge
import com.homelab.app.ui.components.ArcaneCard
import com.homelab.app.ui.components.ArcaneFact
import com.homelab.app.ui.components.ArcaneGauge
import com.homelab.app.ui.components.ArcaneServiceDashboard
import com.homelab.app.ui.components.ArcaneStatTile
import com.homelab.app.ui.components.LocalNavBarInset
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.OrionCodeStyle
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import kotlin.math.roundToInt

@Composable
fun PeanutDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: PeanutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    ArcaneServiceDashboard(
        title = stringResource(R.string.service_peanut),
        accent = ServiceType.PEANUT.primaryColor,
        uiState = uiState,
        isRefreshing = isRefreshing,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.fetchDashboard(forceLoading = false) },
        onRetry = { viewModel.fetchDashboard(forceLoading = true) }
    ) { data ->
        PeanutContent(
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
                ArcaneCard {
                    Text(stringResource(R.string.peanut_no_devices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(data.devices, key = { it.id }) { device -> PeanutDeviceCard(device) }
        }
    }
}

@Composable
private fun PeanutOverviewCard(data: PeanutDashboardData) {
    ArcaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.PEANUT, size = 48.dp, iconSize = 30.dp, cornerRadius = 12.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.service_peanut), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.peanut_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data.version?.let { Text("v$it", style = OrionCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ArcaneStatTile(stringResource(R.string.peanut_stat_devices), "${data.devices.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            ArcaneStatTile(stringResource(R.string.peanut_stat_online), "${data.onlineCount}", StatusGreen, Modifier.weight(1f))
            ArcaneStatTile(
                stringResource(R.string.peanut_stat_on_battery),
                "${data.onBatteryCount}",
                if (data.onBatteryCount > 0) StatusOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f)
            )
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
    ArcaneCard {
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
            ArcaneGauge(
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
            ArcaneGauge(
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                facts.forEach { (label, value) -> ArcaneFact(label, value) }
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
            Text("ups.status: ${device.statusFlags.joinToString(" ")}", style = OrionCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatRuntime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours} h ${minutes.toString().padStart(2, '0')}" else "$minutes min"
}
