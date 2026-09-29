package com.homelab.app.ui.gitea

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.primaryColor
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

val langColors = mapOf(
    "Go" to Color(0xFF00ADD8),
    "JavaScript" to Color(0xFFF7DF1E),
    "TypeScript" to Color(0xFF3178C6),
    "Python" to Color(0xFF3776AB),
    "Rust" to Color(0xFFDEA584),
    "Java" to Color(0xFFB07219),
    "C#" to Color(0xFF178600),
    "C++" to Color(0xFFF34B7D),
    "Ruby" to Color(0xFFCC342D),
    "PHP" to Color(0xFF777BB4),
    "Shell" to Color(0xFF89E051),
    "Dockerfile" to Color(0xFF384D54),
    "HTML" to Color(0xFFE34C26),
    "CSS" to Color(0xFF563D7C),
    "Dart" to Color(0xFF00B4AB),
    "Swift" to Color(0xFFF05138),
    "Kotlin" to Color(0xFFA97BFF)
)

private fun giteaPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A1015),
            Color(0xFF0F1620),
            accent.copy(alpha = 0.045f),
            Color(0xFF0A1118)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF8FAFF),
            Color(0xFFF2F6FF),
            accent.copy(alpha = 0.03f),
            Color(0xFFF6F8FF)
        )
    )
}

private fun giteaNeutralCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF121B29) else Color(0xFFF3F5F7)

private fun giteaNeutralRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF1A2738) else Color(0xFFF8FAFC)

private fun giteaNeutralBorderTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF33465D) else Color(0xFFD3DBE3)

private fun giteaCardColor(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.055f else 0.03f)
    .compositeOver(giteaNeutralCardColor(isDarkTheme))

private fun giteaRaisedCardColor(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.08f else 0.045f)
    .compositeOver(giteaNeutralRaisedCardColor(isDarkTheme))

private fun giteaBorderTone(
    isDarkTheme: Boolean,
    accent: Color
): Color = accent.copy(alpha = if (isDarkTheme) 0.16f else 0.10f)
    .compositeOver(giteaNeutralBorderTone(isDarkTheme))

private fun giteaTrackTone(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF2A374A) else Color(0xFFE0EBD9)

