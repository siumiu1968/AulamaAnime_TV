package com.jing.sakura.auth

import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.jing.sakura.BuildConfig
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimePageData
import com.jing.sakura.extend.executeWithCoroutine
import com.jing.sakura.repo.isSuppressedAnime
import com.jing.sakura.remote.RemoteCommandAckStatus
import com.jing.sakura.remote.RemotePlaybackCommand
import com.jing.sakura.remote.RemotePlaybackCommandParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Calendar

class AulamaAuthRepository(
    private val client: OkHttpClient,
    private val storage: SecureAuthStorage
) {
    private val _session = MutableStateFlow(
        storage.loadSession()?.takeUnless(AuthSession::isExpired)
    )
    val session: StateFlow<AuthSession?> = _session

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

    suspend fun validateAccount(session: AuthSession): AccountValidationResult = runCatching {
        val request = authenticatedRequest("$API_BASE/device/me", session).get().build()
        client.executeWithCoroutine(request).use { response ->
            when (response.code) {
                200 -> {
                    val account = DeviceAuthParser.parseAccountPayload(response.body?.string().orEmpty())
                    val updated = session.copy(account = account)
                    storage.saveSession(updated)
                    _session.value = updated
                    AccountValidationResult.Valid(account)
                }
                401, 403 -> AccountValidationResult.Unauthorized
                else -> AccountValidationResult.Unavailable
            }
        }
    }.getOrDefault(AccountValidationResult.Unavailable)

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

    /**
     * Resolves only the authenticated playback URL. Video bytes are fetched
     * directly by the TV player from the returned CDN URL.
     */
    suspend fun fetchCycaniPlaybackUrl(sectionId: String): String? {
        if (!sectionId.matches(Regex("\\d{1,12}"))) return null
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("cycani")
            .addPathSegment("sections")
            .addPathSegment(sectionId)
            .addPathSegment("play-url")
            .build()
        val body = authenticatedBody(url) ?: return null
        return parseCycaniPlaybackUrl(body)
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
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use false
            }
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
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use false
            }
            response.isSuccessful
        }
    }

    suspend fun fetchNextRemoteCommand(): RemotePlaybackCommand? {
        val session = _session.value ?: return null
        val request = authenticatedRequest("$API_BASE/device/commands/next", session).get().build()
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("遙控指令請求失敗（${response.code}）")
            }
            RemotePlaybackCommandParser.parse(response.body?.string().orEmpty())
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
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use false
            }
            response.isSuccessful || response.code == 409
        }
    }

    suspend fun logout() {
        val existing = _session.value
        try {
            if (existing != null) {
                val request = authenticatedRequest("$API_BASE/device/session", existing).delete().build()
                runCatching { client.executeWithCoroutine(request).close() }
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
        return client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("同步請求失敗（${response.code}）")
            }
            response.body?.string().orEmpty()
        }
    }

    private suspend fun executeAuthenticatedMutation(request: Request): Boolean =
        client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use false
            }
            response.isSuccessful
        }

    private suspend fun executeSearchHistoryMutation(request: Request): List<SearchHistorySyncItem>? =
        client.executeWithCoroutine(request).use { response ->
            if (response.code == 401 || response.code == 403) {
                clearSession()
                return@use null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("搜尋紀錄同步失敗（${response.code}）")
            }
            SearchHistorySyncParser.parse(response.body?.string().orEmpty())
        }

    private suspend fun <T> execute(request: Request, parser: (Int, String, String?) -> T): T =
        client.executeWithCoroutine(request).use { response ->
            parser(
                response.code,
                response.body?.string().orEmpty(),
                response.header("Retry-After")
            )
        }

    companion object {
        private const val API_BASE = "https://aulama.org/anime/api"
        private const val NETWORK_ERROR_MESSAGE = "網絡連線失敗，請稍後再試"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun parseCycaniPlaybackUrl(body: String): String? = runCatching {
    val root = JsonParser.parseString(body).asJsonObject
    root.getAsJsonObject("data")
        ?.get("url")
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?: root.get("url")?.takeUnless { it.isJsonNull }?.asString
}.getOrNull()?.trim()?.takeIf(String::isNotBlank)
