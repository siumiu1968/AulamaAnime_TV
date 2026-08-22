package com.jing.sakura.auth

import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.jing.sakura.BuildConfig
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.extend.executeWithCoroutine
import com.jing.sakura.repo.isSuppressedAnime
import com.jing.sakura.remote.RemoteCommandAckStatus
import com.jing.sakura.remote.RemotePlaybackCommand
import com.jing.sakura.remote.RemotePlaybackCommandParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Calendar

class AulamaAuthRepository(
    initialClient: OkHttpClient,
    private val storage: SecureAuthStorage
) {
    private data class RegionRouteTransport(
        val client: OkHttpClient,
        val generation: Long
    )

    private data class RegionRouteTag(val generation: Long)

    private val _session = MutableStateFlow(
        storage.loadSession()?.takeUnless(AuthSession::isExpired)
    )
    val session: StateFlow<AuthSession?> = _session
    private val _regionBlock = MutableStateFlow<RegionBlockState?>(null)
    val regionBlock: StateFlow<RegionBlockState?> = _regionBlock
    private val regionProbeMutex = Mutex()
    @Volatile
    private var regionRouteTransport = RegionRouteTransport(initialClient, generation = 0L)

    suspend fun requestDeviceCode(nowEpochMs: Long = System.currentTimeMillis()): DeviceCodeRequestResult {
        val body = JsonObject().apply {
            addProperty("deviceId", storage.stableDeviceId())
            addProperty("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            addProperty("appVersion", BuildConfig.VERSION_NAME)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return try {
            execute(
                Request.Builder().url("$API_BASE/device/code").post(body).build()
            ) { code, responseBody, retryAfter ->
                DeviceAuthParser.parseDeviceCode(code, responseBody, retryAfter, nowEpochMs)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DeviceCodeRequestResult.Failed(NETWORK_ERROR_MESSAGE)
        }
    }

    suspend fun pollToken(
        deviceCode: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): DeviceTokenPollResult {
        val body = JsonObject().apply {
            addProperty("device_code", deviceCode)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return try {
            execute(
                Request.Builder().url("$API_BASE/device/token").post(body).build()
            ) { code, responseBody, retryAfter ->
                DeviceAuthParser.parseTokenPoll(code, responseBody, retryAfter, nowEpochMs)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DeviceTokenPollResult.Failed(NETWORK_ERROR_MESSAGE)
        }
    }

    fun authorize(result: DeviceTokenPollResult.Authorized, nowEpochMs: Long) {
        val session = AuthSession(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresAtEpochMs = nowEpochMs + result.expiresInSeconds * 1000L,
            account = result.account
        )
        storage.saveSession(session)
        _session.value = session
    }

    suspend fun validateAccount(session: AuthSession): AccountValidationResult = try {
        val request = authenticatedRequest("$API_BASE/device/me", session).get().build()
        executeResponseOnIo(request) { response, responseBody ->
            when (response.code) {
                200 -> {
                    val account = DeviceAuthParser.parseAccountPayload(responseBody)
                    val updated = session.copy(account = account)
                    storage.saveSession(updated)
                    _session.value = updated
                    AccountValidationResult.Valid(account)
                }
                401 -> AccountValidationResult.Unauthorized
                403 -> if (recordRegionBlock(response, responseBody)) {
                    AccountValidationResult.RegionBlocked
                } else {
                    AccountValidationResult.Unauthorized
                }
                else -> AccountValidationResult.Unavailable
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AccountValidationResult.Unavailable
    }

    suspend fun probeRegionAccess(
        forceFreshConnection: Boolean = false
    ): RegionAccessProbeResult = regionProbeMutex.withLock {
        try {
            val request = Request.Builder()
                .url("$API_BASE/region-probe")
                .header("Accept", "application/json")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                val transport = if (forceFreshConnection) {
                    replaceRegionRouteTransport()
                } else {
                    regionRouteTransport
                }
                transport.client
                    .executeWithCoroutine(request.taggedFor(transport))
                    .use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        val result = classifyRegionAccessProbe(
                            responseCode = response.code,
                            markerHeader = response.header(REGION_BLOCK_HEADER),
                            responseBody = responseBody
                        )
                        if (result == RegionAccessProbeResult.Blocked) {
                            recordRegionBlock(response, responseBody)
                        }
                        _regionBlock.value = regionBlockAfterProbe(_regionBlock.value, result)
                        result
                    }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            RegionAccessProbeResult.Unavailable
        }
    }

    suspend fun fetchRecommendations(): List<AnimeData> {
        return fetchTvHome().recommendations
    }

    suspend fun fetchTvHome(): TvHomePayload {
        val body = authenticatedBody("/home") ?: return TvHomePayload()
        val weekday = Calendar.getInstance().run {
            val day = get(Calendar.DAY_OF_WEEK)
            if (day == Calendar.SUNDAY) 7 else day - 1
        }
        return TvLibraryParser.parseHome(body, weekday).let { payload ->
            payload.copy(
                recommendations = payload.recommendations.filterNot(::isSuppressedAnime),
                todayUpdates = payload.todayUpdates.filterNot(::isSuppressedAnime),
                theaterItems = payload.theaterItems.filterNot(::isSuppressedAnime)
            )
        }
    }

    suspend fun fetchPublicTheaterItems(): List<AnimeData> {
        val request = Request.Builder()
            .url("$API_BASE/catalog/theaters")
            .header("Accept", "application/json")
            .get()
            .build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 403 && recordRegionBlock(response, responseBody)) {
                return@executeResponseOnIo emptyList()
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("劇場版目錄請求失敗（${response.code}）")
            }
            TvLibraryParser.parseTheaterItems(responseBody)
                .filterNot(::isSuppressedAnime)
        }
    }

    suspend fun fetchPublicSchedule(): UpdateTimeLine {
        val request = Request.Builder()
            .url("$API_BASE/schedule")
            .header("Accept", "application/json")
            .get()
            .build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 403 && recordRegionBlock(response, responseBody)) {
                throw IllegalStateException("目前網絡區域未能使用時間表")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("時間表請求失敗（${response.code}）")
            }
            val current = Calendar.getInstance().run {
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                (dayOfWeek + 5) % 7
            }
            TvLibraryParser.parseSchedule(responseBody, current).let { payload ->
                payload.copy(
                    timeline = payload.timeline.map { (label, items) ->
                        label to items.filterNot(::isSuppressedAnime)
                    }
                )
            }
        }
    }

    suspend fun fetchTvLibrary(): TvLibraryPayload {
        val favorites = fetchFavorites()
        val historyItems = authenticatedBody("/history")
            ?.let(TvLibraryParser::parseHistoryItems)
            ?.filterNot { isSuppressedAnime(it.anime) }
            .orEmpty()
        return TvLibraryPayload(
            continueWatching = historyItems.map(TvHistoryItem::anime),
            favorites = favorites,
            historyItems = historyItems
        )
    }

    suspend fun fetchTvAnimeDetail(animeId: String): TvAnimeDetailPayload {
        if (animeId.isBlank()) return TvAnimeDetailPayload()
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("detail")
            .addPathSegment(animeId)
            .build()
        val body = authenticatedBody(url) ?: return TvAnimeDetailPayload()
        return TvLibraryParser.parseAnimeDetail(body).let { payload ->
            payload.copy(
                related = payload.related.filterNot(::isSuppressedAnime),
                recommendations = payload.recommendations.filterNot(::isSuppressedAnime)
            )
        }
    }

    suspend fun fetchCatalogPage(
        filters: Map<String, String>,
        page: Int,
        limit: Int
    ): AnimePageData? {
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("list")
            .apply {
                filters.forEach { (key, value) ->
                    if (value.isNotBlank()) addQueryParameter(key, value)
                }
                addQueryParameter("catalog_mode", "current")
                addQueryParameter("page", page.coerceAtLeast(1).toString())
                addQueryParameter("limit", limit.coerceAtLeast(1).toString())
            }
            .build()
        val body = authenticatedBody(url) ?: return null
        val root = JsonParser.parseString(body).asJsonObject
        val total = root.get("total")?.asInt ?: 0
        val items = RecommendationParser.parseItems(
            root.getAsJsonArray("items") ?: JsonArray()
        ).filterNot(::isSuppressedAnime)
        return AnimePageData(
            page = page,
            hasNextPage = total > page * limit,
            animeList = items
        )
    }

    suspend fun fetchAnimeSearchPage(keyword: String, page: Int): AnimePageData? {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) {
            return AnimePageData(page = page.coerceAtLeast(1), hasNextPage = false, animeList = emptyList())
        }
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", normalizedKeyword)
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
        val body = authenticatedBody(url) ?: return null
        return RecommendationParser.parseSearchPage(body, page).let { result ->
            result.copy(animeList = result.animeList.filterNot(::isSuppressedAnime))
        }
    }

    suspend fun fetchPlaybackSegments(
        animeId: String,
        episodeId: String,
        episodeIndex: Int
    ): PlaybackSegments? {
        if (animeId.isBlank() || episodeId.isBlank() || episodeIndex < 0) return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("playback")
            .addPathSegment("segments")
            .addQueryParameter("animeId", animeId)
            .addQueryParameter("episodeId", episodeId)
            .addQueryParameter("episodeIndex", episodeIndex.toString())
            .build()
        return authenticatedBody(url)?.let(PlaybackSegmentsParser::parse)
    }

    internal suspend fun fetchPlaybackProviders(animeId: String): List<AulamaPlaybackProvider>? {
        if (animeId.isBlank()) return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("playback")
            .addPathSegment("providers")
            .addQueryParameter("animeId", animeId)
            .build()
        return authenticatedBody(url)?.let(PlaybackProviderParser::parseProviders)
    }

    internal suspend fun fetchPlaybackProviderSource(
        animeId: String,
        provider: String,
        episodeIndex: Int
    ): AulamaPlaybackSource? {
        val normalizedProvider = provider.trim().lowercase()
        if (
            animeId.isBlank() ||
            normalizedProvider !in setOf("girigiri_cht", "girigiri_chs", "sakura", "age") ||
            episodeIndex !in 0..4_999
        ) {
            return null
        }
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("playback")
            .addPathSegment("provider")
            .addQueryParameter("animeId", animeId)
            .addQueryParameter("provider", normalizedProvider)
            .addQueryParameter("episodeIndex", episodeIndex.toString())
            .build()
        return authenticatedBody(url)?.let(PlaybackProviderParser::parseSource)
    }

    /**
     * Resolves the playback URL for both signed-in and guest sessions. Video
     * bytes are fetched directly by the TV player from the returned CDN URL.
     */
    suspend fun fetchCycaniPlaybackUrl(sectionId: String): String? {
        if (!sectionId.matches(Regex("\\d{1,12}"))) return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("cycani")
            .addPathSegment("sections")
            .addPathSegment(sectionId)
            .addPathSegment("play-url")
            .build()
        val session = _session.value
        val request = session
            ?.let { authenticatedRequest(url.toString(), it) }
            ?: Request.Builder()
                .url(url)
                .header("Accept", "application/json")
        return executeResponseOnIo(request.get().build()) { response, responseBody ->
            if (response.code == 401 && session != null) {
                clearSession()
                return@executeResponseOnIo null
            }
            if (response.code == 403 && recordRegionBlock(response, responseBody)) {
                return@executeResponseOnIo null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("播放地址請求失敗（${response.code}）")
            }
            parseCycaniPlaybackUrl(responseBody)
        }
    }

    suspend fun fetchFavorites(): List<AnimeData> =
        authenticatedBody("/favorites")
            ?.let(TvLibraryParser::parseFavorites)
            ?.filterNot(::isSuppressedAnime)
            .orEmpty()

    suspend fun saveFavorite(payload: FavoritePayload): Boolean {
        val session = _session.value ?: return false
        val body = JsonObject().apply {
            addProperty("id", payload.id)
            addProperty("title", payload.title)
            addProperty("subtitle", payload.subtitle)
            addProperty("poster", payload.poster)
            add("tags", JsonArray().apply { payload.tags.forEach(::add) })
            addProperty("year", payload.year)
            addProperty("summary", payload.summary)
            addProperty("sourceTypeId", payload.sourceTypeId)
            addProperty("hits", payload.hits)
            addProperty("providerRating", payload.providerRating)
            payload.addedAt.takeIf(String::isNotBlank)?.let { addProperty("addedAt", it) }
            payload.updatedAt.takeIf(String::isNotBlank)?.let { addProperty("updatedAt", it) }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = authenticatedRequest("$API_BASE/favorites", session).post(body).build()
        return executeAuthenticatedMutation(request)
    }

    suspend fun deleteFavorite(animeId: String): Boolean {
        val session = _session.value ?: return false
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("favorites")
            .addPathSegment(animeId)
            .build()
        val request = authenticatedRequest(url.toString(), session).delete().build()
        return executeAuthenticatedMutation(request)
    }

    suspend fun syncPlaybackHistory(payload: PlaybackHistoryPayload): Boolean {
        val session = _session.value ?: return false
        val body = JsonObject().apply {
            addProperty("animeId", payload.animeId)
            addProperty("animeTitle", payload.animeTitle)
            addProperty("poster", payload.poster)
            addProperty("episodeId", payload.episodeId)
            addProperty("episodeLabel", payload.episodeLabel)
            addProperty("episodeIndex", payload.episodeIndex)
            addProperty("episodeCount", payload.episodeCount)
            addProperty("currentTime", payload.currentTimeSeconds)
            addProperty("duration", payload.durationSeconds)
            addProperty("completed", payload.completed)
            addProperty("sourceTypeId", payload.sourceTypeId)
            addProperty("playSessionId", payload.playSessionId)
            addProperty("updatedAt", payload.updatedAt)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = authenticatedRequest("$API_BASE/history", session).post(body).build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo false
            }
            if (response.code == 403) recordRegionBlock(response, responseBody)
            response.isSuccessful
        }
    }

    suspend fun fetchSearchHistory(): List<SearchHistorySyncItem>? {
        val body = authenticatedBody("/search-history") ?: return null
        return SearchHistorySyncParser.parse(body)
    }

    suspend fun saveSearchHistory(keyword: String, updatedAtEpochMs: Long): List<SearchHistorySyncItem>? {
        val session = _session.value ?: return null
        val body = JsonObject().apply {
            addProperty("keyword", keyword.trim())
            addProperty("updatedAt", CloudTimestamp.formatEpochMs(updatedAtEpochMs))
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = authenticatedRequest("$API_BASE/search-history", session).post(body).build()
        return executeSearchHistoryMutation(request)
    }

    suspend fun deleteSearchHistory(
        keyword: String,
        updatedAtEpochMs: Long = System.currentTimeMillis()
    ): List<SearchHistorySyncItem>? {
        val session = _session.value ?: return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("search-history")
            .addPathSegment(keyword.trim())
            .addQueryParameter("updatedAt", CloudTimestamp.formatEpochMs(updatedAtEpochMs))
            .build()
        val request = authenticatedRequest(url.toString(), session).delete().build()
        return executeSearchHistoryMutation(request)
    }

    suspend fun clearSearchHistory(
        updatedAtEpochMs: Long = System.currentTimeMillis()
    ): List<SearchHistorySyncItem>? {
        val session = _session.value ?: return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("search-history")
            .addQueryParameter("updatedAt", CloudTimestamp.formatEpochMs(updatedAtEpochMs))
            .build()
        val request = authenticatedRequest(url.toString(), session).delete().build()
        return executeSearchHistoryMutation(request)
    }

    suspend fun sendRemoteHeartbeat(): Boolean {
        val session = _session.value ?: return false
        val capabilities = com.google.gson.JsonArray().apply {
            add("remote_playback")
            add("auto_next")
        }
        val body = JsonObject().apply {
            add("capabilities", capabilities)
            addProperty("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            addProperty("appVersion", BuildConfig.VERSION_NAME)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = authenticatedRequest("$API_BASE/device/heartbeat", session).post(body).build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo false
            }
            if (response.code == 403) recordRegionBlock(response, responseBody)
            response.isSuccessful
        }
    }

    suspend fun fetchNextRemoteCommand(): RemotePlaybackCommand? {
        val session = _session.value ?: return null
        val request = authenticatedRequest("$API_BASE/device/commands/next", session).get().build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo null
            }
            if (response.code == 403) {
                recordRegionBlock(response, responseBody)
                return@executeResponseOnIo null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("遙控指令請求失敗（${response.code}）")
            }
            RemotePlaybackCommandParser.parse(responseBody)
        }
    }

    suspend fun acknowledgeRemoteCommand(
        commandId: String,
        status: RemoteCommandAckStatus
    ): Boolean {
        val session = _session.value ?: return false
        val body = JsonObject().apply {
            addProperty("status", status.apiValue)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = authenticatedRequest(
            "$API_BASE/device/commands/$commandId/ack",
            session
        ).post(body).build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo false
            }
            if (response.code == 403) recordRegionBlock(response, responseBody)
            response.isSuccessful || response.code == 409
        }
    }

    suspend fun logout() {
        val existing = _session.value
        try {
            if (existing != null) {
                val request = authenticatedRequest("$API_BASE/device/session", existing).delete().build()
                val transport = regionRouteTransport
                withContext(Dispatchers.IO) {
                    runCatching {
                        transport.client.executeWithCoroutine(request.taggedFor(transport)).close()
                    }
                }
            }
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        storage.clearSession()
        _session.value = null
    }

    private fun authenticatedRequest(url: String, session: AuthSession): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "${session.tokenType} ${session.accessToken}")
            .header("Accept", "application/json")

    private suspend fun authenticatedBody(path: String): String? {
        return authenticatedBody("$API_BASE$path".toHttpUrl())
    }

    private suspend fun authenticatedBody(url: okhttp3.HttpUrl): String? {
        val session = _session.value ?: return null
        val request = authenticatedRequest(url.toString(), session).get().build()
        return executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo null
            }
            if (response.code == 403 && recordRegionBlock(response, responseBody)) {
                return@executeResponseOnIo null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("同步請求失敗（${response.code}）")
            }
            responseBody
        }
    }

    private suspend fun executeAuthenticatedMutation(request: Request): Boolean =
        executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo false
            }
            if (response.code == 403) recordRegionBlock(response, responseBody)
            response.isSuccessful
        }

    private suspend fun executeSearchHistoryMutation(request: Request): List<SearchHistorySyncItem>? =
        executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 401) {
                clearSession()
                return@executeResponseOnIo null
            }
            if (response.code == 403 && recordRegionBlock(response, responseBody)) {
                return@executeResponseOnIo null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("搜尋紀錄同步失敗（${response.code}）")
            }
            SearchHistorySyncParser.parse(responseBody)
        }

    private suspend fun <T> execute(request: Request, parser: (Int, String, String?) -> T): T =
        executeResponseOnIo(request) { response, responseBody ->
            if (response.code == 403) recordRegionBlock(response, responseBody)
            parser(
                response.code,
                responseBody,
                response.header("Retry-After")
            )
        }

    private suspend fun <T> executeResponseOnIo(
        request: Request,
        handler: (okhttp3.Response, String) -> T
    ): T {
        val transport = regionRouteTransport
        return withContext(Dispatchers.IO) {
            transport.client.executeWithCoroutine(request.taggedFor(transport)).use { response ->
                handler(response, response.body?.string().orEmpty())
            }
        }
    }

    private fun Request.taggedFor(transport: RegionRouteTransport): Request = newBuilder()
        .tag(RegionRouteTag::class.java, RegionRouteTag(transport.generation))
        .build()

    private fun replaceRegionRouteTransport(): RegionRouteTransport {
        val previous = regionRouteTransport
        val replacement = RegionRouteTransport(
            client = freshRegionRouteClient(previous.client),
            generation = previous.generation + 1L
        )
        regionRouteTransport = replacement
        previous.client.dispatcher.cancelAll()
        previous.client.connectionPool.evictAll()
        previous.client.dispatcher.executorService.shutdown()
        return replacement
    }

    private fun isCurrentRegionRoute(response: okhttp3.Response): Boolean {
        val responseGeneration = response.request
            .tag(RegionRouteTag::class.java)
            ?.generation
        return isCurrentRegionRouteGeneration(
            responseGeneration = responseGeneration,
            currentGeneration = regionRouteTransport.generation
        )
    }

    private fun recordRegionBlock(
        response: okhttp3.Response,
        responseBody: String
    ): Boolean {
        if (!isCurrentRegionRoute(response)) return false
        val blocked = isRegionBlockedResponse(
            responseCode = response.code,
            markerHeader = response.header(REGION_BLOCK_HEADER),
            responseBody = responseBody
        )
        if (!blocked) return false
        _regionBlock.value = RegionBlockState(
            countryCode = blockedCountryCode(
                headerValue = response.header(REGION_COUNTRY_HEADER),
                responseBody = responseBody
            )
        )
        return true
    }

    companion object {
        private const val API_BASE = "https://aulama.org/anime/api"
        private const val NETWORK_ERROR_MESSAGE = "網絡連線失敗，請稍後再試"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun freshRegionRouteClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
    .dispatcher(Dispatcher())
    .connectionPool(ConnectionPool())
    .build()

internal fun isCurrentRegionRouteGeneration(
    responseGeneration: Long?,
    currentGeneration: Long
): Boolean = responseGeneration == null || responseGeneration == currentGeneration

internal fun parseCycaniPlaybackUrl(body: String): String? = runCatching {
    val root = JsonParser.parseString(body).asJsonObject
    root.getAsJsonObject("data")
        ?.get("url")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?: root.get("url")?.takeUnless { it.isJsonNull }?.asString
}.getOrNull()?.trim()?.takeIf(String::isNotBlank)
