package com.jing.sakura.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipExitGracePolicyTest {
    @Test
    fun transientPositionGapDoesNotCommitHide() {
        val policy = PlaybackSkipExitGracePolicy()

        assertEquals(600L, policy.scheduleExit(1_000L))
        assertFalse(policy.shouldCommitExit(1_599L))
        policy.onSegmentActive()

        assertFalse(policy.shouldCommitExit(1_600L))
    }

    @Test
    fun inactiveSegmentCommitsOnlyAfterGraceWindow() {
        val policy = PlaybackSkipExitGracePolicy()

        policy.scheduleExit(1_000L)

        assertFalse(policy.shouldCommitExit(1_599L))
        assertTrue(policy.shouldCommitExit(1_600L))
    }
}
