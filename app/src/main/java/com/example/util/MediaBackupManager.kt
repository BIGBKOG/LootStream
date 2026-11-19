package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaServerEntity
import com.example.data.model.CollectionsFilter
import com.example.data.model.HostServerConfig
import com.example.data.model.MainTab
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.PlayerResizeMode
import com.example.data.model.WatchStatus
import com.example.i18n.AppLanguage
import com.example.preferences.AccentTheme
import com.example.preferences.AppPreferences
import com.example.preferences.BackgroundTheme
import com.example.preferences.CardDensity
import com.example.preferences.DpiScaleMode
import com.example.preferences.TargetFrameRate
import com.example.preferences.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class BackupRestoreSummary(
    val restoredServers: Int,
    val restoredItems: Int,
    val restoredProfile: Boolean,
    val restoredPreferences: Boolean
)

object MediaBackupManager {

    private const val BACKUP_SCHEMA_VERSION = 1

    suspend fun exportBackupJson(context: Context, preferences: AppPreferences): String =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val servers = db.mediaServerDao().getAllOnce().map { it.toDomain() }
            val items = db.mediaItemDao().getAllOnce().map { it.toDomain() }
            buildBackupJson(context, preferences, servers, items)
        }

    fun buildBackupJson(
        context: Context,
        preferences: AppPreferences,
        servers: List<HostServerConfig>,
        items: List<MediaItem>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("app", "LootStream")
        root.put("exportedAt", System.currentTimeMillis())

        // Profile
        val profileObj = JSONObject().apply {
            put("name", preferences.userProfileName)
            put("avatarBase64", UserProfileManager.getAvatarBase64(context, preferences.userProfileAvatarPath))
        }
        root.put("profile", profileObj)

        // Preferences
        val prefsObj = JSONObject().apply {
            put("themeMode", preferences.themeMode.name)
            put("accentTheme", preferences.accentTheme.name)
            put("darkBackgroundTheme", preferences.darkBackgroundTheme.name)
            put("lightBackgroundTheme", preferences.lightBackgroundTheme.name)
            put("cardDensity", preferences.cardDensity.name)
            put("defaultTab", preferences.defaultTab.name)
            put("defaultSourceFilter", preferences.defaultSourceFilter.name)
            put("defaultPlaybackSpeed", preferences.defaultPlaybackSpeed.toDouble())
            put("autoPlayNext", preferences.autoPlayNext)
            put("autoSyncServersOnOpen", preferences.autoSyncServersOnOpen)
            put("showDurationBadge", preferences.showDurationBadge)
            put("showResumeBar", preferences.showResumeBar)
            put("playerResizeMode", preferences.playerResizeMode.name)
            put("collectionsFilter", preferences.collectionsFilter.name)
            put("isAnimationsEnabled", preferences.isAnimationsEnabled)
            put("isLowEndOptimization", preferences.isLowEndOptimization)
            put("targetFrameRate", preferences.targetFrameRate.name)
            put("isDpiOptimizationEnabled", preferences.isDpiOptimizationEnabled)
            put("dpiScaleMode", preferences.dpiScaleMode.name)
            put("isDataSaverMode", preferences.isDataSaverMode)
            put("appLanguage", preferences.appLanguage.code)
        }
        root.put("preferences", prefsObj)

        // Servers (host metadata + access token so user can restore on their own device cleanly)
        val serversArr = JSONArray()
        for (srv in servers) {
            serversArr.put(
                JSONObject().apply {
                    put("id", srv.id)
                    put("name", srv.name)
                    put("serverUrl", srv.serverUrl)
                    put("hostPlatform", srv.hostPlatform)
                    put("accessToken", srv.accessToken)
                    put("deviceId", srv.deviceId)
                    put("serverVersion", srv.serverVersion)
                    put("lastSyncedAt", srv.lastSyncedAt)
                    put("itemCount", srv.itemCount)
                    put("isActive", srv.isActive)
                }
            )
        }
        root.put("servers", serversArr)

        // Media Items
        val itemsArr = JSONArray()
        for (item in items) {
            itemsArr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("subtitle", item.subtitle)
                    put("overview", item.overview)
                    put("mediaType", item.mediaType.name)
                    put("sourceType", item.sourceType.name)
                    put("streamUrl", item.streamUrl)
                    put("posterUrl", item.posterUrl)
                    put("backdropUrl", item.backdropUrl)
                    put("durationMs", item.durationMs)
                    put("playbackPositionMs", item.playbackPositionMs)
                    if (item.releaseYear != null) put("releaseYear", item.releaseYear)
                    if (item.communityRating != null) put("communityRating", item.communityRating)
                    put("genres", JSONArray(item.genres))
                    put("codecBadge", item.codecBadge)
                    if (item.serverId != null) put("serverId", item.serverId)
                    if (item.remoteItemId != null) put("remoteItemId", item.remoteItemId)
                    if (item.seriesName != null) put("seriesName", item.seriesName)
                    if (item.seasonNumber != null) put("seasonNumber", item.seasonNumber)
                    if (item.episodeNumber != null) put("episodeNumber", item.episodeNumber)
                    put("isFavorite", item.isFavorite)
                    put("isWatchlist", item.isWatchlist)
                    put("watchStatus", item.watchStatus.name)
                    put("playCount", item.playCount)
                    put("lastPlayedAt", item.lastPlayedAt)
                    put("addedAt", item.addedAt)
                    put("fileSizeBytes", item.fileSizeBytes)
                }
            )
        }
        root.put("items", itemsArr)
        return root.toString(2)
    }

    suspend fun exportToUri(
        context: Context,
        preferences: AppPreferences,
        destinationUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = exportBackupJson(context, preferences)
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            } ?: error("Unable to open destination file")
            val itemCount = AppDatabase.getInstance(context).mediaItemDao().getAllOnce().size
            preferences.lastBackupTimestamp = System.currentTimeMillis()
            preferences.lastBackupStatus = "Exported $itemCount media items"
            itemCount
        }
    }

    suspend fun importFromUri(
        context: Context,
        preferences: AppPreferences,
        sourceUri: Uri
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: error("Unable to read backup file")
            restoreFromJson(context, preferences, raw)
        }
    }

    suspend fun restoreFromJson(
        context: Context,
        preferences: AppPreferences,
        rawJson: String
    ): BackupRestoreSummary = withContext(Dispatchers.IO) {
        val parsed = parseBackupJson(rawJson)
        val db = AppDatabase.getInstance(context)

        var restoredProfile = false
        parsed.profileName?.let { name ->
            preferences.userProfileName = name
            restoredProfile = true
        }
        if (!parsed.avatarBase64.isNullOrBlank()) {
            UserProfileManager.saveAvatarFromBase64(context, parsed.avatarBase64)?.let { path ->
                preferences.userProfileAvatarPath = path
                restoredProfile = true
            }
        }

        val restoredPrefs = parsed.preferencesObj?.let { p ->
            runCatching {
                p.optString("themeMode").takeIf { it.isNotBlank() }?.let {
                    preferences.themeMode = ThemeMode.valueOf(it)
                }
                p.optString("accentTheme").takeIf { it.isNotBlank() }?.let {
                    preferences.accentTheme = AccentTheme.valueOf(it)
                }
                p.optString("darkBackgroundTheme").takeIf { it.isNotBlank() }?.let {
                    preferences.darkBackgroundTheme = BackgroundTheme.valueOf(it)
                }
                p.optString("lightBackgroundTheme").takeIf { it.isNotBlank() }?.let {
                    preferences.lightBackgroundTheme = BackgroundTheme.valueOf(it)
                }
                p.optString("cardDensity").takeIf { it.isNotBlank() }?.let {
                    preferences.cardDensity = CardDensity.valueOf(it)
                }
                p.optString("defaultTab").takeIf { it.isNotBlank() }?.let {
                    preferences.defaultTab = MainTab.valueOf(it)
                }
                p.optString("defaultSourceFilter").takeIf { it.isNotBlank() }?.let {
                    preferences.defaultSourceFilter = MediaSourceType.valueOf(it)
                }
                if (p.has("defaultPlaybackSpeed")) {
                    preferences.defaultPlaybackSpeed = p.optDouble("defaultPlaybackSpeed", 1.0).toFloat()
                }
                if (p.has("autoPlayNext")) preferences.autoPlayNext = p.optBoolean("autoPlayNext", true)
                if (p.has("autoSyncServersOnOpen")) preferences.autoSyncServersOnOpen = p.optBoolean("autoSyncServersOnOpen", true)
                if (p.has("showDurationBadge")) preferences.showDurationBadge = p.optBoolean("showDurationBadge", true)
                if (p.has("showResumeBar")) preferences.showResumeBar = p.optBoolean("showResumeBar", true)
                p.optString("playerResizeMode").takeIf { it.isNotBlank() }?.let {
                    preferences.playerResizeMode = PlayerResizeMode.valueOf(it)
                }
                p.optString("collectionsFilter").takeIf { it.isNotBlank() }?.let {
                    preferences.collectionsFilter = CollectionsFilter.valueOf(it)
                }
                if (p.has("isAnimationsEnabled")) preferences.isAnimationsEnabled = p.optBoolean("isAnimationsEnabled", true)
                if (p.has("isLowEndOptimization")) preferences.isLowEndOptimization = p.optBoolean("isLowEndOptimization", false)
                p.optString("targetFrameRate").takeIf { it.isNotBlank() }?.let {
                    preferences.targetFrameRate = TargetFrameRate.valueOf(it)
                }
                if (p.has("isDpiOptimizationEnabled")) preferences.isDpiOptimizationEnabled = p.optBoolean("isDpiOptimizationEnabled", true)
                p.optString("dpiScaleMode").takeIf { it.isNotBlank() }?.let {
                    preferences.dpiScaleMode = DpiScaleMode.valueOf(it)
                }
                if (p.has("isDataSaverMode")) preferences.isDataSaverMode = p.optBoolean("isDataSaverMode", false)
                p.optString("appLanguage").takeIf { it.isNotBlank() }?.let {
                    preferences.appLanguage = AppLanguage.fromCode(it)
                }
                true
            }.getOrDefault(false)
        } ?: false

        for (srv in parsed.servers) {
            db.mediaServerDao().upsert(MediaServerEntity.fromDomain(srv))
        }
        if (parsed.items.isNotEmpty()) {
            db.mediaItemDao().upsertAll(parsed.items.map { MediaItemEntity.fromDomain(it) })
        }

        preferences.lastBackupTimestamp = System.currentTimeMillis()
        preferences.lastBackupStatus = "Restored ${parsed.items.size} items & ${parsed.servers.size} servers"

        BackupRestoreSummary(
            restoredServers = parsed.servers.size,
            restoredItems = parsed.items.size,
            restoredProfile = restoredProfile,
            restoredPreferences = restoredPrefs
        )
    }

    data class ParsedBackup(
        val profileName: String?,
        val avatarBase64: String?,
        val preferencesObj: JSONObject?,
        val servers: List<HostServerConfig>,
        val items: List<MediaItem>
    )

    fun parseBackupJson(rawJson: String): ParsedBackup {
        val root = JSONObject(rawJson)
        val profileObj = root.optJSONObject("profile")
        val profileName = profileObj?.optString("name")?.takeIf { profileObj.has("name") }
        val avatarBase64 = profileObj?.optString("avatarBase64")
        val prefsObj = root.optJSONObject("preferences")

        val servers = mutableListOf<HostServerConfig>()
        val serversArr = root.optJSONArray("servers") ?: JSONArray()
        for (i in 0 until serversArr.length()) {
            val s = serversArr.optJSONObject(i) ?: continue
            val id = s.optString("id")
            val url = s.optString("serverUrl")
            if (id.isBlank() || url.isBlank()) continue
            servers.add(
                HostServerConfig(
                    id = id,
                    name = s.optString("name", "LootStream PC Host"),
                    serverUrl = url,
                    hostPlatform = s.optString("hostPlatform", "Windows"),
                    accessToken = s.optString("accessToken", ""),
                    deviceId = s.optString("deviceId", ""),
                    serverVersion = s.optString("serverVersion", "1.0.0"),
                    lastSyncedAt = s.optLong("lastSyncedAt", 0L),
                    itemCount = s.optInt("itemCount", 0),
                    isActive = s.optBoolean("isActive", true)
                )
            )
        }

        val items = mutableListOf<MediaItem>()
        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until itemsArr.length()) {
            val o = itemsArr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val streamUrl = o.optString("streamUrl")
            if (id.isBlank() || streamUrl.isBlank()) continue

            val genresArr = o.optJSONArray("genres")
            val genres = buildList {
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        val txt = genresArr.optString(g).trim()
                        if (txt.isNotEmpty()) add(txt)
                    }
                }
            }

            items.add(
                MediaItem(
                    id = id,
                    title = o.optString("title", "Untitled Media"),
                    subtitle = o.optString("subtitle", ""),
                    overview = o.optString("overview", ""),
                    mediaType = runCatching { MediaType.valueOf(o.optString("mediaType")) }.getOrDefault(MediaType.VIDEO),
                    sourceType = runCatching { MediaSourceType.valueOf(o.optString("sourceType")) }.getOrDefault(MediaSourceType.STREAM_URL),
                    streamUrl = streamUrl,
                    posterUrl = o.optString("posterUrl", ""),
                    backdropUrl = o.optString("backdropUrl", ""),
                    durationMs = o.optLong("durationMs", 0L),
                    playbackPositionMs = o.optLong("playbackPositionMs", 0L),
                    releaseYear = if (o.has("releaseYear") && !o.isNull("releaseYear")) o.optInt("releaseYear") else null,
                    communityRating = if (o.has("communityRating") && !o.isNull("communityRating")) o.optDouble("communityRating") else null,
                    genres = genres,
                    codecBadge = o.optString("codecBadge", ""),
                    serverId = o.optString("serverId").takeIf { it.isNotBlank() },
                    remoteItemId = o.optString("remoteItemId").takeIf { it.isNotBlank() },
                    seriesName = o.optString("seriesName").takeIf { it.isNotBlank() },
                    seasonNumber = if (o.has("seasonNumber") && !o.isNull("seasonNumber")) o.optInt("seasonNumber") else null,
                    episodeNumber = if (o.has("episodeNumber") && !o.isNull("episodeNumber")) o.optInt("episodeNumber") else null,
                    isFavorite = o.optBoolean("isFavorite", false),
                    isWatchlist = o.optBoolean("isWatchlist", false),
                    watchStatus = runCatching { WatchStatus.valueOf(o.optString("watchStatus")) }.getOrDefault(WatchStatus.UNWATCHED),
                    playCount = o.optInt("playCount", 0),
                    lastPlayedAt = o.optLong("lastPlayedAt", 0L),
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                    fileSizeBytes = o.optLong("fileSizeBytes", 0L)
                )
            )
        }

        return ParsedBackup(
            profileName = profileName,
            avatarBase64 = avatarBase64,
            preferencesObj = prefsObj,
            servers = servers,
            items = items
        )
    }
}
