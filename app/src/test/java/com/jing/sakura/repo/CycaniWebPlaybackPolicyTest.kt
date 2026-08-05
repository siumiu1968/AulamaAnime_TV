package com.jing.sakura.repo

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycaniWebPlaybackPolicyTest {
    @Test
    fun requiresExactlyOneTitleAndYearMatch() {
        val match = CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
            expectedTitle = "轉生就是劍",
            expectedYear = "2022",
            candidates = rows(
                """[
                  {"video_id": "11", "title": "转生就是剑", "year": "2022"},
                  {"video_id": "12", "title": "转生就是剑", "year": "2023"}
                ]"""
            )
        )
        assertEquals("11", match?.id)
        assertNull(
            CycaniWebPlaybackPolicy.uniqueTitleYearMatch(
                "轉生就是劍", "2022", rows(
                    """[
                      {"video_id": "11", "title": "转生就是剑", "year": "2022"},
                      {"video_id": "13", "title": "轉生就是劍", "year": "2022"}
                    ]"""
                )
            )
        )
    }

    @Test
    fun selectsOnlyAnUnambiguousEpisodeNumber() {
        val sections = rows(
            """[
              {"id": "a", "title": "第1集"},
              {"id": "b", "title": "第2集"}
            ]"""
        )
        assertEquals("b", CycaniWebPlaybackPolicy.selectExactEpisode("02", sections)?.id)
        assertNull(
            CycaniWebPlaybackPolicy.selectExactEpisode(
                "第1集", rows("""[{"id":"a","title":"第1集"},{"id":"b","title":"EP01"}]""")
            )
        )
    }

    @Test
    fun prefersSameNamedSourceButRetainsFallbackOrder() {
        val ordered = CycaniWebPlaybackPolicy.orderSources(
            rows("""[{"code":"A","title":"備用"},{"code":"B","title":"主線路"}]"""),
            "主線路"
        )
        assertEquals(listOf("B", "A"), ordered.map { it.code })
    }

    @Test
    fun onlyResolvedWebPlaybackReceivesCycaniHeaders() {
        val webHeaders = CycaniWebPlaybackPolicy.playbackHeaders("https://media.example/video.m3u8", true)
        assertEquals("https://www.cycani.org/", webHeaders["Referer"])
        assertEquals("https://www.cycani.org", webHeaders["Origin"])
        val legacyHeaders = CycaniWebPlaybackPolicy.playbackHeaders("https://media.example/video.m3u8", false)
        assertFalse(legacyHeaders.containsKey("Referer"))
        assertTrue(CycaniWebPlaybackPolicy.isTrustedPlaybackUrl("https://media.example/video.m3u8"))
        assertFalse(CycaniWebPlaybackPolicy.isTrustedPlaybackUrl("http://media.example/video.m3u8"))
    }

    private fun rows(json: String) = JsonParser.parseString(json).asJsonArray.map { it.asJsonObject }
}
