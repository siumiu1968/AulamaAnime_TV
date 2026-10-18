package com.jing.sakura.auth

import com.jing.sakura.detail.buildEpisodeProgress
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DetailHistoryTest {
    private class Storage : AulamaAuthStorage {
        override fun loadSession() = AuthSession("test-token", "Bearer", Long.MAX_VALUE,
            AulamaAccount("Test", "free", "a@example.com"))
        override fun saveSession(session: AuthSession) = Unit
        override fun clearSession() = Unit
        override fun stableDeviceId() = "test-device"
    }

    @Test fun webCompletionIsSharedAcrossTvSourcesWithoutLoadingFavoritesOrScores() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }.build()
            val repository = AulamaAuthRepository(client, Storage())
            fun enqueue(secondComplete: Boolean) = server.enqueue(MockResponse().setBody("""
                {"items":[{"animeId":"cw:3946","animeTitle":"藥屋少女的呢喃 第三季",
                "sourceTypeId":"20","episodeId":"web:3","episodeIndex":2,
                "episodeLabel":"第03集","currentTime":300,"duration":1200,
                "episodeProgress":{"0":{"completed":true,"currentTime":0},
                "1":{"completed":$secondComplete,"currentTime":0}}}]}
            """.trimIndent()))
            enqueue(false)
            val first = checkNotNull(repository.fetchTvHistoryItem("cw:3946"))
            assertEquals("20", first.sourceTypeId)
            assertEquals(1f, buildEpisodeProgress(emptyList(), first)[1])
            assertEquals(0.25f, buildEpisodeProgress(emptyList(), first)[3])
            enqueue(true)
            val updated = checkNotNull(repository.fetchTvHistoryItem("cw:3946"))
            assertEquals(1f, buildEpisodeProgress(emptyList(), updated)[2])
            assertEquals(2, server.requestCount)
            repeat(2) { assertEquals("/anime/api/history", server.takeRequest().path) }
        }
    }

    @Test fun anotherTitleCannotSupplyTheEpisodeProgress() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/history")).build())
            }.build()
            server.enqueue(MockResponse().setBody("""{"items":[{"animeId":"cw:999","title":"Other","completed":true}]}"""))
            assertNull(AulamaAuthRepository(client, Storage()).fetchTvHistoryItem("cw:3946"))
        }
    }
}
