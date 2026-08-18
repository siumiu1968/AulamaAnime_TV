package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import java.io.Serializable

data class NavigateToPlayerArg(
    val animeName: String,
    val animeId: String,
    val coverUrl: String,
    val playIndex: Int,
    val playlist: List<AnimePlayListEpisode>,
    val sourceId: String,
    val resumePositionMs: Long = NO_REMOTE_RESUME_POSITION,
    val playlists: List<AnimePlayList> = emptyList(),
    val playlistIndex: Int = 0
) : Serializable {
    companion object {
        const val NO_REMOTE_RESUME_POSITION = -1L
    }
}
