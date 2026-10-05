package com.jing.sakura.detail

import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.data.AnimeData
import com.jing.sakura.room.VideoHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeProgressPolicyTest {
    @Test
    fun mergesLocalAndCloudProgressByEpisodeNumber() {
        val progress = buildEpisodeProgress(
            localHistory = listOf(
                local(label = "第1集", positionMs = 1_400_000, durationMs = 1_440_000),
                local(label = "第02集", positionMs = 300_000, durationMs = 1_200_000)
            ),
            remoteHistory = TvHistoryItem(
                animeId = "7",
                anime = AnimeData(id = "7", url = "", title = "t", sourceId = "cycani"),
                episodeLabel = "第3集",
                episodeIndex = 2,
                currentTimeSeconds = 600.0,
                durationSeconds = 1_200.0,
                episodeProgress = mapOf(1 to 0.75f, 0 to 0.2f)
            )
        )

        // ≥ 90 % counts as watched; the larger of local and cloud wins.
        assertEquals(1f, progress[1])
        assertEquals(0.75f, progress[2])
        assertEquals(0.5f, progress[3])
        assertEquals(null, progress[4])
    }

    private fun local(label: String, positionMs: Long, durationMs: Long) = VideoHistoryEntity(
        episodeId = label,
        sourceId = "cycani",
        animeName = "t",
        animeId = "7",
        lastEpisodeName = label,
        updateTime = 1L,
        lastPlayTime = positionMs,
        videoDuration = durationMs
    )
}
