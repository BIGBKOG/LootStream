package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaLibraryStats
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.components.mediaTypeIcon
import com.example.ui.components.sourceAccentColor
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.expressivePressScale
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(
    continueWatchingItems: List<MediaItem>,
    recentlyAddedItems: List<MediaItem>,
    servers: List<HostServerConfig>,
    stats: MediaLibraryStats,
    isSyncing: Boolean,
    isAnimationsEnabled: Boolean,
    isLowEndOptimization: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem, List<MediaItem>) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenHostDialog: () -> Unit,
    onImportLocalMedia: () -> Unit,
    onOpenStreamUrlDialog: () -> Unit,
    onSyncAllServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val pullRefreshState = rememberPullToRefreshState()
    val enablePressScale = isAnimationsEnabled && !isLowEndOptimization

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_tab_pull_refresh")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_content_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "home_quick_stats_strip") {
                HomeQuickStatsStrip(
                    stats = stats,
                    onOpenLibrary = onOpenLibrary,
                    onOpenVault = onOpenVault,
                    enablePressScale = enablePressScale
                )
            }

            if (stats.totalItems == 0) {
                item(key = "home_empty_theater_card") {
                    EmptyTheaterOnboardingCard(
                        onConnectHost = onOpenHostDialog,
                        onImportLocal = onImportLocalMedia,
                        onAddStreamUrl = onOpenStreamUrlDialog
                    )
                }
            }

            if (continueWatchingItems.isNotEmpty()) {
                item(key = "home_continue_watching") {
                    HomeSectionHeader(
                        title = s.sectionContinueWatching,
                        badge = "${continueWatchingItems.size}",
                        onAction = onOpenLibrary,
                        actionLabel = s.actionSeeAll
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(continueWatchingItems, key = { "cw_${it.id}" }) { item ->
                            ContinueWatchingCarouselCard(
                                item = item,
                                onCardClick = onItemClick,
                                onPlayClick = { onPlayClick(it, continueWatchingItems) }
                            )
                        }
                    }
                }
            }

            if (recentlyAddedItems.isNotEmpty()) {
                item(key = "home_recently_added") {
                    HomeSectionHeader(
                        title = s.sectionRecentlyAdded,
                        badge = "${recentlyAddedItems.size}",
                        onAction = onOpenLibrary,
                        actionLabel = s.actionSeeAll
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(recentlyAddedItems, key = { "recent_${it.id}" }) { item ->
                            RecentlyAddedPosterCard(
                                item = item,
                                onCardClick = onItemClick,
                                onPlayClick = { onPlayClick(it, recentlyAddedItems) }
                            )
                        }
                    }
                }
            }

            item(key = "home_sources_overview") {
                HomeSourcesOverviewCard(
                    servers = servers,
                    stats = stats,
                    isSyncing = isSyncing,
                    onOpenHostDialog = onOpenHostDialog,
                    onImportLocalMedia = onImportLocalMedia,
                    onOpenStreamUrlDialog = onOpenStreamUrlDialog,
                    onSyncAllServers = onSyncAllServers
                )
            }
        }
    }
}

@Composable
private fun HomeQuickStatsStrip(
    stats: MediaLibraryStats,
    onOpenLibrary: () -> Unit,
    onOpenVault: () -> Unit,
    enablePressScale: Boolean = true
) {
    val s = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_quick_stats_strip"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val libraryInteraction = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .expressivePressScale(libraryInteraction, enabled = enablePressScale, pressedScale = 0.94f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(interactionSource = libraryInteraction, indication = null, onClick = onOpenLibrary)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        "${stats.totalItems} ${s.statTotalMedia}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${s.tabLibrary} ➔",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            val vaultInteraction = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .expressivePressScale(vaultInteraction, enabled = enablePressScale, pressedScale = 0.94f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(interactionSource = vaultInteraction, indication = null, onClick = onOpenVault)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bookmarks, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                }
                Column {
                    val watchedFormatted = String.format(Locale.US, "%.1fh", stats.watchedDurationHours)
                    Text(
                        "$watchedFormatted • ${stats.favoritesCount} ★",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(
                        "${s.tabVault} ➔",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTheaterOnboardingCard(
    onConnectHost: () -> Unit,
    onImportLocal: () -> Unit,
    onAddStreamUrl: () -> Unit
) {
    val s = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_empty_theater_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = s.homeEmptyTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = s.homeEmptySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onConnectHost,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_connect_host_btn")
            ) {
                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(s.btnConnectHost, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onImportLocal,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_import_local_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.btnImportLocal, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onAddStreamUrl,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("home_add_stream_url_btn")
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.btnAddStreamUrl, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    badge: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = actionLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ContinueWatchingCarouselCard(
    item: MediaItem,
    onCardClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    val sourceColor = sourceAccentColor(item.sourceType)
    Card(
        modifier = Modifier
            .width(236.dp)
            .clickable { onCardClick(item) }
            .testTag("cw_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.effectiveThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.backdropUrl.ifBlank { item.posterUrl },
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(mediaTypeIcon(item.mediaType), contentDescription = null, tint = sourceColor, modifier = Modifier.size(38.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0B0F19))
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .clickable { onPlayClick(item) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (item.progressFraction > 0.01f) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.formattedRemaining.ifBlank { item.subtitle.ifBlank { item.sourceType.label } },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedPosterCard(
    item: MediaItem,
    onCardClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    val language = LocalAppLanguage.current
    val sourceColor = sourceAccentColor(item.sourceType)
    Card(
        modifier = Modifier
            .width(142.dp)
            .clickable { onCardClick(item) }
            .testTag("recent_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.effectiveThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.posterUrl.ifBlank { item.backdropUrl },
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(mediaTypeIcon(item.mediaType), contentDescription = null, tint = sourceColor, modifier = Modifier.size(36.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xAA0B0F19),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clickable { onPlayClick(item) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.mediaType.localizedLabel(language),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeSourcesOverviewCard(
    servers: List<HostServerConfig>,
    stats: MediaLibraryStats,
    isSyncing: Boolean,
    onOpenHostDialog: () -> Unit,
    onImportLocalMedia: () -> Unit,
    onOpenStreamUrlDialog: () -> Unit,
    onSyncAllServers: () -> Unit
) {
    val s = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_sources_overview_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    Text(
                        text = s.sectionSourcesOverview,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (servers.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onSyncAllServers,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.hostSyncNow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (servers.isEmpty()) {
                Text(
                    text = s.hostNoServers,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                servers.forEach { srv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .clickable(onClick = onOpenHostDialog)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${srv.hostPlatform} @ ${srv.serverUrl}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "${srv.itemCount} items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonPurple.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClick = onOpenHostDialog)
                        .testTag("sources_manage_host_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PC Host (${stats.pcHostCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonPurple, maxLines = 1)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonGreen.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClick = onImportLocalMedia)
                        .testTag("sources_import_local_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Local (${stats.localCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonGreen, maxLines = 1)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonCyan.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClick = onOpenStreamUrlDialog)
                        .testTag("sources_add_url_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("URL (${stats.streamUrlCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan, maxLines = 1)
                    }
                }
            }
        }
    }
}