val HeatmapColors: List<Color>
    @Composable
    get() {
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
        val empty = giteaTrackTone(isDarkTheme)
        val base = ServiceType.GITEA.primaryColor
        return listOf(
            empty,
            base.copy(alpha = 0.25f),
            base.copy(alpha = 0.45f),
            base.copy(alpha = 0.65f),
            base.copy(alpha = 0.85f)
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiteaDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToRepo: (owner: String, repo: String) -> Unit,
    viewModel: GiteaViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val orgs by viewModel.orgs.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val totalBranches by viewModel.totalBranches.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val accent = ServiceType.GITEA.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { giteaPageBackground(isDarkTheme, accent) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) accent.copy(alpha = 0.09f) else accent.copy(alpha = 0.045f)
    }

    val sortedRepos = remember(repos, sortOrder) {
        when (sortOrder) {
            RepoSortOrder.RECENT -> repos.sortedByDescending { it.updated_at }
            RepoSortOrder.ALPHA -> repos.sortedBy { it.name.lowercase() }
        }
    }
    
    val repoStats = remember(repos) {
        Pair(repos.size, repos.sumOf { it.stars_count })
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_gitea), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val haptic = LocalHapticFeedback.current
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
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
                        columns = GridCells.Fixed(1),
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

                    user?.let { u ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            UserOverviewCard(
                                user = u,
                                repos = repoStats.first,
                                stars = repoStats.second,
                                branches = totalBranches
                            )
                        }
                    } ?: run {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniStat(icon = Icons.Default.Folder, iconColor = Color(0xFF4CAF50), value = "${repoStats.first}", label = stringResource(R.string.gitea_repos))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniStat(icon = Icons.Default.Star, iconColor = Color(0xFFFF9800), value = "${repoStats.second}", label = stringResource(R.string.gitea_stars))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniStat(icon = Icons.AutoMirrored.Filled.CallMerge, iconColor = Color(0xFF2196F3), value = "$totalBranches", label = stringResource(R.string.gitea_branches))
                                }
                            }
                        }
                    }

                    if (heatmap.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { HeatmapSection(heatmap = heatmap) }
                    }

                    if (orgs.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { OrgsSection(orgs = orgs) }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${stringResource(R.string.gitea_repos)} (${repoStats.first})", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.weight(1f))
                            val haptic = LocalHapticFeedback.current
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = giteaRaisedCardColor(isDarkTheme, accent),
                                border = BorderStroke(
                                    1.dp,
                                    giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.64f else 0.52f)
                                ),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleSortOrder()
                                }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (sortOrder == RepoSortOrder.RECENT) Icons.Default.AccessTime else Icons.Default.SortByAlpha,
                                        contentDescription = if (sortOrder == RepoSortOrder.RECENT) stringResource(R.string.gitea_sort_recent) else stringResource(R.string.gitea_sort_alpha),
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(if (sortOrder == RepoSortOrder.RECENT) stringResource(R.string.gitea_sort_recent) else stringResource(R.string.gitea_sort_alpha), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (sortedRepos.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = stringResource(R.string.gitea_no_repos), modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.gitea_no_repos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(
                            items = sortedRepos,
                            key = { it.id },
                            span = { GridItemSpan(maxLineSpan) }
                        ) { repo ->
                            RepoCard(repo = repo, onClick = { onNavigateToRepo(repo.owner.login, repo.name) })
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: GiteaUser) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.GITEA.primaryColor
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = giteaCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = stringResource(R.string.service_gitea), tint = ServiceType.GITEA.primaryColor, modifier = Modifier.padding(14.dp))
            }
            Column {
                Text(user.full_name.ifEmpty { user.login }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UserOverviewCard(
    user: GiteaUser,
    repos: Int,
    stars: Int,
    branches: Int
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.GITEA.primaryColor
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = giteaCardColor(isDarkTheme, accent),
        border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = stringResource(R.string.service_gitea),
                        tint = ServiceType.GITEA.primaryColor,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Column {
                    Text(user.full_name.ifEmpty { user.login }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = giteaRaisedCardColor(isDarkTheme, accent),
                border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.62f else 0.52f)),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryStat(
                        icon = Icons.Default.Folder,
                        iconColor = Color(0xFF4CAF50),
                        value = "$repos",
                        label = stringResource(R.string.gitea_repos),
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(color = giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    SummaryStat(
                        icon = Icons.Default.Star,
                        iconColor = Color(0xFFFF9800),
                        value = "$stars",
                        label = stringResource(R.string.gitea_stars),
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(color = giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
                    SummaryStat(
                        icon = Icons.AutoMirrored.Filled.CallMerge,
                        iconColor = Color(0xFF2196F3),
                        value = "$branches",
                        label = stringResource(R.string.gitea_branches),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, value: String, label: String) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = giteaCardColor(isDarkTheme, iconColor),
        border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, iconColor).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier.height(96.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OrgsSection(orgs: List<GiteaOrg>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.GITEA.primaryColor
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gitea_orgs), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            orgs.forEach { org ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = giteaCardColor(isDarkTheme, accent),
                    border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.7f else 0.56f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CorporateFare, contentDescription = stringResource(R.string.gitea_orgs), tint = ServiceType.GITEA.primaryColor, modifier = Modifier.size(16.dp))
                        Text(org.username, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(repo: GiteaRepo, onClick: () -> Unit) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val repoAccent = repo.language
        ?.takeIf { it.isNotBlank() }
        ?.let { langColors[it] }
        ?: ServiceType.GITEA.primaryColor
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyScale"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = giteaCardColor(isDarkTheme, repoAccent),
        border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, repoAccent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (repo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = stringResource(if (repo.isPrivate) R.string.gitea_private else R.string.gitea_public), tint = if (repo.isPrivate) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(repo.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ServiceType.GITEA.primaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.weight(1f))
                    if (repo.fork) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = giteaRaisedCardColor(isDarkTheme, repoAccent),
                            border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, repoAccent).copy(alpha = if (isDarkTheme) 0.62f else 0.52f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = stringResource(R.string.gitea_fork), modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.gitea_fork), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.home_open), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Description
            if (repo.description.isNotEmpty()) {
                Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            // Footer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (repo.language?.isNotEmpty() == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(langColors[repo.language!!] ?: MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(repo.language!!, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.gitea_stars), modifier = Modifier.size(12.dp), tint = Color(0xFFFF9800))
                    Text("${repo.stars_count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = stringResource(R.string.gitea_forks), modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${repo.forks_count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(formatRelativeDate(repo.updated_at, context), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ----- HEATMAP BUILDER -----

@Composable
private fun HeatmapSection(heatmap: List<GiteaHeatmapItem>) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val accent = ServiceType.GITEA.primaryColor
    val grid = buildHeatmapGrid(heatmap)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gitea_contributions), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = giteaCardColor(isDarkTheme, accent),
            border = BorderStroke(1.dp, giteaBorderTone(isDarkTheme, accent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    grid.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { level ->
                                Box(modifier = Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(HeatmapColors[level]))
                            }
                        }
                    }
                }
                
                // Legend
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.gitea_heatmap_less), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HeatmapColors.forEach { color ->
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    }
                    Text(stringResource(R.string.gitea_heatmap_more), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun buildHeatmapGrid(heatmap: List<GiteaHeatmapItem>): List<List<Int>> {
    val contributionMap = mutableMapOf<String, Int>()
    val cal = Calendar.getInstance()
    heatmap.forEach { item ->
        cal.timeInMillis = item.timestamp * 1000L
        val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        contributionMap[key] = (contributionMap[key] ?: 0) + item.contributions
    }

    val maxContrib = max(1, contributionMap.values.maxOrNull() ?: 1)
    
    val today = Calendar.getInstance()
    val dayOfWeek = today.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    val weeksToShow = 20
    val totalDays = weeksToShow * 7 + dayOfWeek + 1
    
    val startDate = today.clone() as Calendar
    startDate.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))

    val weeks = mutableListOf<List<Int>>()
    var currentWeek = mutableListOf<Int>()

    for (i in 0 until totalDays) {
        val d = startDate.clone() as Calendar
        d.add(Calendar.DAY_OF_YEAR, i)
        val key = "${d.get(Calendar.YEAR)}-${d.get(Calendar.MONTH)}-${d.get(Calendar.DAY_OF_MONTH)}"
        val count = contributionMap[key] ?: 0

        val level = when {
            count == 0 -> 0
            count == 1 -> 1
            count in 2..3 -> 2
            count in 4..6 -> 3
            else -> 4
        }

        currentWeek.add(level)
        if (currentWeek.size == 7) {
            weeks.add(currentWeek.toList())
            currentWeek.clear()
        }
    }
    if (currentWeek.isNotEmpty()) {
        weeks.add(currentWeek.toList())
    }
    return weeks
}

private fun formatRelativeDate(dateString: String?, context: android.content.Context): String {
    // Basic approximate parsing for ISO format mostly used by Gitea
    val safeDate = dateString ?: return ""
    try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = formatter.parse(safeDate.take(19)) ?: return safeDate
        val diff = (System.currentTimeMillis() - date.time) / 1000
        val days = (diff / 86400).toInt()
        
        if (days == 0) {
            val hours = (diff / 3600).toInt()
            if (hours > 0) return context.getString(R.string.gitea_date_hours_ago).format(hours)
            return context.getString(R.string.gitea_date_today)
        }
        if (days < 30) return context.getString(R.string.gitea_date_days_ago).format(days)
        val months = days / 30
        return context.getString(R.string.gitea_date_months_ago).format(months)
    } catch (e: Exception) {
        return safeDate
    }
}
