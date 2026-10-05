package com.jing.sakura.history

import com.jing.sakura.data.AnimeData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LibraryShelfPolicyTest {
    private val hongKong = TimeZone.getTimeZone("Asia/Hong_Kong")
    private val now = epoch(2026, Calendar.OCTOBER, 5)

    @Test
    fun historyIsSplitIntoRecentOlderAndCompleted() {
        val recent = anime("recent")
        val older = anime("older")
        val unknown = anime("unknown")
        val done = anime("done")
        val watched = mapOf(
            "recent" to now - DAY,
            "older" to now - 45 * DAY,
            "done" to now - 2 * DAY
        )

        val shelves = buildLibraryShelves(
            inProgress = listOf(older, unknown, recent),
            completed = listOf(done),
            favorites = emptyList(),
            lastWatchedAt = { watched[it.id] ?: 0L },
            nowEpochMs = now,
            timeZone = hongKong
        )

        assertEquals(listOf("最近觀看", "一個月前", "已看完"), shelves.map(LibraryShelf::title))
        assertEquals(listOf("recent", "unknown"), shelves[0].videos.map(AnimeData::id))
        assertEquals(listOf("older"), shelves[1].videos.map(AnimeData::id))
        assertEquals(false, shelves[2].showProgress)
        assertEquals(LibrarySection.History, shelves[2].section)
    }

    @Test
    fun favoritesAreGroupedByLastWatchedMonthNewestFirst() {
        val watched = mapOf(
            "sept" to epoch(2026, Calendar.SEPTEMBER, 30),
            "octEarly" to epoch(2026, Calendar.OCTOBER, 1),
            "octLate" to epoch(2026, Calendar.OCTOBER, 4),
            "lastYear" to epoch(2025, Calendar.DECEMBER, 31)
        )

        val shelves = favoriteMonthShelves(
            favorites = listOf("never", "sept", "octEarly", "lastYear", "octLate").map(::anime),
            lastWatchedAt = { watched[it.id] ?: 0L },
            timeZone = hongKong
        )

        assertEquals(
            listOf("2026年10月", "2026年9月", "2025年12月", "未有觀看紀錄"),
            shelves.map(LibraryShelf::title)
        )
        assertEquals(listOf("octLate", "octEarly"), shelves[0].videos.map(AnimeData::id))
        assertEquals(setOf(LibrarySection.Favorites), shelves.map(LibraryShelf::section).toSet())
        assertEquals(shelves.size, shelves.map(LibraryShelf::key).toSet().size)
    }

    @Test
    fun favoritesWithoutAnyHistoryFormOneShelf() {
        val shelves = favoriteMonthShelves(
            favorites = listOf(anime("a"), anime("b")),
            lastWatchedAt = { 0L },
            timeZone = hongKong
        )

        assertEquals(listOf("全部收藏"), shelves.map(LibraryShelf::title))
        assertEquals(listOf("a", "b"), shelves.single().videos.map(AnimeData::id))
    }

    private fun anime(id: String) = AnimeData(id = id, url = "", title = id, sourceId = "cycani")

    private fun epoch(year: Int, month: Int, day: Int): Long = Calendar.getInstance(hongKong).apply {
        clear()
        set(year, month, day, 20, 0)
    }.timeInMillis

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
