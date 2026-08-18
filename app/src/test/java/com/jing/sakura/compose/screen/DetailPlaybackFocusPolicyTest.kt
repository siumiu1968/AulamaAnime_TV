package com.jing.sakura.compose.screen

import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailPlaybackFocusPolicyTest {
    @Test
    fun focusStopsAtLineHeaderBetweenHeroAndEpisodes() {
        assertEquals(
            DetailPlaybackFocusTarget.LINE,
            DetailPlaybackFocusPolicy.downFromHero(hasEpisodes = true)
        )
        assertEquals(DetailPlaybackFocusTarget.LINE, DetailPlaybackFocusPolicy.upFromEpisode())
        assertEquals(DetailPlaybackFocusTarget.EPISODE, DetailPlaybackFocusPolicy.downFromHeader())
    }

    @Test
    fun lineAvailabilityExplainsWhyAChoiceIsDisabled() {
        assertEquals(
            "可用 · 1 集",
            playbackLineAvailabilityText(
                AnimePlayList("主線路", listOf(AnimePlayListEpisode("第1集", "1")))
            )
        )
        assertEquals(
            "不可用 · 未有可播放集數",
            playbackLineAvailabilityText(AnimePlayList("後備", emptyList()))
        )
    }
}
