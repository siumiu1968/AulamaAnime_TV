package com.jing.sakura.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchSupportStoreTest {
    @Test
    fun `fresh install shows notice once`() {
        assertTrue(
            shouldShowFirstLaunchSupportNotice(
                alreadySeen = false,
                firstInstallTime = 1_000L,
                lastUpdateTime = 1_000L
            )
        )
        assertFalse(
            shouldShowFirstLaunchSupportNotice(
                alreadySeen = true,
                firstInstallTime = 1_000L,
                lastUpdateTime = 1_000L
            )
        )
    }

    @Test
    fun `existing installation upgraded to this version does not show notice`() {
        assertFalse(
            shouldShowFirstLaunchSupportNotice(
                alreadySeen = false,
                firstInstallTime = 1_000L,
                lastUpdateTime = 1_000L + 24 * 60 * 60 * 1_000L
            )
        )
    }

    @Test
    fun `an immediate version update is still not treated as a fresh install`() {
        assertFalse(
            shouldShowFirstLaunchSupportNotice(
                alreadySeen = false,
                firstInstallTime = 1_000L,
                lastUpdateTime = 1_001L
            )
        )
    }
}
