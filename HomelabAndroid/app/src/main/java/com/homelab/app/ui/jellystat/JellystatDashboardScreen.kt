package com.homelab.app.ui.jellystat

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.jellystat.JellystatCountDuration
import com.homelab.app.data.remote.dto.jellystat.JellystatLibraryTypeViews
import com.homelab.app.data.remote.dto.jellystat.JellystatSeriesPoint
import com.homelab.app.data.remote.dto.jellystat.JellystatWatchSummary
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.HomelabTheme
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JellystatDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: JellystatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedDays by viewModel.selectedDays.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f

    val accent = ServiceType.JELLYSTAT.primaryColor
    val watchTone = Color(0xFFA855F7)
    val viewsTone = Color(0xFFEC4899)
    val activeTone = Color(0xFF22D3EE)
    val topLibraryTone = Color(0xFF8B5CF6)
    val averageTone = Color(0xFFF59E0B)
    val pageBrush = remember(isDarkTheme) { jellyPageBackground(isDarkTheme, accent) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) accent.copy(alpha = 0.1f) else accent.copy(alpha = 0.05f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_jellystat),
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
                    IconButton(onClick = { viewModel.fetchSummary(forceLoading = false) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBrush)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pageGlow, Color.Transparent),
                            center = Offset(160f, 80f),
                            radius = 560f
                        )
                    )
            )

            when (val state = uiState) {
                is UiState.Loading, UiState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accent)
                    }
                }

                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchSummary(forceLoading = true) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchSummary(forceLoading = true) },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Success -> {
                    val summary = state.data
                    val contentKey = remember(summary.days, summary.totalViews, summary.totalHours) {
                        "${summary.days}-${summary.totalViews}-${summary.totalHours}"
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (instances.isNotEmpty()) {
                            item {
                                ServiceInstancePicker(
                                    instances = instances,
                                    selectedInstanceId = viewModel.instanceId,
                                    onInstanceSelected = { instance ->
                                        viewModel.setPreferredInstance(instance.id)
                                        onNavigateToInstance(instance.id)
                                    },
                                    label = stringResource(R.string.jellystat_instance_label)
                                )
                            }
                        }

                        item {
                            JellystatHeroCard(
                                summary = summary,
                                selectedDays = selectedDays,
                                isRefreshing = isRefreshing,
                                accent = accent,
                                isDarkTheme = isDarkTheme,
                                watchTone = watchTone,
                                viewsTone = viewsTone
                            )
                        }

                        item {
                            RangePicker(
                                selectedDays = selectedDays,
                                onSelectDays = viewModel::selectDays,
                                accent = accent
                            )
                        }

                        item {
                            StaggeredSection(index = 0, trigger = contentKey) {
                                StatsGrid(
                                    summary = summary,
                                    viewsTone = viewsTone,
                                    activeTone = activeTone,
                                    topLibraryTone = topLibraryTone,
                                    averageTone = averageTone
                                )
                            }
                        }

                        item {
                            StaggeredSection(index = 1, trigger = contentKey) {
                                MediaBreakdownCard(summary = summary, accent = accent)
                            }
                        }

                        item {
                            StaggeredSection(index = 2, trigger = contentKey) {
                                TrendCard(summary = summary, accent = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredSection(
    index: Int,
    trigger: String,
    content: @Composable () -> Unit
) {
    var visible by remember(trigger) { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        visible = false
        delay(index * 80L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
    ) {
        content()
    }
}

private fun jellyPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF090B11),
            Color(0xFF0B0F16),
            accent.copy(alpha = 0.05f),
            Color(0xFF07090E)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF9FAFF),
            Color(0xFFF3F6FF),
            accent.copy(alpha = 0.035f),
            Color(0xFFF6F8FD)
        )
    )
}

private fun jellyCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF121924) else Color(0xFFFAFBFF)

private fun jellyRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF1A2230) else Color(0xFFFFFFFF)

private fun jellyBorderTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF313A4C) else Color(0xFFD5DDF2)

private fun jellyTrackTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF263042) else Color(0xFFE5EAF5)

