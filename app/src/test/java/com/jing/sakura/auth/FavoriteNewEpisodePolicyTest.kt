package com.jing.sakura.auth

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.room.VideoHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FavoriteNewEpisodePolicyTest {
    @Test
    fun showsLatestUnseenScheduledEpisode() {
        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(anime(id = "12662", episode = "第18集")),
            schedule = schedule(anime(id = "cycani:12662", episode = "19・週五23:05後")),
            remoteHistory = emptyList(),
            localHistory = emptyList()
        ).single()

        assertEquals("19・週五23:05後", result.currentEpisode)
        assertEquals("新・第19集", result.newEpisodeBadge)
    }

    @Test
    fun hidesBadgeWhenLatestEpisodeHasRemoteViewingRecord() {
        val favorite = anime(id = "12662", episode = "第19集")
        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(favorite),
            schedule = schedule(favorite),
            remoteHistory = listOf(
                TvHistoryItem(
                    animeId = "12662",
                    anime = favorite,
                    viewedEpisodeIndexes = setOf(18)
                )
            ),
            localHistory = emptyList()
        ).single()

        assertEquals("", result.newEpisodeBadge)
    }

    @Test
    fun hidesBadgeWhenLatestEpisodeHasLocalViewingRecord() {
        val favorite = anime(id = "12662", episode = "第19集")
        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(favorite),
            schedule = schedule(favorite),
            remoteHistory = emptyList(),
            localHistory = listOf(
                VideoHistoryEntity(
                    episodeId = "episode-19",
                    sourceId = "cycani",
                    animeName = "測試動畫",
                    animeId = "cycani:12662",
                    lastEpisodeName = "第19集",
                    updateTime = 1L
                )
            )
        ).single()

        assertEquals("", result.newEpisodeBadge)
    }

    @Test
    fun keepsBadgeWhenOnlyEarlierEpisodeWasViewed() {
        val favorite = anime(id = "12662", episode = "第19集")
        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(favorite),
            schedule = schedule(favorite),
            remoteHistory = listOf(
                TvHistoryItem(
                    animeId = "12662",
                    anime = favorite,
                    viewedEpisodeIndexes = setOf(17)
                )
            ),
            localHistory = emptyList()
        ).single()

        assertEquals("新・第19集", result.newEpisodeBadge)
    }

    @Test
    fun ignoresCompletedOrUnscheduledFavorites() {
        val completed = anime(id = "done", episode = "已完結")
        val unscheduled = anime(id = "other", episode = "第08集")
        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(completed, unscheduled),
            schedule = schedule(completed),
            remoteHistory = emptyList(),
            localHistory = emptyList()
        )

        assertEquals(listOf("", ""), result.map(AnimeData::newEpisodeBadge))
    }

    @Test
    fun parsesSupportedEpisodeLabels() {
        assertEquals(7, favoriteEpisodeNumber("07・週五20:35後"))
        assertEquals(19, favoriteEpisodeNumber("第 019 集"))
        assertEquals(3, favoriteEpisodeNumber("EP03"))
        assertEquals(12, favoriteEpisodeNumber("更新至12集"))
    }

    @Test
    fun sortsUpdatingFavoritesByLatestReleaseThenOlderTitlesByFavoriteTime() {
        val timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        val now = Calendar.getInstance(timeZone).apply {
            set(2026, Calendar.AUGUST, 23, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monday = anime(id = "monday", episode = "第07集", addedAt = 500L)
        val friday = anime(id = "friday", episode = "第07集", addedAt = 400L)
        val saturday = anime(id = "saturday", episode = "第07集", addedAt = 300L)
        val completedOlder = anime(id = "completed-old", episode = "已完結", addedAt = 100L)
        val completedNewer = anime(id = "completed-new", episode = "已完結", addedAt = 200L)

        val result = applyFavoriteNewEpisodeBadges(
            favorites = listOf(completedOlder, monday, completedNewer, friday, saturday),
            schedule = UpdateTimeLine(
                current = 6,
                timeline = listOf(
                    "週一" to listOf(monday.copy(currentEpisode = "07・週一20:00後")),
                    "週二" to emptyList(),
                    "週三" to emptyList(),
                    "週四" to emptyList(),
                    "週五" to listOf(friday.copy(currentEpisode = "07・週五23:00後")),
                    "週六" to listOf(saturday.copy(currentEpisode = "07・週六22:00後")),
                    "週日" to emptyList()
                )
            ),
            remoteHistory = emptyList(),
            localHistory = emptyList(),
            nowEpochMs = now,
            timeZone = timeZone
        )

        assertEquals(
            listOf("saturday", "friday", "monday", "completed-new", "completed-old"),
            result.map(AnimeData::id)
        )
    }

    private fun anime(id: String, episode: String, addedAt: Long = 0L) = AnimeData(
        id = id,
        url = "",
        title = "測試動畫",
        currentEpisode = episode,
        sourceId = "cycani",
        favoriteAddedAtEpochMs = addedAt
    )

    private fun schedule(item: AnimeData) = UpdateTimeLine(
        current = 0,
        timeline = listOf("週一" to listOf(item))
    )
}
