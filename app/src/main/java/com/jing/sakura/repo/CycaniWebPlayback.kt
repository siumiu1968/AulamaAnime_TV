package com.jing.sakura.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.jing.sakura.data.AnimeData
import com.jing.sakura.extend.TraditionalChinese
import com.jing.sakura.extend.getHtml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * Resolves a Cycani Web episode only when title, year and episode identity are
 * unambiguous.  Callers must retain their legacy source as the fallback.
 */
internal class CycaniWebPlaybackResolver(
    private val client: OkHttpClient,
    private val apiBaseUrl: String = WEB_API_BASE,
    private val authenticatedPlayUrlResolver: suspend (String) -> String,
    private val manifestBridgeUrlResolver: (String) -> String = { "" }
) {
    private val artworkMutex = Mutex()
    @Volatile private var artworkIndex: CycaniWebArtworkIndex? = null
    @Volatile private var artworkExpiresAt: Long = 0L


    /**
     * Maps a legacy catalogue item to the current Android/Web backend by its
     * title and year. Legacy and current video IDs are separate namespaces.
     */
    suspend fun fetchDetail(request: CycaniWebTitleRequest): CycaniWebDetail {
        val match = findUniqueVideo(request.title, request.year)
        val detail = dataObject(get("/videos/${match.id}"))
        val confirmed = CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
            expectedTitle = request.title,
            expectedYear = request.year,
            candidates = listOf(detail)
        ) ?: error("Cycani Web detail identity does not match")
        check(confirmed.id == match.id) { "Cycani Web detail ID changed" }
        val resolvedId = match.id
        val sources = CycaniWebPlaybackPolicy.orderSources(dataArray(detail, "play_from"), "")
        val playLists = sources.mapNotNull { source ->
            val sections = loadSections(resolvedId, source.code)
            sections.takeIf { it.isNotEmpty() }?.let { CycaniWebPlayList(source, it) }
        }
        if (playLists.isEmpty()) error("Cycani Web has no playable episodes")

        return CycaniWebDetail(
            id = resolvedId,
            title = detail.string("title").ifBlank { detail.string("name") },
            description = detail.string("description"),
            imageUrl = detail.string("cover_url").ifBlank { detail.string("vod_pic") },
            year = detail.string("year").ifBlank { detail.string("publish_date") },
            state = detail.string("state").ifBlank { detail.string("version") },
            area = detail.string("area"),
            actor = detail.stringList("actor"),
            director = detail.stringList("director"),
            writer = detail.string("writer"),
            playLists = playLists
        )
    }

    suspend fun refreshArtwork(items: List<AnimeData>): List<AnimeData> {
        if (items.isEmpty()) return items
        val index = try {
            loadArtworkIndex()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return items
        }
        return items.map { item ->
            CycaniWebPlaybackPolicy.latestArtwork(item.title, item.year, index)
                .takeIf(String::isNotBlank)
                ?.let { latest -> item.copy(imageUrl = latest) }
                ?: item
        }
    }

    /** Resolves one Web section at the moment it is selected for playback. */
    suspend fun resolveSection(sectionId: String): String {
        require(sectionId.matches(Regex("\\d{1,12}"))) { "Cycani Web section ID is invalid" }
        val directUrl = authenticatedPlayUrlResolver(sectionId)
        if (!CycaniWebPlaybackPolicy.isTrustedPlaybackUrl(directUrl)) {
            error("Cycani Web returned an invalid playback URL")
        }
        val selectedUrl = CycaniWebPlaybackPolicy.selectMainSectionPlaybackUrl(
            directUrl = directUrl,
            manifestBridgeUrl = manifestBridgeUrlResolver(sectionId)
        )
        if (!CycaniWebPlaybackPolicy.isTrustedPlaybackUrl(selectedUrl)) {
            error("Cycani Web returned an invalid manifest bridge URL")
        }
        return selectedUrl
    }

    suspend fun resolve(request: CycaniWebEpisodeRequest): String {
        require(request.title.isNotBlank() && request.year.isNotBlank()) {
            "Cycani Web matching requires title and year"
        }
        val match = findUniqueVideo(request.title, request.year)

        val detail = dataObject(get("/videos/${match.id}"))
        val confirmed = CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
            expectedTitle = request.title,
            expectedYear = request.year,
            candidates = listOf(detail)
        ) ?: error("Cycani Web detail identity does not match")
        check(confirmed.id == match.id) { "Cycani Web detail ID changed" }

        val sources = CycaniWebPlaybackPolicy.orderSources(
            dataArray(detail, "play_from"),
            request.sourceLine
        )
        if (sources.isEmpty()) error("Cycani Web has no playable source")

        for (source in sources) {
            val sections = loadSections(match.id, source.code)
            val section = CycaniWebPlaybackPolicy.selectExactEpisode(
                request.episodeLabel,
                sections.map { item -> JsonObject().apply {
                    addProperty("id", item.id)
                    addProperty("title", item.title)
                } }
            ) ?: continue
            return resolveSection(section.id)
        }
        error("Cycani Web has no exact matching episode")
    }

    private suspend fun findUniqueVideo(title: String, year: String): CycaniWebMatch {
        require(title.isNotBlank() && year.isNotBlank()) {
            "Cycani Web matching requires title and year"
        }
        for (query in searchQueries(title)) {
            val candidates = dataList(get("/videos/search", mapOf(
                "q" to query,
                "page" to "1",
                "page_size" to "20"
            )))
            CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
                expectedTitle = title,
                expectedYear = year,
                candidates = candidates
            )?.let { return it }
        }
        error("Cycani Web has no unique title and year match")
    }

    private suspend fun loadArtworkIndex(): CycaniWebArtworkIndex {
        val now = System.currentTimeMillis()
        artworkIndex?.takeIf { artworkExpiresAt > now }?.let { return it }
        return artworkMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            artworkIndex?.takeIf { artworkExpiresAt > lockedNow }?.let { return@withLock it }
            try {
                val loaded = fetchArtworkIndex()
                artworkIndex = loaded
                artworkExpiresAt = lockedNow + ARTWORK_CACHE_TTL_MS
                loaded
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                artworkIndex?.let {
                    artworkExpiresAt = lockedNow + ARTWORK_STALE_RETRY_MS
                    return@withLock it
                }
                throw error
            }
        }
    }

    private suspend fun fetchArtworkIndex(): CycaniWebArtworkIndex {
        val candidates = withContext(Dispatchers.IO) {
            coroutineScope {
                val firstRoot = get("/videos", artworkPageParameters(1))
                val firstData = dataObject(firstRoot)
                val firstRows = dataArray(firstData, "list")
                val total = firstData.objectOrNull("pager")?.int("total") ?: firstRows.size
                val pageCount = minOf(
                    MAX_ARTWORK_PAGES,
                    maxOf(1, (total + ARTWORK_PAGE_SIZE - 1) / ARTWORK_PAGE_SIZE)
                )
                val requestLimiter = Semaphore(ARTWORK_REQUEST_CONCURRENCY)
                val remaining = (2..pageCount).map { page ->
                    async {
                        requestLimiter.withPermit {
                            dataList(get("/videos", artworkPageParameters(page)))
                        }
                    }
                }.awaitAll()
                firstRows + remaining.flatten()
            }
        }
        return withContext(Dispatchers.Default) {
            CycaniWebPlaybackPolicy.buildArtworkIndex(candidates)
        }
    }

    private fun artworkPageParameters(page: Int): Map<String, String> = mapOf(
        "zone_id" to "1",
        "page" to page.toString(),
        "page_size" to ARTWORK_PAGE_SIZE.toString()
    )

    private fun searchQueries(title: String): List<String> = listOf(
        runCatching { ZhConverterUtil.toSimple(title) }.getOrElse { title },
        title
    ).map(String::trim).filter(String::isNotBlank).distinct()

    private suspend fun loadSections(videoId: String, sourceCode: String): List<CycaniWebSection> {
        val sections = mutableListOf<CycaniWebSection>()
        val pageSize = 100
        for (page in 1..MAX_SECTION_PAGES) {
            val root = get("/videos/$videoId/sections", mapOf(
                "player_code" to sourceCode,
                "page" to page.toString(),
                "page_size" to pageSize.toString()
            ))
            val data = dataObject(root)
            val rows = dataArray(data, "list").mapNotNull { row ->
                val id = row.string("id")
                val title = row.string("title")
                if (id.matches(Regex("\\d{1,12}")) && title.isNotBlank()) CycaniWebSection(id, title) else null
            }
            sections += rows
            val pager = data.objectOrNull("pager")
            val total = pager?.int("total") ?: rows.size
            if (rows.isEmpty() || sections.size >= total) break
        }
        return sections.distinctBy { it.id }
    }

    private suspend fun get(path: String, parameters: Map<String, String> = emptyMap()): JsonObject {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.removePrefix("/"))
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
            .toString()
        val root = JsonParser.parseString(client.getHtml(url) {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept", "application/json")
            header("X-App-Name", "cyc_android")
            header("X-Time-Zone", "Asia/Shanghai")
        }).asJsonObject
        if (root.int("code") != 0) error(root.string("msg").ifBlank { "Cycani Web request failed" })
        return root
    }

    private fun dataObject(root: JsonObject): JsonObject = root.objectOrNull("data")
        ?: error("Cycani Web response has no data object")

    private fun dataList(root: JsonObject): List<JsonObject> {
        val data = dataObject(root)
        return dataArray(data, "list").ifEmpty { dataArray(root, "data") }
    }

    private fun dataArray(objectValue: JsonObject, name: String): List<JsonObject> =
        objectValue.array(name).mapNotNull { value -> value.takeIf { it.isJsonObject }?.asJsonObject }

    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.int(name: String): Int = get(name)?.let { value ->
        runCatching { value.asInt }.getOrElse { value.asString.toIntOrNull() ?: -1 }
    } ?: -1
    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonObject.stringList(name: String): List<String> {
        val value = get(name)?.takeUnless { it.isJsonNull } ?: return emptyList()
        val values = if (value.isJsonArray) {
            value.asJsonArray.mapNotNull { item ->
                item.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotBlank)
            }
        } else {
            value.asString.split(',', '、', '，').map(String::trim).filter(String::isNotBlank)
        }
        return values.distinct()
    }

    private companion object {
        const val WEB_API_BASE = "https://mapi.cycback.org/"
        const val WEB_USER_AGENT = "AulamaAnimeTV/3.0.4 (Android TV)"
        const val MAX_SECTION_PAGES = 10
        const val ARTWORK_PAGE_SIZE = 500
        const val MAX_ARTWORK_PAGES = 20
        const val ARTWORK_REQUEST_CONCURRENCY = 4
        const val ARTWORK_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val ARTWORK_STALE_RETRY_MS = 60 * 1000L
    }
}

