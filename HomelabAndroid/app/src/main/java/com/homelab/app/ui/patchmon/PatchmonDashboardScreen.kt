package com.homelab.app.ui.patchmon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.patchmon.PatchmonHost
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.time.Duration
import java.time.OffsetDateTime

private fun patchmonPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0B0F12),
            Color(0xFF10151B),
            accent.copy(alpha = 0.045f),
            Color(0xFF090D11)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF8F9F7),
            Color(0xFFF5F6F4),
            accent.copy(alpha = 0.018f),
            Color(0xFFF7F8F6)
        )
    )
}

private fun patchmonNeutralCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF121923) else Color(0xFFF3F4F2)

private fun patchmonNeutralRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF1A2330) else Color(0xFFF8F9F7)

private fun patchmonNeutralBorderTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF334052) else Color(0xFFD7DDD6)

private fun patchmonCardColor(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.055f else 0.025f)
    .compositeOver(patchmonNeutralCardColor(isDarkTheme))

private fun patchmonRaisedCardColor(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.08f else 0.03f)
    .compositeOver(patchmonNeutralRaisedCardColor(isDarkTheme))

private fun patchmonBorderTone(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.16f else 0.07f)
    .compositeOver(patchmonNeutralBorderTone(isDarkTheme))

private fun patchmonTrackTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF263142) else Color(0xFFE2E5E1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchmonDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToHostDetail: (String) -> Unit,
    viewModel: PatchmonViewModel = hiltViewModel()
) {
    val hostsState by viewModel.hostsState.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val groups by viewModel.availableGroups.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val patchmonColor = ServiceType.PATCHMON.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { patchmonPageBackground(isDarkTheme, patchmonColor) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) patchmonColor.copy(alpha = 0.09f) else patchmonColor.copy(alpha = 0.026f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_patchmon),
                        style = MaterialTheme.typography.headlineSmall,
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
                    IconButton(onClick = { viewModel.fetchHosts(forceLoading = false) }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (val state = hostsState) {
                is UiState.Loading -> {
                    PatchmonLoadingScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        accent = patchmonColor
                    )
                }

                is UiState.Error -> {
                    PatchmonErrorScreen(
                        message = state.message,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchHosts(forceLoading = true) }
                    )
                }

                is UiState.Success -> {
                    val hosts = state.data

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            ServiceInstancePicker(
                                instances = instances,
                                selectedInstanceId = viewModel.instanceId,
                                onInstanceSelected = { instance ->
                                    viewModel.setPreferredInstance(instance.id)
                                    onNavigateToInstance(instance.id)
                                },
                                label = stringResource(R.string.patchmon_instance_label)
                            )
                        }

                        item {
                            PatchmonOverviewCard(
                                title = stringResource(R.string.patchmon_dashboard_title),
                                summary = summary,
                                accent = patchmonColor,
                                isRefreshing = isRefreshing
                            )
                        }

                        if (groups.isNotEmpty()) {
                            item {
                                GroupFilterRow(
                                    groups = groups,
                                    selectedGroup = selectedGroup,
                                    accent = patchmonColor,
                                    onSelectGroup = viewModel::selectGroup
                                )
                            }
                        }

                        if (hosts.isEmpty()) {
                            item { EmptyHostCard() }
                        } else {
                            itemsIndexed(hosts, key = { _, host -> host.id }) { index, host ->
                                PatchmonHostCard(
                                    host = host,
                                    accent = patchmonColor,
                                    index = index,
                                    onClick = { onNavigateToHostDetail(host.id) }
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun PatchmonLoadingScreen(
    modifier: Modifier = Modifier,
    accent: Color
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(color = accent)
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PatchmonErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.PATCHMON.primaryColor
    val borderColor = patchmonBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.74f else 0.56f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = patchmonCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.error),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AssistChip(
                onClick = onRetry,
                label = {
                    Text(
                        stringResource(R.string.retry),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun PatchmonOverviewCard(
    title: String,
    summary: PatchmonViewModel.DashboardSummary,
    accent: Color,
    isRefreshing: Boolean
) {
    val offlineHosts = (summary.totalHosts - summary.activeHosts).coerceAtLeast(0)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val borderColor = patchmonBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = patchmonCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceIcon(
                    type = ServiceType.PATCHMON,
                    size = 50.dp,
                    iconSize = 30.dp,
                    cornerRadius = 14.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary.totalHosts.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                }
            }

            HorizontalDivider(color = borderColor)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryLine(
                    text = "${summary.activeHosts} ${stringResource(R.string.patchmon_hosts_active)}",
                    color = Color(0xFF4CAF50)
                )
                SummaryLine(
                    text = "$offlineHosts ${stringResource(R.string.home_status_offline)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SummaryLine(
                    text = "${summary.rebootRequired} ${stringResource(R.string.patchmon_reboot_required)}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GroupFilterRow(
    groups: List<String>,
    selectedGroup: String?,
    accent: Color,
    onSelectGroup: (String?) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val borderColor = patchmonBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.7f else 0.58f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = patchmonCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.patchmon_host_groups),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("all") {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { onSelectGroup(null) },
                        label = {
                            Text(
                                stringResource(R.string.patchmon_group_all),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.12f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedGroup == null,
                            borderColor = borderColor,
                            selectedBorderColor = accent.copy(alpha = 0.42f)
                        )
                    )
                }
                items(groups, key = { it }) { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { onSelectGroup(group) },
                        label = {
                            Text(
                                text = group,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.12f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedGroup == group,
                            borderColor = borderColor,
                            selectedBorderColor = accent.copy(alpha = 0.42f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHostCard() {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.PATCHMON.primaryColor
    val borderColor = patchmonBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = patchmonCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = patchmonRaisedCardColor(isDarkTheme, accent),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Text(
                text = stringResource(R.string.patchmon_no_hosts),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PatchmonHostCard(
    host: PatchmonHost,
    accent: Color,
    index: Int,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "patchmon_host_scale"
    )
    val animDelay = (index.coerceAtMost(8) * 14)
    val statusColor = when {
        host.securityUpdatesCount > 0 -> MaterialTheme.colorScheme.error
        host.needsReboot -> MaterialTheme.colorScheme.tertiary
        host.status.equals("active", ignoreCase = true) -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val hostCardAccent = statusColor

    AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 240, delayMillis = animDelay)
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = patchmonCardColor(isDark, hostCardAccent),
            border = BorderStroke(
                1.dp,
                patchmonBorderTone(isDark, hostCardAccent).copy(alpha = if (isDark) 0.72f else 0.58f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = host.friendlyName.ifBlank { host.hostname.ifBlank { host.id } },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hostSummary(host),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.18f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = hostStatusLabel(host.status),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = patchmonBorderTone(isDark, hostCardAccent).copy(alpha = if (isDark) 0.62f else 0.48f)
                )

                HostMetricBar(
                    icon = Icons.Default.Security,
                    iconColor = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.patchmon_security_updates),
                    valueText = host.securityUpdatesCount.toString(),
                    progress = (host.securityUpdatesCount.toFloat() / host.totalPackages.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    barColor = MaterialTheme.colorScheme.error
                )

                HostMetricBar(
                    icon = Icons.Default.Update,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.patchmon_packages_outdated),
                    valueText = host.updatesCount.toString(),
                    progress = (host.updatesCount.toFloat() / host.totalPackages.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    barColor = MaterialTheme.colorScheme.tertiary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = if (isDark) 0.16f else 0.10f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${host.totalPackages} ${stringResource(R.string.patchmon_packages_total)}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (host.needsReboot) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = if (isDark) 0.20f else 0.11f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.patchmon_reboot_badge),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.patchmon_open_details),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val lastUpdate = formatRelativeTime(host.lastUpdate)
                    if (lastUpdate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = lastUpdate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostMetricBar(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    valueText: String,
    progress: Float,
    barColor: Color
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(5.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = patchmonTrackTone(isDarkTheme),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                listOf(barColor.copy(alpha = 0.7f), barColor)
                            )
                        )
                )
            }
        }
    }
}

private fun hostSummary(host: PatchmonHost): String {
    val os = listOf(host.osType, host.osVersion).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(host.ip, os).filter { it.isNotBlank() }.joinToString(" • ")
}

@Composable
private fun hostStatusLabel(raw: String): String {
    val cleaned = raw.trim()
    return when (cleaned.lowercase()) {
        "active" -> stringResource(R.string.patchmon_hosts_active)
        else -> cleaned.ifBlank { stringResource(R.string.not_available) }
    }
}

@Composable
private fun formatRelativeTime(lastUpdate: String?): String {
    val raw = lastUpdate?.trim().orEmpty()
    if (raw.isEmpty()) return ""

    val deltaSeconds = runCatching {
        val updated = OffsetDateTime.parse(raw)
        Duration.between(updated, OffsetDateTime.now()).seconds.coerceAtLeast(0)
    }.getOrNull()

    return if (deltaSeconds == null) {
        stringResource(R.string.patchmon_updated_raw, raw)
    } else {
        when {
            deltaSeconds < 60 -> stringResource(R.string.patchmon_updated_now)
            deltaSeconds < 3600 -> stringResource(R.string.patchmon_updated_minutes_ago, deltaSeconds / 60)
            deltaSeconds < 86400 -> stringResource(R.string.patchmon_updated_hours_ago, deltaSeconds / 3600)
            else -> stringResource(R.string.patchmon_updated_days_ago, deltaSeconds / 86400)
        }
    }
}
