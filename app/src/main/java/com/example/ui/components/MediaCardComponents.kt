package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.expressivePressScale
import java.util.Locale

fun mediaTypeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.AUDIO -> Icons.Default.MusicNote
    MediaType.SERIES, MediaType.EPISODE -> Icons.Default.Tv
    MediaType.LIVE_STREAM -> Icons.Default.LiveTv
    else -> Icons.Default.Movie
}

fun sourceAccentColor(sourceType: MediaSourceType): Color = when (sourceType) {
    MediaSourceType.PC_HOST -> NeonPurple
    MediaSourceType.LOCAL -> NeonGreen
    MediaSourceType.STREAM_URL -> NeonCyan
    MediaSourceType.ALL -> NeonCyan
}

@Composable
fun RichMediaCard(
    item: MediaItem,
    showDurationBadge: Boolean = true,
    showResumeBar: Boolean = true,
    isAnimationsEnabled: Boolean = true,
    isLowEndOptimization: Boolean = false,
    beamAngle: Float = 0f,
    onCardClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleWatchlist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val interactionSource = remember { MutableInteractionSource() }
    val sourceColor = sourceAccentColor(item.sourceType)
    val effectiveAnimations = isAnimationsEnabled && !isLowEndOptimization
    val isHighlighted = item.isResumeAvailable || item.isFavorite

    Card(
        modifier = modifier
            .fillMaxWidth()
            .expressivePressScale(interactionSource, enabled = effectiveAnimations, pressedScale = 0.975f)
            .then(
                if (effectiveAnimations && isHighlighted) {
                    Modifier.borderBeam(
                        strokeWidth = 1.5.dp,
                        cornerRadius = 16.dp,
                        borderColor = sourceColor,
                        beamAngleProvider = { beamAngle },
                        isAnimated = true
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCardClick(item) }
            )
            .testTag("media_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.watchStatus == WatchStatus.WATCHED) {
                NeonGreen.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            }
        )
    ) {
        Column {
            // Backdrop / Poster Artwork Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        sourceColor.copy(alpha = 0.22f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mediaTypeIcon(item.mediaType),
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Bottom gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x33050810),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Top-left Source Pill + Codec Badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC0B0F19),
                        border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = item.sourceType.localizedLabel(language).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = sourceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (item.codecBadge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xBB131926)
                        ) {
                            Text(
                                text = item.codecBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Top-right Duration or Watched badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    if (item.communityRating != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC0B0F19)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = NeonGold, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format(Locale.US, "%.1f", item.communityRating),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGold
                                )
                            }
                        }
                    }
                    if (showDurationBadge && item.formattedDuration.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCC0B0F19)
                        ) {
                            Text(
                                text = item.formattedDuration,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Quick Play circle button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clickable { onPlayClick(item) }
                        .testTag("media_card_play_${item.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = s.actionPlay,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (showResumeBar && item.progressFraction > 0.01f) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            // Card Content Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaLine = buildString {
                            if (item.subtitle.isNotBlank()) append(item.subtitle)
                            else append(item.mediaType.localizedLabel(language))
                            if (item.releaseYear != null) append(" • ${item.releaseYear}")
                            if (item.isResumeAvailable && item.formattedRemaining.isNotBlank()) {
                                append(" • ${item.formattedRemaining}")
                            }
                        }
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (item.watchStatus == WatchStatus.WATCHED) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = s.statWatched,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (item.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.genres.take(3).joinToString(" • "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onPlayClick(item) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("media_cta_play_${item.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                item.isResumeAvailable -> s.actionResume
                                item.watchStatus == WatchStatus.WATCHED -> s.actionReplay
                                else -> s.actionPlay
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = { onToggleWatchlist(item) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("media_queue_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isWatchlist) Icons.Default.PlaylistAddCheck else Icons.Default.PlaylistAdd,
                            contentDescription = if (item.isWatchlist) s.actionInQueue else s.actionAddToQueue,
                            tint = if (item.isWatchlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onToggleFavorite(item) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("media_fav_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (item.isFavorite) s.actionFavorited else s.actionFavorite,
                            tint = if (item.isFavorite) Color(0xFFFB7185) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMediaCard(
    item: MediaItem,
    showDurationBadge: Boolean = true,
    showResumeBar: Boolean = true,
    onCardClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val sourceColor = sourceAccentColor(item.sourceType)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick(item) }
            .testTag("compact_media_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.effectiveThumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.effectiveThumbnail,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = mediaTypeIcon(item.mediaType),
                            contentDescription = null,
                            tint = sourceColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                if (showResumeBar && item.progressFraction > 0.01f) {
                    LinearProgressIndicator(
                        progress = { item.progressFraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val sub = listOfNotNull(
                    item.sourceType.localizedLabel(language),
                    item.codecBadge.takeIf { it.isNotBlank() },
                    item.formattedDuration.takeIf { showDurationBadge && it.isNotBlank() }
                ).joinToString(" • ")
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { onToggleFavorite(item) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (item.isFavorite) Color(0xFFFB7185) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onPlayClick(item) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MinimalMediaCard(
    item: MediaItem,
    showDurationBadge: Boolean = true,
    onCardClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceColor = sourceAccentColor(item.sourceType)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick(item) }
            .testTag("minimal_media_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(sourceColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mediaTypeIcon(item.mediaType),
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val info = listOfNotNull(
                    item.subtitle.takeIf { it.isNotBlank() },
                    item.formattedDuration.takeIf { showDurationBadge && it.isNotBlank() }
                ).joinToString(" • ")
                if (info.isNotBlank()) {
                    Text(
                        text = info,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onPlayClick(item) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
