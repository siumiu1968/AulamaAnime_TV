package com.jing.sakura.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisPolicyTest {
    @Test
    fun currentCatalogChineseSynopsisWins() {
        assertEquals(
            "目前目錄的繁體中文簡介",
            selectNonJapaneseSynopsis(
                "目前目錄的繁體中文簡介",
                "來源詳情的中文簡介"
            )
        )
    }

    @Test
    fun japaneseSynopsisIsNeverSelected() {
        assertEquals("", selectNonJapaneseSynopsis("これは日本語の作品紹介です"))
    }

    @Test
    fun laterChineseFallbackIsSelectedAfterJapaneseCandidate() {
        assertEquals(
            "來源詳情的中文簡介",
            selectNonJapaneseSynopsis(
                "これは日本語の作品紹介です",
                "來源詳情的中文簡介"
            )
        )
    }

    @Test
    fun relatedJapaneseSynopsisStillRequiresEnrichment() {
        assertTrue(shouldFetchSynopsisEnrichment("これは日本語の作品紹介です"))
        assertTrue(shouldFetchSynopsisEnrichment(""))
        assertFalse(shouldFetchSynopsisEnrichment("已經有中文簡介"))
    }
}
