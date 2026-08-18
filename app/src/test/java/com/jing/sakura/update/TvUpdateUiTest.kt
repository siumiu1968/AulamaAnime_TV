package com.jing.sakura.update

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvUpdateUiTest {
    @Test
    fun calculatesDownloadProgressSafely() {
        val halfway = TvDownloadSnapshot(
            status = DownloadManager.STATUS_RUNNING,
            downloadedBytes = 5_000L,
            totalBytes = 10_000L,
            reason = 0
        )
        val unknownSize = halfway.copy(totalBytes = 0L)

        assertEquals(50, halfway.percent)
        assertEquals(null, unknownSize.percent)
    }

    @Test
    fun downloadWithNoProgressTimesOutInsteadOfStayingAtZeroForever() {
        val tracker = TvDownloadStallTracker(initialTimeMs = 1_000L, timeoutMs = 90_000L)

        assertFalse(tracker.hasStalled(downloadedBytes = 0L, nowMs = 90_999L))
        assertTrue(tracker.hasStalled(downloadedBytes = 0L, nowMs = 91_000L))
    }

    @Test
    fun downloadProgressRestartsTheStallWindow() {
        val tracker = TvDownloadStallTracker(initialTimeMs = 1_000L, timeoutMs = 90_000L)

        assertFalse(tracker.hasStalled(downloadedBytes = 1_024L, nowMs = 80_000L))
        assertFalse(tracker.hasStalled(downloadedBytes = 1_024L, nowMs = 169_999L))
        assertTrue(tracker.hasStalled(downloadedBytes = 1_024L, nowMs = 170_000L))
    }

    @Test
    fun turnsMarkdownIntoDisplayItemsWithoutRawMarkers() {
        val items = parseTvReleaseNotes(
            """
            ## 今次更新
            - 修復 **下載進度**
            - 支援 [更新頁面](https://aulama.org)
            """.trimIndent()
        )

        assertEquals(TvReleaseNoteKind.Heading, items.first().kind)
        assertEquals("今次更新", items.first().text)
        assertEquals(2, items.count { it.kind == TvReleaseNoteKind.Bullet })
        assertTrue(items.any { it.text == "修復 下載進度" })
        assertTrue(items.any { it.text == "支援 更新頁面" })
        assertFalse(items.any { it.text.contains('#') || it.text.contains("**") })
    }

    @Test
    fun scrollsLongReleaseNotesWithVerticalDpadOnlyWhenPossible() {
        assertEquals(
            120f,
            releaseNotesScrollDelta(
                direction = 1,
                canScrollBackward = false,
                canScrollForward = true,
                stepPx = 120f
            ),
            0.001f
        )
        assertEquals(
            -120f,
            releaseNotesScrollDelta(
                direction = -1,
                canScrollBackward = true,
                canScrollForward = false,
                stepPx = 120f
            ),
            0.001f
        )
        assertEquals(
            0f,
            releaseNotesScrollDelta(
                direction = 1,
                canScrollBackward = true,
                canScrollForward = false,
                stepPx = 120f
            ),
            0.001f
        )
    }
}
