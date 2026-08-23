package com.jing.sakura.auth

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.room.VideoHistoryEntity
import java.util.Calendar
import java.util.TimeZone

internal fun applyFavoriteNewEpisodeBadges(
    favorites: List<AnimeData>,
    schedule: UpdateTimeLine?,
    remoteHistory: List<TvHistoryItem>,
    localHistory: List<VideoHistoryEntity>,
    nowEpochMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): List<AnimeData> {
    if (favorites.isEmpty()) return favorites
    val scheduleByAnime = schedule
        ?.timeline
        .orEmpty()
        .asSequence()
        .flatMapIndexed { dayIndex, (_, items) ->
            items.asSequence().map { anime -> dayIndex to anime }
        }
        .mapNotNull { (dayIndex, anime) ->
            favoriteIdentity(anime.id).takeIf(String::isNotBlank)?.let {
                it to ScheduledFavorite(anime, dayIndex)
            }
        }
        .toMap()
    val viewedEpisodes = buildSet {
        remoteHistory.forEach { history ->
            val animeId = favoriteIdentity(history.animeId.ifBlank { history.anime.id })
            if (animeId.isBlank()) return@forEach
            history.viewedEpisodeIndexes.forEach { index ->
                if (index in 0 until MAX_EPISODE_NUMBER) {
                    add(FavoriteEpisodeKey(animeId, index + 1))
                }
            }
            favoriteEpisodeNumber(history.episodeLabel)
                .takeIf { it > 0 }
                ?.let { add(FavoriteEpisodeKey(animeId, it)) }
            if (history.episodeId.isNotBlank() && history.episodeIndex in 0 until MAX_EPISODE_NUMBER) {
                add(FavoriteEpisodeKey(animeId, history.episodeIndex + 1))
            }
        }
        localHistory.forEach { history ->
            val animeId = favoriteIdentity(history.animeId)
            val episode = favoriteEpisodeNumber(history.lastEpisodeName)
            if (animeId.isNotBlank() && episode > 0) {
                add(FavoriteEpisodeKey(animeId, episode))
            }
        }
    }

    return favorites.mapIndexed { originalIndex, favorite ->
        val identity = favoriteIdentity(favorite.id)
        val scheduledFavorite = scheduleByAnime[identity]
        val scheduleStatus = scheduledFavorite?.anime?.currentEpisode.orEmpty()
        val latestEpisode = favoriteEpisodeNumber(scheduleStatus)
        val completed = isCompletedFavoriteSchedule(favorite.currentEpisode) ||
            isCompletedFavoriteSchedule(scheduleStatus)
        val badge = if (
            scheduledFavorite != null &&
            latestEpisode > 0 &&
            !completed &&
            FavoriteEpisodeKey(identity, latestEpisode) !in viewedEpisodes
        ) {
            "新・第${latestEpisode.toString().padStart(2, '0')}集"
        } else {
            ""
        }
        PreparedFavorite(
            anime = favorite.copy(
                currentEpisode = if (completed) {
                    favorite.currentEpisode.ifBlank { scheduleStatus }
                } else {
                    scheduleStatus.ifBlank { favorite.currentEpisode }
                },
                newEpisodeBadge = badge
            ),
            originalIndex = originalIndex,
            activelyUpdating = scheduledFavorite != null && !completed,
            latestUpdateEpochMs = scheduledFavorite?.let {
                latestScheduledUpdateEpochMs(
                    dayIndex = it.dayIndex,
                    status = scheduleStatus,
                    nowEpochMs = nowEpochMs,
                    timeZone = timeZone
                )
            } ?: 0L
        )
    }.sortedWith(
        compareBy<PreparedFavorite> { if (it.activelyUpdating) 0 else 1 }
            .thenByDescending(PreparedFavorite::latestUpdateEpochMs)
            .thenByDescending { it.anime.favoriteAddedAtEpochMs }
            .thenBy(PreparedFavorite::originalIndex)
    ).map(PreparedFavorite::anime)
}

internal fun favoriteEpisodeNumber(value: String): Int {
    val text = value.trim()
    if (text.isBlank()) return 0
    val patterns = listOf(
        Regex("""第\s*0*(\d{1,4})\s*[集話话]""", RegexOption.IGNORE_CASE),
        Regex("""(?:episode|ep|e)\s*[-._:]?\s*0*(\d{1,4})(?=$|[^\d])""", RegexOption.IGNORE_CASE),
        Regex("""(?:更新至|更新到|至)\s*0*(\d{1,4})\s*[集話话]?"""),
        Regex("""^\s*0*(\d{1,4})(?=$|[^\d])""")
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1..MAX_EPISODE_NUMBER }
    } ?: 0
}

private fun favoriteIdentity(value: String): String = value
    .trim()
    .lowercase()
    .removePrefix("cycani:")

private fun isCompletedFavoriteSchedule(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.contains("完結") ||
        normalized.contains("完结") ||
        normalized.contains("completed") ||
        normalized.contains("finished") ||
        Regex("""(?:全|共)\s*\d{1,4}\s*[集話话]""").containsMatchIn(normalized)
}

private fun latestScheduledUpdateEpochMs(
    dayIndex: Int,
    status: String,
    nowEpochMs: Long,
    timeZone: TimeZone
): Long {
    val weekStart = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowEpochMs
        val mondayBasedDayIndex = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_MONTH, -mondayBasedDayIndex)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val scheduledMinutes = SCHEDULE_TIME_PATTERN.find(status)?.let { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return@let null
        val minute = match.groupValues[2].toIntOrNull() ?: return@let null
        if (hour !in 0..47 || minute !in 0..59) null else hour * 60 + minute
    } ?: 0
    val candidate = (weekStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, dayIndex.coerceIn(0, 6))
        add(Calendar.MINUTE, scheduledMinutes)
        if (timeInMillis > nowEpochMs) add(Calendar.DAY_OF_MONTH, -7)
    }
    return candidate.timeInMillis
}

private data class ScheduledFavorite(
    val anime: AnimeData,
    val dayIndex: Int
)

private data class PreparedFavorite(
    val anime: AnimeData,
    val originalIndex: Int,
    val activelyUpdating: Boolean,
    val latestUpdateEpochMs: Long
)

private data class FavoriteEpisodeKey(
    val animeId: String,
    val episodeNumber: Int
)

private const val MAX_EPISODE_NUMBER = 10_000
private val SCHEDULE_TIME_PATTERN = Regex("""(\d{1,2})[:：](\d{2})""")
