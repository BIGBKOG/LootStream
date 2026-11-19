package com.example.data.model

import java.util.Locale

enum class MediaSourceType(
    val label: String,
    val badgeColorHex: Long
) {
    ALL("All Sources", 0xFF00E5FF),
    PC_HOST("PC Host", 0xFFAA5CC3),
    LOCAL("Device", 0xFF00FF87),
    STREAM_URL("Stream URL", 0xFF38BDF8)
}

enum class MediaType(
    val label: String,
    val badgeColorHex: Long
) {
    ALL("All Media", 0xFF00E5FF),
    MOVIE("Movies", 0xFF00E5FF),
    SERIES("TV Shows", 0xFFA855F7),
    EPISODE("Episodes", 0xFF8B5CF6),
    VIDEO("Videos", 0xFF38BDF8),
    AUDIO("Audio", 0xFF00FF87),
    LIVE_STREAM("Streams", 0xFFF59E0B)
}

enum class WatchStatus(
    val label: String,
    val colorHex: Long
) {
    UNWATCHED("Unwatched", 0xFF94A3B8),
    IN_PROGRESS("In Progress", 0xFF00E5FF),
    WATCHED("Watched", 0xFF00FF87),
    ON_HOLD("Paused", 0xFFFBBF24)
}

enum class MediaSortOption(val label: String) {
    RECENTLY_PLAYED("Recently Played"),
    NEWEST_ADDED("Recently Added"),
    TITLE_ASC("Title (A-Z)"),
    DURATION_DESC("Longest Duration"),
    RATING_DESC("Highest Rated")
}

enum class MainTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    UP_NEXT("Up Next"),
    COLLECTIONS("Vault"),
    SETTINGS("Settings")
}

enum class CollectionsFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    IN_PROGRESS("Continue"),
    WATCHED("Watched"),
    HOST_ONLY("PC Host"),
    LOCAL_ONLY("Local")
}

enum class PlayerResizeMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom")
}

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val overview: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val sourceType: MediaSourceType = MediaSourceType.LOCAL,
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val releaseYear: Int? = null,
    val communityRating: Double? = null,
    val genres: List<String> = emptyList(),
    val codecBadge: String = "",
    val serverId: String? = null,
    val remoteItemId: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isFavorite: Boolean = false,
    val isWatchlist: Boolean = false,
    val watchStatus: WatchStatus = WatchStatus.UNWATCHED,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0L && playbackPositionMs > 0L) {
            (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isResumeAvailable: Boolean
        get() = playbackPositionMs > 5_000L && watchStatus != WatchStatus.WATCHED && progressFraction in 0.01f..0.95f

    val effectiveThumbnail: String
        get() = posterUrl.ifBlank { backdropUrl }

    val formattedDuration: String
        get() = formatMediaDuration(durationMs)

    val formattedRemaining: String
        get() {
            if (durationMs <= 0L) return ""
            val rem = (durationMs - playbackPositionMs).coerceAtLeast(0L)
            return "${formatMediaDuration(rem)} left"
        }

    val episodeCode: String?
        get() = if (seasonNumber != null && episodeNumber != null) {
            String.format(Locale.US, "S%02dE%02d", seasonNumber, episodeNumber)
        } else null

    val genresText: String
        get() = genres.joinToString(" • ")

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0L) return ""
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
                else -> "${fileSizeBytes / 1024L} KB"
            }
        }
}

data class HostServerConfig(
    val id: String,
    val name: String,
    val serverUrl: String,
    val hostPlatform: String = "Windows",
    val accessToken: String = "",
    val deviceId: String = "",
    val serverVersion: String = "1.0.0",
    val lastSyncedAt: Long = 0L,
    val itemCount: Int = 0,
    val isActive: Boolean = true
)

data class MediaLibraryStats(
    val totalItems: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val streamUrlCount: Int = 0,
    val pcHostCount: Int = 0,
    val localCount: Int = 0,
    val continueWatchingCount: Int = 0,
    val favoritesCount: Int = 0,
    val watchedCount: Int = 0,
    val watchlistCount: Int = 0,
    val totalDurationHours: Double = 0.0,
    val watchedDurationHours: Double = 0.0,
    val connectedServersCount: Int = 0
)

fun formatMediaDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}

fun formatPlayerTimestamp(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
