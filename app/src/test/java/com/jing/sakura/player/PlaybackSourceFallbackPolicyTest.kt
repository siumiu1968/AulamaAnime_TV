package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceFallbackPolicyTest {
    private val currentEpisode = AnimePlayListEpisode("第17集", "route-a-17")
    private val playlists = listOf(
        AnimePlayList("線路 A", listOf(currentEpisode)),
        AnimePlayList("線路 B", listOf(AnimePlayListEpisode("17", "route-b-17"))),
        AnimePlayList("線路 C", listOf(AnimePlayListEpisode("第18集", "route-c-18")))
    )

    @Test
    fun onlyReturnsOtherRoutesContainingTheSameEpisode() {
        assertEquals(
            listOf(PlaybackSourceFallback(1, "線路 B", 0)),
            PlaybackSourceFallbackPolicy.candidates(playlists, 0, currentEpisode)
        )
    }

    @Test
    fun failedRoutesAreNotSuggestedAgain() {
        assertTrue(
            PlaybackSourceFallbackPolicy.candidates(
                playlists,
                currentPlaylistIndex = 0,
                currentEpisode = currentEpisode,
                excludedPlaylistIndexes = setOf(1)
            ).isEmpty()
        )
    }
}
