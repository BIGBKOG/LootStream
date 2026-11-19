package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CollectionsFilter
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import com.example.data.remote.LootStreamHostClient
import com.example.preferences.AppPreferences
import com.example.ui.MediaFilterEngine
import com.example.util.MediaBackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun normalizeServerUrl_addsSchemeAndTrimsTrailingSlash() {
        assertEquals("https://media.example.com", LootStreamHostClient.normalizeServerUrl("https://media.example.com/"))
        assertEquals("http://192.168.1.50:8765", LootStreamHostClient.normalizeServerUrl("192.168.1.50:8765/"))
    }

    @Test
    fun parseLibraryJson_parsesHostPayloadIntoMediaItems() {
        val server = HostServerConfig(
            id = "srv_1",
            name = "Gaming PC",
            serverUrl = "http://192.168.1.42:8765",
            hostPlatform = "Windows",
            accessToken = "tok_123",
            deviceId = "dev_1"
        )
        val json = """
            {
              "serverId": "srv_1",
              "serverName": "Gaming PC",
              "items": [
                {
                  "id": "item_movie_1",
                  "title": "Cyber Drift",
                  "subtitle": "Movies",
                  "overview": "Sci-fi feature.",
                  "mediaType": "MOVIE",
                  "streamUrl": "/api/stream/item_movie_1",
                  "durationMs": 600000,
                  "playbackPositionMs": 120000,
                  "releaseYear": 2024,
                  "communityRating": 8.4,
                  "genres": ["Sci-Fi", "Action"],
                  "codecBadge": "MP4",
                  "isFavorite": true,
                  "watchStatus": "IN_PROGRESS",
                  "playCount": 1,
                  "fileSizeBytes": 104857600
                }
              ]
            }
        """.trimIndent()

        val items = LootStreamHostClient.parseLibraryJson(json, server)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("host_srv_1_item_movie_1", item.id)
        assertEquals("item_movie_1", item.remoteItemId)
        assertEquals(MediaType.MOVIE, item.mediaType)
        assertEquals(MediaSourceType.PC_HOST, item.sourceType)
        assertEquals(600_000L, item.durationMs)
        assertEquals(120_000L, item.playbackPositionMs)
        assertEquals(WatchStatus.IN_PROGRESS, item.watchStatus)
        assertTrue(item.isFavorite)
        assertEquals("http://192.168.1.42:8765/api/stream/item_movie_1", item.streamUrl)
    }

    @Test
    fun filterEngine_filtersAndSortsMediaLibrary() {
        val sample = listOf(
            MediaItem(
                id = "1",
                title = "Beta Track",
                mediaType = MediaType.AUDIO,
                sourceType = MediaSourceType.LOCAL,
                streamUrl = "content://media/1",
                durationMs = 180_000L,
                isFavorite = true,
                watchStatus = WatchStatus.WATCHED
            ),
            MediaItem(
                id = "2",
                title = "Alpha Movie",
                mediaType = MediaType.MOVIE,
                sourceType = MediaSourceType.PC_HOST,
                streamUrl = "http://192.168.1.42:8765/api/stream/2",
                durationMs = 5_400_000L,
                communityRating = 9.1,
                watchStatus = WatchStatus.IN_PROGRESS
            )
        )

        val hostOnly = MediaFilterEngine.filterAndSort(
            items = sample,
            searchQuery = "alpha",
            sourceFilter = MediaSourceType.PC_HOST,
            mediaTypeFilter = MediaType.ALL,
            watchStatusFilter = null,
            sortOption = MediaSortOption.TITLE_ASC
        )
        assertEquals(1, hostOnly.size)
        assertEquals("2", hostOnly.first().id)

        val hostVault = MediaFilterEngine.filterCollections(
            items = sample,
            filter = CollectionsFilter.HOST_ONLY,
            searchQuery = ""
        )
        assertEquals(1, hostVault.size)
        assertEquals("2", hostVault.first().id)

        val favVault = MediaFilterEngine.filterCollections(
            items = sample,
            filter = CollectionsFilter.FAVORITES,
            searchQuery = ""
        )
        assertEquals(1, favVault.size)
        assertEquals("1", favVault.first().id)
    }

    @Test
    fun backupManager_roundTripsJsonPayload() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(context)
        prefs.userProfileName = "Streamer"

        val server = HostServerConfig(
            id = "srv_test",
            name = "Test PC Host",
            serverUrl = "http://10.0.0.5:8765",
            hostPlatform = "Windows",
            accessToken = "secret_tok",
            deviceId = "d_1"
        )
        val media = MediaItem(
            id = "url_1",
            title = "Live Stream",
            mediaType = MediaType.LIVE_STREAM,
            sourceType = MediaSourceType.STREAM_URL,
            streamUrl = "https://example.com/live.m3u8",
            isFavorite = true
        )

        val json = MediaBackupManager.buildBackupJson(context, prefs, listOf(server), listOf(media))
        val parsed = MediaBackupManager.parseBackupJson(json)
        assertEquals("Streamer", parsed.profileName)
        assertEquals(1, parsed.servers.size)
        assertEquals("Test PC Host", parsed.servers.first().name)
        assertEquals("Windows", parsed.servers.first().hostPlatform)
        assertEquals(1, parsed.items.size)
        assertEquals("https://example.com/live.m3u8", parsed.items.first().streamUrl)
        assertTrue(parsed.items.first().isFavorite)
    }
}
