package com.jing.sakura.extend

import android.webkit.CookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.charset.Charset


val json = Json {
    ignoreUnknownKeys = true
}

suspend fun OkHttpClient.executeWithCoroutine(request: Request): Response {
    val def = CompletableDeferred<Result<Response>>()
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            def.complete(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            def.complete(Result.success(response))
        }
    })
    return try {
        def.await().getOrThrow()
    } catch (ex: CancellationException) {
        call.cancel()
        throw ex
    }
}

suspend fun OkHttpClient.newRequest(
    webViewCookieHelper: WebViewCookieHelper? = null,
    block: Request.Builder.() -> Unit
): Response {
    val req = Request.Builder()
        .apply(block)
        .build()
    val resp = executeWithCoroutine(req)
    if (webViewCookieHelper == null || !webViewCookieHelper.shouldIntercept(resp)) {
        return resp
    }
    resp.close()
    try {
        webViewCookieHelper.obtainCookieThroughWebView(resp.request)
    } catch (ex: Exception) {
        if (ex is CancellationException) {
            throw ex
        }
        throw RuntimeException("通过DDOS检测失败,${ex.message}", ex)
    }
    return executeWithCoroutine(Request.Builder().apply(block).build())
}


suspend fun OkHttpClient.getHtml(
    url: String,
    webViewCookieHelper: WebViewCookieHelper? = null,
    requestHandler: Request.Builder.() -> Unit = {}
): String =
    newRequest(webViewCookieHelper = webViewCookieHelper) {
        get()
        url(url)
        requestHandler()
    }.bodyString()


suspend fun OkHttpClient.getDocument(
    url: String,
    webViewCookieHelper: WebViewCookieHelper? = null,
    requestHandler: Request.Builder.() -> Unit = {}
): Document = newRequest(webViewCookieHelper = webViewCookieHelper) {
    get()
    url(url)
    requestHandler()
}.asDocument()


suspend fun OkHttpClient.newGetRequest(
    webViewCookieHelper: WebViewCookieHelper? = null,
    block: Request.Builder.() -> Unit
): Response {
    return newRequest(webViewCookieHelper = webViewCookieHelper) {
        block()
        get()
    }
}

fun Response.asDocument(): Document {
    return Jsoup.parse(this.bodyString()).apply {
        setBaseUri(request.url.toString())
    }
}


inline fun <reified T> Response.jsonBody(): T {
    return json.decodeFromString<T>(bodyString())
}

fun Response.bodyString(charset: Charset = Charsets.UTF_8): String {
    if (code != 200) {
        throw RuntimeException("请求${request.url}失败,code: $code")
    }
    return this.use {
        val body = it.body ?: throw RuntimeException("请求${request.url}失败,响应体为空")
        body.byteString().string(charset)
    }
}


class AndroidCookieJar : CookieJar {
    private val manager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())
        return if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = 0): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            .map { it.substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    fun put(url: String, vararg cookies: Cookie) {
        val urlString = url.toHttpUrl().toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    fun removeAll() {
        manager.removeAllCookies {}
    }

    fun flush() {
        manager.flush()
    }
}

internal class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { incoming ->
            this.cookies.removeAll { stored ->
                stored.expiresAt <= now || stored.hasSameIdentityAs(incoming)
            }
            if (incoming.expiresAt > now) {
                this.cookies += incoming
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    private fun Cookie.hasSameIdentityAs(other: Cookie): Boolean =
        name == other.name && domain == other.domain && path == other.path
}

/**
 * Mirrors network cookies in memory so requests can continue when the device has no usable
 * WebView provider. WebView failures are treated as permanent for this jar instance.
 */
internal class WebViewCompatibleCookieJar(
    private val webViewJarProvider: () -> CookieJar = { AndroidCookieJar() },
    private val fallback: CookieJar = InMemoryCookieJar(),
    private val onWebViewFailure: (Throwable) -> Unit = {},
) : CookieJar {
    @Volatile
    private var webViewUnavailable = false

    @Volatile
    private var webViewJar: CookieJar? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        fallback.saveFromResponse(url, cookies)
        val jar = webViewJarOrNull() ?: return
        try {
            jar.saveFromResponse(url, cookies)
        } catch (error: Throwable) {
            handleWebViewFailure(error)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val jar = webViewJarOrNull() ?: return fallback.loadForRequest(url)
        return try {
            jar.loadForRequest(url)
        } catch (error: Throwable) {
            handleWebViewFailure(error)
            fallback.loadForRequest(url)
        }
    }

    private fun webViewJarOrNull(): CookieJar? {
        if (webViewUnavailable) return null
        webViewJar?.let { return it }
        return synchronized(this) {
            if (webViewUnavailable) return@synchronized null
            webViewJar ?: try {
                webViewJarProvider().also { webViewJar = it }
            } catch (error: Throwable) {
                handleWebViewFailure(error)
                null
            }
        }
    }

    private fun handleWebViewFailure(error: Throwable) {
        if (error !is RuntimeException && error !is LinkageError) throw error
        val shouldReport = !webViewUnavailable
        webViewUnavailable = true
        webViewJar = null
        if (shouldReport) onWebViewFailure(error)
    }
}
