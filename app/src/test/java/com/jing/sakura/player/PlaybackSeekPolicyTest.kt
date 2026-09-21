package com.jing.sakura.player
import org.junit.Assert.assertEquals
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
}
