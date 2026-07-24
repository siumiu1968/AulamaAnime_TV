package com.jing.sakura.detail

import com.jing.sakura.data.AnimeData
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedAnimeMergeTest {

    @Test
    fun `fills blank cloud fields from source while preserving cloud order`() {
        val cloud = listOf(
            anime(id = "b", title = "雲端 B"),
            anime(id = "a", title = "雲端 A")
        )
        val source = listOf(
            anime(id = "a", title = "來源 A", description = "簡介 A"),
            anime(id = "b", title = "來源 B", description = "簡介 B")
        )

        val merged = mergeRelatedAnime(cloud, source, fallbackSourceId = "20")

        assertEquals(listOf("b", "a"), merged.map(AnimeData::id))
        assertEquals(listOf("簡介 B", "簡介 A"), merged.map(AnimeData::description))
        assertEquals(listOf("雲端 B", "雲端 A"), merged.map(AnimeData::title))
    }

    @Test
    fun `appends source-only items and applies fallback source id`() {
        val cloud = listOf(anime(id = "a"))
        val source = listOf(anime(id = "a"), anime(id = "c", description = "簡介 C"))

        val merged = mergeRelatedAnime(cloud, source, fallbackSourceId = "20")

        assertEquals(listOf("a", "c"), merged.map(AnimeData::id))
        assertEquals(listOf("20", "20"), merged.map(AnimeData::sourceId))
        assertEquals("簡介 C", merged.last().description)
    }

    private fun anime(
        id: String,
        title: String = "作品 $id",
        description: String = "",
        sourceId: String = ""
    ) = AnimeData(
        id = id,
        url = "",
        title = title,
        description = description,
        sourceId = sourceId
    )
}
