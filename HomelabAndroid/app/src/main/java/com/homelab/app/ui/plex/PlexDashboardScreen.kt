package com.homelab.app.ui.plex

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.plex.PlexDashboardData
import com.homelab.app.data.remote.dto.plex.PlexLibrary
import com.homelab.app.data.remote.dto.plex.PlexRecentItem
import com.homelab.app.data.remote.dto.plex.PlexSession
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlexDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: PlexViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f

    val accent = ServiceType.PLEX.primaryColor
    val moviesTone = Color(0xFFF59E0B)
    val showsTone = Color(0xFF8B5CF6)
    val musicTone = Color(0xFFEC4899)
    val sessionsTone = Color(0xFF22D3EE)

    val pageBrush = remember(isDarkTheme) { plexPageBackground(isDarkTheme, accent) }
    val pageGlow = remember(isDarkTheme) { if (isDarkTheme) accent.copy(alpha = 0.12f) else accent.copy(alpha = 0.06f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_plex),
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
                    IconButton(onClick = { viewModel.fetchDashboard(forceLoading = false) }) {
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
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard(forceLoading = true) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Success -> {
                    val data = state.data
                    val contentKey = remember(data.serverInfo.version, data.stats.totalItems) {
                        "${data.serverInfo.version}-${data.stats.totalItems}"
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    label = stringResource(R.string.service_instance_label)
                                )
                            }
                        }

                        item {
                            PlexHeroCard(
                                data = data,
                                isRefreshing = isRefreshing,
                                accent = accent,
                                isDarkTheme = isDarkTheme,
                                sessionsTone = sessionsTone
                            )
                        }

                        item {
                            StatsGrid(
                                data = data,
                                moviesTone = moviesTone,
                                showsTone = showsTone,
                                musicTone = musicTone
                            )
                        }

                        if (data.libraries.isNotEmpty()) {
                            item {
                                LibrariesSection(
                                    libraries = data.libraries,
                                    accent = accent,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }

                        if (data.activeSessions.isNotEmpty()) {
                            item {
                                ActiveSessionsSection(
                                    sessions = data.activeSessions,
                                    accent = accent,
                                    isDarkTheme = isDarkTheme,
                                    sessionsTone = sessionsTone
                                )
                            }
                        }

                        if (data.recentlyAdded.isNotEmpty()) {
                            item {
                                RecentlyAddedSection(
                                    items = data.recentlyAdded,
                                    accent = accent,
                                    isDarkTheme = isDarkTheme
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

private fun plexPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
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
            accent.copy(alpha = 0.04f),
            Color(0xFFF6F8FD)
        )
    )
}

private fun plexCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF121924) else Color(0xFFFAFBFF)

private fun plexRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF1A2230) else Color(0xFFFFFFFF)

private fun plexBorderTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF313A4C) else Color(0xFFD5DDF2)

@Composable
private fun PlexHeroCard(
    data: PlexDashboardData,
    isRefreshing: Boolean,
    accent: Color,
    isDarkTheme: Boolean,
    sessionsTone: Color
) {
    val cardColor = plexCardColor(isDarkTheme)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(
                    type = ServiceType.PLEX,
                    size = 52.dp,
                    iconSize = 34.dp,
                    cornerRadius = 14.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.service_plex),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.plex_overview_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (data.activeSessions.isNotEmpty()) {
                    Surface(
                        color = sessionsTone.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Text(
                                    text = "${data.activeSessions.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = sessionsTone
                                )
                            }
                            Text(
                                text = stringResource(R.string.plex_active_sessions),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Surface(
                color = plexRaisedCardColor(isDarkTheme),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = data.serverInfo.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = data.serverInfo.platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "v" + data.serverInfo.version,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isRefreshing) {
                LinearPulse(accent = accent)
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
private fun StatsGrid(
    data: PlexDashboardData,
    moviesTone: Color,
    showsTone: Color,
    musicTone: Color
) {
    val formatter = java.text.NumberFormat.getInstance()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${stringResource(R.string.plex_total_items)} • ${formatter.format(data.stats.totalItems)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCardSmall(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.plex_movies),
                value = formatter.format(data.stats.totalMovies),
                iconTint = moviesTone,
                icon = Icons.Default.LocalMovies
            )
            StatCardSmall(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.plex_shows),
                value = formatter.format(data.stats.totalShows),
                iconTint = showsTone,
                icon = Icons.Default.Tv
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val episodesTone = Color(0xFF60A5FA)
            StatCardSmall(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.plex_episodes),
                value = formatter.format(data.stats.totalEpisodes),
                iconTint = episodesTone,
                icon = Icons.Default.Tv
            )
            StatCardSmall(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.plex_music),
                value = formatter.format(data.stats.totalMusic),
                iconTint = musicTone,
                icon = Icons.Default.MusicNote
            )
        }
    }
}

