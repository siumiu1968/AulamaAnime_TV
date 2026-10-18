package com.jing.sakura.detail

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jing.sakura.data.AnimeDetailPageData
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Reusable catalogue data only. Watch progress and favourites remain in their own stores. */
class DetailPageCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val usableSpace: () -> Long = { directory.usableSpace.takeIf { it > 0 } ?: directory.parentFile?.usableSpace ?: 0L }
) {
    data class Entry(
        val detail: AnimeDetailPageData,
        val savedAt: Long,
        val enrichedAt: Long,
        val relatedVersion: Int = 0
    ) {
        fun needsEnrichment(nowMs: Long): Boolean =
            relatedVersion != RELATED_VERSION || enrichedAt <= 0 || nowMs < enrichedAt || nowMs - enrichedAt >= ENRICHMENT_AGE_MS

        fun isBehind(expectedEpisodes: Int): Boolean = expectedEpisodes > 0 &&
            expectedEpisodes > (detail.playLists.getOrNull(detail.defaultPlayListIndex)?.episodeList?.size ?: 0)

        fun mergeCore(fresh: AnimeDetailPageData, sourceId: String): AnimeDetailPageData =
            fresh.copy(otherAnimeList = when {
                fresh.relatedReady -> fresh.otherAnimeList
                relatedVersion == RELATED_VERSION -> mergeRelatedAnime(detail.otherAnimeList, fresh.otherAnimeList, sourceId)
                else -> emptyList()
            }, relatedReady = fresh.relatedReady || relatedVersion == RELATED_VERSION)

        fun forDisplay(): Entry = copy(detail = detail.copy(
            otherAnimeList = if (relatedVersion == RELATED_VERSION) detail.otherAnimeList else emptyList(),
            relatedReady = relatedVersion == RELATED_VERSION
        ))
    }

    private data class Rating(val score: Int, val savedAt: Long)
    private val gson = Gson()
    private val memory = ConcurrentHashMap<String, Entry>()

    fun peek(sourceId: String, animeId: String): Entry? =
        memory[key("detail", sourceId, animeId)]?.takeIf { validAge(it.savedAt, DETAIL_AGE_MS) }?.forDisplay()

    @Synchronized
    fun get(sourceId: String, animeId: String): Entry? {
        trim()
        peek(sourceId, animeId)?.let { return it }
        val key = key("detail", sourceId, animeId)
        val entry = runCatching {
            read(key, Entry::class.java)?.takeIf {
                validAge(it.savedAt, DETAIL_AGE_MS) && it.detail.animeId == animeId &&
                    it.detail.animeName.isNotBlank() && it.detail.playLists.isNotEmpty()
            }
        }.getOrNull() ?: return null
        remember(key, entry)
        return entry.forDisplay()
    }

    @Synchronized
    fun put(sourceId: String, detail: AnimeDetailPageData, enrichedAt: Long = 0L, checkedAt: Long = now()) {
        if (detail.animeId.isBlank() || detail.playLists.isEmpty()) return
        val key = key("detail", sourceId, detail.animeId)
        val entry = Entry(detail.copy(otherAnimeList = detail.otherAnimeList.take(24),
            lastPlayEpisodePosition = 0 to 0), checkedAt, enrichedAt, if (enrichedAt > 0) RELATED_VERSION else 0)
        remember(key, entry)
        persistentDetail(entry.detail)?.let { write(key, entry.copy(detail = it)) }
    }

    @Synchronized
    fun rating(accountEmail: String, animeId: String): Int? {
        if (accountEmail.isBlank()) return null
        trim()
        val entry = read(key("rating", accountEmail.trim().lowercase(Locale.ROOT), animeId), Rating::class.java)
        return entry?.takeIf { validAge(it.savedAt, RATING_AGE_MS) && it.score in 0..5 }?.score
    }

    @Synchronized
    fun putRating(accountEmail: String, animeId: String, score: Int) {
        if (accountEmail.isBlank() || score !in 0..5) return
        write(key("rating", accountEmail.trim().lowercase(Locale.ROOT), animeId), Rating(score, now()))
    }

    private fun validAge(savedAt: Long, maxAge: Long): Boolean =
        savedAt > 0 && now() >= savedAt && now() - savedAt < maxAge

    private fun persistentDetail(detail: AnimeDetailPageData): AnimeDetailPageData? = runCatching {
        detail.copy(playLists = detail.playLists.map { line ->
            line.copy(episodeList = line.episodeList.map episode@{ episode ->
                val id = episode.episodeId
                if (id.startsWith("http", ignoreCase = true)) return null
                if (!id.startsWith("cycapi:")) return@episode episode
                val json = JsonParser.parseString(checkNotNull(id.removePrefix("cycapi:").decodeBase64()).utf8()).asJsonObject
                if (!json.get("u")?.asString.isNullOrBlank()) {
                    // Persist stable section/title/provider references, never a signed media URL.
                    val resolvable = listOf("ws", "wt", "xp").any { !json.get(it)?.asString.isNullOrBlank() }
                    if (!resolvable) return null
                    json.addProperty("u", "")
                }
                episode.copy(episodeId = "cycapi:" + json.toString().encodeUtf8().base64())
            })
        })
    }.getOrNull()

    private fun remember(key: String, entry: Entry) {
        memory[key] = entry
        if (memory.size > MAX_MEMORY_ENTRIES) {
            memory.entries.minByOrNull { it.value.savedAt }?.let { memory.remove(it.key, it.value) }
        }
    }

    private fun <T> read(key: String, type: Class<T>): T? = runCatching {
        val file = File(directory, "$key.json")
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        gson.fromJson(file.readText(), type)
    }.getOrNull()

    private fun write(key: String, value: Any) {
        // A full disk or corrupt disposable cache must never prevent opening a title.
        runCatching {
            directory.mkdirs()
            trim()
            if (usableSpace() < MIN_FREE_BYTES) return
            val json = gson.toJson(value)
            if (json.toByteArray().size > MAX_FILE_BYTES) return
            val target = File(directory, "$key.json")
            val temporary = File.createTempFile("detail-", ".tmp", directory)
            try {
                temporary.writeText(json)
                check(temporary.renameTo(target))
                target.setLastModified(now())
            } finally {
                temporary.delete()
            }
            trim()
        }
    }

    /** Only this disposable cache directory is touched; no account/history data is removed. */
    private fun trim() {
        runCatching {
            val files = directory.listFiles { file -> file.extension == "json" }.orEmpty()
            if (usableSpace() < MIN_FREE_BYTES) {
                files.forEach(File::delete)
                return
            }
            val retained = files.filter { file ->
                val age = now() - file.lastModified()
                if (age < 0 || age >= DETAIL_AGE_MS || file.length() > MAX_FILE_BYTES) {
                    file.delete()
                    false
                } else true
            }.sortedByDescending(File::lastModified)
            var total = 0L
            retained.forEachIndexed { index, file ->
                total += file.length()
                if (index >= MAX_ENTRIES || total > MAX_DISK_BYTES) file.delete()
            }
        }
    }

    private fun key(kind: String, scope: String, animeId: String): String =
        MessageDigest.getInstance("SHA-256").digest("$kind\u0000$scope\u0000$animeId".toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val RELATED_VERSION = 1
        private const val MAX_ENTRIES = 120
        private const val MAX_MEMORY_ENTRIES = 16
        private const val MAX_FILE_BYTES = 220_000L
        private const val MAX_DISK_BYTES = 8L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 64L * 1024L * 1024L
        private const val DETAIL_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val ENRICHMENT_AGE_MS = 6L * 60L * 60L * 1_000L
        private const val RATING_AGE_MS = 15L * 60L * 1_000L
    }
}

internal fun detailExpectedEpisodeCount(episodeInfo: String): Int =
    Regex("""^\s*(?:更新至\s*第?|全|第)?\s*(\d{1,4})(?:集|\s|[·・•]|$)""")
        .find(episodeInfo)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
