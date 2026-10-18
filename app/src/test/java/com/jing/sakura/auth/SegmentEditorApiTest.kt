package com.jing.sakura.auth

import com.google.gson.JsonParser
import com.jing.sakura.player.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SegmentEditorApiTest {
    private class Storage(role: String = "super_admin") : AulamaAuthStorage {
        var current: AuthSession? = AuthSession("test-token", "Bearer", Long.MAX_VALUE, AulamaAccount("Test", role))
        override fun loadSession() = current
        override fun saveSession(session: AuthSession) { current = session }
        override fun clearSession() { current = null }
        override fun stableDeviceId() = "test-device"
    }
    private val target = SegmentEditorTarget("cw:3946", "cycapi:test", 1, "第02集", "girigiri_cht", "主線路B 繁中")
    private val response = """{"ok":true,"item":null,"rawSegments":{"all":{"introStart":1.2,"introEnd":90},"provider":null},"permissions":{"canEdit":true}}"""
    private fun repository(server: MockWebServer, storage: Storage = Storage()) = AulamaAuthRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            chain.proceed(request.newBuilder().url(server.url("/").newBuilder()
                .encodedPath(request.url.encodedPath).encodedQuery(request.url.encodedQuery).build()).build())
        }.build(), storage)

    @Test fun roleAndServerDenialCannotOpenOrWrite() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (role in listOf("admin", "member", "free")) {
                val repo = repository(server, Storage(role))
                assertTrue(runCatching { repo.fetchSegmentEditor(target) }.isFailure)
                assertTrue(runCatching { repo.saveSegmentEditor(target, SegmentDraft(), 100000, true) }.isFailure)
            }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"permissions":{"canEdit":false}}"""))
            assertTrue(runCatching { repository(server).fetchSegmentEditor(target) }.isFailure)
        }
    }

    @Test fun authenticatedReadAndWriteUseExactEpisodeLineScopeAndFractionalSeconds() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val repo = repository(server)
            server.enqueue(MockResponse().setBody(response))
            val existing = repo.fetchSegmentEditor(target)
            val read = server.takeRequest()
            assertEquals("Bearer test-token", read.getHeader("Authorization"))
            assertEquals("girigiri_cht", read.requestUrl!!.queryParameter("provider"))
            assertEquals("1", read.requestUrl!!.queryParameter("episodeIndex"))
            for (all in listOf(true, false)) {
                server.enqueue(MockResponse().setBody(response))
                repo.saveSegmentEditor(target, existing.all, 120000, all)
                val write = server.takeRequest()
                assertEquals("PUT", write.method)
                assertEquals("Bearer test-token", write.getHeader("Authorization"))
                val body = JsonParser.parseString(write.body.readUtf8()).asJsonObject
                assertEquals(if (all) "all" else "provider", body["scope"].asString)
                assertEquals(target.provider, body["provider"].asString)
                assertEquals(target.episodeId, body["episodeId"].asString)
                assertEquals(1.2, body["introStart"].asDouble, 0.0001)
                assertTrue(body["outroStart"].isJsonNull)
            }
        }
    }

    @Test fun bothExplicitAbsencesRetrySafelyAfterPartialFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val repo = repository(server)
            val cleared = SegmentDraft().clear("intro").clear("outro")
            server.enqueue(MockResponse().setBody(response))
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { repo.saveSegmentEditor(target, cleared, 120000, true) }.isFailure)
            repeat(2) { server.enqueue(MockResponse().setBody(response)) }
            repo.saveSegmentEditor(target, cleared, 120000, true)
            assertEquals(4, server.requestCount)
            repeat(2) {
                for (kind in listOf("intro", "outro")) {
                    val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
                    assertEquals(kind, body["clearSegment"].asString)
                    assertTrue(body["introStart"].isJsonNull)
                }
            }
        }
    }

    @Test fun revokedSessionCannotAppearAsSavedAndInvalidRangesNeverReachServer() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val storage = Storage(); val repo = repository(server, storage)
            val incomplete = SegmentDraft().set(SegmentMarker.INTRO_START, 1000)
            assertTrue(runCatching { repo.saveSegmentEditor(target, incomplete, 120000, true) }.isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
            assertTrue(runCatching { repo.saveSegmentEditor(target, SegmentDraft(), 120000, true) }.isFailure)
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            assertTrue(runCatching { repo.saveSegmentEditor(target, SegmentDraft(), 120000, true) }.isFailure)
            assertNull(repo.session.value)
            assertNull(storage.current)
            assertEquals(2, server.requestCount)
        }
    }
}
