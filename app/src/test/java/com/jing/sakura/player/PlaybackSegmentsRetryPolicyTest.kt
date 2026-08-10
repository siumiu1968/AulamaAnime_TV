package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSegmentsRetryPolicyTest {
    @Test
    fun retriesAtMostThreeTimesWithBackoff() {
        assertEquals(3, PlaybackSegmentsRetryPolicy.MAX_ATTEMPTS)
        assertEquals(250L, PlaybackSegmentsRetryPolicy.retryDelayAfter(1))
        assertEquals(750L, PlaybackSegmentsRetryPolicy.retryDelayAfter(2))
        assertNull(PlaybackSegmentsRetryPolicy.retryDelayAfter(3))
    }

    @Test
    fun staleEpisodeResponseCannotBePublished() {
        assertTrue(PlaybackSegmentsRetryPolicy.belongsToActiveEpisode(4, "ep-4", 4, "ep-4"))
        assertFalse(PlaybackSegmentsRetryPolicy.belongsToActiveEpisode(5, "ep-5", 4, "ep-4"))
    }
}
