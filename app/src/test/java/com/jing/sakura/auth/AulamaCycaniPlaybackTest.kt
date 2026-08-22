package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
