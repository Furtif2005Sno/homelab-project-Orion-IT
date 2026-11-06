package com.homelab.app.ui.amp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Square
import com.homelab.app.R
import com.homelab.app.data.repository.AmpAction
import com.homelab.app.data.repository.AmpAppState
import com.homelab.app.data.repository.AmpDashboardData
import com.homelab.app.data.repository.AmpGameInstance
import com.homelab.app.data.repository.AmpMetric
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ArcaneBadge
import com.homelab.app.ui.components.ArcaneButton
import com.homelab.app.ui.components.ArcaneCard
import com.homelab.app.ui.components.ArcaneGauge
import com.homelab.app.ui.components.ArcaneOutlineButton
import com.homelab.app.ui.components.ArcaneServiceDashboard
import com.homelab.app.ui.components.ArcaneStatTile
import com.homelab.app.ui.components.LocalNavBarInset
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import kotlin.math.roundToInt

@Composable
fun AmpDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: AmpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val busyInstanceId by viewModel.busyInstanceId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingStop by remember { mutableStateOf<AmpGameInstance?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ArcaneServiceDashboard(
        title = stringResource(R.string.service_amp),
        accent = ServiceType.AMP.primaryColor,
        uiState = uiState,
        isRefreshing = isRefreshing,
        onNavigateBack = onNavigateBack,
        onRefresh = { viewModel.fetchDashboard(forceLoading = false) },
        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
        snackbarHostState = snackbarHostState
    ) { data ->
        AmpContent(
            data = data,
            instances = instances,
            selectedInstanceId = viewModel.instanceId,
            busyInstanceId = busyInstanceId,
            onInstanceSelected = {
                viewModel.setPreferredInstance(it.id)
                onNavigateToInstance(it.id)
            },
            onAction = { target, action ->
                // Stopping a game server kicks its players: ask first.
                if (action == AmpAction.STOP) pendingStop = target else viewModel.runAction(target, action)
            }
        )
    }

    pendingStop?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingStop = null },
            title = { Text(stringResource(R.string.amp_stop_confirm_title, target.friendlyName)) },
            text = { Text(stringResource(R.string.amp_stop_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.runAction(target, AmpAction.STOP)
                    pendingStop = null
                }) { Text(stringResource(R.string.amp_action_stop), color = StatusRed) }
            },
            dismissButton = { TextButton(onClick = { pendingStop = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun AmpContent(
    data: AmpDashboardData,
    instances: List<ServiceInstance>,
    selectedInstanceId: String,
    busyInstanceId: String?,
    onInstanceSelected: (ServiceInstance) -> Unit,
    onAction: (AmpGameInstance, AmpAction) -> Unit
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
                    label = stringResource(R.string.amp_instance_label)
                )
            }
        }
        item {
            ArcaneCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceIcon(type = ServiceType.AMP, size = 48.dp, iconSize = 30.dp, cornerRadius = 12.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(stringResource(R.string.service_amp), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.amp_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ArcaneStatTile(stringResource(R.string.amp_stat_instances), "${data.instances.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    ArcaneStatTile(stringResource(R.string.amp_stat_running), "${data.runningCount}", StatusGreen, Modifier.weight(1f))
                    val players = data.instances.sumOf { it.users?.current?.roundToInt() ?: 0 }
                    ArcaneStatTile(stringResource(R.string.amp_stat_players), "$players", StatusBlue, Modifier.weight(1f))
                }
            }
        }
        if (data.instances.isEmpty()) {
            item {
                ArcaneCard {
                    Text(stringResource(R.string.amp_no_instances), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(data.instances, key = { it.id }) { instance ->
                AmpInstanceCard(instance, busy = busyInstanceId == instance.id, anyBusy = busyInstanceId != null, onAction = onAction)
            }
        }
    }
}

@Composable
private fun AmpInstanceCard(
    instance: AmpGameInstance,
    busy: Boolean,
    anyBusy: Boolean,
    onAction: (AmpGameInstance, AmpAction) -> Unit
) {
    val (stateLabel, stateColor) = when (instance.state) {
        AmpAppState.RUNNING -> stringResource(R.string.amp_state_running) to StatusGreen
        AmpAppState.STARTING, AmpAppState.RESTARTING -> stringResource(R.string.amp_state_starting) to StatusBlue
        AmpAppState.STOPPING -> stringResource(R.string.amp_state_stopping) to StatusOrange
        AmpAppState.STOPPED, AmpAppState.OFFLINE -> stringResource(R.string.amp_state_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
        AmpAppState.SLEEPING -> stringResource(R.string.amp_state_sleeping) to StatusBlue
        AmpAppState.UPDATING -> stringResource(R.string.amp_state_updating) to StatusOrange
        AmpAppState.FAILED -> stringResource(R.string.amp_state_failed) to StatusRed
        AmpAppState.SUSPENDED -> stringResource(R.string.amp_state_suspended) to StatusOrange
        AmpAppState.UNKNOWN -> stringResource(R.string.peanut_state_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val running = instance.state == AmpAppState.RUNNING || instance.state == AmpAppState.STARTING || instance.state == AmpAppState.RESTARTING

    ArcaneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.friendlyName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(instance.module, instance.name.takeIf { it != instance.friendlyName }).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            ArcaneBadge(text = stateLabel, color = stateColor)
        }

        if (running) {
            instance.cpu?.let { MetricGauge(stringResource(R.string.amp_cpu), it) }
            instance.memory?.let { MetricGauge(stringResource(R.string.amp_memory), it) }
            instance.users?.let { users ->
                val max = users.max?.roundToInt()
                Text(
                    text = stringResource(R.string.amp_players_value, users.current.roundToInt(), max?.toString() ?: "∞"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (running) {
                ArcaneOutlineButton(
                    text = stringResource(R.string.amp_action_restart),
                    icon = Lucide.RotateCw,
                    onClick = { onAction(instance, AmpAction.RESTART) },
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f)
                )
                ArcaneOutlineButton(
                    text = stringResource(R.string.amp_action_stop),
                    icon = Lucide.Square,
                    onClick = { onAction(instance, AmpAction.STOP) },
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f)
                )
            } else {
                ArcaneButton(
                    text = if (busy) stringResource(R.string.amp_state_starting) else stringResource(R.string.amp_action_start),
                    icon = Lucide.Play,
                    onClick = { onAction(instance, AmpAction.START) },
                    enabled = !anyBusy && instance.state != AmpAppState.SUSPENDED,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricGauge(label: String, metric: AmpMetric) {
    val fraction = when {
        metric.percent != null -> metric.percent / 100.0
        metric.max != null -> metric.current / metric.max
        else -> 0.0
    }.toFloat()
    val units = metric.units.orEmpty()
    val valueText = when {
        units == "%" -> "${metric.current.roundToInt()} %"
        metric.max != null -> "${metric.current.roundToInt()} / ${metric.max.roundToInt()} $units".trim()
        else -> "${metric.current.roundToInt()} $units".trim()
    }
    ArcaneGauge(
        label = label,
        valueText = valueText,
        fraction = fraction,
        color = when {
            fraction >= 0.9f -> StatusRed
            fraction >= 0.7f -> StatusOrange
            else -> StatusBlue
        }
    )
}
