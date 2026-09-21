package com.jing.sakura.auth

// Match the web library: finishing a single episode is not finishing the series.
internal fun isHistorySeriesCompleted(item: TvHistoryItem): Boolean {
    if (item.episodeCount < 1 || item.episodeIndex != item.episodeCount - 1) return false
    return item.completed || (item.durationSeconds > 0 &&
        item.durationSeconds - item.currentTimeSeconds.coerceAtLeast(0.0) <= 360.0)
}
