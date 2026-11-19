package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import com.example.i18n.LocalAppStrings
import com.example.preferences.CardDensity
import com.example.ui.components.CompactMediaCard
import com.example.ui.components.MinimalMediaCard
import com.example.ui.components.RichMediaCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextTimelineTabContent(
    continueWatchingItems: List<MediaItem>,
    queueItems: List<MediaItem>,
    recentlyWatchedItems: List<MediaItem>,
    cardDensity: CardDensity,
    showDurationBadge: Boolean,
    showResumeBar: Boolean,
    isSyncing: Boolean,
    isAnimationsEnabled: Boolean,
    isLowEndOptimization: Boolean,
    beamAngle: Float,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem, List<MediaItem>) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val pullRefreshState = rememberPullToRefreshState()
    val hasAny = continueWatchingItems.isNotEmpty() || queueItems.isNotEmpty() || recentlyWatchedItems.isNotEmpty()

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        if (!hasAny) {
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
                            .background(NeonCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = s.upNextEmptyTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = s.upNextEmptySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("up_next_timeline_list")
            ) {
                if (continueWatchingItems.isNotEmpty()) {
                    item(key = "hdr_continue") {
                        TimelineSectionHeader(
                            title = s.upNextContinueSection,
                            count = continueWatchingItems.size,
                            icon = Icons.Default.PlayCircle,
                            accent = NeonCyan
                        )
                    }
                    items(continueWatchingItems, key = { "tl_cw_${it.id}" }) { item ->
                        TimelineMediaCardSlot(
                            item = item,
                            cardDensity = cardDensity,
                            showDurationBadge = showDurationBadge,
                            showResumeBar = showResumeBar,
                            isAnimationsEnabled = isAnimationsEnabled,
                            isLowEndOptimization = isLowEndOptimization,
                            beamAngle = beamAngle,
                            onItemClick = onItemClick,
                            onPlayClick = { onPlayClick(it, continueWatchingItems) },
                            onToggleFavorite = onToggleFavorite,
                            onToggleWatchlist = onToggleWatchlist
                        )
                    }
                }

                if (queueItems.isNotEmpty()) {
                    item(key = "hdr_queue") {
                        TimelineSectionHeader(
                            title = s.upNextQueueSection,
                            count = queueItems.size,
                            icon = Icons.Default.PlaylistPlay,
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(queueItems, key = { "tl_q_${it.id}" }) { item ->
                        TimelineMediaCardSlot(
                            item = item,
                            cardDensity = cardDensity,
                            showDurationBadge = showDurationBadge,
                            showResumeBar = showResumeBar,
                            isAnimationsEnabled = isAnimationsEnabled,
                            isLowEndOptimization = isLowEndOptimization,
                            beamAngle = beamAngle,
                            onItemClick = onItemClick,
                            onPlayClick = { onPlayClick(it, queueItems) },
                            onToggleFavorite = onToggleFavorite,
                            onToggleWatchlist = onToggleWatchlist
                        )
                    }
                }

                if (recentlyWatchedItems.isNotEmpty()) {
                    item(key = "hdr_watched") {
                        TimelineSectionHeader(
                            title = s.upNextWatchedSection,
                            count = recentlyWatchedItems.size,
                            icon = Icons.Default.CheckCircle,
                            accent = NeonGreen
                        )
                    }
                    items(recentlyWatchedItems, key = { "tl_w_${it.id}" }) { item ->
                        TimelineMediaCardSlot(
                            item = item,
                            cardDensity = cardDensity,
                            showDurationBadge = showDurationBadge,
                            showResumeBar = showResumeBar,
                            isAnimationsEnabled = isAnimationsEnabled,
                            isLowEndOptimization = isLowEndOptimization,
                            beamAngle = beamAngle,
                            onItemClick = onItemClick,
                            onPlayClick = { onPlayClick(it, recentlyWatchedItems) },
                            onToggleFavorite = onToggleFavorite,
                            onToggleWatchlist = onToggleWatchlist
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    accent: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.16f)
        ) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TimelineMediaCardSlot(
    item: MediaItem,
    cardDensity: CardDensity,
    showDurationBadge: Boolean,
    showResumeBar: Boolean,
    isAnimationsEnabled: Boolean,
    isLowEndOptimization: Boolean,
    beamAngle: Float,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit
) {
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