@Composable
private fun StatCardSmall(
    modifier: Modifier,
    title: String,
    value: String,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val cardColor = plexCardColor(darkTheme)
    val borderColor = plexBorderTone(darkTheme).copy(alpha = if (darkTheme) 0.7f else 0.6f)
    val iconChipAlpha = if (darkTheme) 0.24f else 0.18f
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = iconChipAlpha),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    }
                }
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun colorForLibType(type: String): Color {
    return when (type) {
        "movie" -> Color(0xFFF59E0B)
        "show" -> Color(0xFF3B82F6)
        "episode" -> Color(0xFF60A5FA)
        "artist", "album", "track" -> Color(0xFFEC4899)
        "photo" -> Color(0xFF10B981)
        else -> Color(0xFF8B5CF6)
    }
}

@Composable
private fun LibrariesSection(
    libraries: List<PlexLibrary>,
    accent: Color,
    isDarkTheme: Boolean
) {
    val total = (libraries.sumOf { it.itemCount }).coerceAtLeast(1)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = plexCardColor(isDarkTheme),
        border = BorderStroke(1.dp, plexBorderTone(isDarkTheme))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plex_libraries),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text(
                        text = "${libraries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            libraries.forEach { lib ->
                val ratio = lib.itemCount.toDouble() / total.toDouble()
                val tint = colorForLibType(lib.type)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (lib.type) {
                            "movie" -> Icons.Default.LocalMovies
                            "show", "episode" -> Icons.Default.Tv
                            "artist", "album", "track" -> Icons.Default.MusicNote
                            else -> Icons.Default.Storage
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lib.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = java.text.NumberFormat.getInstance().format(lib.itemCount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = tint
                            )
                            if (lib.type == "show" && lib.episodeCount > 0) {
                                Text(
                                    text = "${java.text.NumberFormat.getInstance().format(lib.episodeCount)} ep.",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    val barBg = if (isDarkTheme) Color(0xFF1E2636) else Color(0xFFEAEEFA)
                    Surface(
                        color = barBg,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart) {
                            Surface(
                                color = tint,
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier.fillMaxWidth(ratio.toFloat().coerceAtLeast(0.02f)).height(8.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTag(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.13f),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ActiveSessionsSection(
    sessions: List<PlexSession>,
    accent: Color,
    isDarkTheme: Boolean,
    sessionsTone: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = sessionsTone.copy(alpha = if (isDarkTheme) 0.08f else 0.05f),
        border = BorderStroke(1.dp, sessionsTone.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plex_active_sessions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = sessionsTone.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Surface(color = Color.Green, shape = CircleShape, modifier = Modifier.size(6.dp)) {}
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${sessions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = sessionsTone
                        )
                    }
                }
            }
            
            sessions.forEach { session ->
                val isPlaying = session.playerState == "playing"
                val stateColor = if (isPlaying) Color.Green else Color(0xFFF59E0B)
                val cardBg = if (isDarkTheme) Color(0xFF1A2230).copy(alpha = 0.5f) else Color(0xFFFFFFFF).copy(alpha = 0.5f)
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, plexBorderTone(isDarkTheme))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = stateColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null, tint = stateColor, modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.displayTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (session.displaySubtitle.isNotEmpty()) {
                                    Text(
                                        text = session.displaySubtitle,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = session.username,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    maxLines = 1
                                )
                                Text(
                                    text = session.playerPlatform.replaceFirstChar { it.uppercase() },
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        val barBg = if (isDarkTheme) Color(0xFF1E2636) else Color(0xFFEAEEFA)
                        Surface(
                            color = barBg,
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        ) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                Surface(
                                    color = accent,
                                    shape = RoundedCornerShape(percent = 50),
                                    modifier = Modifier.fillMaxWidth(session.progressRatio.coerceIn(0.02, 1.0).toFloat()).height(5.dp)
                                ) {}
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (session.resolutionLabel.isNotEmpty()) {
                                SessionTag(text = session.resolutionLabel, tint = Color(0xFF60A5FA))
                            }
                            if (session.isTranscoding) {
                                SessionTag(text = "Transcode", tint = Color(0xFFF59E0B))
                            } else {
                                SessionTag(text = "Direct", tint = Color(0xFF10B981))
                            }
                            if (session.bandwidth > 0) {
                                val mbps = String.format(java.util.Locale.US, "%.1f Mbps", session.bandwidthMbps)
                                SessionTag(text = mbps, tint = accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    items: List<PlexRecentItem>,
    accent: Color,
    isDarkTheme: Boolean
) {
    val recentTone = Color(0xFFA855F7)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = plexCardColor(isDarkTheme),
        border = BorderStroke(1.dp, plexBorderTone(isDarkTheme))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plex_recently_added),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = recentTone.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Text(
                        text = "${items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = recentTone,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            items.filter { it.displayTitle.isNotBlank() }.take(10).forEach { item ->
                val tint = colorForLibType(item.type)
                val icon = when (item.type) {
                    "movie" -> Icons.Default.LocalMovies
                    "show", "episode" -> Icons.Default.Tv
                    "artist", "album", "track" -> Icons.Default.MusicNote
                    else -> Icons.Default.Storage
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = tint.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.year != null) {
                            Text(
                                text = "${item.year}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val dateStr = if (item.addedAt > 0) {
                        android.text.format.DateUtils.getRelativeTimeSpanString(item.addedAt).toString()
                    } else ""
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
