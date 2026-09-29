package com.homelab.app.ui.portainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.portainer.PortainerEndpoint
import com.homelab.app.data.remote.dto.portainer.DockerSnapshotRaw
import com.homelab.app.ui.components.M3ExpressiveButtonCard
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.util.ResourceFormatters
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

private fun portainerPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A1018),
            Color(0xFF0E1520),
            accent.copy(alpha = 0.05f),
            Color(0xFF0A111A)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF8F8F5),
            Color(0xFFF4F4F1),
            accent.copy(alpha = 0.015f),
            Color(0xFFF7F7F4)
        )
    )
}

private fun portainerCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.055f else 0.03f
): Color {
    val base = if (isDarkTheme) Color(0xFF121C2A) else Color(0xFFF4F4F1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.22f)) } ?: base
}

private fun portainerRaisedCardColor(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.05f else 0.025f
): Color {
    val base = if (isDarkTheme) Color(0xFF1A2638) else Color(0xFFF9F9F7)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.18f)) } ?: base
}

private fun portainerBorderTone(
    isDarkTheme: Boolean,
    accent: Color? = null,
    tint: Float = if (isDarkTheme) 0.14f else 0.08f
): Color {
    val base = if (isDarkTheme) Color(0xFF34465F) else Color(0xFFD4D6D1)
    return accent?.let { lerp(base, it, tint.coerceIn(0f, 0.35f)) } ?: base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortainerDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToContainers: (endpointId: Int) -> Unit,
    viewModel: PortainerViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val endpoints by viewModel.endpoints.collectAsStateWithLifecycle()
    val selectedEndpoint by viewModel.selectedEndpoint.collectAsStateWithLifecycle()
    val containers by viewModel.containers.collectAsStateWithLifecycle()
    val accent = ServiceType.PORTAINER.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { portainerPageBackground(isDarkTheme, accent) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) accent.copy(alpha = 0.09f) else accent.copy(alpha = 0.045f)
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_portainer), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.fetchAll()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Loading, is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchAll() },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchAll() },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is UiState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ServiceInstancePicker(
                                instances = instances,
                                selectedInstanceId = viewModel.instanceId,
                                onInstanceSelected = { instance ->
                                    viewModel.setPreferredInstance(instance.id)
                                    onNavigateToInstance(instance.id)
                                }
                            )
                        }

                        // Endpoint Picker if > 1
                        if (endpoints.size > 1) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = stringResource(R.string.portainer_endpoints),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(endpoints) { ep ->
                                        EndpointCard(
                                            endpoint = ep,
                                            isSelected = selectedEndpoint?.id == ep.id,
                                            onClick = { viewModel.selectEndpoint(ep) }
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedEndpoint != null) {
                            val raw = selectedEndpoint?.snapshots?.firstOrNull()?.dockerSnapshotRaw
                            val hasInfo = (raw?.resolvedOperatingSystem?.isNotBlank() == true && raw.resolvedOperatingSystem != "N/A") ||
                                    (raw?.resolvedServerVersion?.isNotBlank() == true && raw.resolvedServerVersion != "N/A") ||
                                    (raw?.resolvedArchitecture?.isNotBlank() == true && raw.resolvedArchitecture != "N/A")

                            if (hasInfo) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(R.string.portainer_info_title),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    ServerInfoSection(endpoint = selectedEndpoint!!, raw = raw!!)
                                }
                            }

                            val snapshot = selectedEndpoint?.snapshots?.firstOrNull()
                            val total = containers.size
                            val running = containers.count { it.state == "running" }
                            val stopped = containers.count { it.state == "exited" || it.state == "dead" }
                            val stackCount = snapshot?.stackCount ?: 0

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = stringResource(R.string.portainer_containers).uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ContainerSummaryCard(
                                    total = total,
                                    running = running,
                                    stopped = stopped,
                                    stacks = stackCount,
                                    onViewAll = { onNavigateToContainers(selectedEndpoint!!.id) }
                                )
                            }

                            if (snapshot != null) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(R.string.beszel_resources_title),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item { ResourceCard(icon = Icons.Default.Image, value = "${snapshot.imageCount}", label = stringResource(R.string.portainer_images), color = Color(0xFFFF9800)) }
                                item { ResourceCard(icon = Icons.Default.SdStorage, value = "${snapshot.volumeCount}", label = stringResource(R.string.portainer_volumes), color = ServiceType.PORTAINER.primaryColor) }
                                item {
                                    val cpuValue = if (snapshot.totalCpu > 0) "${snapshot.totalCpu}" else stringResource(R.string.not_available)
                                    ResourceCard(
                                        icon = Icons.Default.Memory,
                                        value = cpuValue,
                                        label = stringResource(R.string.portainer_cpu_label),
                                        color = Color(0xFF2196F3)
                                    )
                                }
                                item {
                                    val context = LocalContext.current
                                    val memoryValue = if (snapshot.totalMemory > 0) {
                                        ResourceFormatters.formatBytes(snapshot.totalMemory.toDouble(), context)
                                    } else {
                                        stringResource(R.string.not_available)
                                    }
                                    ResourceCard(
                                        icon = Icons.Default.Storage,
                                        value = memoryValue,
                                        label = stringResource(R.string.beszel_memory),
                                        color = Color(0xFF9C27B0)
                                    )
                                }

                                if (snapshot.healthyContainerCount > 0 || snapshot.unhealthyContainerCount > 0) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = stringResource(R.string.portainer_health),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (snapshot.healthyContainerCount > 0) {
                                        item(span = { GridItemSpan(maxLineSpan) }) { MiniStatCard(label = stringResource(R.string.portainer_healthy), value = snapshot.healthyContainerCount, color = Color(0xFF4CAF50)) }
                                    }
                                    if (snapshot.unhealthyContainerCount > 0) {
                                        item(span = { GridItemSpan(maxLineSpan) }) { MiniStatCard(label = stringResource(R.string.portainer_unhealthy), value = snapshot.unhealthyContainerCount, color = Color(0xFFF44336)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(
    endpoint: PortainerEndpoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyScale"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ),
        shape = RoundedCornerShape(16.dp),
        color = portainerCardColor(
            isDarkTheme,
            accent = when {
                isSelected -> ServiceType.PORTAINER.primaryColor
                endpoint.isOnline -> Color(0xFF4CAF50)
                else -> Color(0xFFF44336)
            },
            tint = if (isSelected) if (isDarkTheme) 0.16f else 0.10f else if (isDarkTheme) 0.10f else 0.06f
        ),
        border = BorderStroke(
            1.dp,
            portainerBorderTone(
                isDarkTheme,
                accent = when {
                    isSelected -> ServiceType.PORTAINER.primaryColor
                    endpoint.isOnline -> Color(0xFF4CAF50)
                    else -> Color(0xFFF44336)
                }
            ).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isSelected) {
                    Surface(
                        color = ServiceType.PORTAINER.primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.portainer_active),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ServiceType.PORTAINER.primaryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = endpoint.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = endpoint.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerInfoSection(endpoint: PortainerEndpoint, raw: DockerSnapshotRaw) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val shellAccent = if (isDarkTheme) ServiceType.PORTAINER.primaryColor else null
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = portainerCardColor(isDarkTheme, accent = shellAccent, tint = if (isDarkTheme) 0.11f else 0f),
        border = BorderStroke(1.dp, portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ServiceType.PORTAINER.primaryColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = stringResource(R.string.portainer_info_title),
                        tint = ServiceType.PORTAINER.primaryColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = endpoint.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (endpoint.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = stringResource(if (endpoint.isOnline) R.string.home_status_online else R.string.home_status_offline),
                            tint = if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (endpoint.isOnline) R.string.home_status_online else R.string.home_status_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = portainerRaisedCardColor(isDarkTheme, accent = shellAccent, tint = if (isDarkTheme) 0.06f else 0f),
                border = BorderStroke(1.dp, portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.64f else 0.52f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val os = raw.resolvedOperatingSystem?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    val docker = raw.resolvedServerVersion?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    val arch = raw.resolvedArchitecture?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    
                    val snapshot = endpoint.snapshots?.firstOrNull()
                    val cpuCores = snapshot?.totalCpu?.takeIf { it > 0 }
                    val cpuCoresLabel = cpuCores?.let { "$it ${stringResource(R.string.beszel_cores)}" }
                        ?: stringResource(R.string.not_available)
                    
                    InfoRow(label = stringResource(R.string.portainer_os_label), value = os)
                    HorizontalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    
                    InfoRow(label = stringResource(R.string.portainer_docker_label), value = docker)
                    HorizontalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    
                    InfoRow(label = stringResource(R.string.portainer_arch_label), value = arch)
                    HorizontalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    
                    InfoRow(label = stringResource(R.string.portainer_cpu_label), value = cpuCoresLabel)
                    HorizontalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    
                    val context = LocalContext.current
                    InfoRow(label = stringResource(R.string.portainer_ram_label), value = snapshot?.totalMemory?.takeIf { it > 0 }?.let { ResourceFormatters.formatBytes(it.toDouble(), context) } ?: stringResource(R.string.not_available))
                }
            }
        }
    }
}

// --- Formatters moved to ResourceFormatters ---

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun MiniStatCard(label: String, value: Int, color: Color) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        modifier = Modifier.height(70.dp),
        shape = RoundedCornerShape(16.dp),
        color = portainerCardColor(isDarkTheme, accent = color, tint = if (isDarkTheme) 0.10f else 0.06f),
        border = BorderStroke(1.dp, portainerBorderTone(isDarkTheme, accent = color).copy(alpha = if (isDarkTheme) 0.72f else 0.58f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "$value", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ResourceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        modifier = Modifier,
        shape = RoundedCornerShape(16.dp),
        color = portainerCardColor(isDarkTheme, accent = color, tint = if (isDarkTheme) 0.10f else 0.06f),
        border = BorderStroke(1.dp, portainerBorderTone(isDarkTheme, accent = color).copy(alpha = if (isDarkTheme) 0.72f else 0.58f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = if (isDarkTheme) 0.18f else 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContainerSummaryCard(
    total: Int,
    running: Int,
    stopped: Int,
    stacks: Int,
    onViewAll: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val shellAccent = if (isDarkTheme) ServiceType.PORTAINER.primaryColor else null
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = portainerCardColor(isDarkTheme, accent = shellAccent, tint = if (isDarkTheme) 0.09f else 0f),
        border = BorderStroke(1.dp, portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryStatColumn(
                    label = stringResource(R.string.portainer_total),
                    value = total,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f), modifier = Modifier.height(36.dp))
                SummaryStatColumn(
                    label = stringResource(R.string.portainer_running),
                    value = running,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f), modifier = Modifier.height(36.dp))
                SummaryStatColumn(
                    label = stringResource(R.string.portainer_stopped),
                    value = stopped,
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(color = portainerBorderTone(isDarkTheme, accent = shellAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f), modifier = Modifier.height(36.dp))
                SummaryStatColumn(
                    label = stringResource(R.string.portainer_stacks),
                    value = stacks,
                    color = ServiceType.PORTAINER.primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }

            M3ExpressiveButtonCard(
                text = stringResource(R.string.portainer_all_containers),
                icon = Icons.Default.ChevronRight,
                color = ServiceType.PORTAINER.primaryColor,
                modifier = Modifier.fillMaxWidth(),
                onClick = onViewAll
            )
        }
    }
}

@Composable
private fun SummaryStatColumn(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
