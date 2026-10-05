package com.jing.sakura.home

import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.data.AnimeData
import com.jing.sakura.room.VideoHistoryEntity

internal const val HOME_UP_NEXT_LIMIT = 12
internal const val HOME_UP_NEXT_TITLE = "繼續觀看"

/**
 * The single 「繼續觀看」 shelf on 首頁, in the spirit of Apple TV's Up Next: titles the viewer
 * is part-way through, plus followed favourites that have a new episode they have not seen yet,
 * most recently watched first. It is a shortcut only; the full history and every favourite live
 * in 我的片庫, so 首頁 no longer repeats the whole favourites shelf.
 *
 * Favourites the viewer never started are left out: they are wishes, not something to resume.
 */
internal fun buildHomeUpNext(
    inProgress: List<AnimeData>,
    favorites: List<AnimeData>,
    lastWatchedAt: (AnimeData) -> Long,
    limit: Int = HOME_UP_NEXT_LIMIT
): List<AnimeData> {
    val favoritesById = favorites.associateBy { animeIdentity(it.id) }
    val resumable = inProgress.map { anime ->
        val badge = favoritesById[animeIdentity(anime.id)]?.newEpisodeBadge.orEmpty()
        if (badge.isNotBlank() && anime.newEpisodeBadge.isBlank()) anime.copy(newEpisodeBadge = badge) else anime
    }
    val resumableIds = resumable.mapTo(hashSetOf()) { animeIdentity(it.id) }
    val followedWithNewEpisode = favorites.filter { favorite ->
        favorite.newEpisodeBadge.isNotBlank() &&
            animeIdentity(favorite.id) !in resumableIds &&
            lastWatchedAt(favorite) > 0L
    }
    // Stable sort: titles without a timestamp keep the server's recency order.
    return (resumable + followedWithNewEpisode)
        .sortedByDescending(lastWatchedAt)
        .take(limit)
}

/** Normalises catalogue ids so `cycani:123` and `123` refer to the same title. */
internal fun animeIdentity(id: String): String = id.trim().lowercase().removePrefix("cycani:")

/**
 * Most recent time each title was watched on any device (cloud history) or on this TV (local
 * history, one row per episode), keyed by normalised id.
 */
internal fun lastWatchedLookup(
    remoteHistory: List<TvHistoryItem>,
    localHistory: List<VideoHistoryEntity>
): (AnimeData) -> Long {
    val latest = HashMap<String, Long>()
    fun record(id: String, watchedAt: Long) {
        if (id.isBlank() || watchedAt <= 0L) return
        val key = animeIdentity(id)
        if (watchedAt > (latest[key] ?: 0L)) latest[key] = watchedAt
    }
    remoteHistory.forEach { record(it.animeId.ifBlank { it.anime.id }, it.updatedAtEpochMs) }
    localHistory.forEach { record(it.animeId, it.updateTime) }
    return { anime -> latest[animeIdentity(anime.id)] ?: 0L }
}
