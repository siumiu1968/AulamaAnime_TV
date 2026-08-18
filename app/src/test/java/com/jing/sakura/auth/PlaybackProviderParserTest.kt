package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProviderParserTest {
    @Test
    fun parsesTheSameThreeProviderContractAsWeb() {
        val providers = PlaybackProviderParser.parseProviders(
            """{"ok":true,"providers":[
                {"id":"cycani","available":true,"matchedTitle":"主線","episodeCount":0,"lineCount":1},
                {"id":"sakura","available":true,"matchedTitle":"別名","episodeCount":12,"lineCount":3},
                {"id":"age","available":false,"episodeCount":0,"lineCount":0,"reason":"未配對"}
            ]}"""
        )

        assertEquals(listOf("cycani", "sakura", "age"), providers.map { it.id })
        assertTrue(providers[1].available)
        assertEquals(12, providers[1].episodeCount)
        assertFalse(providers[2].available)
    }

    @Test
    fun acceptsDirectExternalPlaybackButRejectsEmbedOnlyResponse() {
        val direct = PlaybackProviderParser.parseSource(
            """{"ok":true,"provider":"age","directUrl":"https://media.example/a.m3u8","sourceLine":"AGE"}"""
        )
        val embedOnly = PlaybackProviderParser.parseSource(
            """{"ok":true,"provider":"age","embedUrl":"https://embed.example/player"}"""
        )

        assertEquals("https://media.example/a.m3u8", direct?.url)
        assertNull(embedOnly)
    }
}