internal data class CycaniWebTitleRequest(val title: String, val year: String)

internal data class CycaniWebEpisodeRequest(
    val title: String,
    val year: String,
    val episodeLabel: String,
    val sourceLine: String
)

internal data class CycaniWebMatch(val id: String, val title: String, val year: String)
internal data class CycaniWebSource(val code: String, val title: String)
internal data class CycaniWebSection(val id: String, val title: String)
internal data class CycaniWebPlayList(
    val source: CycaniWebSource,
    val sections: List<CycaniWebSection>
)
internal data class CycaniWebDetail(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val year: String,
    val state: String,
    val area: String,
    val actor: List<String>,
    val director: List<String>,
    val writer: String,
    val playLists: List<CycaniWebPlayList>
)

internal data class CycaniWebArtworkIndex(
    val byTitleYear: Map<String, String>,
    val byTitle: Map<String, String>
)

internal enum class CycaniPlaybackResolutionPath {
    WEB_SECTION,
    WEB_MATCH,
    DIRECT,
    UNAVAILABLE
}

internal object CycaniPlaybackResolutionPolicy {
    fun select(
        directUrl: String,
        webSectionId: String,
        hasWebRequest: Boolean
    ): CycaniPlaybackResolutionPath = when {
        webSectionId.isNotBlank() -> CycaniPlaybackResolutionPath.WEB_SECTION
        hasWebRequest -> CycaniPlaybackResolutionPath.WEB_MATCH
        directUrl.isNotBlank() -> CycaniPlaybackResolutionPath.DIRECT
        else -> CycaniPlaybackResolutionPath.UNAVAILABLE
    }
}

