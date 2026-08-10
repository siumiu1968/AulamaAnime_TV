package com.jing.sakura.player

/** Small pure policy so network retries cannot publish a stale episode's segments. */
internal object PlaybackSegmentsRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun retryDelayAfter(failedAttempt: Int): Long? = when (failedAttempt) {
        1 -> 250L
        2 -> 750L
        else -> null
    }

    fun belongsToActiveEpisode(
        activeIndex: Int,
        activeEpisodeId: String?,
        requestIndex: Int,
        requestEpisodeId: String
    ): Boolean = activeIndex == requestIndex && activeEpisodeId == requestEpisodeId
}
