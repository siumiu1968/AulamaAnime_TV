package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayListEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodePlaybackSequencePolicyTest {
    private val canonicalEpisodes = listOf(
        AnimePlayListEpisode("第1集", "one"),
        AnimePlayListEpisode("第2集", "two"),
        AnimePlayListEpisode("第3集", "three")
    )

    @Test
    fun reversedDisplaySelectionResolvesBackToCanonicalPlaybackIndex() {
        assertEquals(2, EpisodePlaybackSequencePolicy.indexOfEpisode(canonicalEpisodes, "three"))
        assertNull(EpisodePlaybackSequencePolicy.nextIndex(2, canonicalEpisodes.size))
    }

    @Test
    fun nextEpisodeNeverWrapsToAnEarlierEpisode() {
        assertEquals(1, EpisodePlaybackSequencePolicy.nextIndex(0, canonicalEpisodes.size))
        assertNull(EpisodePlaybackSequencePolicy.nextIndex(2, canonicalEpisodes.size))
    }
}
