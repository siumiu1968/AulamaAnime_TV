package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthEntryPolicyTest {
    @Test
    fun keepsTheBrandVisibleForTheMinimumOpeningMoment() {
        assertEquals(
            AuthEntryPolicy.MIN_BRAND_DISPLAY_MS,
            AuthEntryPolicy.remainingBrandDisplayMs(startedAtMs = 1_000L, nowMs = 1_000L)
        )
        assertEquals(
            600L,
            AuthEntryPolicy.remainingBrandDisplayMs(startedAtMs = 1_000L, nowMs = 1_500L)
        )
    }

    @Test
    fun doesNotDelayAfterTheMinimumOpeningMoment() {
        assertEquals(
            0L,
            AuthEntryPolicy.remainingBrandDisplayMs(startedAtMs = 1_000L, nowMs = 2_100L)
        )
        assertEquals(
            0L,
            AuthEntryPolicy.remainingBrandDisplayMs(startedAtMs = 1_000L, nowMs = 2_500L)
        )
    }
}
