package com.jing.sakura.repo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.AulamaAuthStorage
import com.jing.sakura.auth.AuthSession
import com.jing.sakura.data.Resource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

class CycaniCatalogPlaybackTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var source: CycaniSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val localUrl = server.url("/").newBuilder()
                    .encodedPath(original.url.encodedPath)
                    .encodedQuery(original.url.encodedQuery)
                    .build()
                chain.proceed(original.newBuilder().url(localUrl).build())
            }
            .build()
        val repository = AulamaAuthRepository(client, SignedInStorage())
        source = CycaniSource(client, repository)
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun newTitleDetailPlaysItsExactSectionWithoutLegacyLookupOrTitleSearch() = runBlocking {
        enqueue(currentDetail())
        enqueue("""{"ok":true,"url":"https://media.example/goblin-01.mp4"}""")

        val detail = source.fetchDetailPage("cw:3939")

        assertEquals("cw:3939", detail.animeId)
        assertEquals(0, detail.defaultPlayListIndex)
        val main = detail.playLists.first()
        assertEquals("主線路A", main.name)
        assertTrue(main.defaultPlayList)
        assertEquals(listOf("第01集", "第02集", "第03集"), main.episodeList.map { it.episode })
        val payloads = main.episodeList.map { decodePayload(it.episodeId) }
        assertEquals(listOf("52401", "52479", "52516"), payloads.map { it.get("ws").asString })
        assertTrue(payloads.all { it.get("s").asString == "CYC_Main" })
        assertTrue(payloads.all { it.get("u").asString.isEmpty() })
        assertTrue(payloads.all { it.get("wt").asString.isEmpty() })
        assertEquals(1, server.requestCount)

        val backup = detail.playLists[1]
        assertFalse(backup.defaultPlayList)
        assertEquals(5, backup.episodeList.size)
        val backupPayload = decodePayload(backup.episodeList.last().episodeId)
        assertEquals("girigiri_cht", backupPayload.get("xp").asString)
        assertEquals("cw:3939", backupPayload.get("xa").asString)
        assertEquals(4, backupPayload.get("xi").asInt)

        val result = source.fetchVideoUrl("cw:3939", main.episodeList.first().episodeId)

        assertTrue(result is Resource.Success)
        val video = (result as Resource.Success).data
        assertEquals("https://media.example/goblin-01.mp4", video.url)
        assertEquals("https://www.cycani.org/", video.headers["Referer"])
        assertEquals("https://www.cycani.org", video.headers["Origin"])
        assertCatalogAndSectionRequests("cw:3939", "52401")
    }

    @Test
    fun newTitleHlsPlaybackUsesTheManifestBridgeForTheSelectedSection() = runBlocking {
        enqueue(currentDetail())
        enqueue(
            """{"ok":true,"data":{"url":"https://media.example/signed/episode.mp3?token=opaque","mediaKind":"hls"}}"""
        )

        val detail = source.fetchDetailPage("cw:3939")
        val result = source.fetchVideoUrl("cw:3939", detail.playLists.first().episodeList[2].episodeId)

        assertTrue(result is Resource.Success)
        assertEquals(
            "https://aulama.org/anime/api/cycani/sections/52516/manifest.m3u8",
            (result as Resource.Success).data.url
        )
        assertCatalogAndSectionRequests("cw:3939", "52516")
    }

    @Test
    fun futureCatalogNamespacesUseTheWebsiteDetailAndExactSectionPlayback() = runBlocking {
        val id = "next:season-A-2027"
        enqueue(currentDetail(id))
        enqueue("""{"url":"https://media.example/future-01.mp4"}""")

        val detail = source.fetchDetailPage(id)
        val result = source.fetchVideoUrl(id, detail.playLists.first().episodeList.first().episodeId)

        assertTrue(result is Resource.Success)
        assertEquals(id, detail.animeId)
        assertEquals("https://media.example/future-01.mp4", (result as Resource.Success).data.url)
        assertCatalogAndSectionRequests(id, "52401")
    }

    @Test
    fun savedLegacyBase64PayloadsStillPlayWithoutNetworkRequests() = runBlocking {
        val legacyJson = """{"u":"https://media.example/legacy-01.mp4","p":false}"""
        val legacyPayload = "cycapi:" + Base64.getMimeEncoder(20, byteArrayOf(10))
            .encodeToString(legacyJson.toByteArray(Charsets.UTF_8))

        val result = source.fetchVideoUrl("12781", legacyPayload)

        assertTrue(result is Resource.Success)
        assertEquals("https://media.example/legacy-01.mp4", (result as Resource.Success).data.url)
        assertEquals(0, server.requestCount)
    }

    private fun assertCatalogAndSectionRequests(animeId: String, sectionId: String) {
        assertEquals(2, server.requestCount)
        val detailRequest = takeRequest()
        val playbackRequest = takeRequest()
        assertEquals(listOf("anime", "api", "detail", animeId), detailRequest.requestUrl?.pathSegments)
        assertEquals(
            listOf("anime", "api", "cycani", "sections", sectionId, "play-url"),
            playbackRequest.requestUrl?.pathSegments
        )
        assertEquals("Bearer test-access-token", detailRequest.getHeader("Authorization"))
        assertEquals("Bearer test-access-token", playbackRequest.getHeader("Authorization"))
        assertTrue(listOf(detailRequest, playbackRequest).none {
            it.path.orEmpty().contains("/video/info/") || it.path.orEmpty().contains("/videos/search")
        })
    }

    private fun takeRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)) { "Expected backend request" }

    private fun decodePayload(episodeId: String): JsonObject {
        assertTrue(episodeId.startsWith("cycapi:"))
        val raw = Base64.getDecoder().decode(episodeId.removePrefix("cycapi:"))
        return JsonParser.parseString(String(raw, Charsets.UTF_8)).asJsonObject
    }

    private fun enqueue(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private fun currentDetail(id: String = "cw:3939"): String = """
        {"ok":true,"item":{
          "id":"$id","title":"轉生哥布林有問題嗎？","year":"2026",
          "summary":"由哥布林掀起的奇幻故事。","poster":"https://img.example/goblin.jpg",
          "episodes":[
            {"id":"52401","webVideoId":"3939","webSectionId":"52401","webSourceCode":"cychub","label":"第01集","sourceLine":"CYC_Main"},
            {"id":"52479","webVideoId":"3939","webSectionId":"52479","webSourceCode":"cychub","label":"第02集","sourceLine":"CYC_Main"},
            {"id":"52516","webVideoId":"3939","webSectionId":"52516","webSourceCode":"cychub","label":"第03集","sourceLine":"CYC_Main"},
            {"id":"provider-only-4","label":"第04集"},
            {"id":"provider-only-5","label":"第05集"}
          ],
          "playLists":[{"code":"cychub","name":"CYC_Main","count":3}],
          "providerEpisodeCounts":{"cycani":3,"girigiri_cht":5}
        }}
    """.trimIndent()

    private class SignedInStorage : AulamaAuthStorage {
        private var session: AuthSession? = AuthSession(
            accessToken = "test-access-token",
            tokenType = "Bearer",
            expiresAtEpochMs = Long.MAX_VALUE,
            account = AulamaAccount("Test viewer", "user")
        )

        override fun loadSession(): AuthSession? = session

        override fun saveSession(session: AuthSession) {
            this.session = session
        }

        override fun clearSession() {
            session = null
        }

        override fun stableDeviceId(): String = "test-tv-device"
    }
}
