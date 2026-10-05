package com.jing.sakura.home

import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.data.AnimeData
import com.jing.sakura.room.VideoHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUpNextPolicyTest {
    @Test
    fun mergesFollowedFavouritesWithNewEpisodesByRecency() {
        val watched = mapOf("binge" to 300L, "weekly" to 200L, "old" to 100L)
        val upNext = buildHomeUpNext(
            inProgress = listOf(anime("binge"), anime("old")),
            favorites = listOf(
                anime("cycani:weekly", badge = "新・第08集"),
                anime("wishlist", badge = "新・第03集"),
                anime("quiet")
            ),
            lastWatchedAt = { watched[animeIdentity(it.id)] ?: 0L }
        )

        assertEquals(listOf("binge", "cycani:weekly", "old"), upNext.map(AnimeData::id))
    }

    @Test
    fun inProgressTitlesPickUpTheirFavouriteBadge() {
        val upNext = buildHomeUpNext(
            inProgress = listOf(anime("show")),
            favorites = listOf(anime("cycani:SHOW", badge = "新・第05集")),
            lastWatchedAt = { 1L }
        )

        assertEquals(listOf("新・第05集"), upNext.map(AnimeData::newEpisodeBadge))
    }

    @Test
    fun shelfIsCapped() {
        val upNext = buildHomeUpNext(
            inProgress = (1..20).map { anime("a$it") },
            favorites = emptyList(),
            lastWatchedAt = { 0L }
        )

        assertEquals(HOME_UP_NEXT_LIMIT, upNext.size)
        assertEquals("a1", upNext.first().id)
    }

    @Test
    fun lastWatchedUsesTheNewestOfCloudAndLocalHistory() {
        val lookup = lastWatchedLookup(
            remoteHistory = listOf(
                TvHistoryItem(animeId = "cycani:7", anime = anime("7"), updatedAtEpochMs = 500L)
            ),
            localHistory = listOf(
                history(animeId = "7", updateTime = 900L),
                history(animeId = "7", updateTime = 400L),
                history(animeId = "8", updateTime = 50L)
            )
        )

        assertEquals(900L, lookup(anime("cycani:7")))
        assertEquals(50L, lookup(anime("8")))
        assertEquals(0L, lookup(anime("9")))
    }

    private fun anime(id: String, badge: String = "") =
        AnimeData(id = id, url = "", title = id, sourceId = "cycani", newEpisodeBadge = badge)

    private fun history(animeId: String, updateTime: Long) = VideoHistoryEntity(
        episodeId = "$animeId:$updateTime",
        sourceId = "cycani",
        animeName = animeId,
        animeId = animeId,
        lastEpisodeName = "第1集",
        updateTime = updateTime
    )
}
