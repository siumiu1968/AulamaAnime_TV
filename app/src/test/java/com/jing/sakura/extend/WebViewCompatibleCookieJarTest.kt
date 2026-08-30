package com.jing.sakura.extend

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCompatibleCookieJarTest {
    @Test
    fun keepsHttpCookiesWhenWebViewProviderIsUnavailable() {
        val failures = mutableListOf<Throwable>()
        val brokenWebViewJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                throw NullPointerException("missing WebView package")
            }
        }
        val cookieJar = WebViewCompatibleCookieJar(
            webViewJarProvider = { brokenWebViewJar },
            onWebViewFailure = failures::add,
        )
        val server = MockWebServer().apply {
            enqueue(MockResponse().setHeader("Set-Cookie", "session=abc; Path=/"))
            enqueue(MockResponse().setBody("ok"))
            start()
        }

        try {
            val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
            client.newCall(Request.Builder().url(server.url("/login")).build()).execute().close()
            client.newCall(Request.Builder().url(server.url("/account")).build()).execute().close()

            assertEquals(null, server.takeRequest().getHeader("Cookie"))
            assertEquals("session=abc", server.takeRequest().getHeader("Cookie"))
            assertEquals(1, failures.size)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fallbackRespectsCookieScopeExpiryAndReplacement() {
        val jar = InMemoryCookieJar()
        val httpsUrl = "https://example.com/account/profile".toHttpUrl()
        jar.saveFromResponse(
            httpsUrl,
            listOf(
                cookie("session", "old", "/account"),
                cookie("root", "yes", "/"),
                cookie("secure", "yes", "/", secure = true),
                cookie("expired", "no", "/", expiresAt = 0),
            ),
        )
        jar.saveFromResponse(httpsUrl, listOf(cookie("session", "new", "/account")))

        val httpsCookies = jar.loadForRequest(httpsUrl).associate { it.name to it.value }
        assertEquals("new", httpsCookies["session"])
        assertEquals("yes", httpsCookies["root"])
        assertEquals("yes", httpsCookies["secure"])
        assertTrue("expired" !in httpsCookies)

        val httpCookies = jar.loadForRequest("http://example.com/account".toHttpUrl())
        assertTrue(httpCookies.none { it.name == "secure" })
        assertTrue(jar.loadForRequest("https://example.com/other".toHttpUrl()).none { it.name == "session" })
    }

    private fun cookie(
        name: String,
        value: String,
        path: String,
        secure: Boolean = false,
        expiresAt: Long = Long.MAX_VALUE,
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain("example.com")
        .path(path)
        .expiresAt(expiresAt)
        .apply { if (secure) secure() }
        .build()
}
