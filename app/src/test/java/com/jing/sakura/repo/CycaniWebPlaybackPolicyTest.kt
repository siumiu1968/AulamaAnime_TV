package com.jing.sakura.repo

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycaniWebPlaybackPolicyTest {
    @Test
    fun authenticatedPlaybackFailureNeverRepeatsTheSameDirectEndpoint() {
        assertFalse(shouldUseDirectCycaniPlayUrlFallback(hasAuthRepository = true))
        assertTrue(shouldUseDirectCycaniPlayUrlFallback(hasAuthRepository = false))
    }

    @Test
    fun webPlaybackFailureDoesNotFallBackToTheLegacySourcePath() {
        assertEquals(
            CycaniPlaybackResolutionPath.WEB_SECTION,
            CycaniPlaybackResolutionPolicy.select(
                directUrl = "",
                webSectionId = "51500",
                hasWebRequest = true
            )
        )
        assertEquals(
            CycaniPlaybackResolutionPath.WEB_MATCH,
            CycaniPlaybackResolutionPolicy.select(
                directUrl = "",
                webSectionId = "",
                hasWebRequest = true
            )
        )
        assertEquals(
            CycaniPlaybackResolutionPath.UNAVAILABLE,
            CycaniPlaybackResolutionPolicy.select("", "", false)
        )
    }

    @Test
    fun blocksOnlyTheRetiredOldPcEndpoint() {
        assertTrue(isRetiredCycaniOldPcUrl("https://vhub.babel.gold/oldpc?url=signed"))
        assertTrue(isRetiredCycaniOldPcUrl("https://VHub.Babel.Gold/oldpc/"))
        assertFalse(isRetiredCycaniOldPcUrl("https://vhub.babel.gold/hls/video.m3u8"))
        assertFalse(isRetiredCycaniOldPcUrl("https://vhub.babel.gold/oldpc-safe"))
        assertFalse(isRetiredCycaniOldPcUrl("https://vhub-babel.gold/oldpc"))
    }

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

    @Test
    fun bridgesOnlyHttpsVhubHlsManifests() {
        val bridge = "https://aulama.org/anime/api/cycani/sections/51796/manifest.m3u8"
        assertEquals(
            bridge,
            CycaniWebPlaybackPolicy.selectMainSectionPlaybackUrl(
                "https://vhub.babel.gold/hls/episode-19/index.m3u8?token=signed",
                bridge
            )
        )
        assertEquals(
            "https://media.example/episode-19.m3u8",
            CycaniWebPlaybackPolicy.selectMainSectionPlaybackUrl(
                "https://media.example/episode-19.m3u8",
                bridge
            )
        )
        assertEquals(
            "https://vhub.babel.gold/video/episode-19.mp4",
            CycaniWebPlaybackPolicy.selectMainSectionPlaybackUrl(
                "https://vhub.babel.gold/video/episode-19.mp4",
                bridge
            )
        )
        assertEquals(
            "http://vhub.babel.gold/hls/episode-19.m3u8",
            CycaniWebPlaybackPolicy.selectMainSectionPlaybackUrl(
                "http://vhub.babel.gold/hls/episode-19.m3u8",
                bridge
            )
        )
    }

    @Test
    fun updatesLegacyArtworkFromTheCurrentWebCatalogue() {
        val index = CycaniWebPlaybackPolicy.buildArtworkIndex(rows(
            """[
              {
                "video_id": "3772",
                "title": "关于我转生变成史莱姆这档事 第四季",
                "year": "2026",
                "cover_url": "https://img2.cycimg.me/pic/cover/l/6c/a4/515594_ZRYPc.jpg"
              }
            ]"""
        ))
        assertEquals(
            "https://img2.cycimg.me/pic/cover/l/6c/a4/515594_ZRYPc.jpg",
            CycaniWebPlaybackPolicy.latestArtwork(
                "關於我轉生變成史萊姆這檔事 第四季",
                "2026",
                index
            )
        )
    }

    @Test
    fun refusesAmbiguousTitleOnlyArtworkMatches() {
        val index = CycaniWebPlaybackPolicy.buildArtworkIndex(rows(
            """[
              {"video_id":"1","title":"同名作品","year":"2025","cover_url":"https://img.example/one.jpg"},
              {"video_id":"2","title":"同名作品","year":"2026","cover_url":"https://img.example/two.jpg"}
            ]"""
        ))
        assertEquals("", CycaniWebPlaybackPolicy.latestArtwork("同名作品", "", index))
        assertEquals(
            "https://img.example/two.jpg",
            CycaniWebPlaybackPolicy.latestArtwork("同名作品", "2026", index)
        )
    }

    private fun rows(json: String) = JsonParser.parseString(json).asJsonArray.map { it.asJsonObject }
}
