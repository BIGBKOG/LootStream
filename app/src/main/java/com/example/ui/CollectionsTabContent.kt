package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.data.model.CollectionsFilter
import com.example.data.model.MediaItem
import com.example.data.model.MediaLibraryStats
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.preferences.CardDensity
import com.example.ui.components.CompactMediaCard
import com.example.ui.components.MinimalMediaCard
import com.example.ui.components.RichMediaCard
import com.example.ui.density.ScreenDensityInfo
import com.example.ui.theme.NeonGreen

@Composable
fun CollectionsTabContent(
    items: List<MediaItem>,
    stats: MediaLibraryStats,
    collectionsFilter: CollectionsFilter,
    cardDensity: CardDensity,
    showDurationBadge: Boolean,
    showResumeBar: Boolean,
    isAnimationsEnabled: Boolean,
    isLowEndOptimization: Boolean,
    beamAngle: Float,
    screenDensityInfo: ScreenDensityInfo,
    listState: LazyListState,
    gridState: LazyGridState,
    onSelectFilter: (CollectionsFilter) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val language = LocalAppLanguage.current

    Column(modifier = modifier.fillMaxSize()) {
        VaultSummaryBanner(
            stats = stats,
            isAnimationsEnabled = isAnimationsEnabled && !isLowEndOptimization
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CollectionsFilter.entries, key = { it.name }) { filter ->
                val selected = collectionsFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(
                            text = filter.localizedLabel(language),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen.copy(alpha = 0.18f),
                        selectedLabelColor = NeonGreen
                    ),
                    modifier = Modifier.testTag("vault_filter_${filter.name.lowercase()}")
                )
            }
        }

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
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(NeonGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = s.vaultEmptyTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = s.vaultEmptySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (screenDensityInfo.gridColumns > 1 && cardDensity == CardDensity.RICH_CARDS) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(screenDensityInfo.gridColumns),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().testTag("vault_media_grid")
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
                modifier = Modifier.fillMaxSize().testTag("vault_media_list")
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
