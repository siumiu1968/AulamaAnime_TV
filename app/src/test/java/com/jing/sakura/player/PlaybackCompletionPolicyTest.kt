package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompletionPolicyTest {
    @Test
    fun completedEpisodeDoesNotResumeInsideCredits() {
        assertEquals(
            0L,
            PlaybackCompletionPolicy.resumePosition(
                lastPositionMs = 1_395_000L,
                durationMs = 1_400_000L
            )
        )
    }

    @Test
    fun incompleteEpisodeKeepsItsResumePosition() {
        assertEquals(
            420_000L,
            PlaybackCompletionPolicy.resumePosition(
                lastPositionMs = 420_000L,
                durationMs = 1_400_000L
            )
        )
    }

    @Test
    fun completedCloudHistoryMapsToTheEndOrZeroWhenDurationIsUnknown() {
        assertEquals(
            1_400_000L,
            PlaybackCompletionPolicy.mappedCloudPosition(420_000L, 1_400_000L, completed = true)
        )
        assertEquals(
            0L,
            PlaybackCompletionPolicy.mappedCloudPosition(420_000L, 0L, completed = true)
        )
    }
}
