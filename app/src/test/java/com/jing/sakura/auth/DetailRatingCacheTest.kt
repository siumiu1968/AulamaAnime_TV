package com.jing.sakura.auth

import com.jing.sakura.detail.DetailPageCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DetailRatingCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private class Storage : AulamaAuthStorage {
        override fun loadSession() = AuthSession("test-token", "Bearer", Long.MAX_VALUE,
            AulamaAccount("Test", "free", "a@example.com"))
        override fun saveSession(session: AuthSession) = Unit
        override fun clearSession() = Unit
        override fun stableDeviceId() = "test-device"
    }
    private fun repository(server: MockWebServer): AulamaAuthRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            val url = server.url("/").newBuilder().encodedPath(original.url.encodedPath).build()
            chain.proceed(original.newBuilder().url(url).build())
        }.build()
        return AulamaAuthRepository(client, Storage(), DetailPageCache(temporary.newFolder()))
    }

    @Test fun repeatVisitsReuseStarsAndAPlayerRatingSaveUpdatesTheSameCache() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            server.enqueue(MockResponse().setBody("""{"items":[{"animeId":"cw:3963","rating":4}]}"""))
            assertEquals(4, repository.fetchMyRating("cw:3963"))
            assertEquals(4, repository.fetchMyRating("cw:3963"))
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(repository.saveRating(AnimeRatingPayload("cw:3963", "Test", "", 5,
                emptyList(), "", 0.0, "cycani", "")))
            assertEquals(5, repository.fetchMyRating("cw:3963"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun temporaryFailureIsRetriedButASuccessfulUnratedResultIsCached() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = repository(server)
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""{"items":[]}"""))
            assertNull(repository.fetchMyRating("cw:3963"))
            assertEquals(0, repository.fetchMyRating("cw:3963"))
            assertEquals(0, repository.fetchMyRating("cw:3963"))
            assertEquals(2, server.requestCount)
        }
    }
}