@Composable
private fun JellystatHeroCard(
    summary: JellystatWatchSummary,
    selectedDays: Int,
    isRefreshing: Boolean,
    accent: Color,
    isDarkTheme: Boolean,
    watchTone: Color,
    viewsTone: Color
) {
    val cardColor = jellyCardColor(isDarkTheme)
    val borderColor = accent.copy(alpha = if (isDarkTheme) 0.45f else 0.3f)
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cardColor,
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = if (isDarkTheme) {
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.22f),
                                Color(0xFF181F2D).copy(alpha = 0.86f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.12f),
                                Color(0xFFFFFFFF).copy(alpha = 0.97f)
                            )
                        )
                    }
                )
                .animateContentSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = if (isDarkTheme) 0.2f else 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.jellystat_overview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.jellystat_overview_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = accent.copy(alpha = if (isDarkTheme) 0.2f else 0.14f),
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(R.string.jellystat_range_short, selectedDays),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    label = stringResource(R.string.jellystat_watch_time),
                    value = formatHours(summary.totalHours),
                    iconTint = watchTone
                )
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow,
                    label = stringResource(R.string.jellystat_views),
                    value = formatInt(summary.totalViews),
                    iconTint = viewsTone
                )
            }

            if (isRefreshing) {
                LinearPulse(accent = accent)
            }
        }
    }
}

@Composable
private fun HeroMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val containerColor = jellyRaisedCardColor(darkTheme)
    val borderColor = jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.7f else 0.58f)
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            iconTint.copy(alpha = if (darkTheme) 0.16f else 0.1f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LinearPulse(accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {}
}

@Composable
private fun RangePicker(
    selectedDays: Int,
    onSelectDays: (Int) -> Unit,
    accent: Color
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val ranges = listOf(7, 30, 90)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = jellyCardColor(darkTheme),
        border = BorderStroke(1.1.dp, jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.72f else 0.56f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ranges.forEach { days ->
                val selected = days == selectedDays
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.03f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "jellystat_range_scale_$days"
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDays(days) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        accent.copy(alpha = if (darkTheme) 0.28f else 0.18f)
                    } else {
                        Color.Transparent
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            accent.copy(alpha = if (darkTheme) 0.76f else 0.46f)
                        } else {
                            jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.34f else 0.42f)
                        }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.jellystat_range_short, days),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) {
                                if (darkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
private fun InsightRow(
    summary: JellystatWatchSummary,
    topLibraryTone: Color,
    activeTone: Color
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 420.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InsightCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.jellystat_top_library),
                    value = summary.topLibraryName ?: stringResource(R.string.jellystat_no_activity),
                    subtitle = if (summary.topLibraryName == null) null else formatHours(summary.topLibraryHours),
                    icon = Icons.Default.LocalMovies,
                    iconTint = topLibraryTone
                )
                InsightCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.jellystat_active_days),
                    value = summary.activeDays.toString(),
                    subtitle = stringResource(R.string.jellystat_window_days, summary.days),
                    icon = Icons.Default.CalendarMonth,
                    iconTint = activeTone
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.jellystat_top_library),
                    value = summary.topLibraryName ?: stringResource(R.string.jellystat_no_activity),
                    subtitle = if (summary.topLibraryName == null) null else formatHours(summary.topLibraryHours),
                    icon = Icons.Default.LocalMovies,
                    iconTint = topLibraryTone
                )

                InsightCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.jellystat_active_days),
                    value = summary.activeDays.toString(),
                    subtitle = stringResource(R.string.jellystat_window_days, summary.days),
                    icon = Icons.Default.CalendarMonth,
                    iconTint = activeTone
                )
            }
        }
    }
}

