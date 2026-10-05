package com.jing.sakura.player

import com.jing.sakura.auth.SeriesCompletion
import com.jing.sakura.data.AnimeData

/**
 * When to show the end-of-season sheet (rating + what to watch next). Mirrors the web's
 * `postWatchPolicy`: only after the final episode of a season that has actually finished airing,
 * never after every episode, and never on the newest episode of a show that is still airing.
 */
internal object SeasonFinalePolicy {
    /** The credits window at the end of the final episode in which the sheet may appear. */
    const val CREDITS_WINDOW_MS = 180_000L
    const val RECOMMENDATION_LIMIT = 6
    private const val STALE_SERIES_MS = 14L * 24 * 60 * 60 * 1000
    private const val END_TOLERANCE_MS = 2_000L

    private val notFinished = Regex("""未完|未播|待播|即將|即将|停播|休播|延期|not.?finished""", RegexOption.IGNORE_CASE)
    private val finishedStatus = Regex(
        """已完[結结]|已完成|全\s*\d+\s*[集話话]|完[結结]|全集|\bcompleted\b|\bfinished\b""",
        RegexOption.IGNORE_CASE
    )
    private val airingStatus = Regex("""更新|連載|连载|\d+\s*[集話话]""")

    fun seriesHasFinished(completion: SeriesCompletion, nowEpochMs: Long): Boolean {
        val status = completion.sourceStatus.trim()
        if (notFinished.containsMatchIn(status)) return false
        if (completion.finished || finishedStatus.containsMatchIn(status)) return true
        // A show whose last upstream update is two weeks old has stopped releasing episodes.
        val updatedAt = completion.sourceUpdatedAtEpochMs
        return updatedAt > 0L &&
            nowEpochMs - updatedAt > STALE_SERIES_MS &&
            airingStatus.containsMatchIn(status)
    }

    /** The playing episode is the last of the season and no announced episode is missing. */
    fun isFinalEpisode(playIndex: Int, episodeCount: Int, expectedEpisodes: Int): Boolean =
        episodeCount > 0 &&
            playIndex == episodeCount - 1 &&
            expectedEpisodes <= episodeCount

    fun isInCredits(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L &&
            positionMs > 0L &&
            positionMs <= durationMs + END_TOLERANCE_MS &&
            durationMs - positionMs <= CREDITS_WINDOW_MS

    fun shouldOffer(
        completion: SeriesCompletion,
        playIndex: Int,
        episodeCount: Int,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long
    ): Boolean = isFinalEpisode(playIndex, episodeCount, completion.expectedEpisodes) &&
        seriesHasFinished(completion, nowEpochMs) &&
        isInCredits(positionMs, durationMs)

    /** Related titles (sequels, same franchise) first, then personal recommendations. */
    fun nextWatchCandidates(
        selfId: String,
        related: List<AnimeData>,
        recommendations: List<AnimeData>,
        limit: Int = RECOMMENDATION_LIMIT
    ): List<AnimeData> {
        val seen = hashSetOf(identity(selfId))
        return (related + recommendations)
            .filter { it.id.isNotBlank() && it.imageUrl.isNotBlank() && seen.add(identity(it.id)) }
            .take(limit)
    }

    private fun identity(id: String): String = id.trim().lowercase().removePrefix("cycani:")
}
