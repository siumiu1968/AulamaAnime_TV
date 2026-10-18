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
    fun `source metadata cannot append generic recommendations`() {
        val cloud = listOf(anime(id = "a"))
        val source = listOf(anime(id = "a"), anime(id = "c", description = "簡介 C"))

        val merged = mergeRelatedAnime(cloud, source, fallbackSourceId = "20")

        assertEquals(listOf("a"), merged.map(AnimeData::id))
        assertEquals(listOf("20"), merged.map(AnimeData::sourceId))
        assertEquals("", merged.single().description)
    }

    @Test
    fun `empty verified related result never falls back to source recommendations`() {
        assertEquals(emptyList<AnimeData>(), mergeRelatedAnime(
            cloud = emptyList(), source = listOf(anime("generic")), fallbackSourceId = "20"
        ))
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