@Composable
private fun InsightCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Surface(
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(
    summary: JellystatWatchSummary,
    viewsTone: Color,
    activeTone: Color,
    topLibraryTone: Color,
    averageTone: Color
) {
    val averageHours = if (summary.days > 0) summary.totalHours / summary.days else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.jellystat_top_library),
                value = summary.topLibraryName ?: stringResource(R.string.jellystat_no_activity),
                subtitle = if (summary.topLibraryName == null) {
                    stringResource(R.string.jellystat_no_activity)
                } else {
                    formatHours(summary.topLibraryHours)
                },
                iconTint = topLibraryTone,
                icon = Icons.Default.VideoLibrary
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.jellystat_active_days),
                value = summary.activeDays.toString(),
                subtitle = stringResource(R.string.jellystat_window_days, summary.days),
                iconTint = activeTone,
                icon = Icons.Default.CalendarMonth
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.jellystat_views),
                value = formatInt(summary.totalViews),
                subtitle = stringResource(R.string.jellystat_days_with_playback),
                iconTint = viewsTone,
                icon = Icons.Default.PlayArrow
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.jellystat_avg_per_day),
                value = formatHours(averageHours),
                subtitle = stringResource(R.string.jellystat_average_watch_time),
                iconTint = averageTone,
                icon = Icons.Default.Schedule
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val cardColor = jellyCardColor(darkTheme)
    val borderColor = jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.7f else 0.6f)
    val iconChipAlpha = if (darkTheme) 0.24f else 0.18f
    Surface(
        modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        border = BorderStroke(1.2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            iconTint.copy(alpha = if (darkTheme) 0.14f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconTint.copy(alpha = iconChipAlpha),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedContent(targetState = value, label = "metric_value") { target ->
                Text(
                    text = target,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class MediaItem(
    val label: String,
    val value: Int,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun MediaBreakdownCard(summary: JellystatWatchSummary, accent: Color) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val values = summary.viewsByType
    val items = listOf(
        MediaItem(stringResource(R.string.jellystat_songs), values.audio, Color(0xFFF43F5E), Icons.Default.MusicNote),
        MediaItem(stringResource(R.string.jellystat_movies), values.movie, Color(0xFFF59E0B), Icons.Default.LocalMovies),
        MediaItem(stringResource(R.string.jellystat_episodes), values.series, Color(0xFF3B82F6), Icons.Default.Tv),
        MediaItem(stringResource(R.string.jellystat_other), values.other, Color(0xFF8B5CF6), Icons.Default.VideoLibrary)
    )

    val total = values.totalViews.coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = jellyCardColor(darkTheme),
        border = BorderStroke(1.1.dp, jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.7f else 0.58f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.jellystat_media_type_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = formatInt(values.totalViews),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 390.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaLegend(items = items, total = total)

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = jellyRaisedCardColor(darkTheme),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            JellystatRingChart(
                                items = items,
                                total = total,
                                accent = accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp)
                                    .height(148.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.06f)) {
                            MediaLegend(items = items, total = total)
                        }

                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = jellyRaisedCardColor(darkTheme),
                            modifier = Modifier.weight(0.94f)
                        ) {
                            JellystatRingChart(
                                items = items,
                                total = total,
                                accent = accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp)
                                    .height(164.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaLegend(items: List<MediaItem>, total: Int) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            val targetRatio = if (total == 0) 0f else item.value / total.toFloat()
            val animatedRatio by animateFloatAsState(
                targetValue = targetRatio.coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessVeryLow
                ),
                label = "media_ratio_${item.label}"
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = item.color.copy(alpha = 0.14f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatInt(item.value),
                        style = MaterialTheme.typography.titleSmall,
                        color = item.color,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Surface(
                    color = jellyTrackTone(darkTheme),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                ) {
                    if (animatedRatio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRatio)
                                .height(8.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(item.color.copy(alpha = 0.76f), item.color)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellystatRingChart(
    items: List<MediaItem>,
    total: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "jellystat_ring_reveal"
    )
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val trackColor = jellyTrackTone(darkTheme)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val arcTopLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = stroke
            )

            var start = -90f
            items.forEach { item ->
                if (item.value <= 0) return@forEach
                val sweep = (item.value.toFloat() / total.toFloat()) * 360f * reveal
                drawArc(
                    color = item.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = stroke
                )
                start += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatInt(total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.jellystat_views),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(84.dp)
            )
        }
    }
}

@Composable
private fun TrendCard(summary: JellystatWatchSummary, accent: Color) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val recent = remember(summary.points) { summary.points.takeLast(8) }
    val maxHours = (recent.maxOfOrNull { it.totalDurationSeconds / 3600.0 } ?: 0.0).coerceAtLeast(1.0)
    val maxViews = recent.maxOfOrNull { it.totalViews }?.coerceAtLeast(1) ?: 1

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = jellyCardColor(darkTheme),
        border = BorderStroke(1.1.dp, jellyBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.7f else 0.58f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.jellystat_recent_trend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = recent.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.jellystat_no_data_for_period),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recent.forEach { point ->
                    val hours = point.totalDurationSeconds / 3600.0
                    val hoursRatio = if (hours <= 0.0) 0f else (hours / maxHours).toFloat()
                    val viewsRatio = if (point.totalViews <= 0) 0f else point.totalViews.toFloat() / maxViews.toFloat()
                    val signalRatio = kotlin.math.max(hoursRatio, viewsRatio)
                    val targetRatio = if (signalRatio <= 0f) 0f
                    else (0.16f + (kotlin.math.sqrt(signalRatio.toDouble()).toFloat() * 0.68f)).coerceAtMost(0.88f)
                    val animatedRatio by animateFloatAsState(
                        targetValue = targetRatio,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
                        label = "trend_${point.key}"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = jellyRaisedCardColor(darkTheme),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDateLabel(point.key),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.width(58.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = jellyTrackTone(darkTheme),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                            ) {
                                if (animatedRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedRatio)
                                            .height(8.dp)
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(accent.copy(alpha = 0.52f), accent)
                                                )
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatHours(hours),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.jellystat_views_suffix, point.totalViews),
                                    style = MaterialTheme.typography.labelMedium,
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
    }
}

private fun formatInt(value: Int): String = java.text.NumberFormat.getInstance().format(value)

private fun formatHours(value: Double): String {
    if (value in 0.000001..0.999999) {
        val minutes = kotlin.math.floor(value * 60.0).toInt()
        if (minutes <= 0) return "<1m"
        return "${minutes}m"
    }
    return if (value >= 100.0) String.format(Locale.getDefault(), "%.0fh", value)
    else String.format(Locale.getDefault(), "%.1fh", value)
}

private fun formatDateLabel(raw: String): String {
    val parsePatterns = listOf("MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "yyyy/MM/dd")
    parsePatterns.forEach { pattern ->
        val parser = SimpleDateFormat(pattern, Locale.US)
        val date = parser.parse(raw) ?: return@forEach
        val out = SimpleDateFormat("MMM d", Locale.getDefault())
        return out.format(date)
    }
    return raw
}

private fun jellystatPreviewSummary(): JellystatWatchSummary {
    val points = listOf(
        JellystatSeriesPoint(
            key = "2026-03-14",
            totalViews = 9,
            totalDurationSeconds = 3.5 * 3600,
            breakdown = mapOf(
                "Movie" to JellystatCountDuration(count = 4, durationSeconds = 2.0 * 3600),
                "Series" to JellystatCountDuration(count = 3, durationSeconds = 1.1 * 3600),
                "Audio" to JellystatCountDuration(count = 2, durationSeconds = 0.4 * 3600)
            )
        ),
        JellystatSeriesPoint(
            key = "2026-03-15",
            totalViews = 6,
            totalDurationSeconds = 2.4 * 3600,
            breakdown = mapOf(
                "Movie" to JellystatCountDuration(count = 2, durationSeconds = 1.3 * 3600),
                "Series" to JellystatCountDuration(count = 3, durationSeconds = 0.9 * 3600),
                "Audio" to JellystatCountDuration(count = 1, durationSeconds = 0.2 * 3600)
            )
        ),
        JellystatSeriesPoint(
            key = "2026-03-16",
            totalViews = 4,
            totalDurationSeconds = 1.6 * 3600,
            breakdown = mapOf(
                "Movie" to JellystatCountDuration(count = 1, durationSeconds = 0.6 * 3600),
                "Series" to JellystatCountDuration(count = 2, durationSeconds = 0.8 * 3600),
                "Audio" to JellystatCountDuration(count = 1, durationSeconds = 0.2 * 3600)
            )
        )
    )

    return JellystatWatchSummary(
        days = 30,
        totalHours = points.sumOf { it.totalDurationSeconds } / 3600.0,
        totalViews = 42,
        activeDays = 12,
        topLibraryName = "Movie",
        topLibraryHours = 14.2,
        viewsByType = JellystatLibraryTypeViews(audio = 8, movie = 20, series = 12, other = 2),
        points = points
    )
}

@Preview(name = "Jellystat Cards Light", showBackground = true, widthDp = 420)
@Preview(
    name = "Jellystat Cards Dark",
    showBackground = true,
    widthDp = 420,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun JellystatCardsPreview() {
    HomelabTheme(dynamicColor = false) {
        val accent = ServiceType.JELLYSTAT.primaryColor
        val summary = jellystatPreviewSummary()
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            JellystatHeroCard(
                summary = summary,
                selectedDays = 30,
                isRefreshing = false,
                accent = accent,
                isDarkTheme = isDarkTheme,
                watchTone = Color(0xFFA855F7),
                viewsTone = Color(0xFFEC4899)
            )
            RangePicker(
                selectedDays = 30,
                onSelectDays = {},
                accent = accent
            )
            StatsGrid(
                summary = summary,
                viewsTone = Color(0xFFEC4899),
                activeTone = Color(0xFF22D3EE),
                topLibraryTone = Color(0xFF8B5CF6),
                averageTone = Color(0xFFF59E0B)
            )
            MediaBreakdownCard(summary = summary, accent = accent)
            TrendCard(summary = summary, accent = accent)
        }
    }
}
