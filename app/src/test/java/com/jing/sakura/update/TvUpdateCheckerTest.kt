package com.jing.sakura.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class TvUpdateCheckerTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesPublishedManifestAndResolvesRelativeApkUrl() {
        server.enqueue(jsonResponse(aulamaManifest(version = "3.0.5", versionCode = 1026)))

        val result = checker().checkForUpdateDetailed()

        assertTrue(result is TvUpdateCheckResult.Available)
        val update = (result as TvUpdateCheckResult.Available).update
        assertEquals("3.0.5", update.version)
        assertEquals(1026, update.versionCode)
        assertEquals(server.url("/anime/downloads/aulama-anime-tv.apk").toString(), update.downloadUrl)
        assertEquals(TEST_SHA256, update.sha256)
        assertEquals("primary notes", update.notes)
        assertEquals("/primary", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun usesVersionCodeForPrimaryManifestDecision() {
        server.enqueue(jsonResponse(aulamaManifest(version = "99.0.0", versionCode = 1025)))

        val result = checker().checkForUpdateDetailed()

        assertEquals(TvUpdateCheckResult.UpToDate, result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retriesTransientResponseOnlyOnceBeforePrimarySucceeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(jsonResponse(aulamaManifest(version = "3.0.5", versionCode = 1026)))

        val result = checker().checkForUpdateDetailed()

        assertTrue(result is TvUpdateCheckResult.Available)
        assertEquals(2, server.requestCount)
        assertEquals("/primary", server.takeRequest().path)
        assertEquals("/primary", server.takeRequest().path)
    }

    @Test
    fun retriesIOExceptionOnceBeforePrimarySucceeds() {
        var attempts = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                attempts += 1
                if (attempts == 1) throw IOException("temporary connection failure")
                chain.proceed(chain.request())
            }
            .build()
        server.enqueue(jsonResponse(aulamaManifest(version = "3.0.5", versionCode = 1026)))

        val result = checker(client).checkForUpdateDetailed()

        assertTrue(result is TvUpdateCheckResult.Available)
        assertEquals(2, attempts)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun fallsBackToGithubWithoutRetryingPermanentPrimaryFailure() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(jsonResponse(githubRelease("3.0.5")))

        val result = checker().checkForUpdateDetailed()

        assertTrue(result is TvUpdateCheckResult.Available)
        val update = (result as TvUpdateCheckResult.Available).update
        assertEquals("3.0.5", update.version)
        assertEquals(null, update.versionCode)
        assertEquals(TEST_SHA256, update.sha256)
        assertEquals(server.url("/github/aulama-anime-tv-v3.0.5.apk").toString(), update.downloadUrl)
        assertEquals(2, server.requestCount)
        assertEquals("/primary", server.takeRequest().path)
        assertEquals("/fallback", server.takeRequest().path)
    }

    @Test
    fun retriesTransientPrimaryAtMostOnceThenUsesFallback() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(jsonResponse(githubRelease("3.0.5")))

        val result = checker().checkForUpdateDetailed()

        assertTrue(result is TvUpdateCheckResult.Available)
        assertEquals(3, server.requestCount)
        assertEquals("/primary", server.takeRequest().path)
        assertEquals("/primary", server.takeRequest().path)
        assertEquals("/fallback", server.takeRequest().path)
    }

    @Test
    fun productionPolicyRejectsCleartextAndUnknownDownloadHosts() {
        assertThrows(IOException::class.java) {
            PRODUCTION_TV_UPDATE_URL_POLICY.requireDownloadUrl("http://aulama.org/anime/app.apk")
        }
        assertThrows(IOException::class.java) {
            PRODUCTION_TV_UPDATE_URL_POLICY.requireDownloadUrl("https://example.com/app.apk")
        }
    }

    @Test
    fun calculatesSha256ForDownloadedContent() {
        val hash = sha256Hex(ByteArrayInputStream("Aulama Anime TV".toByteArray()))

        assertEquals("81022aaa9db54f71fc92df48be572cbe081d2d7521cc92985b04c3952d678abd", hash)
    }

    private fun checker(client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    ): TvUpdateChecker {
        val localhostPolicy = TvUpdateUrlPolicy(
            updateHosts = setOf("localhost"),
            downloadHosts = setOf("localhost"),
            requireHttps = false
        )
        return TvUpdateChecker(
            client = client,
            primary = TvUpdateEndpoint(server.url("/primary").toString(), TvUpdateSourceFormat.AULAMA_MANIFEST),
            fallback = TvUpdateEndpoint(server.url("/fallback").toString(), TvUpdateSourceFormat.GITHUB_RELEASE),
            currentVersionCode = 1025,
            currentVersionName = "3.0.4",
            userAgent = "Aulama-Anime-TV/Test",
            urlPolicy = localhostPolicy,
            retryDelayMillis = 0L
        )
    }

    private fun aulamaManifest(version: String, versionCode: Int): String = """
        {
          "status": "published",
          "platform": "android-tv",
          "version": "$version",
          "versionCode": $versionCode,
          "apk": "/anime/downloads/aulama-anime-tv.apk",
          "sha256": "$TEST_SHA256",
          "notes": "primary notes"
        }
    """.trimIndent()

    private fun githubRelease(version: String): String = """
        {
          "tag_name": "v$version",
          "draft": false,
          "prerelease": false,
          "body": "fallback notes",
          "assets": [
            {
              "name": "aulama-anime-tv-v$version.apk",
              "browser_download_url": "${server.url("/github/aulama-anime-tv-v$version.apk")}",
              "digest": "sha256:$TEST_SHA256"
            }
          ]
        }
    """.trimIndent()

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val TEST_SHA256 = "cb62fc760382caaf5341a78e013910e61344da456cee92008ef0dacf7937b97f"
    }
}