internal fun isRetiredCycaniOldPcUrl(value: String): Boolean {
    val url = value.trim().toHttpUrlOrNull() ?: return false
    return url.host.equals("vhub.babel.gold", ignoreCase = true) &&
        url.encodedPath.trimEnd('/') == "/oldpc"
}

internal object CycaniWebPlaybackPolicy {
    fun selectMainSectionPlaybackUrl(
        directUrl: String,
        manifestBridgeUrl: String
    ): String {
        val direct = directUrl.trim()
        val url = direct.toHttpUrlOrNull()
        val requiresManifestBridge = url?.isHttps == true &&
            url.host.equals("vhub.babel.gold", ignoreCase = true) &&
            url.encodedPath.endsWith(".m3u8", ignoreCase = true)
        return if (requiresManifestBridge) manifestBridgeUrl.trim() else direct
    }

    fun buildArtworkIndex(candidates: List<JsonObject>): CycaniWebArtworkIndex {
        val titleYearCandidates = mutableMapOf<String, MutableMap<String, String>>()
        val titleCandidates = mutableMapOf<String, MutableMap<String, String>>()
        val canonicalCache = HashMap<String, String>(candidates.size.coerceAtLeast(16))
        candidates.forEach { candidate ->
            val id = candidate.string("video_id").ifBlank { candidate.string("id") }
            val cover = candidate.string("cover_url").ifBlank { candidate.string("vod_pic") }
            val year = yearOf(candidate.string("year").ifBlank { candidate.string("publish_date") })
            val titles = listOf(
                candidate.string("title"),
                candidate.string("name"),
                candidate.string("subtitle"),
                candidate.string("english_title"),
                candidate.string("englishTitle")
            ).filter(String::isNotBlank)
                .map { title -> canonicalCache.getOrPut(title) { canonicalRaw(title) } }
                .filter(String::isNotBlank)
                .distinct()
            if (id.isBlank() || cover.isBlank()) return@forEach
            titles.forEach { title ->
                titleCandidates.getOrPut(title, ::linkedMapOf)[id] = cover
                if (year.isNotBlank()) titleYearCandidates.getOrPut("$year:$title", ::linkedMapOf)[id] = cover
            }
        }
        fun unique(rows: Map<String, Map<String, String>>): Map<String, String> = rows.mapNotNull { (key, values) ->
            values.values.singleOrNull()?.let { key to it }
        }.toMap()
        return CycaniWebArtworkIndex(
            byTitleYear = unique(titleYearCandidates),
            byTitle = unique(titleCandidates)
        )
    }

