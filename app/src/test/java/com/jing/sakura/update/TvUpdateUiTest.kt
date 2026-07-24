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
}
