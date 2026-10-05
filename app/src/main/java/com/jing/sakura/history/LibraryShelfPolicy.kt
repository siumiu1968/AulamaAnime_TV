package com.jing.sakura.history

import com.jing.sakura.data.AnimeData
import java.util.Calendar
import java.util.TimeZone

/** Which part of 我的片庫 a shelf belongs to; shown as the shelf's eyebrow. */
internal enum class LibrarySection(val label: String) {
    History("觀看紀錄"),
    Favorites("我的收藏")
}

/**
 * One horizontal shelf on 我的片庫.
 *
 * 首頁 only keeps a short 「繼續觀看」 shortcut; the library is the complete archive, organised by
 * time like the web version: viewing history split into the last month, older and finished
 * titles, then favourites grouped by the month they were last watched.
 */
internal data class LibraryShelf(
    val key: String,
    val section: LibrarySection,
    val title: String,
    val videos: List<AnimeData>,
    val showProgress: Boolean
)

internal const val LIBRARY_RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

internal fun buildLibraryShelves(
    inProgress: List<AnimeData>,
    completed: List<AnimeData>,
    favorites: List<AnimeData>,
    lastWatchedAt: (AnimeData) -> Long,
    nowEpochMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): List<LibraryShelf> = buildList {
    val recentCutoff = nowEpochMs - LIBRARY_RECENT_WINDOW_MS
    // A record without a timestamp is kept with the recent titles rather than hidden away.
    val (recent, older) = inProgress
        .sortedByRecency(lastWatchedAt)
        .partition { lastWatchedAt(it).let { watched -> watched <= 0L || watched >= recentCutoff } }
    if (recent.isNotEmpty()) {
        add(LibraryShelf("history:recent", LibrarySection.History, "最近觀看", recent, showProgress = true))
    }
    if (older.isNotEmpty()) {
        add(LibraryShelf("history:older", LibrarySection.History, "一個月前", older, showProgress = true))
    }
    if (completed.isNotEmpty()) {
        add(
            LibraryShelf(
                "history:completed",
                LibrarySection.History,
                "已看完",
                completed.sortedByRecency(lastWatchedAt),
                showProgress = false
            )
        )
    }
    addAll(favoriteMonthShelves(favorites, lastWatchedAt, timeZone))
}

/** Favourites grouped by the year and month they were last watched, newest month first. */
internal fun favoriteMonthShelves(
    favorites: List<AnimeData>,
    lastWatchedAt: (AnimeData) -> Long,
    timeZone: TimeZone = TimeZone.getDefault()
): List<LibraryShelf> {
    if (favorites.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance(timeZone)
    val groups = favorites.groupBy { favorite ->
        val watchedAt = lastWatchedAt(favorite)
        if (watchedAt <= 0L) {
            UNWATCHED_MONTH
        } else {
            calendar.timeInMillis = watchedAt
            calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1
        }
    }
    val allUnwatched = groups.keys.singleOrNull() == UNWATCHED_MONTH
    return groups.entries
        .sortedByDescending { it.key }
        .map { (month, items) ->
            val title = when {
                allUnwatched -> "全部收藏"
                month == UNWATCHED_MONTH -> "未有觀看紀錄"
                else -> "${month / 100}年${month % 100}月"
            }
            LibraryShelf(
                key = "favorites:$month",
                section = LibrarySection.Favorites,
                title = title,
                // Within a month the favourites keep their incoming order on ties, which
                // already puts titles that are still airing first.
                videos = items.sortedByRecency(lastWatchedAt),
                showProgress = false
            )
        }
}

private fun List<AnimeData>.sortedByRecency(lastWatchedAt: (AnimeData) -> Long): List<AnimeData> =
    sortedByDescending(lastWatchedAt)

private const val UNWATCHED_MONTH = -1
