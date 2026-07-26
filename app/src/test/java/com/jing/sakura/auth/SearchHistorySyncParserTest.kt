package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistorySyncParserTest {
    @Test
    fun `sorts server records newest first and keeps five`() {
        val items = (1..7).joinToString(",") { index ->
            """{"keyword":"搜尋 $index","updatedAt":"2026-07-26T12:00:0${index}.000Z"}"""
        }

        val result = SearchHistorySyncParser.parse("""{"ok":true,"items":[$items]}""")

        assertEquals(listOf("搜尋 7", "搜尋 6", "搜尋 5", "搜尋 4", "搜尋 3"), result.map { it.keyword })
    }

    @Test
    fun `ignores malformed records`() {
        val result = SearchHistorySyncParser.parse(
            """{"items":[{"keyword":""},{"keyword":"日常","updatedAt":"2026-07-26T12:00:00Z"}]}"""
        )

        assertEquals(listOf("日常"), result.map { it.keyword })
    }
}
