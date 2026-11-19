package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaServerEntity
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaLibraryStats
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import com.example.data.remote.LootStreamHostClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val itemDao = database.mediaItemDao()
    private val serverDao = database.mediaServerDao()

    fun observeMediaItems(): Flow<List<MediaItem>> =
        itemDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeServers(): Flow<List<HostServerConfig>> =
        serverDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getAllMediaOnce(): List<MediaItem> =
        itemDao.getAllOnce().map { it.toDomain() }

    suspend fun getAllServersOnce(): List<HostServerConfig> =
        serverDao.getAllOnce().map { it.toDomain() }

    fun computeStats(items: List<MediaItem>, servers: List<HostServerConfig>): MediaLibraryStats {
        var movieCount = 0
        var seriesCount = 0
        var audioCount = 0
        var videoCount = 0
        var streamUrlCount = 0
        var pcHostCount = 0
        var localCount = 0
        var continueWatchingCount = 0
        var favoritesCount = 0
        var watchedCount = 0
        var watchlistCount = 0
        var totalDurationMs = 0L
        var watchedDurationMs = 0L

        for (item in items) {
            when (item.mediaType) {
                MediaType.MOVIE -> movieCount++
                MediaType.SERIES, MediaType.EPISODE -> seriesCount++
                MediaType.AUDIO -> audioCount++
                MediaType.VIDEO -> videoCount++
                MediaType.LIVE_STREAM, MediaType.ALL -> videoCount++
            }
            when (item.sourceType) {
                MediaSourceType.PC_HOST -> pcHostCount++
                MediaSourceType.LOCAL -> localCount++
                MediaSourceType.STREAM_URL -> streamUrlCount++
                MediaSourceType.ALL -> Unit
            }
            if (item.isResumeAvailable) continueWatchingCount++
            if (item.isFavorite) favoritesCount++
            if (item.watchStatus == WatchStatus.WATCHED) {
                watchedCount++
                watchedDurationMs += item.durationMs
            } else if (item.playbackPositionMs > 0L) {
                watchedDurationMs += item.playbackPositionMs.coerceAtMost(item.durationMs)
            }
            if (item.isWatchlist) watchlistCount++
            totalDurationMs += item.durationMs.coerceAtLeast(0L)
        }

        return MediaLibraryStats(
            totalItems = items.size,
            movieCount = movieCount,
            seriesCount = seriesCount,
            audioCount = audioCount,
            videoCount = videoCount,
            streamUrlCount = streamUrlCount,
            pcHostCount = pcHostCount,
            localCount = localCount,
            continueWatchingCount = continueWatchingCount,
            favoritesCount = favoritesCount,
            watchedCount = watchedCount,
            watchlistCount = watchlistCount,
            totalDurationHours = totalDurationMs / 3_600_000.0,
            watchedDurationHours = watchedDurationMs / 3_600_000.0,
            connectedServersCount = servers.count { it.isActive }
        )
    }

    suspend fun connectHostServer(
        rawUrl: String,
        pin: String
    ): Result<HostServerConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = LootStreamHostClient.normalizeServerUrl(rawUrl)
            val deviceId = UUID.randomUUID().toString()
            val pairResult = LootStreamHostClient.pairWithHost(
                context = context,
                rawUrl = normalizedUrl,
                pin = pin
            ).getOrThrow()

            val config = HostServerConfig(
                id = pairResult.serverId,
                name = pairResult.serverName,
                serverUrl = normalizedUrl,
                hostPlatform = pairResult.hostPlatform,
                accessToken = pairResult.accessToken,
                deviceId = deviceId,
                serverVersion = pairResult.serverVersion,
                lastSyncedAt = 0L,
                itemCount = 0,
                isActive = true
            )
            serverDao.upsert(MediaServerEntity.fromDomain(config))
            val syncedCount = syncServer(config.id).getOrElse { 0 }
            config.copy(
                lastSyncedAt = System.currentTimeMillis(),
                itemCount = syncedCount
            )
        }
    }

    suspend fun syncServer(serverId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = serverDao.getById(serverId) ?: error("Host configuration not found")
            val server = entity.toDomain()
            val fetchedItems = LootStreamHostClient.fetchLibraryItems(context, server).getOrThrow()

            // Preserve local watchlist flags across server refreshes
            val existingById = itemDao.getAllOnce().associateBy { it.id }
            val merged = fetchedItems.map { item ->
                val prev = existingById[item.id]
                val preservedWatchlist = prev?.isWatchlist ?: item.isWatchlist
                val preservedAddedAt = prev?.addedAt ?: item.addedAt
                MediaItemEntity.fromDomain(
                    item.copy(
                        isWatchlist = preservedWatchlist,
                        addedAt = preservedAddedAt
                    )
                )
            }

            itemDao.deleteByServerId(server.id)
            if (merged.isNotEmpty()) {
                itemDao.upsertAll(merged)
            }
            serverDao.upsert(
                entity.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    itemCount = merged.size
                )
            )
            merged.size
        }
    }

    suspend fun syncAllServers(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val servers = serverDao.getAllOnce().filter { it.isActive }
            var totalSynced = 0
            for (srv in servers) {
                totalSynced += syncServer(srv.id).getOrThrow()
            }
            totalSynced
        }
    }

    suspend fun removeServer(serverId: String) = withContext(Dispatchers.IO) {
        itemDao.deleteByServerId(serverId)
        serverDao.deleteById(serverId)
    }

    suspend fun importLocalMedia(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0
        val resolver = context.contentResolver
        val imported = mutableListOf<MediaItemEntity>()
        val thumbDir = File(context.cacheDir, "local_thumbs").apply { mkdirs() }

        for (uri in uris) {
            runCatching {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            var displayName = ""
            var sizeBytes = 0L
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            val mimeType = resolver.getType(uri).orEmpty()
            val cleanFileTitle = displayName
                .substringBeforeLast('.')
                .replace('_', ' ')
                .ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "Local Media" }

            var durationMs = 0L
            var metaTitle = ""
            var artist = ""
            var album = ""
            var codecBadge = ""
            var thumbPath = ""

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0

                val isAudio = mimeType.startsWith("audio/") || (width == 0 && height == 0 && album.isNotBlank())
                val ext = displayName.substringAfterLast('.', "").uppercase()
                codecBadge = when {
                    maxOf(width, height) >= 3800 -> "4K"
                    maxOf(width, height) >= 1900 -> "1080P"
                    maxOf(width, height) >= 1200 -> "720P"
                    ext.isNotBlank() && ext.length <= 5 -> ext
                    isAudio -> "AUDIO"
                    else -> "LOCAL"
                }

                val embeddedPic = retriever.embeddedPicture
                val frameBitmap = if (embeddedPic == null && !isAudio) {
                    runCatching { retriever.getFrameAtTime(1_000_000L) }.getOrNull()
                } else null

                val hashKey = uri.toString().hashCode().toString().replace("-", "m")
                val thumbFile = File(thumbDir, "thumb_$hashKey.jpg")
                if (embeddedPic != null) {
                    FileOutputStream(thumbFile).use { it.write(embeddedPic) }
                    thumbPath = Uri.fromFile(thumbFile).toString()
                } else if (frameBitmap != null) {
                    FileOutputStream(thumbFile).use { out ->
                        frameBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    frameBitmap.recycle()
                    thumbPath = Uri.fromFile(thumbFile).toString()
                }

                val mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO
                val title = metaTitle.ifBlank { cleanFileTitle }
                val subtitle = when {
                    isAudio -> listOf(artist, album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Device Audio" }
                    else -> listOfNotNull(
                        codecBadge.takeIf { it.isNotBlank() },
                        "Device Storage"
                    ).joinToString(" • ")
                }

                val stableId = "local_${uri.toString().hashCode().toUInt()}"
                imported.add(
                    MediaItemEntity.fromDomain(
                        MediaItem(
                            id = stableId,
                            title = title,
                            subtitle = subtitle,
                            overview = if (displayName.isNotBlank()) "Imported from device: $displayName" else "Local device media file",
                            mediaType = mediaType,
                            sourceType = MediaSourceType.LOCAL,
                            streamUrl = uri.toString(),
                            posterUrl = thumbPath,
                            durationMs = durationMs,
                            codecBadge = codecBadge,
                            addedAt = System.currentTimeMillis(),
                            fileSizeBytes = sizeBytes
                        )
                    )
                )
            } catch (_: Throwable) {
                val isAudio = mimeType.startsWith("audio/")
                val ext = displayName.substringAfterLast('.', "").uppercase()
                val stableId = "local_${uri.toString().hashCode().toUInt()}"
                imported.add(
                    MediaItemEntity.fromDomain(
                        MediaItem(
                            id = stableId,
                            title = cleanFileTitle,
                            subtitle = if (isAudio) "Device Audio" else "Device Video",
                            overview = "Imported from device storage",
                            mediaType = if (isAudio) MediaType.AUDIO else MediaType.VIDEO,
                            sourceType = MediaSourceType.LOCAL,
                            streamUrl = uri.toString(),
                            posterUrl = "",
                            durationMs = durationMs,
                            codecBadge = ext.ifBlank { if (isAudio) "AUDIO" else "LOCAL" },
                            addedAt = System.currentTimeMillis(),
                            fileSizeBytes = sizeBytes
                        )
                    )
                )
            } finally {
                runCatching { retriever.release() }
            }
        }

        if (imported.isNotEmpty()) {
            itemDao.upsertAll(imported)
        }
        imported.size
    }

    suspend fun addDirectStreamUrl(
        title: String,
        streamUrl: String,
        mediaType: MediaType,
        posterUrl: String = "",
        subtitle: String = ""
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedUrl = streamUrl.trim()
            require(trimmedUrl.startsWith("http://", true) || trimmedUrl.startsWith("https://", true) || trimmedUrl.startsWith("rtsp://", true)) {
                "Stream URL must start with http://, https://, or rtsp://"
            }
            val cleanTitle = title.trim().ifBlank {
                trimmedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Custom Stream" }
            }
            val lower = trimmedUrl.lowercase()
            val codecBadge = when {
                lower.contains(".m3u8") -> "HLS"
                lower.contains(".mpd") -> "DASH"
                lower.contains(".mp4") -> "MP4"
                lower.contains(".mkv") -> "MKV"
                lower.contains(".mp3") -> "MP3"
                lower.contains(".flac") -> "FLAC"
                else -> "STREAM"
            }
            val item = MediaItem(
                id = "stream_${UUID.randomUUID()}",
                title = cleanTitle,
                subtitle = subtitle.trim().ifBlank { "$codecBadge Network Stream" },
                overview = "Direct network stream: $trimmedUrl",
                mediaType = mediaType,
                sourceType = MediaSourceType.STREAM_URL,
                streamUrl = trimmedUrl,
                posterUrl = posterUrl.trim(),
                codecBadge = codecBadge,
                addedAt = System.currentTimeMillis()
            )
            itemDao.upsert(MediaItemEntity.fromDomain(item))
            item
        }
    }

    suspend fun updatePlaybackProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
        incrementPlayCount: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val current = itemDao.getById(itemId)?.toDomain() ?: return@withContext
        val effectiveDuration = if (durationMs > 0L) durationMs else current.durationMs
        val progress = if (effectiveDuration > 0L) positionMs.toFloat() / effectiveDuration.toFloat() else 0f
        val newStatus = when {
            effectiveDuration > 0L && progress >= 0.90f -> WatchStatus.WATCHED
            positionMs > 5_000L -> WatchStatus.IN_PROGRESS
            else -> current.watchStatus
        }
        val finalPosition = if (newStatus == WatchStatus.WATCHED && effectiveDuration > 0L && progress >= 0.95f) 0L else positionMs

        itemDao.updatePlaybackProgress(
            id = itemId,
            positionMs = finalPosition,
            durationMs = effectiveDuration,
            watchStatus = newStatus.name,
            lastPlayedAt = System.currentTimeMillis(),
            playIncrement = if (incrementPlayCount) 1 else 0
        )

        if (current.sourceType == MediaSourceType.PC_HOST && !current.serverId.isNullOrBlank() && !current.remoteItemId.isNullOrBlank()) {
            val server = serverDao.getById(current.serverId)?.toDomain()
            if (server != null) {
                LootStreamHostClient.reportPlaybackProgress(
                    context = context,
                    server = server,
                    remoteItemId = current.remoteItemId,
                    positionMs = finalPosition,
                    durationMs = effectiveDuration,
                    watchStatus = newStatus
                )
            }
        }
    }

    suspend fun toggleFavorite(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val next = !item.isFavorite
        itemDao.updateFavorite(item.id, next)
        if (item.sourceType == MediaSourceType.PC_HOST && !item.serverId.isNullOrBlank() && !item.remoteItemId.isNullOrBlank()) {
            val server = serverDao.getById(item.serverId)?.toDomain()
            if (server != null) {
                LootStreamHostClient.markFavorite(context, server, item.remoteItemId, next)
            }
        }
        item.copy(isFavorite = next)
    }

    suspend fun toggleWatchlist(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val next = !item.isWatchlist
        itemDao.updateWatchlist(item.id, next)
        item.copy(isWatchlist = next)
    }

    suspend fun updateWatchStatus(item: MediaItem, status: WatchStatus): MediaItem = withContext(Dispatchers.IO) {
        itemDao.updateWatchStatus(item.id, status.name)
        if (item.sourceType == MediaSourceType.PC_HOST && !item.serverId.isNullOrBlank() && !item.remoteItemId.isNullOrBlank()) {
            val server = serverDao.getById(item.serverId)?.toDomain()
            if (server != null && (status == WatchStatus.WATCHED || status == WatchStatus.UNWATCHED)) {
                LootStreamHostClient.markWatchStatus(context, server, item.remoteItemId, status)
            }
        }
        item.copy(watchStatus = status)
    }

    suspend fun deleteMediaItem(id: String) = withContext(Dispatchers.IO) {
        itemDao.deleteById(id)
    }

    suspend fun clearLocalAndStreamMedia() = withContext(Dispatchers.IO) {
        itemDao.deleteAllNonServerMedia()
    }
}
