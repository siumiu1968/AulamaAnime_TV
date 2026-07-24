package com.jing.sakura.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionPolicyTest {
    @Test
    fun returningToScreenRejectsOldPreviewTimerAndAcceptsNewSession() {
        assertFalse(
            shouldStartPreview(
                scheduledSession = 4,
                currentSession = 5,
                isScreenResumed = true,
                hasFocusedContent = true
            )
        )
        assertTrue(
            shouldStartPreview(
                scheduledSession = 5,
                currentSession = 5,
                isScreenResumed = true,
                hasFocusedContent = true
            )
        )
    }

    @Test
    fun pausedOrUnfocusedScreenCannotStartPreview() {
        assertFalse(shouldStartPreview(3, 3, isScreenResumed = false, hasFocusedContent = true))
        assertFalse(shouldStartPreview(3, 3, isScreenResumed = true, hasFocusedContent = false))
    }
}
