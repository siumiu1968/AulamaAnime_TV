package com.jing.sakura.player

import com.jing.sakura.auth.SeriesCompletion
import com.jing.sakura.data.AnimeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonFinalePolicyTest {
    private val now = 1_790_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val finished = SeriesCompletion(sourceStatus = "全12集", expectedEpisodes = 12)

    @Test
    fun offersOnlyInTheCreditsOfTheFinalEpisodeOfAFinishedSeason() {
        assertTrue(offer(finished, playIndex = 11, positionMs = 23 * MINUTE, durationMs = 24 * MINUTE))
        // Not the last episode: every other episode keeps auto-advancing as before.
        assertFalse(offer(finished, playIndex = 10, positionMs = 24 * MINUTE, durationMs = 24 * MINUTE))
        // Leaving mid-episode is not finishing it.
        assertFalse(offer(finished, playIndex = 11, positionMs = 10 * MINUTE, durationMs = 24 * MINUTE))
    }

    @Test
    fun theNewestEpisodeOfAnAiringShowIsNotASeasonFinale() {
        val airing = SeriesCompletion(sourceStatus = "更新至第5集", sourceUpdatedAtEpochMs = now - 3 * day)
        assertFalse(offer(airing, playIndex = 11, positionMs = 24 * MINUTE, durationMs = 24 * MINUTE))
        assertFalse(SeasonFinalePolicy.seriesHasFinished(SeriesCompletion(sourceStatus = "即將完結"), now))
    }

    @Test
    fun aShowThatStoppedUpdatingTwoWeeksAgoCountsAsFinished() {
        val stale = SeriesCompletion(sourceStatus = "更新至第12集", sourceUpdatedAtEpochMs = now - 20 * day)
        assertTrue(SeasonFinalePolicy.seriesHasFinished(stale, now))
        assertTrue(SeasonFinalePolicy.seriesHasFinished(SeriesCompletion(finished = true), now))
        assertTrue(SeasonFinalePolicy.seriesHasFinished(SeriesCompletion(sourceStatus = "已完結"), now))
    }

    @Test
    fun missingAnnouncedEpisodesBlockTheSheet() {
        val announced = finished.copy(expectedEpisodes = 13)
        assertFalse(offer(announced, playIndex = 11, positionMs = 24 * MINUTE, durationMs = 24 * MINUTE))
    }

    @Test
    fun nextWatchSkipsTheCurrentTitleDuplicatesAndPosterlessItems() {
        val picks = SeasonFinalePolicy.nextWatchCandidates(
            selfId = "cycani:1",
            related = listOf(anime("1"), anime("2"), anime("3", poster = "")),
            recommendations = listOf(anime("cycani:2"), anime("4"), anime("5"), anime("6"), anime("7"), anime("8"))
        )
        assertEquals(listOf("2", "4", "5", "6", "7", "8"), picks.map(AnimeData::id))
    }

    @Test
    fun alreadyRatedViewersGoStraightToRecommendations() {
        assertEquals(SeasonFinaleStage.NextWatch, initialSeasonFinaleStage(4))
        assertEquals(SeasonFinaleStage.Rating, initialSeasonFinaleStage(0))
    }

    private fun offer(completion: SeriesCompletion, playIndex: Int, positionMs: Long, durationMs: Long) =
        SeasonFinalePolicy.shouldOffer(
            completion = completion,
            playIndex = playIndex,
            episodeCount = 12,
            positionMs = positionMs,
            durationMs = durationMs,
            nowEpochMs = now
        )

    private fun anime(id: String, poster: String = "https://img/$id.jpg") =
        AnimeData(id = id, url = "", title = id, imageUrl = poster, sourceId = "cycani")

    private companion object {
        const val MINUTE = 60_000L
    }
}