    fun latestArtwork(title: String, year: String, index: CycaniWebArtworkIndex): String {
        val canonicalTitles = canonicalVariants(title)
        if (canonicalTitles.isEmpty()) return ""
        val normalizedYear = yearOf(year)
        if (normalizedYear.isNotBlank()) {
            val matches = canonicalTitles.mapNotNull { canonicalTitle ->
                index.byTitleYear["$normalizedYear:$canonicalTitle"]
            }.distinct()
            if (matches.isNotEmpty()) return matches.singleOrNull().orEmpty()
        }
        return canonicalTitles.mapNotNull(index.byTitle::get)
            .distinct()
            .singleOrNull()
            .orEmpty()
    }

    fun uniqueTitleYearMatch(
        expectedTitle: String,
        expectedYear: String,
        candidates: List<JsonObject>
    ): CycaniWebMatch? {
        val titles = canonicalVariants(expectedTitle).toSet()
        val year = yearOf(expectedYear)
        if (titles.isEmpty() || year.isBlank()) return null
        val matches = candidates.mapNotNull { candidate ->
            val candidateYear = yearOf(candidate.string("year"))
            val candidateTitles = listOf(
                candidate.string("title"), candidate.string("name"), candidate.string("subtitle"),
                candidate.string("english_title"), candidate.string("englishTitle")
            ).filter(String::isNotBlank).map(::canonicalRaw)
            val id = candidate.string("video_id").ifBlank { candidate.string("id") }
            if (id.isBlank() || candidateYear != year || candidateTitles.none(titles::contains)) null
            else CycaniWebMatch(id, candidate.string("title").ifBlank { candidate.string("name") }, candidateYear)
        }.distinctBy { it.id }
        return matches.singleOrNull()
    }

