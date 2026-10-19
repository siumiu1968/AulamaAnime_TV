package com.jing.sakura.home

import org.junit.Assert.*
import org.junit.Test

class HomeEntrancePolicyTest {
    @Test fun artworkAndHeroShareASmoothBoundedEntrance() {
        assertEquals(0f, homeEntranceFraction(-1f, 0f, 0.62f), 0f)
        assertEquals(0.5f, homeEntranceFraction(0.31f, 0f, 0.62f), 0.0001f)
        assertEquals(1f, homeEntranceFraction(1f, 0f, 0.62f), 0f)
    }
    @Test fun visibleCardsStartInOrderAndAllFinishBeforeTimelineEnds() {
        val delays = (0..7).map(::homeCardEntranceDelay)
        assertTrue(delays.zipWithNext().all { (a, b) -> a < b })
        assertTrue(delays.all { homeEntranceFraction(1f, it, 0.28f) == 1f })
        assertEquals(delays.first(), homeCardEntranceDelay(-1), 0f)
        assertEquals(delays.last(), homeCardEntranceDelay(Int.MAX_VALUE), 0f)
    }
}
