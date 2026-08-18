package com.jing.sakura.player

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class MislabelledHlsMediaInterceptorTest {
    private val loopbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            listOf(InetAddress.getByName("127.0.0.1"))
    }

    @Test
    fun rewritesAlipayByteRangeFmp4MimeWithoutChangingRequestOrBody() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "image/png")
                    .setBody("fMP4 bytes")
            )
            start()
        }
        try {
            val client = OkHttpClient.Builder()
                .dns(loopbackDns)
                .addNetworkInterceptor(MislabelledHlsMediaInterceptor)
                .build()
            val url = server.url("/medaicore/afts/img/id/original")
                .newBuilder()
                .host("mdn.alipayobjects.com")
                .build()

            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Range", "bytes=12421-13687")
                    .build()
            ).execute().use { response ->
                assertEquals("video/mp4", response.header("Content-Type"))
                assertEquals("fMP4 bytes", response.body?.string())
            }
            assertEquals("bytes=12421-13687", server.takeRequest().getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun leavesOrdinaryAlipayImagesUnchanged() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("PNG"))
            start()
        }
        try {
            val client = OkHttpClient.Builder()
                .dns(loopbackDns)
                .addNetworkInterceptor(MislabelledHlsMediaInterceptor)
                .build()
            val url = server.url("/medaicore/afts/img/id/original")
                .newBuilder()
                .host("mdn.alipayobjects.com")
                .build()

            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals("image/png", response.header("Content-Type"))
            }
        } finally {
            server.shutdown()
        }
    }
}
