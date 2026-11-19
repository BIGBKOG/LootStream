package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.preferences.CardDensity
import com.example.ui.components.CompactMediaCard
import com.example.ui.components.MinimalMediaCard
import com.example.ui.components.RichMediaCard
import com.example.ui.density.ScreenDensityInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryTabContent(
    items: List<MediaItem>,
    sourceFilter: MediaSourceType,
    mediaTypeFilter: MediaType,
    watchStatusFilter: WatchStatus?,
    sortOption: MediaSortOption,
    cardDensity: CardDensity,
    showDurationBadge: Boolean,
    showResumeBar: Boolean,
    isSyncing: Boolean,
    isAnimationsEnabled: Boolean,
    isLowEndOptimization: Boolean,
    beamAngle: Float,
    screenDensityInfo: ScreenDensityInfo,
    listState: LazyListState,
    gridState: LazyGridState,
    onRefresh: () -> Unit,
    onSelectSource: (MediaSourceType) -> Unit,
    onSelectMediaType: (MediaType) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onResetFilters: () -> Unit,
    onOpenAddMedia: () -> Unit,
    onOpenHostDialog: () -> Unit,
    onImportLocalMedia: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val pullRefreshState = rememberPullToRefreshState()
    val hasActiveExtraFilters = sourceFilter != MediaSourceType.ALL ||
        mediaTypeFilter != MediaType.ALL ||
        watchStatusFilter != null ||
        sortOption != MediaSortOption.RECENTLY_PLAYED

    Column(modifier = modifier.fillMaxSize()) {
        // Source Filter Chips + Add button
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(MediaSourceType.entries, key = { it.name }) { src ->
                val selected = sourceFilter == src
                FilterChip(
                    selected = selected,
                    onClick = { onSelectSource(src) },
                    label = {
                        Text(
                            text = src.localizedLabel(language),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("source_chip_${src.name.lowercase()}")
                )
            }
            item(key = "add_media_chip") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(34.dp)
                        .clickable(onClick = onOpenAddMedia)
                        .testTag("library_add_media_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = s.actionAddMedia,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Media Type Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val typeChoices = listOf(MediaType.ALL, MediaType.MOVIE, MediaType.SERIES, MediaType.VIDEO, MediaType.AUDIO, MediaType.LIVE_STREAM)
            items(typeChoices, key = { it.name }) { type ->
                val selected = mediaTypeFilter == type
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .clickable { onSelectMediaType(type) }
                        .testTag("type_chip_${type.name.lowercase()}")
                ) {
                    Text(
                        text = type.localizedLabel(language),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (hasActiveExtraFilters) {
            ActiveMediaFiltersSummaryPill(
                sourceFilter = sourceFilter,
                mediaTypeFilter = mediaTypeFilter,
                watchStatusFilter = watchStatusFilter,
                sortOption = sortOption,
                onOpenFilterSheet = onOpenFilterSheet,
                onResetFilters = onResetFilters
            )
        }

        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (items.isEmpty()) {
                val bob = rememberEmptyBob(enabled = isAnimationsEnabled && !isLowEndOptimization)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { translationY = bob }
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = s.libraryEmptyTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = s.libraryEmptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onOpenHostDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.actionConnectServer, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onImportLocalMedia,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.btnImportLocal, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (screenDensityInfo.gridColumns > 1 && cardDensity == CardDensity.RICH_CARDS) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(screenDensityInfo.gridColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().testTag("library_media_grid")
                ) {
                    items(items, key = { it.id }) { item ->
                        RichMediaCard(
                            item = item,
                            showDurationBadge = showDurationBadge,
                            showResumeBar = showResumeBar,
                            isAnimationsEnabled = isAnimationsEnabled,
                            isLowEndOptimization = isLowEndOptimization,
                            beamAngle = beamAngle,
                            onCardClick = onItemClick,
                            onPlayClick = onPlayClick,
                            onToggleFavorite = onToggleFavorite,
                            onToggleWatchlist = onToggleWatchlist
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(if (cardDensity == CardDensity.MINIMAL_LIST) 8.dp else 14.dp),
                    modifier = Modifier.fillMaxSize().testTag("library_media_list")
                ) {
                    items(items, key = { it.id }) { item ->
                        when (cardDensity) {
                            CardDensity.RICH_CARDS -> RichMediaCard(
                                item = item,
                                showDurationBadge = showDurationBadge,
                                showResumeBar = showResumeBar,
                                isAnimationsEnabled = isAnimationsEnabled,
                                isLowEndOptimization = isLowEndOptimization,
                                beamAngle = beamAngle,
                                onCardClick = onItemClick,
                                onPlayClick = onPlayClick,
                                onToggleFavorite = onToggleFavorite,
                                onToggleWatchlist = onToggleWatchlist
                            )
                            CardDensity.COMPACT_CARDS -> CompactMediaCard(
                                item = item,
                                showDurationBadge = showDurationBadge,
                                showResumeBar = showResumeBar,
                                onCardClick = onItemClick,
                                onPlayClick = onPlayClick,
                                onToggleFavorite = onToggleFavorite
                            )
                            CardDensity.MINIMAL_LIST -> MinimalMediaCard(
                                item = item,
                                showDurationBadge = showDurationBadge,
                                onCardClick = onItemClick,
                                onPlayClick = onPlayClick
                            )
                        }
                    }
                }
            }
        }
    }
}
