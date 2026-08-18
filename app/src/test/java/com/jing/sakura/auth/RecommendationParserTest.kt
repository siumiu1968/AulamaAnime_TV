package com.jing.sakura.auth

import com.jing.sakura.repo.CycaniSource
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationParserTest {
    @Test
    fun mapsHomeRecommendationsToCycaniAnimeData() {
        val items = RecommendationParser.parse(
            """
            {
              "ok": true,
              "recommendations": [
                {
                  "id": "42",
                  "title": "推薦動畫",
                  "poster": "/anime/api/image?url=https%3A%2F%2Fimg.example.com%2F42.jpg",
                  "currentEpisode": "更新至第 8 集",
                  "summary": "簡介",
                  "tags": ["奇幻", "冒險"]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, items.size)
        assertEquals("42", items.single().id)
        assertEquals("推薦動畫", items.single().title)
        assertEquals("https://img.example.com/42.jpg", items.single().imageUrl)
        assertEquals("奇幻、冒險", items.single().tags)
        assertEquals(CycaniSource.SOURCE_ID, items.single().sourceId)
    }

    @Test
    fun parsesWebsiteSearchResultsWithoutAddingRecommendationSections() {
        val page = RecommendationParser.parseSearchPage(
            """
            {
              "ok": true,
              "total": 1,
              "identifiedAnimeId": "12756",
              "items": [
                {
                  "id": "12756",
                  "title": "無職轉生 第三季 ～到了異世界就拿出真本事～",
                  "poster": "/anime/api/image?url=https%3A%2F%2Fimg.example.com%2Fmushoku.jpg",
                  "currentEpisode": "08 · 週日23:05後",
                  "year": "2026"
                }
              ],
              "related": [{"id": "other", "title": "相關作品"}],
              "recommendations": [{"id": "personal", "title": "為你推薦"}]
            }
            """.trimIndent(),
            requestedPage = 1
        )

        assertEquals(1, page.page)
        assertEquals(false, page.hasNextPage)
        assertEquals(listOf("12756"), page.animeList.map { it.id })
        assertEquals(
            "無職轉生 第三季 ～到了異世界就拿出真本事～",
            page.animeList.single().title
        )
    }
}
