package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val overview: String,
    val mediaType: String,
    val sourceType: String,
    val streamUrl: String,
    val posterUrl: String,
    val backdropUrl: String,
    val durationMs: Long,
    val playbackPositionMs: Long,
    val releaseYear: Int?,
    val communityRating: Double?,
    val genresCsv: String,
    val codecBadge: String,
    val serverId: String?,
    val remoteItemId: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val isFavorite: Boolean,
    val isWatchlist: Boolean,
    val watchStatus: String,
    val playCount: Int,
    val lastPlayedAt: Long,
    val addedAt: Long,
    val fileSizeBytes: Long
) {
    fun toDomain(): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            subtitle = subtitle,
            overview = overview,
            mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.VIDEO),
            sourceType = runCatching { MediaSourceType.valueOf(sourceType) }.getOrDefault(MediaSourceType.LOCAL),
            streamUrl = streamUrl,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            durationMs = durationMs,
            playbackPositionMs = playbackPositionMs,
            releaseYear = releaseYear,
            communityRating = communityRating,
            genres = genresCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            codecBadge = codecBadge,
            serverId = serverId,
            remoteItemId = remoteItemId,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            isFavorite = isFavorite,
            isWatchlist = isWatchlist,
            watchStatus = runCatching { WatchStatus.valueOf(watchStatus) }.getOrDefault(WatchStatus.UNWATCHED),
            playCount = playCount,
            lastPlayedAt = lastPlayedAt,
            addedAt = addedAt,
            fileSizeBytes = fileSizeBytes
        )
    }

    companion object {
        fun fromDomain(item: MediaItem): MediaItemEntity {
            return MediaItemEntity(
                id = item.id,
                title = item.title,
                subtitle = item.subtitle,
                overview = item.overview,
                mediaType = item.mediaType.name,
                sourceType = item.sourceType.name,
                streamUrl = item.streamUrl,
                posterUrl = item.posterUrl,
                backdropUrl = item.backdropUrl,
                durationMs = item.durationMs,
                playbackPositionMs = item.playbackPositionMs,
                releaseYear = item.releaseYear,
                communityRating = item.communityRating,
                genresCsv = item.genres.joinToString(","),
                codecBadge = item.codecBadge,
                serverId = item.serverId,
                remoteItemId = item.remoteItemId,
                seriesName = item.seriesName,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                isFavorite = item.isFavorite,
                isWatchlist = item.isWatchlist,
                watchStatus = item.watchStatus.name,
                playCount = item.playCount,
                lastPlayedAt = item.lastPlayedAt,
                addedAt = item.addedAt,
                fileSizeBytes = item.fileSizeBytes
            )
        }
    }
}

@Entity(tableName = "media_servers")
data class MediaServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serverUrl: String,
    val hostPlatform: String,
    val accessToken: String,
    val deviceId: String,
    val serverVersion: String,
    val lastSyncedAt: Long,
    val itemCount: Int,
    val isActive: Boolean
) {
    fun toDomain(): HostServerConfig {
        return HostServerConfig(
            id = id,
            name = name,
            serverUrl = serverUrl,
            hostPlatform = hostPlatform,
            accessToken = accessToken,
            deviceId = deviceId,
            serverVersion = serverVersion,
            lastSyncedAt = lastSyncedAt,
            itemCount = itemCount,
            isActive = isActive
        )
    }

    companion object {
        fun fromDomain(server: HostServerConfig): MediaServerEntity {
            return MediaServerEntity(
                id = server.id,
                name = server.name,
                serverUrl = server.serverUrl,
                hostPlatform = server.hostPlatform,
                accessToken = server.accessToken,
                deviceId = server.deviceId,
                serverVersion = server.serverVersion,
                lastSyncedAt = server.lastSyncedAt,
                itemCount = server.itemCount,
                isActive = server.isActive
            )
        }
    }
}
