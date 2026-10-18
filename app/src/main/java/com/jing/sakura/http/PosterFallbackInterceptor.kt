package com.jing.sakura.http

import java.io.IOException
import java.net.URLDecoder
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An unavailable thumbnail service must not prevent a public cover from loading. */
class PosterFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fallbacks = posterFallbackRequests(request)
        if (fallbacks.isEmpty()) return chain.proceed(request)
        val proxyRequest = request.newBuilder().header("X-Aulama-Image-Cache", "1").build()
        var lastError: IOException? = null
        for ((index, attempt) in (listOf(proxyRequest) + fallbacks).withIndex()) {
            var response: Response? = null
            var keepResponse = false
            try {
                response = chain.withConnectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                    .withReadTimeout(if (index == 0) 4 else 5, java.util.concurrent.TimeUnit.SECONDS).proceed(attempt)
                // Coil owns the cached bytes and must receive a successful validation unchanged.
                if (index == 0 && response.code == 304 || response.isSuccessful && hasImageBytes(response)) {
                    keepResponse = true
                    return response
                }
                lastError = IOException("封面回應未包含有效圖片（${response.code}）")
            } catch (error: IOException) {
                if (chain.call().isCanceled()) throw error
                lastError = error
            } finally {
                if (!keepResponse) response?.close()
            }
        }
        throw IOException("封面暫時未能載入", lastError)
    }
}

private fun hasImageBytes(response: Response): Boolean {
    val type = response.header("Content-Type").orEmpty().substringBefore(';')
    if (!type.startsWith("image/") && type != "application/octet-stream") return false
    val bytes = response.peekBody(16).bytes()
    val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    val text = bytes.toString(Charsets.ISO_8859_1)
    return hex.startsWith("ffd8ff") || hex.startsWith("89504e470d0a1a0a") ||
        text.startsWith("GIF87a") || text.startsWith("GIF89a") ||
        text.startsWith("RIFF") && text.substring(8.coerceAtMost(text.length)).startsWith("WEBP") ||
        text.length >= 12 && text.substring(4, 8) == "ftyp" && text.substring(8, 12) in setOf("avif", "avis")
}

/** Only matching, reviewed cover paths may use the canonical mirror; never guess another title. */
internal fun posterFallbackRequests(request: Request): List<Request> {
    val original = originalPosterRequest(request) ?: return emptyList()
    var cover = original.url
    val path = Regex("^/pic/cover/[lms]/[a-f0-9]{2}/[a-f0-9]{2}/[0-9]+_[A-Za-z0-9]+\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE)
    if (cover.isHttps && Regex("gimg[0-9]+\\.baidu\\.com").matches(cover.host) && cover.encodedPath.startsWith("/gimg/")) {
        val encoded = Regex("[?&]src=([^&]+)").find(cover.toString())?.groupValues?.get(1)
        val nested = encoded?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.let { if (it.startsWith("//")) "https:$it" else if (it.startsWith("https://")) it else "https://$it" }
            ?.toHttpUrlOrNull()
        if (nested != null && nested.isHttps && nested.port == 443 && nested.username.isEmpty() && nested.password.isEmpty() &&
            nested.host in setOf("img1.cycapp.org", "img2.cycimg.me", "lain.bgm.tv") && path.matches(nested.encodedPath)) cover = nested
    }
    if (!cover.isHttps || cover.port != 443 || cover.host !in setOf("img1.cycapp.org", "img2.cycimg.me", "lain.bgm.tv") || !path.matches(cover.encodedPath)) return listOf(original)
    return listOf(cover.newBuilder().host("lain.bgm.tv").build(), cover.newBuilder().host("img2.cycimg.me").build(), cover)
        .distinct().map { Request.Builder().url(it).header("Accept", "image/webp,image/*").build() }
}

internal fun originalPosterRequest(request: Request): Request? {
    val url = request.url
    if (url.scheme != "https" || url.host != "aulama.org" || url.port != 443 || url.encodedPath != "/anime/api/image") return null
    val original = url.queryParameter("url")?.toHttpUrlOrNull() ?: return null
    if (original.host == "aulama.org" || original.username.isNotEmpty() || original.password.isNotEmpty()) return null
    // Build fresh headers so account credentials can never reach an image provider.
    return Request.Builder().url(original).header("Accept", "image/webp,image/*").build()
}
