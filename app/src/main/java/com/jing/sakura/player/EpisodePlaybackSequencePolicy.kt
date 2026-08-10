package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayListEpisode

/** Playback always follows the source's canonical episode sequence, never the UI display order. */
internal object EpisodePlaybackSequencePolicy {
    fun indexOfEpisode(episodes: List<AnimePlayListEpisode>, episodeId: String): Int =
        episodes.indexOfFirst { it.episodeId == episodeId }

    fun nextIndex(currentIndex: Int, size: Int): Int? =
        (currentIndex + 1).takeIf { currentIndex >= 0 && it < size }

    fun previousIndex(currentIndex: Int): Int? =
        (currentIndex - 1).takeIf { it >= 0 }
}
