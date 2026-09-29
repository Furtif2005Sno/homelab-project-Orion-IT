package com.homelab.app.ui.patchmon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.patchmon.PatchmonAgentQueueResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostInfo
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostNetwork
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostStats
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostSystem
import com.homelab.app.data.remote.dto.patchmon.PatchmonIntegrationsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonNotesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonPackagesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonReportsResponse
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState

private fun patchmonDetailPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
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

private fun patchmonDetailCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF121923) else Color(0xFFFBFBF9)

private fun patchmonDetailRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF1A2330) else Color(0xFFFEFEFC)

private fun patchmonDetailBorderTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF334052) else Color(0xFFD9DED7)

private fun patchmonDetailBorderColor(isDarkTheme: Boolean): Color =
    patchmonDetailBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.56f else 0.40f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PatchmonHostDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: PatchmonHostDetailViewModel = hiltViewModel()
) {
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val infoState by viewModel.infoState.collectAsStateWithLifecycle()
    val statsState by viewModel.statsState.collectAsStateWithLifecycle()
    val systemState by viewModel.systemState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val packagesState by viewModel.packagesState.collectAsStateWithLifecycle()
    val reportsState by viewModel.reportsState.collectAsStateWithLifecycle()
    val agentQueueState by viewModel.agentQueueState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val integrationsState by viewModel.integrationsState.collectAsStateWithLifecycle()
    val dockerEnabled by viewModel.dockerEnabled.collectAsStateWithLifecycle()
    val updatesOnly by viewModel.updatesOnly.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val isDeleted by viewModel.isDeleted.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.45f

    val accent = ServiceType.PATCHMON.primaryColor
    val pageBrush = remember(isDark) { patchmonDetailPageBackground(isDark, accent) }
    val pageGlow = remember(isDark) {
        if (isDark) accent.copy(alpha = 0.09f) else accent.copy(alpha = 0.026f)
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val hostName = (infoState as? UiState.Success)?.data?.friendlyName?.ifBlank {
        (infoState as? UiState.Success)?.data?.hostname.orEmpty()
    }.orEmpty().ifBlank { stringResource(R.string.patchmon_host_detail_title) }

    val tabs = remember(selectedTab, dockerEnabled) {
        buildList {
            add(PatchmonHostDetailViewModel.DetailTab.OVERVIEW)
            add(PatchmonHostDetailViewModel.DetailTab.SYSTEM)
            add(PatchmonHostDetailViewModel.DetailTab.NETWORK)
            add(PatchmonHostDetailViewModel.DetailTab.PACKAGES)
            add(PatchmonHostDetailViewModel.DetailTab.REPORTS)
            add(PatchmonHostDetailViewModel.DetailTab.AGENT)
            if (dockerEnabled) add(PatchmonHostDetailViewModel.DetailTab.DOCKER)
            add(PatchmonHostDetailViewModel.DetailTab.NOTES)
        }
    }
    val tabListState = rememberLazyListState()

    LaunchedEffect(dockerEnabled, selectedTab) {
        if (!dockerEnabled && selectedTab == PatchmonHostDetailViewModel.DetailTab.DOCKER) {
            viewModel.selectTab(PatchmonHostDetailViewModel.DetailTab.OVERVIEW)
        }
    }

    LaunchedEffect(selectedTab, tabs) {
        val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
        tabListState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = hostName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshCurrentTab) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.patchmon_delete_host),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ServiceInstancePicker(
                    instances = instances,
                    selectedInstanceId = viewModel.instanceId,
                    onInstanceSelected = { instance -> onNavigateToInstance(instance.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    label = stringResource(R.string.patchmon_instance_label)
                )

                if (!deleteError.isNullOrBlank()) {
                    InlineErrorCard(
                        message = deleteError.orEmpty(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        onClose = viewModel::clearDeleteError
                    )
                }

                PatchmonDetailHeroCard(
                    infoState = infoState,
                    statsState = statsState,
                    accent = accent,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = patchmonDetailCardColor(isDark),
                    border = BorderStroke(1.dp, patchmonDetailBorderColor(isDark)),
                    modifier = Modifier
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                ) {
                    LazyRow(
                        state = tabListState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tabs, key = { it.name }) { tab ->
                            val selected = selectedTab == tab
                            val tabAccent = tab.accentColor()
                            FilterChip(
                                modifier = Modifier.widthIn(min = 82.dp, max = 126.dp),
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                label = {
                                    Text(
                                        text = tab.title(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tab.icon(),
                                        contentDescription = null,
                                        tint = if (selected) tabAccent else tabAccent.copy(alpha = 0.85f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tabAccent.copy(alpha = if (isDark) 0.20f else 0.16f),
                                    containerColor = patchmonDetailRaisedCardColor(isDark),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLeadingIconColor = tabAccent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = patchmonDetailBorderTone(isDark).copy(alpha = if (isDark) 0.64f else 0.56f),
                                    selectedBorderColor = tabAccent.copy(alpha = 0.52f)
                                )
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    label = "patchmon_detail_tabs",
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        if (forward) {
                            (slideInHorizontally(
                                animationSpec = tween(240),
                                initialOffsetX = { it / 6 }
                            ) + fadeIn(animationSpec = tween(180))).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(220),
                                    targetOffsetX = { -(it / 8) }
                                ) + fadeOut(animationSpec = tween(140))
                            )
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(240),
                                initialOffsetX = { -(it / 6) }
                            ) + fadeIn(animationSpec = tween(180))).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(220),
                                    targetOffsetX = { it / 8 }
                                ) + fadeOut(animationSpec = tween(140))
                            )
                        }
                    }
                ) { tab ->
                    when (tab) {
                        PatchmonHostDetailViewModel.DetailTab.OVERVIEW -> OverviewTab(
                            infoState = infoState,
                            statsState = statsState,
                            notesState = notesState,
                            integrationsState = integrationsState
                        )
                        PatchmonHostDetailViewModel.DetailTab.SYSTEM -> SystemTab(systemState)
                        PatchmonHostDetailViewModel.DetailTab.NETWORK -> NetworkTab(networkState)
                        PatchmonHostDetailViewModel.DetailTab.PACKAGES -> PackagesTab(
                            packagesState = packagesState,
                            updatesOnly = updatesOnly,
                            onToggleUpdatesOnly = viewModel::toggleUpdatesOnly
                        )
                        PatchmonHostDetailViewModel.DetailTab.REPORTS -> ReportsTab(reportsState)
                        PatchmonHostDetailViewModel.DetailTab.AGENT -> AgentTab(agentQueueState)
                        PatchmonHostDetailViewModel.DetailTab.DOCKER -> DockerTab(integrationsState)
                        PatchmonHostDetailViewModel.DetailTab.NOTES -> NotesTab(notesState)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.patchmon_delete_title)) },
            text = { Text(stringResource(R.string.patchmon_delete_message, hostName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteHost()
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PatchmonDetailHeroCard(
    infoState: UiState<PatchmonHostInfo>,
    statsState: UiState<PatchmonHostStats>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val info = (infoState as? UiState.Success)?.data
    val stats = (statsState as? UiState.Success)?.data

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = patchmonDetailCardColor(isDark),
        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDark))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceIcon(
                    type = ServiceType.PATCHMON,
                    size = 42.dp,
                    iconSize = 27.dp,
                    cornerRadius = 12.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info?.friendlyName?.ifBlank { info.hostname }
                            ?: stringResource(R.string.patchmon_host_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(info?.hostname, info?.ip)
                            .filterNotNull()
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (stats != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HeroMiniStat(
                        value = stats.securityUpdates.toString(),
                        label = stringResource(R.string.patchmon_security_updates),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMiniStat(
                        value = stats.outdatedPackages.toString(),
                        label = stringResource(R.string.patchmon_packages_outdated),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMiniStat(
                        value = stats.totalInstalledPackages.toString(),
                        label = stringResource(R.string.patchmon_packages_total),
                        color = accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (statsState is UiState.Loading || statsState is UiState.Idle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMiniStat(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = if (isDark) 0.18f else 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = if (isDark) 0.36f else 0.26f))
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 82.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InlineErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun OverviewTab(
    infoState: UiState<PatchmonHostInfo>,
    statsState: UiState<PatchmonHostStats>,
    notesState: UiState<PatchmonNotesResponse>,
    integrationsState: UiState<PatchmonIntegrationsResponse>
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StateCard(infoState) { info ->
                MetricGridCard(
                    rows = listOf(
                        Pair(stringResource(R.string.patchmon_detail_hostname), info.hostname),
                        Pair(stringResource(R.string.patchmon_detail_id), info.id),
                        Pair(stringResource(R.string.patchmon_architecture), listOf(info.osType, info.osVersion).filter { it.isNotBlank() }.joinToString(" ")),
                        Pair(stringResource(R.string.patchmon_detail_network), info.ip),
                        Pair(stringResource(R.string.patchmon_machine_id), info.machineId.orEmpty()),
                        Pair(stringResource(R.string.patchmon_agent_version), info.agentVersion.orEmpty())
                    )
                )
            }
        }

        item {
            StateCard(statsState) { stats ->
                StatsMatrix(
                    firstLabel = stringResource(R.string.patchmon_packages_total),
                    firstValue = stats.totalInstalledPackages.toString(),
                    secondLabel = stringResource(R.string.patchmon_packages_outdated),
                    secondValue = stats.outdatedPackages.toString(),
                    thirdLabel = stringResource(R.string.patchmon_security_updates),
                    thirdValue = stats.securityUpdates.toString(),
                    fourthLabel = stringResource(R.string.patchmon_repositories),
                    fourthValue = stats.totalRepos.toString()
                )
            }
        }

        item {
            StateCard(integrationsState) { integrations ->
                val docker = integrations.integrations["docker"]
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = patchmonDetailCardColor(isDarkTheme),
                    border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = stringResource(R.string.patchmon_docker_integration),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (docker?.enabled == true) {
                                stringResource(R.string.patchmon_docker_enabled)
                            } else {
                                stringResource(R.string.patchmon_docker_disabled)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            StateCard(notesState) { notes ->
                val text = notes.notes?.trim().orEmpty()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = patchmonDetailCardColor(isDarkTheme),
                    border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stringResource(R.string.patchmon_notes_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = text.ifBlank { stringResource(R.string.patchmon_notes_empty) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemTab(state: UiState<PatchmonHostSystem>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    StateScrollable(state) { system ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricGridCard(
                rows = listOf(
                    Pair(stringResource(R.string.patchmon_architecture), system.architecture.orEmpty()),
                    Pair(stringResource(R.string.patchmon_kernel_running), system.kernelVersion.orEmpty()),
                    Pair(stringResource(R.string.patchmon_kernel_installed), system.installedKernelVersion.orEmpty()),
                    Pair(stringResource(R.string.patchmon_uptime), system.systemUptime.orEmpty()),
                    Pair(stringResource(R.string.patchmon_cpu_model), system.cpuModel.orEmpty()),
                    Pair(stringResource(R.string.patchmon_cpu_cores), system.cpuCores?.toString().orEmpty()),
                    Pair(stringResource(R.string.patchmon_ram_installed), system.ramInstalled.orEmpty()),
                    Pair(stringResource(R.string.patchmon_swap_size), system.swapSize.orEmpty()),
                    Pair(
                        stringResource(R.string.patchmon_reboot_required),
                        if (system.needsReboot) (system.rebootReason ?: stringResource(R.string.yes)) else stringResource(R.string.no)
                    )
                )
            )

            if (system.diskDetails.isNotEmpty()) {
                SectionHeader(
                    icon = Icons.Default.Memory,
                    title = stringResource(R.string.patchmon_disk_usage)
                )
                system.diskDetails.forEach { disk ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = patchmonDetailCardColor(isDarkTheme),
                        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = disk.filesystem,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${disk.used} / ${disk.size} (${disk.usePercent})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (disk.mountedOn.isNotBlank()) {
                                Text(
                                    text = disk.mountedOn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkTab(state: UiState<PatchmonHostNetwork>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    StateScrollable(state) { network ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricGridCard(
                rows = listOf(
                    Pair(stringResource(R.string.patchmon_ip_address), network.ip),
                    Pair(stringResource(R.string.patchmon_gateway), network.gatewayIp.orEmpty()),
                    Pair(stringResource(R.string.patchmon_dns_servers), network.dnsServers.joinToString(", "))
                )
            )

            if (network.networkInterfaces.isNotEmpty()) {
                SectionHeader(
                    icon = Icons.Default.NetworkCheck,
                    title = stringResource(R.string.patchmon_network_interfaces)
                )
                network.networkInterfaces.forEach { iface ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = patchmonDetailCardColor(isDarkTheme),
                        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = iface.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = iface.ip.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = iface.mac.ifBlank { "—" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesTab(
    packagesState: UiState<PatchmonPackagesResponse>,
    updatesOnly: Boolean,
    onToggleUpdatesOnly: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = patchmonDetailCardColor(isDarkTheme),
                border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = updatesOnly,
                        onClick = { if (!updatesOnly) onToggleUpdatesOnly() },
                        label = {
                            Text(
                                text = stringResource(R.string.patchmon_updates_only),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.20f else 0.14f),
                            containerColor = patchmonDetailRaisedCardColor(isDarkTheme),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = updatesOnly,
                            borderColor = patchmonDetailBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.64f else 0.56f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
                        )
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = !updatesOnly,
                        onClick = { if (updatesOnly) onToggleUpdatesOnly() },
                        label = {
                            Text(
                                text = stringResource(R.string.patchmon_show_all_packages),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.20f else 0.14f),
                            containerColor = patchmonDetailRaisedCardColor(isDarkTheme),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = !updatesOnly,
                            borderColor = patchmonDetailBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.64f else 0.56f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)
                        )
                    )
                }
            }
        }

        item {
            StateCard(packagesState) { payload ->
                if (payload.packages.isEmpty()) {
                    EmptySectionCard(text = stringResource(R.string.patchmon_no_packages))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        payload.packages.forEach { pkg ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = patchmonDetailCardColor(isDarkTheme),
                                border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pkg.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (pkg.isSecurityUpdate) {
                                            AssistChip(
                                                onClick = {},
                                                enabled = false,
                                                label = { Text(stringResource(R.string.patchmon_security_chip)) },
                                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                                                    disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${pkg.currentVersion ?: "-"} → ${pkg.availableVersion ?: "-"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!pkg.description.isNullOrBlank()) {
                                        Text(
                                            text = pkg.description,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
private fun ReportsTab(state: UiState<PatchmonReportsResponse>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    StateScrollable(state) { reports ->
        if (reports.reports.isEmpty()) {
            EmptySectionCard(text = stringResource(R.string.patchmon_no_reports))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reports.reports.forEach { report ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = patchmonDetailCardColor(isDarkTheme),
                        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = report.date ?: report.id,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${pluralStringResource(
                                    R.plurals.patchmon_report_updates_count,
                                    report.outdatedPackages,
                                    report.outdatedPackages
                                )} • ${pluralStringResource(
                                    R.plurals.patchmon_report_security_count,
                                    report.securityUpdates,
                                    report.securityUpdates
                                )}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!report.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = report.errorMessage,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentTab(state: UiState<PatchmonAgentQueueResponse>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    StateScrollable(state) { queue ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsMatrix(
                firstLabel = stringResource(R.string.patchmon_queue_waiting),
                firstValue = queue.queueStatus.waiting.toString(),
                secondLabel = stringResource(R.string.patchmon_queue_active),
                secondValue = queue.queueStatus.active.toString(),
                thirdLabel = stringResource(R.string.patchmon_queue_delayed),
                thirdValue = queue.queueStatus.delayed.toString(),
                fourthLabel = stringResource(R.string.patchmon_queue_failed),
                fourthValue = queue.queueStatus.failed.toString()
            )

            if (queue.jobHistory.isEmpty()) {
                EmptySectionCard(text = stringResource(R.string.patchmon_no_jobs))
            } else {
                queue.jobHistory.forEach { job ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = patchmonDetailCardColor(isDarkTheme),
                        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = job.jobName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${job.status} • ${job.createdAt ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!job.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = job.errorMessage,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockerTab(state: UiState<PatchmonIntegrationsResponse>) {
    StateScrollable(state) { integrations ->
        val docker = integrations.integrations["docker"]
        if (docker == null) {
            EmptySectionCard(text = stringResource(R.string.patchmon_docker_missing))
        } else if (!docker.enabled) {
            EmptySectionCard(text = stringResource(R.string.patchmon_docker_disabled))
        } else {
            StatsMatrix(
                firstLabel = stringResource(R.string.patchmon_docker_containers),
                firstValue = (docker.containersCount ?: 0).toString(),
                secondLabel = stringResource(R.string.patchmon_docker_volumes),
                secondValue = (docker.volumesCount ?: 0).toString(),
                thirdLabel = stringResource(R.string.patchmon_docker_networks),
                thirdValue = (docker.networksCount ?: 0).toString(),
                fourthLabel = stringResource(R.string.patchmon_tab_docker),
                fourthValue = stringResource(R.string.patchmon_docker_enabled)
            )
        }
    }
}

@Composable
private fun NotesTab(state: UiState<PatchmonNotesResponse>) {
    StateScrollable(state) { notes ->
        val text = notes.notes?.trim().orEmpty()
        EmptySectionCard(
            text = if (text.isBlank()) stringResource(R.string.patchmon_notes_empty) else text,
            alignStart = true
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatsMatrix(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    thirdLabel: String,
    thirdValue: String,
    fourthLabel: String,
    fourthValue: String
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = patchmonDetailCardColor(isDarkTheme),
        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniStatTile(label = firstLabel, value = firstValue, modifier = Modifier.weight(1f))
                MiniStatTile(label = secondLabel, value = secondValue, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniStatTile(label = thirdLabel, value = thirdValue, modifier = Modifier.weight(1f))
                MiniStatTile(label = fourthLabel, value = fourthValue, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = patchmonDetailRaisedCardColor(isDarkTheme),
        border = BorderStroke(
            1.dp,
            patchmonDetailBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.62f else 0.52f)
        )
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 82.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricGridCard(rows: List<Pair<String, String>>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = patchmonDetailCardColor(isDarkTheme),
        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            rows.forEachIndexed { index, row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = row.first,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.second.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = patchmonDetailBorderTone(isDarkTheme).copy(alpha = if (isDarkTheme) 0.54f else 0.46f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySectionCard(
    text: String,
    alignStart: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = patchmonDetailCardColor(isDarkTheme),
        border = BorderStroke(1.dp, patchmonDetailBorderColor(isDarkTheme)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center
        )
    }
}

@Composable
private fun <T> StateScrollable(
    state: UiState<T>,
    content: @Composable (T) -> Unit
) {
    StateCard(state) { value ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                content(value)
            }
        }
    }
}

@Composable
private fun <T> StateCard(
    state: UiState<T>,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading, UiState.Idle -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        is UiState.Error -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.20f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { state.retryAction?.invoke() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        UiState.Offline -> {
            EmptySectionCard(text = stringResource(R.string.error_server_unreachable))
        }
        is UiState.Success -> content(state.data)
    }
}

@Composable
private fun PatchmonHostDetailViewModel.DetailTab.title(): String {
    return when (this) {
        PatchmonHostDetailViewModel.DetailTab.OVERVIEW -> stringResource(R.string.patchmon_tab_overview)
        PatchmonHostDetailViewModel.DetailTab.SYSTEM -> stringResource(R.string.patchmon_tab_system)
        PatchmonHostDetailViewModel.DetailTab.NETWORK -> stringResource(R.string.patchmon_tab_network)
        PatchmonHostDetailViewModel.DetailTab.PACKAGES -> stringResource(R.string.patchmon_tab_packages)
        PatchmonHostDetailViewModel.DetailTab.REPORTS -> stringResource(R.string.patchmon_tab_reports)
        PatchmonHostDetailViewModel.DetailTab.AGENT -> stringResource(R.string.patchmon_tab_agent)
        PatchmonHostDetailViewModel.DetailTab.DOCKER -> stringResource(R.string.patchmon_tab_docker)
        PatchmonHostDetailViewModel.DetailTab.NOTES -> stringResource(R.string.patchmon_tab_notes)
    }
}

private fun PatchmonHostDetailViewModel.DetailTab.icon(): ImageVector {
    return when (this) {
        PatchmonHostDetailViewModel.DetailTab.OVERVIEW -> Icons.AutoMirrored.Filled.ViewList
        PatchmonHostDetailViewModel.DetailTab.SYSTEM -> Icons.Default.Memory
        PatchmonHostDetailViewModel.DetailTab.NETWORK -> Icons.Default.NetworkCheck
        PatchmonHostDetailViewModel.DetailTab.PACKAGES -> Icons.Default.Update
        PatchmonHostDetailViewModel.DetailTab.REPORTS -> Icons.Default.SyncAlt
        PatchmonHostDetailViewModel.DetailTab.AGENT -> Icons.Default.TaskAlt
        PatchmonHostDetailViewModel.DetailTab.DOCKER -> Icons.Default.Hub
        PatchmonHostDetailViewModel.DetailTab.NOTES -> Icons.AutoMirrored.Filled.Notes
    }
}

private fun PatchmonHostDetailViewModel.DetailTab.accentColor(): Color {
    return when (this) {
        PatchmonHostDetailViewModel.DetailTab.OVERVIEW -> Color(0xFF4F8DF7)
        PatchmonHostDetailViewModel.DetailTab.SYSTEM -> Color(0xFF38BDF8)
        PatchmonHostDetailViewModel.DetailTab.NETWORK -> Color(0xFF22C55E)
        PatchmonHostDetailViewModel.DetailTab.PACKAGES -> Color(0xFFEAB308)
        PatchmonHostDetailViewModel.DetailTab.REPORTS -> Color(0xFFA78BFA)
        PatchmonHostDetailViewModel.DetailTab.AGENT -> Color(0xFF84CC16)
        PatchmonHostDetailViewModel.DetailTab.DOCKER -> Color(0xFF0EA5E9)
        PatchmonHostDetailViewModel.DetailTab.NOTES -> Color(0xFFF97316)
    }
}
