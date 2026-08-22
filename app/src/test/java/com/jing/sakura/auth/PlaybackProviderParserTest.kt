package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProviderParserTest {
    @Test
    fun parsesTheSameFiveProviderContractAsWeb() {
        val providers = PlaybackProviderParser.parseProviders(
            """{"ok":true,"providers":[
                {"id":"cycani","available":true,"matchedTitle":"主線","episodeCount":0,"lineCount":1},
                {"id":"girigiri_cht","available":true,"matchedTitle":"繁中版本","episodeCount":7,"lineCount":1},
                {"id":"girigiri_chs","available":false,"episodeCount":0,"lineCount":0,"reason":"未提供簡中"},
                {"id":"sakura","available":true,"matchedTitle":"別名","episodeCount":12,"lineCount":3},
                {"id":"age","available":false,"episodeCount":0,"lineCount":0,"reason":"未配對"}
            ]}"""
        )

        assertEquals(
            listOf("cycani", "girigiri_cht", "girigiri_chs", "sakura", "age"),
            providers.map { it.id }
        )
        assertTrue(providers[1].available)
        assertEquals(7, providers[1].episodeCount)
        assertFalse(providers[2].available)
    }

    @Test
    fun acceptsDirectExternalPlaybackButRejectsEmbedOnlyResponse() {
        val direct = PlaybackProviderParser.parseSource(
            """{"ok":true,"provider":"girigiri_cht","directUrl":"https://media.example/a.m3u8","sourceLine":"繁中"}"""
        )
        val embedOnly = PlaybackProviderParser.parseSource(
            """{"ok":true,"provider":"age","embedUrl":"https://embed.example/player"}"""
        )

        assertEquals("girigiri_cht", direct?.provider)
        assertEquals("https://media.example/a.m3u8", direct?.url)
        assertNull(embedOnly)
    }
}
