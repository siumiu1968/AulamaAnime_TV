package com.jing.sakura.detail

import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.auth.favoriteEpisodeNumber
import com.jing.sakura.room.VideoHistoryEntity

/** An episode this close to its end reads as watched, like the web's full episode battery. */
internal const val EPISODE_WATCHED_RATIO = 0.9f

/**
 * Watched fraction for each episode number, merged from this TV's per-episode history and the
 * account's cloud history. Keyed by episode number so every playback line shares the progress.
 */
internal fun buildEpisodeProgress(
    localHistory: List<VideoHistoryEntity>,
    remoteHistory: TvHistoryItem?
): Map<Int, Float> {
    val progress = HashMap<Int, Float>()
    fun record(episodeNumber: Int, ratio: Float) {
        if (episodeNumber <= 0) return
        val normalized = ratio.coerceIn(0f, 1f).let { if (it >= EPISODE_WATCHED_RATIO) 1f else it }
        if (normalized > (progress[episodeNumber] ?: 0f)) progress[episodeNumber] = normalized
    }
    localHistory.forEach { row ->
        val ratio = if (row.videoDuration > 0L) row.lastPlayTime.toFloat() / row.videoDuration else 0f
        record(favoriteEpisodeNumber(row.lastEpisodeName), ratio)
    }
    remoteHistory?.let { item ->
        item.episodeProgress.forEach { (index, ratio) -> record(index + 1, ratio) }
        val current = favoriteEpisodeNumber(item.episodeLabel).takeIf { it > 0 } ?: (item.episodeIndex + 1)
        val ratio = when {
            item.completed -> 1f
            item.durationSeconds > 0.0 -> (item.currentTimeSeconds / item.durationSeconds).toFloat()
            else -> 0f
        }
        record(current, ratio)
    }
    return progress
}
