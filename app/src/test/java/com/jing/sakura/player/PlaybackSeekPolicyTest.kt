package com.jing.sakura.player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class PlaybackSeekPolicyTest {
    @Test fun shortUnknownAndBoundaryDurationsUseFiveSeconds() {
        listOf(-1L, 0L, 24 * 60_000L, 30 * 60_000L).forEach { assertEquals(5_000L, PlaybackSeekPolicy.incrementMs(it)) }
    }
    @Test fun longProgramsAlwaysUseTenSeconds() {
        listOf(30 * 60_000L + 1, 90 * 60_000L, 240 * 60_000L).forEach { assertEquals(10_000L, PlaybackSeekPolicy.incrementMs(it)) }
    }
    @Test fun leanbackPercentageProducesTheSameStepAsTransportButtons() {
        listOf(1_439_873L, 1_800_000L, 1_800_001L, 5_400_000L).forEach {
            assertEquals(PlaybackSeekPolicy.incrementMs(it).toDouble(), it * PlaybackSeekPolicy.fraction(it).toDouble(), 1.0)
        }
    }
    @Test fun shortClipsKeepBothTapAndHeldStepsProportional() {
        for (duration in listOf(1_000L, 10_000L, 60_000L, 120_000L)) {
            val tap = PlaybackSeekPolicy.incrementMs(duration)
            val held = PlaybackSeekPolicy.heldIncrementMs(duration, 20_000)
            assertTrue(tap in 1..duration / 10)
            assertTrue(held in tap..maxOf(tap, duration / 50))
        }
        assertEquals(600L, PlaybackSeekPolicy.incrementMs(60_000))
        assertEquals(1_200L, PlaybackSeekPolicy.heldIncrementMs(60_000, 20_000))
    }
    @Test fun longHoldsAccelerateInStagesWithDurationAndAbsoluteCaps() {
        val duration = 24 * 60_000L
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 28_800L, 28_800L),
            listOf(0L, 800L, 2_000L, 4_000L, 6_000L).map { PlaybackSeekPolicy.heldIncrementMs(duration, it) })
        assertEquals(60_000L, PlaybackSeekPolicy.heldIncrementMs(90 * 60_000L, 60_000L))
        assertEquals(5_000L, PlaybackSeekPolicy.heldIncrementMs(0, 60_000L))
    }
}
