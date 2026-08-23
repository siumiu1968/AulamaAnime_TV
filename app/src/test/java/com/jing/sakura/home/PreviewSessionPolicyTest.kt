package com.jing.sakura.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionPolicyTest {
    @Test
    fun previewStartsSevenSecondsAfterFocus() {
        assertEquals(
            7_000L,
            PREVIEW_DIM_DELAY_MS + PREVIEW_START_AFTER_DIM_DELAY_MS
        )
    }

    @Test
    fun returningToScreenRejectsOldPreviewTimerAndAcceptsNewSession() {
        assertFalse(
            shouldStartPreview(
                scheduledSession = 4,
                currentSession = 5,
                isScreenResumed = true,
                hasFocusedContent = true,
                previewEnabled = true
            )
        )
        assertTrue(
            shouldStartPreview(
                scheduledSession = 5,
                currentSession = 5,
                isScreenResumed = true,
                hasFocusedContent = true,
                previewEnabled = true
            )
        )
    }

    @Test
    fun pausedOrUnfocusedScreenCannotStartPreview() {
        assertFalse(shouldStartPreview(3, 3, false, true, previewEnabled = true))
        assertFalse(shouldStartPreview(3, 3, true, false, previewEnabled = true))
    }

    @Test
    fun disabledPreferenceRejectsPreviewAndKeepsCardsOpaque() {
        assertFalse(shouldStartPreview(3, 3, true, true, previewEnabled = false))
        assertEquals(
            1f,
            previewCardAlpha(
                rowFocused = true,
                selected = false,
                dimUnselected = true,
                previewActive = true,
                previewEnabled = false
            )
        )
    }

    @Test
    fun enabledPreviewUsesSharedDimAndPlaybackOpacity() {
        assertEquals(0.28f, previewCardAlpha(true, false, true, false, true))
        assertEquals(0.10f, previewCardAlpha(true, false, true, true, true))
        assertEquals(1f, previewCardAlpha(true, true, true, true, true))
    }

    @Test
    fun homePreviewOnlyBecomesVisibleForTheFocusedRowSelection() {
        assertTrue(
            isHomePreviewPlaybackActive(
                previewEnabled = true,
                isScreenResumed = true,
                hasFocusedRow = true,
                previewArmed = true,
                firstFrameReady = true,
                readyAnimeId = "row-anime",
                readySourceId = "source-a",
                focusedAnimeId = "row-anime",
                focusedSourceId = "source-a"
            )
        )
        assertFalse(
            isHomePreviewPlaybackActive(
                previewEnabled = true,
                isScreenResumed = true,
                hasFocusedRow = false,
                previewArmed = true,
                firstFrameReady = true,
                readyAnimeId = "row-anime",
                readySourceId = "source-a",
                focusedAnimeId = "featured-anime",
                focusedSourceId = "source-a"
            )
        )
        assertFalse(
            isHomePreviewPlaybackActive(
                previewEnabled = true,
                isScreenResumed = true,
                hasFocusedRow = true,
                previewArmed = true,
                firstFrameReady = true,
                readyAnimeId = "row-anime",
                readySourceId = "source-a",
                focusedAnimeId = "row-anime",
                focusedSourceId = "source-b"
            )
        )
    }

    @Test
    fun timelinePreviewRejectsAReadyVideoFromThePreviousSelection() {
        assertFalse(
            isPreviewPlaybackActive(
                previewEnabled = true,
                isScreenResumed = true,
                hasFocusedContent = true,
                previewArmed = true,
                firstFrameReady = true,
                readyAnimeId = "old-anime",
                readySourceId = "source-a",
                focusedAnimeId = "new-anime",
                focusedSourceId = "source-a"
            )
        )
    }

    @Test
    fun previewLoadRetriesOnlyTheCurrentRequestOnce() {
        assertTrue(shouldRetryPreviewLoad("preview-a", "preview-a", retryCount = 0))
        assertFalse(shouldRetryPreviewLoad("preview-a", "preview-a", retryCount = 1))
        assertFalse(shouldRetryPreviewLoad("preview-a", "preview-b", retryCount = 0))
    }
}
