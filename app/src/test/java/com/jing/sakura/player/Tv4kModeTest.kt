package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Test

class Tv4kModeTest {
    @Test
    fun qualityModesDegradeOneStepAtATime() {
        assertEquals(Tv4kMode.FAST, Tv4kMode.BALANCED.fallback())
        assertEquals(Tv4kMode.RESOURCE_SAVER, Tv4kMode.FAST.fallback())
        assertEquals(Tv4kMode.OFF, Tv4kMode.RESOURCE_SAVER.fallback())
        assertEquals(Tv4kMode.OFF, Tv4kMode.OFF.fallback())
    }
}