    fun orderSources(rows: List<JsonObject>, requestedSourceLine: String): List<CycaniWebSource> {
        val expected = canonicalVariants(requestedSourceLine).toSet()
        return rows.mapNotNull { row ->
            val code = row.string("code")
            if (!code.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) null
            else CycaniWebSource(code, row.string("title").ifBlank { row.string("name") }.ifBlank { code })
        }.sortedBy { source ->
            if (expected.isNotEmpty() && canonicalRaw(source.title) in expected) 0 else 1
        }
    }

    fun selectExactEpisode(expectedLabel: String, rows: List<JsonObject>): CycaniWebSection? {
        val expectedNumber = episodeNumber(expectedLabel)
        val expectedTitles = canonicalVariants(expectedLabel).toSet()
        val matches = rows.mapNotNull { row ->
            val id = row.string("id")
            val title = row.string("title")
            if (id.isBlank() || title.isBlank()) null else CycaniWebSection(id, title)
        }.filter { section ->
            if (expectedNumber != null) episodeNumber(section.title) == expectedNumber
            else expectedTitles.isNotEmpty() && canonicalRaw(section.title) in expectedTitles
        }
        return matches.singleOrNull()
    }

    fun isTrustedPlaybackUrl(value: String): Boolean = runCatching {
        val url = value.toHttpUrl()
        url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.host.isNotBlank()
    }.getOrDefault(false)

    fun playbackHeaders(url: String, isCycaniWebSource: Boolean): Map<String, String> {
        val headers = linkedMapOf("User-Agent" to TV_USER_AGENT, "Accept" to "*/*")
        if (isCycaniWebSource && isTrustedPlaybackUrl(url)) {
            headers["Referer"] = "https://www.cycani.org/"
            headers["Origin"] = "https://www.cycani.org"
        }
        return headers
    }

    private fun canonicalRaw(value: String): String = value.normalizeNfkc()
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, "")

    private fun canonicalVariants(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return listOf(
            value,
            runCatching { ZhConverterUtil.toSimple(value) }.getOrElse { value },
            TraditionalChinese.convert(value)
        ).map(::canonicalRaw).filter(String::isNotBlank).distinct()
    }

    private fun yearOf(value: String): String = YEAR_PATTERN.find(value)?.value.orEmpty()

    private fun episodeNumber(value: String): String? = Regex(
        "(?:ep(?:isode)?\\s*[:#._-]?|第\\s*)?0*(\\d+(?:\\.\\d+)?)\\s*(?:集|話|话)?",
        RegexOption.IGNORE_CASE
    ).find(value.normalizeNfkc())?.groupValues?.getOrNull(1)

    private fun String.normalizeNfkc(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)
    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private const val TV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36"
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]")
    private val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
}
