package com.jing.sakura.auth

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class AulamaPlaybackProvider(
    val id: String,
    val available: Boolean,
    val matchedTitle: String,
    val episodeCount: Int,
    val lineCount: Int,
    val reason: String
)

internal data class AulamaPlaybackSource(
    val provider: String,
    val url: String,
    val sourceLine: String
)

internal object PlaybackProviderParser {
    fun parseProviders(body: String): List<AulamaPlaybackProvider> = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("ok")?.asBoolean != true) return@runCatching emptyList()
        root.getAsJsonArray("providers").orEmpty().mapNotNull { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = row.string("id").trim().lowercase()
            if (id !in SUPPORTED_PROVIDERS) return@mapNotNull null
            AulamaPlaybackProvider(
                id = id,
                available = row.get("available")?.takeUnless { it.isJsonNull }?.asBoolean == true,
                matchedTitle = row.string("matchedTitle"),
                episodeCount = row.int("episodeCount").coerceIn(0, 5_000),
                lineCount = row.int("lineCount").coerceIn(0, 100),
                reason = row.string("reason")
            )
        }.distinctBy(AulamaPlaybackProvider::id)
    }.getOrDefault(emptyList())

    fun parseSource(body: String): AulamaPlaybackSource? = runCatching {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("ok")?.asBoolean != true) return@runCatching null
        val provider = root.string("provider").trim().lowercase()
        if (provider !in EXTERNAL_PROVIDERS) return@runCatching null
        val directUrl = sequenceOf("directUrl", "playbackUrl", "url")
            .map { field -> root.string(field) }
            .map(String::trim)
            .firstOrNull(::isSupportedDirectUrl)
            ?: return@runCatching null
        AulamaPlaybackSource(
            provider = provider,
            url = directUrl,
            sourceLine = root.string("sourceLine")
        )
    }.getOrNull()

    private fun isSupportedDirectUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty()
    }

    private fun com.google.gson.JsonObject.string(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun com.google.gson.JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

    private fun com.google.gson.JsonArray?.orEmpty(): List<com.google.gson.JsonElement> =
        this?.toList().orEmpty()

    private val SUPPORTED_PROVIDERS = setOf(
        "cycani",
        "girigiri_cht",
        "girigiri_chs",
        "sakura",
        "age"
    )
    private val EXTERNAL_PROVIDERS = setOf("girigiri_cht", "girigiri_chs", "sakura", "age")
}
