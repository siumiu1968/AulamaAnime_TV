package com.jing.sakura.compose.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeFadePolicyTest {
    @Test
    fun edgeFadeWaitsForMarqueeToStart() {
        assertFalse(shouldDrawMarqueeEdgeFade(scrolls = true, marqueeStarted = false))
        assertTrue(shouldDrawMarqueeEdgeFade(scrolls = true, marqueeStarted = true))
    }
}
