package com.jing.sakura.repo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.extend.TraditionalChinese
import com.jing.sakura.extend.getHtml
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * Resolves a Cycani Web episode only when title, year and episode identity are
 * unambiguous.  Callers must retain their legacy source as the fallback.
 */
internal class CycaniWebPlaybackResolver(private val client: OkHttpClient) {

    suspend fun resolve(request: CycaniWebEpisodeRequest): String {
        require(request.title.isNotBlank() && request.year.isNotBlank()) {
            "Cycani Web matching requires title and year"
        }
        val match = CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
            expectedTitle = request.title,
            expectedYear = request.year,
            candidates = dataList(get("/videos/search", mapOf(
                "q" to request.title,
                "page" to "1",
                "page_size" to "20"
            )))
        ) ?: error("Cycani Web has no unique title and year match")

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
            val sections = dataList(get("/videos/${match.id}/sections", mapOf(
                "player_code" to source.code,
                "page" to "1",
                "page_size" to "100"
            )))
            val section = CycaniWebPlaybackPolicy.selectExactEpisode(
                request.episodeLabel,
                sections
            ) ?: continue
            val play = dataObject(get("/sections/${section.id}/play-url"))
            val url = play.string("url")
            if (CycaniWebPlaybackPolicy.isTrustedPlaybackUrl(url)) return url
        }
        error("Cycani Web has no exact matching episode")
    }

    private suspend fun get(path: String, parameters: Map<String, String> = emptyMap()): JsonObject {
        val url = WEB_API_BASE.toHttpUrl().newBuilder()
            .addPathSegments(path.removePrefix("/"))
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
            .toString()
        val root = JsonParser.parseString(client.getHtml(url) {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept", "application/json")
            header("X-App-Name", "cyc_web")
            header("X-App-Version", "cycweb")
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

    private companion object {
        const val WEB_API_BASE = "https://www.cycani.org/api/"
        const val WEB_USER_AGENT = "AulamaAnimeTV/3.0"
    }
}

internal data class CycaniWebEpisodeRequest(
    val title: String,
    val year: String,
    val episodeLabel: String,
    val sourceLine: String
)

internal data class CycaniWebMatch(val id: String, val title: String, val year: String)
internal data class CycaniWebSource(val code: String, val title: String)
internal data class CycaniWebSection(val id: String, val title: String)

internal object CycaniWebPlaybackPolicy {
    fun uniqueTitleYearMatch(
        expectedTitle: String,
        expectedYear: String,
        candidates: List<JsonObject>
    ): CycaniWebMatch? {
        val title = canonical(expectedTitle)
        val year = yearOf(expectedYear)
        if (title.isBlank() || year.isBlank()) return null
        val matches = candidates.mapNotNull { candidate ->
            val candidateYear = yearOf(candidate.string("year"))
            val candidateTitles = listOf(
                candidate.string("title"), candidate.string("name"), candidate.string("subtitle"),
                candidate.string("english_title"), candidate.string("englishTitle")
            ).map(::canonical)
            val id = candidate.string("video_id").ifBlank { candidate.string("id") }
            if (id.isBlank() || candidateYear != year || title !in candidateTitles) null
            else CycaniWebMatch(id, candidate.string("title").ifBlank { candidate.string("name") }, candidateYear)
        }.distinctBy { it.id }
        return matches.singleOrNull()
    }

    fun orderSources(rows: List<JsonObject>, requestedSourceLine: String): List<CycaniWebSource> {
        val expected = canonical(requestedSourceLine)
        return rows.mapNotNull { row ->
            val code = row.string("code")
            if (!code.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) null
            else CycaniWebSource(code, row.string("title").ifBlank { row.string("name") }.ifBlank { code })
        }.sortedBy { source -> if (expected.isNotBlank() && canonical(source.title) == expected) 0 else 1 }
    }

    fun selectExactEpisode(expectedLabel: String, rows: List<JsonObject>): CycaniWebSection? {
        val expectedNumber = episodeNumber(expectedLabel)
        val expectedTitle = canonical(expectedLabel)
        val matches = rows.mapNotNull { row ->
            val id = row.string("id")
            val title = row.string("title")
            if (id.isBlank() || title.isBlank()) null else CycaniWebSection(id, title)
        }.filter { section ->
            if (expectedNumber != null) episodeNumber(section.title) == expectedNumber
            else expectedTitle.isNotBlank() && canonical(section.title) == expectedTitle
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

    private fun canonical(value: String): String = value.normalizeNfkc()
        .let(TraditionalChinese::convert)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun yearOf(value: String): String = Regex("(?:19|20)\\d{2}").find(value)?.value.orEmpty()

    private fun episodeNumber(value: String): String? = Regex(
        "(?:ep(?:isode)?\\s*[:#._-]?|第\\s*)?0*(\\d+(?:\\.\\d+)?)\\s*(?:集|話|话)?",
        RegexOption.IGNORE_CASE
    ).find(value.normalizeNfkc())?.groupValues?.getOrNull(1)

    private fun String.normalizeNfkc(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)
    private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private const val TV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36"
}
