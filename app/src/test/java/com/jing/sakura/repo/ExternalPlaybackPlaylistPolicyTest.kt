package com.jing.sakura.repo

import com.jing.sakura.auth.AulamaPlaybackProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlaybackPlaylistPolicyTest {
    @Test
    fun keepsCatalogIdentityAndShowsOnlyAvailableLanguageLinesInWebOrder() {
        val playlists = ExternalPlaybackPlaylistPolicy.playlists(
            catalogAnimeId = "12781",
            providers = listOf(
                provider("age", available = false, episodeCount = 0),
                provider("girigiri_chs", available = true, episodeCount = 1),
                provider("sakura", available = true, episodeCount = 0),
                provider("girigiri_cht", available = true, episodeCount = 7)
            ),
            canonicalEpisodeLabels = listOf("第1集", "第2集", "第3集")
        )

        assertEquals(
            listOf("girigiri_cht", "girigiri_chs", "sakura"),
            playlists.map { it.providerId }
        )
        assertEquals(listOf(7, 1, 3), playlists.map { it.episodes.size })
        assertTrue(playlists.flatMap { it.episodes }.all { it.catalogAnimeId == "12781" })
        assertEquals((0 until 7).toList(), playlists.first().episodes.map { it.index })
    }

    @Test
    fun omitsGirigiriLanguageLineWithoutPlayableEpisodes() {
        val playlists = ExternalPlaybackPlaylistPolicy.playlists(
            catalogAnimeId = "12781",
            providers = listOf(provider("girigiri_cht", available = true, episodeCount = 0)),
            canonicalEpisodeLabels = listOf("第1集")
        )

        assertTrue(playlists.isEmpty())
    }

    @Test
    fun girigiriOnlyNamespaceUsesAuthenticatedDetailAndKeepsItsCatalogId() {
        assertTrue(usesAuthenticatedAulamaDetail("gg:GV27102"))
        val playlists = ExternalPlaybackPlaylistPolicy.playlists(
            catalogAnimeId = "gg:GV27102",
            providers = listOf(
                provider("girigiri_cht", available = true, episodeCount = 2),
                provider("girigiri_chs", available = true, episodeCount = 1)
            ),
            canonicalEpisodeLabels = listOf("第01集", "第02集")
        )

        assertEquals(listOf("girigiri_cht", "girigiri_chs"), playlists.map { it.providerId })
        assertTrue(
            playlists.flatMap { it.episodes }.all { it.catalogAnimeId == "gg:GV27102" }
        )
    }

    @Test
    fun currentCycaniNamespaceUsesWebsiteDetailAndKeepsItsCatalogIdForBackups() {
        assertTrue(usesAuthenticatedAulamaDetail("cw:3939"))
        assertTrue(usesAuthenticatedAulamaDetail("CW:3939"))
        val playlists = ExternalPlaybackPlaylistPolicy.playlists(
            catalogAnimeId = "cw:3939",
            providers = listOf(
                provider("girigiri_cht", available = true, episodeCount = 5),
                provider("sakura", available = true, episodeCount = 3)
            ),
            canonicalEpisodeLabels = listOf("第01集", "第02集", "第03集")
        )

        assertEquals(listOf("girigiri_cht", "sakura"), playlists.map { it.providerId })
        assertEquals(listOf(5, 3), playlists.map { it.episodes.size })
        assertTrue(playlists.flatMap { it.episodes }.all { it.catalogAnimeId == "cw:3939" })
        assertEquals((0 until 5).toList(), playlists.first().episodes.map { it.index })
        assertEquals("第5集", playlists.first().episodes.last().label)
    }

    @Test
    fun legacyNumericIdsKeepTheirExistingDetailRoute() {
        assertFalse(usesAuthenticatedAulamaDetail("3939"))
        assertFalse(usesAuthenticatedAulamaDetail("12781"))
    }

    @Test
    fun futureCatalogIdsAvoidTheLegacyNumericDetailEndpoint() {
        assertTrue(usesAuthenticatedAulamaDetail("next:season-A-2027"))
        assertTrue(usesAuthenticatedAulamaDetail("new-catalog-title"))
        assertTrue(usesAuthenticatedAulamaDetail("３９３９"))
        assertFalse(usesAuthenticatedAulamaDetail(""))
    }

    private fun provider(
        id: String,
        available: Boolean,
        episodeCount: Int
    ) = AulamaPlaybackProvider(
        id = id,
        available = available,
        matchedTitle = "",
        episodeCount = episodeCount,
        lineCount = if (available) 1 else 0,
        reason = ""
    )
}
