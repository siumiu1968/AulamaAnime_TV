package com.jing.sakura.auth

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AulamaApiSessionTest {
    private val session = AuthSession(
        accessToken = "test-device-token",
        tokenType = "Bearer",
        expiresAtEpochMs = Long.MAX_VALUE,
        account = AulamaAccount(name = "Test", role = "會員")
    )

    @Test
    fun authenticatesRegionProbePublicScheduleAndArtwork() {
        listOf("region-probe", "schedule", "image?url=poster").forEach { path ->
            val request = Request.Builder().url("https://aulama.org/anime/api/$path").build()
            assertEquals("Bearer test-device-token", request.withAulamaApiSession(session).header("Authorization"))
        }
    }

    @Test
    fun neverSendsCredentialsToOtherOriginsPathsPortsOrHttp() {
        listOf(
            "https://cdn.example/anime/api/image",
            "https://aulama.org.evil.example/anime/api/image",
            "http://aulama.org/anime/api/schedule",
            "https://aulama.org:444/anime/api/schedule",
            "https://aulama.org/account/",
            "https://aulama.org/anime/api-elsewhere"
        ).forEach { url ->
            assertNull(Request.Builder().url(url).build().withAulamaApiSession(session).header("Authorization"))
        }
    }

    @Test
    fun guestsAndExpiredSessionsRemainAnonymous() {
        val request = Request.Builder().url("https://aulama.org/anime/api/region-probe").build()
        assertNull(request.withAulamaApiSession(null).header("Authorization"))
        assertNull(request.withAulamaApiSession(session.copy(expiresAtEpochMs = 0)).header("Authorization"))
    }

    @Test
    fun doesNotReplaceAnExplicitRequestIdentity() {
        val request = Request.Builder().url("https://aulama.org/anime/api/device/me")
            .header("Authorization", "Bearer explicitly-selected-session").build()
        assertEquals("Bearer explicitly-selected-session", request.withAulamaApiSession(session).header("Authorization"))
    }
}
