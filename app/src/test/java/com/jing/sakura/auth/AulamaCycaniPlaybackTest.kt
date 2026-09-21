package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AulamaCycaniPlaybackTest {
    @Test
    fun buildsNumericManifestBridgeUrlWithoutEmbeddingTheDirectManifest() {
        assertEquals(
            "https://aulama.org/anime/api/cycani/sections/51796/manifest.m3u8",
            buildCycaniManifestBridgeUrl("https://aulama.org/anime/api", "51796")
        )
        assertNull(
            buildCycaniManifestBridgeUrl(
                "https://aulama.org/anime/api",
                "https://vhub.babel.gold/episode-19.m3u8"
            )
        )
    }

    @Test
    fun parsesDirectUrlResponse() {
        assertEquals(
            "https://cdn.example/episode.m3u8",
            parseCycaniPlaybackUrl("""{"url":"https://cdn.example/episode.m3u8"}""")
        )
    }

    @Test
    fun parsesWrappedUrlResponseForCompatibility() {
        assertEquals(
            "https://cdn.example/episode.m3u8",
            parseCycaniPlaybackUrl(
                """{"ok":true,"data":{"url":"https://cdn.example/episode.m3u8"}}"""
            )
        )
    }

    @Test
    fun rejectsMissingOrMalformedUrlResponse() {
        assertNull(parseCycaniPlaybackUrl("""{"data":{}}"""))
        assertNull(parseCycaniPlaybackUrl("not-json"))
    }

    @Test
    fun preservesBackendMediaKindForPlaybackSelection() {
        assertEquals(
            CycaniPlaybackUrl(
                url = "https://cdn.example/signed/episode.mp3?token=opaque",
                mediaKind = "hls"
            ),
            parseCycaniPlaybackUrlPayload(
                """{"data":{"url":"https://cdn.example/signed/episode.mp3?token=opaque","mediaKind":"hls"}}"""
            )
        )
    }

    @Test
    fun retriesOnlyTransientPlayUrlStatusesAndRefreshesRetryRequests() {
        listOf(408, 429, 502, 503, 504).forEach {
            assertTrue(CycaniPlaybackUrlRetryPolicy.shouldRetryStatus(it))
        }
        assertFalse(CycaniPlaybackUrlRetryPolicy.shouldRetryStatus(500))
        assertFalse(CycaniPlaybackUrlRetryPolicy.shouldForceRefresh(0))
        assertTrue(CycaniPlaybackUrlRetryPolicy.shouldForceRefresh(1))
        assertTrue(CycaniPlaybackUrlRetryPolicy.shouldForceRefresh(2))
        assertEquals(180L, CycaniPlaybackUrlRetryPolicy.retryDelayAfter(0))
        assertEquals(450L, CycaniPlaybackUrlRetryPolicy.retryDelayAfter(1))
        assertNull(CycaniPlaybackUrlRetryPolicy.retryDelayAfter(2))
    }
}
