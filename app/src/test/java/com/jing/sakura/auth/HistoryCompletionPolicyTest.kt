package com.jing.sakura.auth
import com.jing.sakura.data.AnimeData
import org.junit.Assert.*
import org.junit.Test
class HistoryCompletionPolicyTest {
    private val item=TvHistoryItem("a",AnimeData(id="a",url="",title="A",currentEpisode="",imageUrl="",sourceId="cycani"),episodeIndex=11,episodeCount=12,durationSeconds=1440.0)
    @Test fun finishedFinalEpisodeAppearsInCompletedSection() { assertTrue(isHistorySeriesCompleted(item.copy(completed=true))) }
    @Test fun finishingAnEarlierEpisodeDoesNotCompleteSeries() { assertFalse(isHistorySeriesCompleted(item.copy(episodeIndex=10,completed=true))) }
    @Test fun newAvailableEpisodeReturnsSeriesToContinueWatching() {
        val parsed=TvLibraryParser.parseHistoryItems("""{"items":[{"animeId":"a","animeTitle":"A","episodeIndex":11,"episodeCount":12,"availableEpisodeCount":13,"completed":true}]}""").single()
        assertFalse(isHistorySeriesCompleted(parsed))
        assertEquals(13,parsed.episodeCount)
    }
    @Test fun endThresholdMatchesWebAndUnknownDurationStaysUnfinished() {
        assertTrue(isHistorySeriesCompleted(item.copy(currentTimeSeconds=1080.0)))
        assertFalse(isHistorySeriesCompleted(item.copy(currentTimeSeconds=1079.0)))
        assertFalse(isHistorySeriesCompleted(item.copy(durationSeconds=0.0)))
    }
}
