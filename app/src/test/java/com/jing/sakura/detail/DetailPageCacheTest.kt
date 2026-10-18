package com.jing.sakura.detail

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DetailPageCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private var now = 1_800_000_000_000L
    private var freeBytes = 1024L * 1024L * 1024L
    private val directory by lazy { temporary.newFolder("cache") }
    private fun cache(root: File = directory) = DetailPageCache(root, { now }, { freeBytes })
    private fun detail(id: String = "cw:3963", episodes: Int = 1) = AnimeDetailPageData(
        animeId = id, animeName = "測試動畫", description = "簡介", imageUrl = "https://images.example/cover.jpg",
        playLists = listOf(AnimePlayList("主線路A", (1..episodes).map {
            AnimePlayListEpisode("第${it}集", "section-$it")
        })),
        otherAnimeList = listOf(AnimeData("related:2", "", "相關作品", imageUrl = "cover", sourceId = "cycani", rating = "7.8")),
        infoList = listOf("評分：7.8"), lastPlayEpisodePosition = 0 to 5
    )

    @Test fun restartRetainsRelatedIdsRatingsAndStableEpisodeReferencesWithoutWatchProgress() {
        cache().put("cycani", detail(), now)
        val restored = cache().get("cycani", "cw:3963")!!
        assertEquals("related:2", restored.detail.otherAnimeList.single().id)
        assertEquals("7.8", restored.detail.otherAnimeList.single().rating)
        assertEquals("section-1", restored.detail.playLists.single().episodeList.single().episodeId)
        assertEquals(0 to 0, restored.detail.lastPlayEpisodePosition)
        assertFalse(restored.needsEnrichment(now))
    }

    @Test fun freshCoreAddsEpisodesWithoutDiscardingRelatedScoresOrRenewingTheirExpiry() {
        val cache = cache()
        cache.put("cycani", detail(), now)
        val previous = cache.get("cycani", "cw:3963")!!
        now += 5 * HOUR
        val updated = previous.mergeCore(detail(episodes = 2).copy(otherAnimeList = emptyList()), "cycani")
        cache.put("cycani", updated, previous.enrichedAt)
        assertEquals(2, cache.get("cycani", "cw:3963")!!.detail.playLists.single().episodeList.size)
        assertEquals("7.8", cache.get("cycani", "cw:3963")!!.detail.otherAnimeList.single().rating)
        now += HOUR
        assertTrue(cache.get("cycani", "cw:3963")!!.needsEnrichment(now))
    }

    @Test fun aNewerTimetableRequiresFreshEpisodesBeforeUsingCachedDetail() {
        cache().put("cycani", detail(episodes = 1), now)
        val old = cache().get("cycani", "cw:3963")!!
        assertTrue(old.isBehind(detailExpectedEpisodeCount("02 · 週六20:35後")))
        assertFalse(old.isBehind(detailExpectedEpisodeCount("01 · 週六20:35後")))
        assertFalse(old.isBehind(detailExpectedEpisodeCount("已完結")))
        cache().put("cycani", old.mergeCore(detail(episodes = 2), "cycani"), old.enrichedAt)
        assertFalse(cache().get("cycani", "cw:3963")!!.isBehind(2))
        assertEquals(12, detailExpectedEpisodeCount("全12集"))
    }

    @Test fun titleSourcesAndViewerRatingsAreIsolatedAndRatingsExpire() {
        val cache = cache()
        cache.put("cycani", detail(), now)
        assertNull(cache.get("another-source", "cw:3963"))
        cache.putRating("A@EXAMPLE.COM", "cw:3963", 4)
        assertEquals(4, cache().rating("a@example.com", "cw:3963"))
        assertNull(cache().rating("b@example.com", "cw:3963"))
        assertNull(cache().rating("", "cw:3963"))
        now += 15 * 60_000L
        assertNull(cache().rating("a@example.com", "cw:3963"))
        assertTrue(directory.listFiles()!!.none { it.readText().contains("example.com") })
    }

    @Test fun lowSpacePurgesOnlyThisCacheAndContinuesInMemoryWithoutWriting() {
        val cache = cache()
        cache.put("cycani", detail(), now)
        val history = temporary.newFile("history.json").apply { writeText("keep") }
        freeBytes = 32L * 1024L * 1024L
        cache.put("cycani", detail("cw:2"), now)
        assertTrue(directory.listFiles()!!.isEmpty())
        assertNotNull(cache.peek("cycani", "cw:2"))
        assertNull(cache().get("cycani", "cw:2"))
        assertEquals("keep", history.readText())
    }

    @Test fun oldFilesAreActuallyRemovedAfterSevenDays() {
        cache().put("cycani", detail(), now)
        assertTrue(directory.listFiles()!!.isNotEmpty())
        now += 7 * 24 * HOUR
        assertNull(cache().get("cycani", "cw:3963"))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun corruptOrUnavailableDiskDoesNotBreakAnOpenTitle() {
        cache().put("cycani", detail(), now)
        directory.listFiles()!!.single().writeText("{broken")
        assertNull(cache().get("cycani", "cw:3963"))
        val blocked = temporary.newFile("not-a-directory")
        val memoryOnly = cache(blocked)
        memoryOnly.put("cycani", detail(), now)
        assertNotNull(memoryOnly.peek("cycani", "cw:3963"))
    }

    @Test fun storageStaysWithinEightMegabytesAndEvictsOldestEntries() {
        val cache = cache()
        repeat(48) { index ->
            cache.put("cycani", detail("cw:$index").copy(description = "x".repeat(200_000)), now)
            now++
        }
        assertTrue(directory.listFiles()!!.sumOf(File::length) <= 8L * 1024L * 1024L)
        assertNull(cache().get("cycani", "cw:0"))
        assertNotNull(cache().get("cycani", "cw:47"))
        assertNull(cache.peek("cycani", "cw:0"))
    }

    @Test fun signedMediaUrlsAreNotPersistedButStableSectionsRemainPlayable() {
        val encoded = "cycapi:" + """{"u":"https://media.example/stream?token=private","ws":"52422"}""".encodeUtf8().base64()
        val reusable = detail().copy(playLists = listOf(AnimePlayList("主線路A", listOf(AnimePlayListEpisode("第1集", encoded)))))
        cache().put("cycani", reusable, now)
        val stored = cache().get("cycani", reusable.animeId)!!.detail.playLists.single().episodeList.single().episodeId
        val payload = JsonParser.parseString(stored.removePrefix("cycapi:").decodeBase64()!!.utf8()).asJsonObject
        assertEquals("", payload.get("u").asString)
        assertEquals("52422", payload.get("ws").asString)
        assertFalse(directory.listFiles()!!.single().readText().contains("token=private"))
    }

    private companion object { const val HOUR = 60L * 60L * 1_000L }
}
