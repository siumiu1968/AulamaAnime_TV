package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AulamaCycaniPlaybackTest {
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
