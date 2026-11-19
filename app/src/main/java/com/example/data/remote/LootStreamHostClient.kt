package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class HostPairResult(
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val hostPlatform: String,
    val accessToken: String
)

object LootStreamHostClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun normalizeServerUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "Host address cannot be empty" }
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    suspend fun pairWithHost(
        context: Context,
        rawUrl: String,
        pin: String
    ): Result<HostPairResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(rawUrl)
            val client = ApiClient.getOkHttpClient(context)

            val payload = JSONObject().apply {
                put("pin", pin.trim())
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" })
            }

            val pairRequest = Request.Builder()
                .url("$baseUrl/api/pair")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(pairRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    error(
                        when (code) {
                            401 -> "Incorrect pairing PIN"
                            else -> "Host pairing failed (HTTP $code)"
                        }
                    )
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val serverId = json.optString("serverId").ifBlank { "host_${baseUrl.hashCode()}" }
                val serverName = json.optString("serverName").ifBlank { "LootStream PC Host" }
                val version = json.optString("version", "1.0.0")
                val platform = json.optString("hostPlatform", "Windows")
                val token = json.optString("token", "")

                HostPairResult(
                    serverId = serverId,
                    serverName = serverName,
                    serverVersion = version,
                    hostPlatform = platform,
                    accessToken = token
                )
            }
        }
    }

    suspend fun fetchLibraryItems(
        context: Context,
        server: HostServerConfig
    ): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(server.serverUrl)
            val client = ApiClient.getOkHttpClient(context)

            val builder = Request.Builder()
                .url("$baseUrl/api/library")
                .get()
            if (server.accessToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${server.accessToken}")
            }

            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Host library sync failed (HTTP ${response.code})")
                }
                val body = response.body?.string().orEmpty()
                parseLibraryJson(body, server)
            }
        }
    }

    fun parseLibraryJson(rawJson: String, server: HostServerConfig): List<MediaItem> {
        val baseUrl = normalizeServerUrl(server.serverUrl)
        val root = JSONObject(rawJson)
        val array = root.optJSONArray("items") ?: JSONArray()
        val now = System.currentTimeMillis()
        val results = ArrayList<MediaItem>(array.length())

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val remoteId = obj.optString("id")
            if (remoteId.isBlank()) continue

            val mediaType = runCatching {
                MediaType.valueOf(obj.optString("mediaType", "VIDEO").uppercase())
            }.getOrDefault(MediaType.VIDEO)

            val watchStatus = runCatching {
                WatchStatus.valueOf(obj.optString("watchStatus", "UNWATCHED").uppercase())
            }.getOrDefault(WatchStatus.UNWATCHED)

            val relStream = obj.optString("streamUrl").ifBlank { "/api/stream/$remoteId" }
            val fullStreamUrl = if (relStream.startsWith("http://", true) || relStream.startsWith("https://", true)) {
                relStream
            } else {
                val normalizedPath = if (relStream.startsWith("/")) relStream else "/$relStream"
                "$baseUrl$normalizedPath"
            }

            val rawPoster = obj.optString("posterUrl", "")
            val fullPoster = when {
                rawPoster.isBlank() -> ""
                rawPoster.startsWith("http://", true) || rawPoster.startsWith("https://", true) -> rawPoster
                else -> "$baseUrl${if (rawPoster.startsWith("/")) rawPoster else "/$rawPoster"}"
            }

            val rawBackdrop = obj.optString("backdropUrl", "")
            val fullBackdrop = when {
                rawBackdrop.isBlank() -> ""
                rawBackdrop.startsWith("http://", true) || rawBackdrop.startsWith("https://", true) -> rawBackdrop
                else -> "$baseUrl${if (rawBackdrop.startsWith("/")) rawBackdrop else "/$rawBackdrop"}"
            }

            val genresArr = obj.optJSONArray("genres")
            val genres = buildList {
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        val name = genresArr.optString(g).trim()
                        if (name.isNotEmpty()) add(name)
                    }
                }
            }

            val posMs = obj.optLong("playbackPositionMs", 0L).coerceAtLeast(0L)
            val durMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L)
            val seasonNum = if (obj.has("seasonNumber") && !obj.isNull("seasonNumber")) obj.optInt("seasonNumber") else null
            val episodeNum = if (obj.has("episodeNumber") && !obj.isNull("episodeNumber")) obj.optInt("episodeNumber") else null

            results.add(
                MediaItem(
                    id = "host_${server.id}_$remoteId",
                    title = obj.optString("title").ifBlank { "Untitled Media" },
                    subtitle = obj.optString("subtitle").ifBlank { server.name },
                    overview = obj.optString("overview", ""),
                    mediaType = mediaType,
                    sourceType = MediaSourceType.PC_HOST,
                    streamUrl = fullStreamUrl,
                    posterUrl = fullPoster,
                    backdropUrl = fullBackdrop,
                    durationMs = durMs,
                    playbackPositionMs = posMs,
                    releaseYear = if (obj.has("releaseYear") && !obj.isNull("releaseYear")) obj.optInt("releaseYear") else null,
                    communityRating = if (obj.has("communityRating") && !obj.isNull("communityRating")) obj.optDouble("communityRating") else null,
                    genres = genres,
                    codecBadge = obj.optString("codecBadge", "HOST"),
                    serverId = server.id,
                    remoteItemId = remoteId,
                    seriesName = obj.optString("seriesName").takeIf { it.isNotBlank() },
                    seasonNumber = seasonNum,
                    episodeNumber = episodeNum,
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isWatchlist = posMs > 5_000L,
                    watchStatus = watchStatus,
                    playCount = obj.optInt("playCount", 0),
                    lastPlayedAt = obj.optLong("lastPlayedAt", 0L),
                    addedAt = obj.optLong("addedAt", now - i),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L)
                )
            )
        }
        return results
    }

    suspend fun reportPlaybackProgress(
        context: Context,
        server: HostServerConfig,
        remoteItemId: String,
        positionMs: Long,
        durationMs: Long,
        watchStatus: WatchStatus
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(server.serverUrl)
            val payload = JSONObject().apply {
                put("positionMs", positionMs.coerceAtLeast(0L))
                put("durationMs", durationMs.coerceAtLeast(0L))
                put("watchStatus", watchStatus.name)
            }
            val builder = Request.Builder()
                .url("$baseUrl/api/items/$remoteItemId/progress")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (server.accessToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${server.accessToken}")
            }
            ApiClient.getOkHttpClient(context).newCall(builder.build()).execute().close()
        }
    }

    suspend fun markFavorite(
        context: Context,
        server: HostServerConfig,
        remoteItemId: String,
        favorite: Boolean
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(server.serverUrl)
            val payload = JSONObject().apply { put("isFavorite", favorite) }
            val builder = Request.Builder()
                .url("$baseUrl/api/items/$remoteItemId/favorite")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (server.accessToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${server.accessToken}")
            }
            ApiClient.getOkHttpClient(context).newCall(builder.build()).execute().close()
        }
    }

    suspend fun markWatchStatus(
        context: Context,
        server: HostServerConfig,
        remoteItemId: String,
        watchStatus: WatchStatus
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(server.serverUrl)
            val payload = JSONObject().apply { put("watchStatus", watchStatus.name) }
            val builder = Request.Builder()
                .url("$baseUrl/api/items/$remoteItemId/watched")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (server.accessToken.isNotBlank()) {
                builder.header("Authorization", "Bearer ${server.accessToken}")
            }
            ApiClient.getOkHttpClient(context).newCall(builder.build()).execute().close()
        }
    }
}
