package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class HostPairResult(
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val hostPlatform: String,
    val accessToken: String
)

data class DiscoveredHost(
    val serverId: String,
    val serverName: String,
    val hostPlatform: String,
    val port: Int,
    val urls: List<String>,
    val requiresPin: Boolean,
    val quickCodeActive: Boolean
) {
    val primaryUrl: String get() = urls.firstOrNull().orEmpty()
}

data class QuickPairPayload(
    val url: String,
    val code: String,
    val name: String = ""
)

object LootStreamHostClient {

    const val DISCOVERY_PORT = 8766
    const val DEFAULT_HTTP_PORT = 8765
    private const val DISCOVERY_MULTICAST_GROUP = "239.255.87.66"
    private const val DISCOVERY_PROBE = "LOOTSTREAM_DISCOVER_V1"
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

    fun parsePairQrPayload(rawText: String): QuickPairPayload? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("lootstream://", ignoreCase = true)) {
            return runCatching {
                val uri = URI(text)
                val query = uri.rawQuery.orEmpty()
                val params = HashMap<String, String>()
                if (query.isNotEmpty()) {
                    for (pair in query.split('&')) {
                        val eq = pair.indexOf('=')
                        if (eq > 0) {
                            val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                            val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                            params[k] = v
                        }
                    }
                }
                val rawUrl = params["url"].orEmpty().trim()
                if (rawUrl.isBlank()) return@runCatching null
                val cleanCode = params["code"].orEmpty().filter { it.isDigit() }.take(6)
                val name = params["name"].orEmpty().trim()
                QuickPairPayload(
                    url = normalizeServerUrl(rawUrl),
                    code = cleanCode,
                    name = name
                )
            }.getOrNull()
        }

        if (text.startsWith("{") && text.endsWith("}")) {
            return runCatching {
                val obj = JSONObject(text)
                val rawUrl = obj.optString("url").trim()
                if (rawUrl.isBlank()) return@runCatching null
                val cleanCode = obj.optString("code", obj.optString("quickCode", ""))
                    .filter { it.isDigit() }
                    .take(6)
                QuickPairPayload(
                    url = normalizeServerUrl(rawUrl),
                    code = cleanCode,
                    name = obj.optString("name", obj.optString("serverName", "")).trim()
                )
            }.getOrNull()
        }

        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return runCatching {
                QuickPairPayload(
                    url = normalizeServerUrl(text),
                    code = "",
                    name = ""
                )
            }.getOrNull()
        }

        return null
    }

    suspend fun discoverLanHosts(context: Context): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val localIps = getLocalIpv4Addresses()
        val found = linkedMapOf<String, DiscoveredHost>()

        // 1. Fast UDP broadcast/multicast discovery on port 8766
        runCatching {
            val targets = linkedSetOf<InetAddress>()
            targets.add(InetAddress.getByName("255.255.255.255"))
            targets.add(InetAddress.getByName(DISCOVERY_MULTICAST_GROUP))
            targets.add(InetAddress.getByName("10.0.2.2"))
            for (iface in NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ia in iface.interfaceAddresses) {
                    val bcast = ia.broadcast
                    if (bcast is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val probeBytes = DISCOVERY_PROBE.toByteArray(Charsets.UTF_8)

                repeat(2) {
                    for (addr in targets) {
                        runCatching {
                            socket.send(DatagramPacket(probeBytes, probeBytes.size, addr, DISCOVERY_PORT))
                        }
                    }
                    Thread.sleep(60)
                }

                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + 950L
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (!msg.startsWith("{")) continue
                        val json = JSONObject(msg)
                        if (json.optString("service") != "lootstream-host") continue

                        val senderIp = packet.address?.hostAddress
                        val port = json.optInt("port", DEFAULT_HTTP_PORT)
                        val rawUrls = buildList {
                            val arr = json.optJSONArray("urls")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val u = arr.optString(i).trim()
                                    if (u.isNotBlank()) add(u)
                                }
                            }
                            if (!senderIp.isNullOrBlank()) {
                                val senderUrl = "http://$senderIp:$port"
                                if (!contains(senderUrl)) add(0, senderUrl)
                            }
                        }
                        val prioritizedUrls = prioritizeUrlsForLocalSubnets(rawUrls, localIps)
                        val host = DiscoveredHost(
                            serverId = json.optString("serverId").ifBlank { prioritizedUrls.firstOrNull().orEmpty() },
                            serverName = json.optString("serverName").ifBlank { "LootStream Host" },
                            hostPlatform = json.optString("hostPlatform", "PC"),
                            port = port,
                            urls = prioritizedUrls,
                            requiresPin = json.optBoolean("requiresPin", false),
                            quickCodeActive = json.optBoolean("quickCodeActive", true)
                        )
                        if (host.urls.isNotEmpty()) {
                            found[host.serverId] = host
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue until deadline expires
                    }
                }
            }
        }

        if (found.isNotEmpty()) {
            return@withContext found.values.toList()
        }

        // 2. HTTP /24 subnet probe fallback if UDP broadcast is blocked on router
        val subnetPrefixes = localIps
            .filter { isPrivateIpv4(it) }
            .map { ip -> ip.substringBeforeLast('.') }
            .distinct()
            .take(2)

        val candidateHosts = buildList {
            add("10.0.2.2")
            for (prefix in subnetPrefixes) {
                for (octet in 1..254) {
                    val candidate = "$prefix.$octet"
                    if (!localIps.contains(candidate)) add(candidate)
                }
            }
        }

        if (candidateHosts.isNotEmpty()) {
            val probeClient = ApiClient.getOkHttpClient(context).newBuilder()
                .connectTimeout(280, TimeUnit.MILLISECONDS)
                .readTimeout(350, TimeUnit.MILLISECONDS)
                .build()
            val semaphore = Semaphore(48)
            val probed = coroutineScope {
                candidateHosts.map { ip ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            probeHostInfo(probeClient, "http://$ip:$DEFAULT_HTTP_PORT")
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            for (h in probed) {
                found[h.serverId] = h
            }
        }

        found.values.toList()
    }

    private fun probeHostInfo(client: okhttp3.OkHttpClient, baseUrl: String): DiscoveredHost? {
        return runCatching {
            val req = Request.Builder().url("$baseUrl/api/info").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                val serverName = json.optString("serverName")
                val serverId = json.optString("serverId")
                if (serverName.isBlank() && serverId.isBlank()) return null
                DiscoveredHost(
                    serverId = serverId.ifBlank { "host_${baseUrl.hashCode()}" },
                    serverName = serverName.ifBlank { "LootStream Host" },
                    hostPlatform = json.optString("hostPlatform", "PC"),
                    port = json.optInt("port", DEFAULT_HTTP_PORT),
                    urls = listOf(baseUrl),
                    requiresPin = json.optBoolean("requiresPin", false),
                    quickCodeActive = true
                )
            }
        }.getOrNull()
    }

    private fun getLocalIpv4Addresses(): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        return ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")
    }

    private fun prioritizeUrlsForLocalSubnets(urls: List<String>, localIps: List<String>): List<String> {
        val localPrefixes = localIps.mapNotNull {
            val parts = it.split('.')
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else null
        }
        return urls.sortedByDescending { u ->
            val host = u.substringAfter("://").substringBefore(':')
            val matchesLocalSubnet = localPrefixes.any { host.startsWith(it) }
            when {
                matchesLocalSubnet -> 3
                isPrivateIpv4(host) -> 2
                else -> 1
            }
        }
    }

    suspend fun pairWithAnyDiscoveredHost(
        context: Context,
        quickCode: String,
        knownHosts: List<DiscoveredHost>,
        preferredUrl: String? = null
    ): Result<Pair<String, HostPairResult>> = withContext(Dispatchers.IO) {
        val cleanCode = quickCode.filter { it.isDigit() }
        if (cleanCode.length != 6) {
            return@withContext Result.failure(IllegalArgumentException("Enter the 6-digit Easy Login code shown on your PC"))
        }

        if (!preferredUrl.isNullOrBlank()) {
            val res = pairWithHost(context, preferredUrl, pin = "", quickCode = cleanCode)
            if (res.isSuccess) {
                return@withContext Result.success(normalizeServerUrl(preferredUrl) to res.getOrThrow())
            }
            val msg = res.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("Incorrect", ignoreCase = true)) {
                return@withContext Result.failure(res.exceptionOrNull()!!)
            }
        }

        val hosts = knownHosts.ifEmpty { discoverLanHosts(context) }
        if (hosts.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "No LootStream Host found on this Wi-Fi. Open your Linux or Windows Host on the same Wi-Fi, or scan the QR code / enter IP manually."
                )
            )
        }

        var lastAuthError: Throwable? = null
        var lastConnError: Throwable? = null

        for (host in hosts) {
            for (url in host.urls) {
                if (url == preferredUrl) continue
                val res = pairWithHost(context, url, pin = "", quickCode = cleanCode)
                if (res.isSuccess) {
                    return@withContext Result.success(normalizeServerUrl(url) to res.getOrThrow())
                }
                val err = res.exceptionOrNull()
                if (err?.message?.contains("Incorrect", ignoreCase = true) == true) {
                    lastAuthError = err
                    break
                } else if (err != null) {
                    lastConnError = err
                }
            }
        }

        Result.failure(lastAuthError ?: lastConnError ?: IllegalStateException("Could not pair using that 6-digit code"))
    }

    suspend fun pairWithHost(
        context: Context,
        rawUrl: String,
        pin: String,
        quickCode: String = ""
    ): Result<HostPairResult> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = normalizeServerUrl(rawUrl)
            val client = ApiClient.getOkHttpClient(context)
            val cleanQuickCode = quickCode.filter { it.isDigit() }

            val payload = JSONObject().apply {
                if (cleanQuickCode.isNotEmpty()) {
                    put("quickCode", cleanQuickCode)
                } else {
                    put("pin", pin.trim())
                }
                put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" })
            }

            val pairRequest = Request.Builder()
                .url("$baseUrl/api/pair")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(pairRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = response.code
                    val serverErr = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                    error(
                        when {
                            serverErr.isNotBlank() -> serverErr
                            code == 401 && cleanQuickCode.isNotEmpty() -> "Incorrect 6-digit code"
                            code == 401 -> "Incorrect pairing PIN"
                            else -> "Host pairing failed (HTTP $code)"
                        }
                    )
                }
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
