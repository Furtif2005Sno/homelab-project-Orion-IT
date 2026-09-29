package com.homelab.app.ui.beszel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.beszel.BeszelContainerHealth
import com.homelab.app.data.remote.dto.beszel.BeszelContainerRecord
import com.homelab.app.data.remote.dto.beszel.BeszelContainerStat
import com.homelab.app.data.remote.dto.beszel.BeszelContainerStatsRecord
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import android.content.ClipData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// MARK: - Data types

private data class StackedPoint(
    val date: Date,
    val name: String,
    val yStart: Double,
    val yEnd: Double
)

private enum class StackedChartType { CPU, MEMORY, NETWORK }

private enum class ContainerHealthFilter {
    ALL, HEALTHY, UNHEALTHY, STARTING, NONE;

    fun matches(record: BeszelContainerRecord): Boolean = when (this) {
        ALL -> true
        HEALTHY -> record.healthEnum == BeszelContainerHealth.HEALTHY
        UNHEALTHY -> record.healthEnum == BeszelContainerHealth.UNHEALTHY
        STARTING -> record.healthEnum == BeszelContainerHealth.STARTING
        NONE -> record.healthEnum == BeszelContainerHealth.NONE
    }
}

// MARK: - Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelContainersScreen(
    systemId: String,
    onNavigateBack: () -> Unit,
    viewModel: BeszelViewModel = hiltViewModel()
) {
    val containerState by viewModel.containerRecordsState.collectAsStateWithLifecycle()
    val containerStats by viewModel.containerStats.collectAsStateWithLifecycle()
    val containerStatsLoading by viewModel.containerStatsLoading.collectAsStateWithLifecycle()
    val systemDetailState by viewModel.systemDetailState.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    var healthFilter by remember { mutableStateOf(ContainerHealthFilter.ALL) }
    val chartsVisibility by viewModel.containerChartsVisibility.collectAsStateWithLifecycle()
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedChartType by remember { mutableStateOf<StackedChartType?>(null) }
    var selectedContainer by remember { mutableStateOf<BeszelContainerRecord?>(null) }

    LaunchedEffect(systemId) {
        viewModel.fetchContainers(systemId)
    }

    // Build stacked chart data
    val cpuPoints by produceState(initialValue = emptyList<StackedPoint>(), containerStats, records) {
        value = withContext(Dispatchers.Default) {
            buildStackedPoints(containerStats, records) { it.cpuValue }
        }
    }
    val memoryPoints by produceState(initialValue = emptyList<StackedPoint>(), containerStats, records) {
        value = withContext(Dispatchers.Default) {
            buildStackedPoints(containerStats, records) { it.memoryValue }
        }
    }
    val networkPoints by produceState(initialValue = emptyList<StackedPoint>(), containerStats, records) {
        value = withContext(Dispatchers.Default) {
            buildStackedPoints(containerStats, records) { it.netSent + it.netReceived }
        }
    }
    val hasNetworkData = remember(networkPoints) { networkPoints.any { it.yEnd > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.beszel_containers_screen_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.beszel_container_filter))
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            // Health filter options
                            ContainerHealthFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = healthFilterLabel(filter),
                                                fontWeight = if (healthFilter == filter) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        healthFilter = filter
                                        showFilterMenu = false
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            // Chart toggles
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = chartsVisibility.cpu,
                                            onCheckedChange = null
                                        )
                                        Text(stringResource(R.string.beszel_cpu))
                                    }
                                },
                                onClick = {
                                    viewModel.updateContainerChartsVisibility { current -> current.copy(cpu = !current.cpu) }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = chartsVisibility.memory,
                                            onCheckedChange = null
                                        )
                                        Text(stringResource(R.string.beszel_memory))
                                    }
                                },
                                onClick = {
                                    viewModel.updateContainerChartsVisibility { current -> current.copy(memory = !current.memory) }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = chartsVisibility.network,
                                            onCheckedChange = null
                                        )
                                        Text(stringResource(R.string.beszel_network))
                                    }
                                },
                                onClick = {
                                    viewModel.updateContainerChartsVisibility { current -> current.copy(network = !current.network) }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.fetchContainers(systemId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = containerState) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchContainers(systemId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchContainers(systemId) },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                val containers = state.data
                val filteredContainers by remember(containers, searchText, healthFilter) {
                    derivedStateOf {
                        containers.filter { record ->
                            val matchesSearch = searchText.isEmpty() ||
                                record.name.contains(searchText, ignoreCase = true) ||
                                (record.image?.contains(searchText, ignoreCase = true) == true)
                            matchesSearch && healthFilter.matches(record)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search bar
                    item {
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text(stringResource(R.string.beszel_container_search)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    }

                    // Stacked charts
                    if (chartsVisibility.cpu) {
                        item {
                            when {
                                containerStatsLoading && cpuPoints.isEmpty() -> {
                                    StackedChartPlaceholder(title = stringResource(R.string.beszel_docker_cpu_usage))
                                }
                                cpuPoints.isNotEmpty() -> {
                                    StackedChartCard(
                                        title = stringResource(R.string.beszel_docker_cpu_usage),
                                        points = cpuPoints,
                                        formatY = { String.format("%.0f%%", it) },
                                        onClick = { selectedChartType = StackedChartType.CPU },
                                        showPercentAxis = true,
                                        yAxisFormatter = { v -> String.format("%.0f%%", v) }
                                    )
                                }
                                else -> {
                                    StackedChartEmpty(title = stringResource(R.string.beszel_docker_cpu_usage))
                                }
                            }
                        }
                    }
                    if (chartsVisibility.memory) {
                        item {
                            when {
                                containerStatsLoading && memoryPoints.isEmpty() -> {
                                    StackedChartPlaceholder(title = stringResource(R.string.beszel_docker_memory_usage))
                                }
                                memoryPoints.isNotEmpty() -> {
                                    StackedChartCard(
                                        title = stringResource(R.string.beszel_docker_memory_usage),
                                        points = memoryPoints,
                                        formatY = { formatMB(it) },
                                        onClick = { selectedChartType = StackedChartType.MEMORY },
                                        axisFormatter = { v -> formatMB(v) }
                                    )
                                }
                                else -> {
                                    StackedChartEmpty(title = stringResource(R.string.beszel_docker_memory_usage))
                                }
                            }
                        }
                    }
                    if (chartsVisibility.network) {
                        item {
                            when {
                                containerStatsLoading && networkPoints.isEmpty() -> {
                                    StackedChartPlaceholder(title = stringResource(R.string.beszel_docker_network_io))
                                }
                                hasNetworkData -> {
                                    StackedChartCard(
                                        title = stringResource(R.string.beszel_docker_network_io),
                                        points = networkPoints,
                                        formatY = { formatNetRateBytesPerSec(it) },
                                        onClick = { selectedChartType = StackedChartType.NETWORK },
                                        axisFormatter = { v -> formatNetRateBytesPerSec(v) }
                                    )
                                }
                                else -> {
                                    StackedChartEmpty(title = stringResource(R.string.beszel_docker_network_io))
                                }
                            }
                        }
                    }

                    // Container list
                    if (filteredContainers.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.beszel_no_containers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column {
                                    filteredContainers.forEachIndexed { index, container ->
                                        ContainerRow(
                                            container = container,
                                            onClick = { selectedContainer = container }
                                        )
                                        if (index < filteredContainers.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 56.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
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

    // Chart detail bottom sheet
    selectedChartType?.let { chartType ->
        val points = when (chartType) {
            StackedChartType.CPU -> cpuPoints
            StackedChartType.MEMORY -> memoryPoints
            StackedChartType.NETWORK -> networkPoints
        }
        val title = when (chartType) {
            StackedChartType.CPU -> stringResource(R.string.beszel_docker_cpu_usage)
            StackedChartType.MEMORY -> stringResource(R.string.beszel_docker_memory_usage)
            StackedChartType.NETWORK -> stringResource(R.string.beszel_docker_network_io)
        }
        val formatter: (Double) -> String = when (chartType) {
            StackedChartType.CPU -> { v -> String.format("%.1f%%", v) }
            StackedChartType.MEMORY -> { v -> formatMB(v) }
            StackedChartType.NETWORK -> { v -> formatNetRateBytesPerSec(v) }
        }
        StackedChartDetailSheet(
            title = title,
            points = points,
            formatValue = formatter,
            onDismiss = { selectedChartType = null }
        )
    }

    // Container detail bottom sheet
    selectedContainer?.let { container ->
        ContainerDetailSheet(
            container = container,
            systemId = systemId,
            containerStats = containerStats,
            systemRecords = records,
            viewModel = viewModel,
            onDismiss = {
                selectedContainer = null
                viewModel.resetContainerDetail()
            }
        )
    }
}

// MARK: - Container Row

@Composable
private fun ContainerRow(
    container: BeszelContainerRecord,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ServiceType.BESZEL.primaryColor.copy(alpha = 0.08f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = ServiceType.BESZEL.primaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                container.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContainerStat(icon = Icons.Default.Memory, value = String.format("%.1f%%", container.cpuValue))
                ContainerStat(icon = Icons.Default.Dns, value = formatMB(container.memoryValue))
                if (container.netValue > 0) {
                    ContainerStat(icon = Icons.Default.NetworkCheck, value = formatNetRateBytesPerSec(container.netValue))
                }
            }
            container.image?.takeIf { it.isNotEmpty() }?.let { image ->
                Text(
                    image,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val health = container.healthEnum
            if (health != BeszelContainerHealth.NONE) {
                Text(
                    text = healthLabel(health),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = healthColor(health),
                    modifier = Modifier
                        .background(healthColor(health).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            container.status?.takeIf { it.isNotEmpty() }?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

// MARK: - Health helpers

@Composable
private fun healthFilterLabel(filter: ContainerHealthFilter): String = when (filter) {
    ContainerHealthFilter.ALL -> stringResource(R.string.all)
    ContainerHealthFilter.HEALTHY -> stringResource(R.string.beszel_health_healthy)
    ContainerHealthFilter.UNHEALTHY -> stringResource(R.string.beszel_health_unhealthy)
    ContainerHealthFilter.STARTING -> stringResource(R.string.beszel_health_starting)
    ContainerHealthFilter.NONE -> stringResource(R.string.beszel_health_none)
}

@Composable
private fun healthLabel(health: BeszelContainerHealth): String = when (health) {
    BeszelContainerHealth.NONE -> stringResource(R.string.beszel_health_none)
    BeszelContainerHealth.STARTING -> stringResource(R.string.beszel_health_starting)
    BeszelContainerHealth.HEALTHY -> stringResource(R.string.beszel_health_healthy)
    BeszelContainerHealth.UNHEALTHY -> stringResource(R.string.beszel_health_unhealthy)
}

@Composable
private fun healthColor(health: BeszelContainerHealth): Color = when (health) {
    BeszelContainerHealth.NONE -> Color.Gray
    BeszelContainerHealth.STARTING -> StatusOrange
    BeszelContainerHealth.HEALTHY -> StatusGreen
    BeszelContainerHealth.UNHEALTHY -> StatusRed
}

// MARK: - Stacked Chart Card

@Composable
private fun StackedChartCard(
    title: String,
    points: List<StackedPoint>,
    formatY: (Double) -> String,
    onClick: () -> Unit,
    showPercentAxis: Boolean = false,
    yAxisFormatter: ((Double) -> String)? = null,
    axisTicks: List<Double>? = null,
    axisFormatter: ((Double) -> String)? = null
) {
    val names = remember(points) { points.map { it.name }.distinct() }
    val dates = remember(points) { points.map { it.date }.distinct() }
    val maxY = remember(points) { points.maxOfOrNull { it.yEnd }?.coerceAtLeast(1.0) ?: 1.0 }
    val percentAxis = showPercentAxis
    val axisMax = if (percentAxis) maxY.coerceAtLeast(1.0) else (axisTicks?.maxOrNull() ?: maxY)
    val chartHeight = 160.dp
    val axisMaxPadded = axisMax * 1.05
    val percentAxisTicks = if (percentAxis) axisTicksForHeight(axisMaxPadded, chartHeight, 44.dp, maxTicks = 2) else emptyList()
    val customAxisTicks = if (!percentAxis) {
        axisTicks?.filter { it >= 0.0 }?.distinct()?.sortedDescending()
            ?: axisFormatter?.let { axisTicksForHeight(axisMaxPadded, chartHeight, 44.dp, maxTicks = 2) }
            ?: emptyList()
    } else {
        emptyList()
    }
    val showAxis = percentAxis || customAxisTicks.isNotEmpty()
    val usedAxisTicks = if (percentAxis) percentAxisTicks else customAxisTicks
    val usedAxisMax = if (percentAxis) axisMaxPadded else (customAxisTicks.firstOrNull() ?: axisMaxPadded)
    val percentLabelFormatter: (Double) -> String = { v ->
        when {
            axisMaxPadded < 1.0 -> String.format("%.2f%%", v)
            axisMaxPadded < 10.0 -> String.format("%.1f%%", v)
            else -> String.format("%.0f%%", v)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Text(
                text = "${names.size} containers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            val axisWidth = when {
                !showAxis -> 0.dp
                percentAxis -> 32.dp
                else -> 52.dp
            }
            val axisSpacer = if (showAxis) 4.dp else 0.dp

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAxis) {
                    Column(
                        modifier = Modifier
                            .width(axisWidth)
                            .height(chartHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        usedAxisTicks.forEach { value ->
                            Text(
                                text = when {
                                    percentAxis -> yAxisFormatter?.invoke(value) ?: percentLabelFormatter(value)
                                    axisFormatter != null -> axisFormatter(value)
                                    else -> formatY(value)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                ,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                    Spacer(Modifier.width(axisSpacer))
                }

                StackedAreaChart(
                    points = points,
                    modifier = Modifier.weight(1f).height(chartHeight),
                    maxValueOverride = if (showAxis) usedAxisMax else null,
                    gridlines = if (showAxis) usedAxisTicks else null
                )
            }

            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val startTime = dates.firstOrNull()?.let { timeFormatter.format(it) }
            val endTime = dates.lastOrNull()?.let { timeFormatter.format(it) }
            val midTime = dates.getOrNull(dates.size / 2)?.let { timeFormatter.format(it) }
            val showMid = midTime != null && midTime != startTime && midTime != endTime
            if (startTime != null && endTime != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = axisWidth + axisSpacer),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        startTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showMid) {
                        Text(
                            midTime ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Text(
                        endTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StackedChartPlaceholder(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun StackedChartEmpty(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.not_available),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

// MARK: - Stacked Area Chart (Canvas)

@Composable
private fun StackedAreaChart(
    points: List<StackedPoint>,
    modifier: Modifier = Modifier,
    scrubEnabled: Boolean = false,
    onScrub: ((Date?) -> Unit)? = null,
    selectedDate: Date? = null,
    maxValueOverride: Double? = null,
    gridlines: List<Double>? = null
) {
    if (points.isEmpty()) return

    val names = remember(points) { points.map { it.name }.distinct().sorted() }
    val colors = remember(names) { generateStackedColors(names.size) }
    val dates = remember(points) { points.map { it.date }.distinct().sorted() }
    val maxY = remember(points, maxValueOverride) {
        maxValueOverride ?: points.maxOfOrNull { it.yEnd }?.coerceAtLeast(1.0) ?: 1.0
    }
    val dateIndexMap = remember(dates) { dates.withIndex().associate { (i, d) -> d to i } }
    val groupedPoints = remember(points, names) {
        names.map { name -> points.filter { it.name == name }.sortedBy { it.date } }
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    val haptic = LocalHapticFeedback.current
    var dragX by remember(points) { mutableStateOf(0f) }
    var graphWidth by remember(points) { mutableStateOf(1f) }

    Canvas(
        modifier = modifier
            .onSizeChanged { graphWidth = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (scrubEnabled && onScrub != null) {
                    Modifier
                        .pointerInput(points) {
                            detectTapGestures(
                                onTap = { offset ->
                                    dragX = offset.x
                                    val idx = ((offset.x / size.width.toFloat().coerceAtLeast(1f)) * (dates.size - 1))
                                        .roundToInt().coerceIn(0, dates.size - 1)
                                    onScrub(dates[idx])
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val nextX = (dragX + delta).coerceIn(0f, graphWidth)
                                dragX = nextX
                                val idx = ((nextX / graphWidth) * (dates.size - 1))
                                    .roundToInt().coerceIn(0, dates.size - 1)
                                val newDate = dates[idx]
                                if (newDate != selectedDate) {
                                    onScrub(newDate)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragStarted = { offset ->
                                dragX = offset.x.coerceIn(0f, graphWidth)
                                val idx = ((dragX / graphWidth) * (dates.size - 1))
                                    .roundToInt().coerceIn(0, dates.size - 1)
                                onScrub(dates[idx])
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragStopped = { onScrub(null) }
                        )
                } else Modifier
            )
    ) {
        val w = size.width
        val h = size.height

        gridlines?.forEach { value ->
            val y = h - ((value / maxY).coerceIn(0.0, 1.0) * h).toFloat()
            drawLine(
                if (value == 0.0) gridColor.copy(alpha = 0.7f) else gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (value == 0.0) 1.6.dp.toPx() else 1.dp.toPx()
            )
        }

        // Draw stacked areas using pre-computed groups
        val datesCount = dates.size
        groupedPoints.forEachIndexed { nameIdx, namePoints ->
            if (namePoints.size < 2) return@forEachIndexed
            val color = colors.getOrElse(nameIdx) { Color.Gray }

            val fillPath = Path()
            val linePath = Path()

            namePoints.forEachIndexed { i, pt ->
                val dateIdx = dateIndexMap[pt.date] ?: return@forEachIndexed
                val x = if (datesCount > 1) dateIdx.toFloat() / (datesCount - 1) * w else w / 2
                val yTop = h - (pt.yEnd / maxY * h).toFloat()
                val yBottom = h - (pt.yStart / maxY * h).toFloat()

                if (i == 0) {
                    fillPath.moveTo(x, yBottom)
                    fillPath.lineTo(x, yTop)
                    linePath.moveTo(x, yTop)
                } else {
                    fillPath.lineTo(x, yTop)
                    linePath.lineTo(x, yTop)
                }
            }
            // Close fill
            for (i in namePoints.indices.reversed()) {
                val dateIdx = dateIndexMap[namePoints[i].date] ?: continue
                val x = if (datesCount > 1) dateIdx.toFloat() / (datesCount - 1) * w else w / 2
                val yBottom = h - (namePoints[i].yStart / maxY * h).toFloat()
                fillPath.lineTo(x, yBottom)
            }
            fillPath.close()

            drawPath(fillPath, color.copy(alpha = 0.5f))
            drawPath(linePath, color, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Scrub line
        if (scrubEnabled && selectedDate != null) {
            val dateIdx = dateIndexMap[selectedDate]
            if (dateIdx != null) {
                val x = if (dates.size > 1) dateIdx.toFloat() / (dates.size - 1) * w else w / 2
                drawLine(
                    Color.White.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

private fun generateStackedColors(count: Int): List<Color> {
    if (count == 0) return emptyList()
    if (count == 1) return listOf(Color.hsl(216f, 0.8f, 0.6f))
    return (0 until count).map { i ->
        val hue = 288f * (1f - i.toFloat() / (count - 1).coerceAtLeast(1))
        Color.hsl(hue, 0.7f, 0.65f)
    }
}

// MARK: - Stacked Chart Detail Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StackedChartDetailSheet(
    title: String,
    points: List<StackedPoint>,
    formatValue: (Double) -> String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val names = remember(points) { points.map { it.name }.distinct().sorted() }
    val colors = remember(names) { generateStackedColors(names.size) }
    val dates = remember(points) { points.map { it.date }.distinct().sorted() }
    var selectedDate by remember { mutableStateOf<Date?>(null) }

    val displayDate = selectedDate ?: dates.lastOrNull()
    val dateIndex = remember(points) {
        val idx = mutableMapOf<Date, MutableList<StackedPoint>>()
        points.forEach { p -> idx.getOrPut(p.date) { mutableListOf() }.add(p) }
        idx
    }

    val valuesAtDate = remember(displayDate, dateIndex) {
        val atDate = displayDate?.let { dateIndex[it] }.orEmpty()
        atDate.associate { it.name to (it.yEnd - it.yStart) }
    }
    val total = remember(valuesAtDate) { valuesAtDate.values.sum() }
    val sortedNames = remember(valuesAtDate, names) { names.sortedByDescending { valuesAtDate[it] ?: 0.0 } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                StackedAreaChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(12.dp),
                    scrubEnabled = true,
                    onScrub = { selectedDate = it },
                    selectedDate = selectedDate
                )
            }

            // Values card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    displayDate?.let { d ->
                        val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                        Text(
                            fmt.format(d),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.beszel_total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatValue(total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    sortedNames.forEachIndexed { index, name ->
                        val colorIdx = names.indexOf(name)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(10.dp).background(
                                    colors.getOrElse(colorIdx) { Color.Gray },
                                    CircleShape
                                )
                            )
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatValue(valuesAtDate[name] ?: 0.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < sortedNames.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Container Detail Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerDetailSheet(
    container: BeszelContainerRecord,
    systemId: String,
    containerStats: List<BeszelContainerStatsRecord>,
    systemRecords: List<BeszelSystemRecord>,
    viewModel: BeszelViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logsState by viewModel.containerLogs.collectAsStateWithLifecycle()
    val infoState by viewModel.containerInfo.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) } // 0=Info, 1=Logs, 2=Details

    val chartSeries by produceState<ContainerSeries?>(initialValue = null, container, containerStats, systemRecords) {
        value = withContext(Dispatchers.Default) {
            buildContainerSeries(container.name, containerStats, systemRecords)
        }
    }

    LaunchedEffect(selectedTab, container.id) {
        if (selectedTab == 1 && logsState is UiState.Idle) {
            viewModel.fetchContainerLogs(systemId, container.id, container.name)
        } else if (selectedTab == 2 && infoState is UiState.Idle) {
            viewModel.fetchContainerInfo(systemId, container.id, container.name)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(container.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            // Tab row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.beszel_container_info),
                    stringResource(R.string.beszel_container_logs),
                    stringResource(R.string.beszel_container_details)
                ).forEachIndexed { index, label ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedTab == index) ServiceType.BESZEL.primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.clickable { selectedTab = index }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == index) ServiceType.BESZEL.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Info tab
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoRow(stringResource(R.string.beszel_cpu), String.format("%.1f%%", container.cpuValue))
                            InfoRow(stringResource(R.string.beszel_memory), formatMB(container.memoryValue))
                            if (container.netValue > 0) {
                                InfoRow(stringResource(R.string.beszel_network), formatNetRateBytesPerSec(container.netValue))
                            }
                            container.image?.takeIf { it.isNotEmpty() }?.let {
                                InfoRow(stringResource(R.string.beszel_container_image), it)
                            }
                            container.status?.takeIf { it.isNotEmpty() }?.let {
                                InfoRow(stringResource(R.string.beszel_container_status), it)
                            }
                            val health = container.healthEnum
                            if (health != BeszelContainerHealth.NONE) {
                                InfoRow(stringResource(R.string.beszel_container_health_label), healthLabel(health))
                            }
                        }
                    }

                    chartSeries?.let { series ->
                        if (series.hasData()) {
                            ContainerSeriesCard(
                                title = stringResource(R.string.beszel_cpu),
                                data = series.cpu,
                                accent = ServiceType.BESZEL.primaryColor,
                                valueFormatter = { v -> String.format("%.1f%%", v) },
                                showPercentAxis = true
                            )
                            ContainerSeriesCard(
                                title = stringResource(R.string.beszel_memory),
                                data = series.memory,
                                accent = StatusPurple,
                                valueFormatter = { v -> formatMB(v) },
                                axisFormatter = { v -> formatMB(v) }
                            )
                            if (series.network.any { it > 0.0 }) {
                                ContainerSeriesCard(
                                    title = stringResource(R.string.beszel_network),
                                    data = series.network,
                                    accent = StatusGreen,
                                    valueFormatter = { v -> formatNetRateBytesPerSec(v) }
                                )
                            }
                        } else {
                            Text(stringResource(R.string.not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                1 -> {
                    // Logs tab
                    when (val state = logsState) {
                        is UiState.Loading -> CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor, modifier = Modifier.padding(16.dp))
                        is UiState.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.fetchContainerLogs(systemId, container.id, container.name) }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        is UiState.Success -> {
                            val logs = state.data
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.fetchContainerLogs(systemId, container.id, container.name) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.refresh))
                                }
                                TextButton(
                                    onClick = {
                                        clipboardScope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(
                                                    ClipData.newPlainText(
                                                        context.getString(R.string.copy),
                                                        logs
                                                    )
                                                )
                                            )
                                        }
                                    },
                                    enabled = logs.isNotBlank()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy))
                                }
                            }
                            if (logs.isBlank()) {
                                Text(stringResource(R.string.beszel_no_logs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val lines = remember(logs) {
                                    logs.replace("\r\n", "\n").split("\n")
                                }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    SelectionContainer {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 160.dp, max = 420.dp)
                                                .padding(12.dp)
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                lines.forEachIndexed { index, line ->
                                                    LogLine(
                                                        index = index,
                                                        line = line,
                                                        numberColor = ServiceType.BESZEL.primaryColor.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                2 -> {
                    // Details (JSON) tab
                    when (val state = infoState) {
                        is UiState.Loading -> CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor, modifier = Modifier.padding(16.dp))
                        is UiState.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.fetchContainerInfo(systemId, container.id, container.name) }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        is UiState.Success -> {
                            val info = state.data
                            if (info.isBlank()) {
                                Text(stringResource(R.string.not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val detailItems = remember(info) { parseDetailItems(info) }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            clipboardScope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            context.getString(R.string.copy),
                                                            info
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.copy))
                                    }
                                }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        detailItems.forEach { item ->
                                            when (item) {
                                                is DetailItem.Row -> DetailRow(item.key, item.value)
                                                is DetailItem.Block -> DetailBlock(item.title, item.body)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class ContainerSeries(
    val cpu: List<Double>,
    val memory: List<Double>,
    val network: List<Double>
) {
    fun hasData(): Boolean = cpu.size > 1 || memory.size > 1 || network.size > 1
}

private sealed class DetailItem {
    data class Row(val key: String, val value: String) : DetailItem()
    data class Block(val title: String, val body: String) : DetailItem()
}

@Composable
private fun DetailRow(label: String, value: String) {
    val valueColor = if (isNumericValue(value)) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ServiceType.BESZEL.primaryColor,
            modifier = Modifier.widthIn(min = 88.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = ServiceType.BESZEL.primaryColor
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLine(index: Int, line: String, numberColor: Color) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val upper = line.uppercase(Locale.ROOT)
    val highlightColor = when {
        upper.contains(" ERROR") || upper.contains("ERROR ") || upper.contains("FATAL") || upper.contains("CRITICAL") -> StatusRed
        upper.contains(" WARN") || upper.contains("WARNING") -> StatusOrange
        upper.contains(" INFO") -> StatusGreen
        upper.contains(" DEBUG") || upper.contains(" TRACE") -> StatusPurple
        upper.contains("HTTP 5") || upper.contains("HTTP/5") || upper.contains("STATUS 5") -> StatusRed
        else -> null
    }
    val linkColor = ServiceType.BESZEL.primaryColor
    val urlRegex = remember { Regex("https?://[^\\s\\]\\)]+") }
    val lineAnnotated = remember(line, highlightColor, linkColor) {
        buildAnnotatedString {
            append(line)
            urlRegex.findAll(line).forEach { match ->
                addStyle(
                    SpanStyle(color = linkColor),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText(
                                    context.getString(R.string.copy),
                                    line
                                )
                            )
                        )
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%4d", index + 1),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = numberColor,
            softWrap = false
        )
        Text(
            text = lineAnnotated,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

private fun isNumericValue(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    val numeric = Regex("""^[-+]?\d+(\.\d+)?\s*(%|[kKmMgGtTpP]i?B|[kKmMgGtTpP]i?B/s)?$""")
    return numeric.matches(trimmed)
}

private fun parseDetailItems(raw: String): List<DetailItem> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return listOf(DetailItem.Block("", trimmed))
    }
    return try {
        val json = org.json.JSONObject(trimmed)
        val keys = json.keys().asSequence().toList().sorted()
        buildList {
            for (key in keys) {
                val value = json.opt(key)
                when (value) {
                    is org.json.JSONObject -> {
                        add(DetailItem.Block(key, value.toString(2)))
                    }
                    is org.json.JSONArray -> {
                        add(DetailItem.Block(key, value.toString(2)))
                    }
                    is String -> {
                        val normalized = value.trim()
                        val nested = parseNestedJson(normalized)
                        if (nested != null) {
                            add(DetailItem.Block(key, nested))
                        } else {
                            add(DetailItem.Row(key, value))
                        }
                    }
                    else -> {
                        add(DetailItem.Row(key, value?.toString() ?: ""))
                    }
                }
            }
        }
    } catch (_: Exception) {
        listOf(DetailItem.Block("", trimmed))
    }
}

private fun parseNestedJson(value: String): String? {
    if (!(value.startsWith("{") && value.endsWith("}")) && !(value.startsWith("[") && value.endsWith("]"))) {
        return null
    }
    return try {
        org.json.JSONObject(value).toString(2)
    } catch (_: Exception) {
        try {
            org.json.JSONArray(value).toString(2)
        } catch (_: Exception) {
            null
        }
    }
}

private fun buildContainerSeries(
    containerName: String,
    containerStats: List<BeszelContainerStatsRecord>,
    systemRecords: List<BeszelSystemRecord>
): ContainerSeries {
    val cpu = mutableListOf<Double>()
    val memory = mutableListOf<Double>()
    val network = mutableListOf<Double>()

    if (containerStats.isNotEmpty()) {
        val sorted = containerStats.sortedBy { it.created ?: "" }
        for (record in sorted) {
            val stat = record.stats.firstOrNull { it.name == containerName } ?: continue
            cpu.add(maxOf(0.0, stat.cpuValue))
            memory.add(maxOf(0.0, stat.memoryValue))
            network.add(maxOf(0.0, stat.netSent + stat.netReceived))
        }
    } else {
        val sorted = systemRecords.sortedBy { it.created ?: "" }
        for (record in sorted) {
            val container = record.stats.dc?.firstOrNull { it.name == containerName } ?: continue
            cpu.add(maxOf(0.0, container.cpuValue))
            memory.add(maxOf(0.0, container.mValue))
            val netValue = (container.bandwidthUpBytesPerSec ?: 0.0) + (container.bandwidthDownBytesPerSec ?: 0.0)
            network.add(maxOf(0.0, netValue))
        }
    }

    return ContainerSeries(cpu = cpu, memory = memory, network = network)
}

@Composable
private fun ContainerSeriesCard(
    title: String,
    data: List<Double>,
    accent: Color,
    valueFormatter: (Double) -> String,
    showPercentAxis: Boolean = false,
    axisTicks: List<Double>? = null,
    axisFormatter: ((Double) -> String)? = null
) {
    if (data.size < 2) return
    val latest = data.lastOrNull() ?: 0.0
    val maxY = data.maxOrNull() ?: 0.0
    val percentAxis = showPercentAxis
    val axisMax = if (percentAxis) maxY.coerceAtLeast(1.0) else maxY
    val chartHeight = 72.dp
    val axisMaxPaddedLine = axisMax * 1.05
    val percentAxisTicks = if (percentAxis) axisTicksForHeight(axisMaxPaddedLine, chartHeight, 32.dp, maxTicks = 2) else emptyList()
    val customAxisTicks = if (!percentAxis) {
        axisTicks?.filter { it >= 0.0 }?.distinct()?.sortedDescending()
            ?: axisFormatter?.let { axisTicksForHeight(axisMaxPaddedLine, chartHeight, 32.dp, maxTicks = 2) }
            ?: emptyList()
    } else {
        emptyList()
    }
    val showAxis = percentAxis || (customAxisTicks.isNotEmpty())
    val usedAxisTicks = if (percentAxis) percentAxisTicks else customAxisTicks
    val usedAxisMax = if (percentAxis) axisMaxPaddedLine else (customAxisTicks.firstOrNull() ?: axisMaxPaddedLine)
    val percentLabelFormatter: (Double) -> String = { v ->
        when {
            axisMaxPaddedLine < 1.0 -> String.format("%.2f%%", v)
            axisMaxPaddedLine < 10.0 -> String.format("%.1f%%", v)
            else -> String.format("%.0f%%", v)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    valueFormatter(latest),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            val axisWidth = when {
                !showAxis -> 0.dp
                percentAxis -> 32.dp
                else -> 52.dp
            }
            val axisSpacer = if (showAxis) 4.dp else 0.dp

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAxis) {
                    Column(
                        modifier = Modifier
                            .width(axisWidth)
                            .height(chartHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        usedAxisTicks.forEach { value ->
                            Text(
                                text = when {
                                    percentAxis -> percentLabelFormatter(value)
                                    axisFormatter != null -> axisFormatter(value)
                                    else -> valueFormatter(value)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                ,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                    Spacer(Modifier.width(axisSpacer))
                }

                SmoothLineGraph(
                    data = data,
                    graphColor = accent,
                    gridlines = if (showAxis) usedAxisTicks else null,
                    minValueOverride = if (showAxis) 0.0 else null,
                    maxValueOverride = if (showAxis) usedAxisMax else null,
                    heightDp = chartHeight
                )
            }
        }
    }
}

// MARK: - Stacked Points Builder

private fun axisTicksForHeight(
    max: Double,
    chartHeight: androidx.compose.ui.unit.Dp,
    minGap: androidx.compose.ui.unit.Dp,
    maxTicks: Int
): List<Double> {
    if (max <= 0.0) return emptyList()
    val available = (chartHeight / minGap).toInt() + 1
    val count = available.coerceIn(2, maxTicks)
    val step = max / (count - 1)
    return (count - 1 downTo 0).map { it * step }
}

private val dateFormats = listOf(
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
)

private fun parseDate(value: String?): Date? {
    if (value == null) return null
    for (fmt in dateFormats) {
        try { return fmt.parse(value) } catch (_: Exception) {}
    }
    return null
}

private fun buildStackedPoints(
    containerStats: List<BeszelContainerStatsRecord>,
    systemRecords: List<com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord>,
    selector: (BeszelContainerStat) -> Double
): List<StackedPoint> {
    // Prefer container_stats if available, fallback to system records
    if (containerStats.isNotEmpty()) {
        return buildFromContainerStats(containerStats, selector)
    }
    return buildFromSystemRecords(systemRecords, selector)
}

private fun buildFromContainerStats(
    stats: List<BeszelContainerStatsRecord>,
    selector: (BeszelContainerStat) -> Double
): List<StackedPoint> {
    val sorted = stats.sortedBy { it.created ?: "" }
    val names = mutableSetOf<String>()
    val series = mutableListOf<Pair<Date, Map<String, Double>>>()

    for (record in sorted) {
        val date = parseDate(record.created) ?: continue
        val values = mutableMapOf<String, Double>()
        for (stat in record.stats) {
            values[stat.name] = maxOf(0.0, selector(stat))
            names.add(stat.name)
        }
        series.add(date to values)
    }

    return stack(series, names.sorted().toList())
}

private fun buildFromSystemRecords(
    records: List<com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord>,
    selector: (BeszelContainerStat) -> Double
): List<StackedPoint> {
    val sorted = records.sortedBy { it.created ?: "" }
    val names = mutableSetOf<String>()
    val series = mutableListOf<Pair<Date, Map<String, Double>>>()

    for (record in sorted) {
        val date = parseDate(record.created) ?: continue
        val containers = record.stats.dc ?: continue
        val values = mutableMapOf<String, Double>()
        for (container in containers) {
            val stat = BeszelContainerStat(
                n = container.name,
                c = container.cpuValue,
                m = container.mValue,
                ns = container.bandwidthUpBytesPerSec?.let { it / (1024.0 * 1024.0) },
                nr = container.bandwidthDownBytesPerSec?.let { it / (1024.0 * 1024.0) }
            )
            values[container.name] = maxOf(0.0, selector(stat))
            names.add(container.name)
        }
        series.add(date to values)
    }

    return stack(series, names.sorted().toList())
}

private fun stack(
    series: List<Pair<Date, Map<String, Double>>>,
    names: List<String>
): List<StackedPoint> {
    val lastKnown = mutableMapOf<String, Double>()
    val points = ArrayList<StackedPoint>(series.size * names.size.coerceAtLeast(1))

    for ((date, values) in series) {
        for ((name, value) in values) {
            lastKnown[name] = value
        }
        var running = 0.0
        for (name in names) {
            val value = lastKnown[name] ?: 0.0
            val start = running
            running += value
            points.add(StackedPoint(date = date, name = name, yStart = start, yEnd = running))
        }
    }
    return points
}
