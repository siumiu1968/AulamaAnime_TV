package com.jing.sakura.player

import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import java.text.Normalizer
import java.util.Locale

internal data class PlaybackSourceFallback(
    val playlistIndex: Int,
    val sourceName: String,
    val episodeIndex: Int
)

internal object PlaybackSourceFallbackPolicy {
    fun candidates(
        playlists: List<AnimePlayList>,
        currentPlaylistIndex: Int,
        currentEpisode: AnimePlayListEpisode,
        excludedPlaylistIndexes: Set<Int> = emptySet()
    ): List<PlaybackSourceFallback> {
        val episodeKey = episodeKey(currentEpisode.episode)
        return playlists.mapIndexedNotNull { playlistIndex, playlist ->
            if (playlistIndex == currentPlaylistIndex || playlistIndex in excludedPlaylistIndexes) {
                return@mapIndexedNotNull null
            }
            val episodeIndex = playlist.episodeList.indexOfFirst {
                episodeKey(it.episode) == episodeKey
            }
            episodeIndex.takeIf { it >= 0 }?.let {
                PlaybackSourceFallback(playlistIndex, playlist.name, it)
            }
        }
    }

    private fun episodeKey(label: String): String {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFKC).trim()
        val number = EPISODE_NUMBER.matchEntire(normalized)?.groupValues?.getOrNull(1)
        return number?.trimStart('0')?.ifEmpty { "0" }
            ?: normalized.lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
    }

    private val EPISODE_NUMBER = Regex(
        "^(?:ep(?:isode)?\\s*[:#._-]?|第\\s*)?0*(\\d+(?:\\.\\d+)?)\\s*(?:集|話|话)?$",
        RegexOption.IGNORE_CASE
    )
}
