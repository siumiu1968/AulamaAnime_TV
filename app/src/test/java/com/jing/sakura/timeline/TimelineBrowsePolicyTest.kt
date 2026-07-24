package com.jing.sakura.timeline

import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.UpdateTimeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBrowsePolicyTest {
    @Test
    fun carouselKeepsALargeLoopAroundTheSelectedItem() {
        val start = timelineInitialVirtualIndex(itemCount = 7)

        assertTrue(start > 7)
        assertEquals(0, timelineLogicalIndex(start, itemCount = 7))
        assertEquals(1, timelineLogicalIndex(start + 1, itemCount = 7))
        assertEquals(6, timelineLogicalIndex(start - 1, itemCount = 7))
    }

    @Test
    fun prefetchIsLimitedToSelectedAndAdjacentDays() {
        val data = UpdateTimeLine(
            current = 3,
            timeline = (0 until 7).map { day ->
                "day-$day" to (0 until 8).map { item -> anime("$day-$item") }
            }
        )

        val prefetched = timelinePrefetchAnime(data, selectedDayIndex = 0)

        assertEquals(15, prefetched.size)
        assertEquals(
            setOf("0", "1", "6"),
            prefetched.map { it.id.substringBefore('-') }.toSet()
        )
    }

    @Test
    fun japaneseCopyNeverAppearsBeforeLocalizedSynopsis() {
        val japanese = "これは日本語の作品紹介です"

        assertEquals("", resolveTimelineSynopsis(japanese, null))
        assertEquals(
            "這是預先載入的繁體中文簡介",
            resolveTimelineSynopsis(japanese, "這是預先載入的繁體中文簡介")
        )
        assertTrue(japanese.requiresTimelineTranslation())
        assertFalse("這是中文簡介".requiresTimelineTranslation())
    }

    private fun anime(id: String) = AnimeData(
        id = id,
        url = "https://example.test/$id",
        title = id,
        imageUrl = "https://example.test/$id.jpg",
        sourceId = "test"
    )
}
