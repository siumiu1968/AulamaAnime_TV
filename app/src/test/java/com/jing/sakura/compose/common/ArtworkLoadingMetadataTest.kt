package com.jing.sakura.compose.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkLoadingMetadataTest {
    @Test
    fun splitsAndDeduplicatesCommonTagFormats() {
        assertEquals(
            listOf("動作", "奇幻", "冒險", "校園"),
            artworkLoadingTagItems("動作、奇幻 / 冒險 | 動作，校園")
        )
    }

    @Test
    fun ignoresBlankTagMetadata() {
        assertEquals(emptyList<String>(), artworkLoadingTagItems("  / 、 |  "))
    }
}
