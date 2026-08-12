package com.jing.sakura.repo

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CycaniWebPlaybackResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: CycaniWebPlaybackResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = CycaniWebPlaybackResolver(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/api/").toString(),
            authenticatedPlayUrlResolver = { error("Playback is not expected in this test") }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun detailLoadsWebSectionsAndKeepsTheirIdsForDeferredPlayback() = runBlocking {
        enqueue(
            """{"code":0,"msg":"","data":{"list":[{"video_id":3871,"title":"测试动画","year":2026}],"pager":{"page":1,"page_size":20,"total":1}}}"""
        )
        enqueue(
            """{"code":0,"msg":"","data":{"id":3871,"title":"测试动画","description":"简介","cover_url":"https://img.example/cover.jpg","year":2026,"state":"TV","area":"日本","actor":[],"director":[],"writer":"","play_from":[{"code":"cychub","title":"CYC_Main"}]}}"""
        )
        enqueue(
            """{"code":0,"msg":"","data":{"list":[{"id":51500,"title":"第01集"}],"pager":{"page":1,"page_size":100,"total":1}}}"""
        )

        val detail = resolver.fetchDetail(CycaniWebTitleRequest("測試動畫", "2026"))

        assertEquals("测试动画", detail.title)
        assertEquals("51500", detail.playLists.single().sections.single().id)
        val searchRequest = server.takeRequest()
        assertTrue(searchRequest.path.orEmpty().startsWith("/api/videos/search?"))
        assertEquals("cyc_android", searchRequest.getHeader("X-App-Name"))
        assertEquals("/api/videos/3871", server.takeRequest().path)
        val sectionsRequest = server.takeRequest()
        assertTrue(sectionsRequest.path.orEmpty().startsWith("/api/videos/3871/sections?"))
        assertEquals("cyc_android", sectionsRequest.getHeader("X-App-Name"))
    }

    @Test
    fun playUrlIsResolvedThroughAuthenticatedBackendOnlyWhenPlaybackStarts() = runBlocking {
        var resolvedSectionId = ""
        resolver = CycaniWebPlaybackResolver(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/api/").toString(),
            authenticatedPlayUrlResolver = { sectionId ->
                resolvedSectionId = sectionId
                "https://media.example/episode-1.m3u8"
            }
        )

        val url = resolver.resolveSection("51500")

        assertEquals("https://media.example/episode-1.m3u8", url)
        assertEquals("51500", resolvedSectionId)
        assertEquals(0, server.requestCount)
    }

    private fun enqueue(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }
}
