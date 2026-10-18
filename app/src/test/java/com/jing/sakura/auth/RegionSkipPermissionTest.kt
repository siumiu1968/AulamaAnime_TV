package com.jing.sakura.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RegionSkipPermissionTest {
    private class Storage : AulamaAuthStorage {
        var current: AuthSession? = AuthSession(
            "test-token", "Bearer", Long.MAX_VALUE, AulamaAccount("Test", "super_admin")
        )
        override fun loadSession() = current
        override fun saveSession(session: AuthSession) { current = session }
        override fun clearSession() { current = null }
        override fun stableDeviceId() = "test-device"
    }

    private fun client(server: MockWebServer) = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val local = server.url("/").newBuilder()
                .encodedPath(request.url.encodedPath).encodedQuery(request.url.encodedQuery).build()
            chain.proceed(request.newBuilder().url(local).build())
        }.build()

    @Test
    fun localHighestRoleCannotShowSkipWithoutServerPermission() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"can_skip":false}"""))
            val repository = AulamaAuthRepository(client(server), Storage())
            assertFalse(repository.refreshRegionSkipPermission())
            assertFalse(repository.canSkipRegion.value)
            val request = server.takeRequest()
            assertEquals("/hermes-auth/anime-region/status", request.path)
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun onlyVerifiedSkipClearsBlockAndLogoutRevokesButton() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(403).setHeader(REGION_BLOCK_HEADER, "1"))
            server.enqueue(MockResponse().setBody("""{"can_skip":true}"""))
            val repository = AulamaAuthRepository(client(server), Storage())
            assertEquals(RegionAccessProbeResult.Blocked, repository.probeRegionAccess())
            assertTrue(repository.canSkipRegion.value)
            assertEquals(null, server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"can_skip":true}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertEquals(RegionAccessProbeResult.Allowed, repository.skipRegionRestriction())
            server.takeRequest()
            assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
            assertEquals(null, repository.regionBlock.value)
            repository.clearSession()
            assertFalse(repository.canSkipRegion.value)
        }
    }

    @Test
    fun revokedOrUnavailablePermissionDoesNotMakeAnExemptionRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = AulamaAuthRepository(client(server), Storage())
            listOf(401, 403, 503).forEach { status ->
                server.enqueue(MockResponse().setResponseCode(status))
                assertEquals(RegionAccessProbeResult.Blocked, repository.skipRegionRestriction())
                assertFalse(repository.canSkipRegion.value)
                assertEquals("/hermes-auth/anime-region/status", server.takeRequest().path)
            }
            assertEquals(3, server.requestCount)
        }
    }
}
