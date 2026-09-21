package com.jing.sakura.http

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An unavailable thumbnail service must not prevent a public cover from loading. */
class PosterFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fallback = originalPosterRequest(request) ?: return chain.proceed(request)
        try {
            val response = chain.withReadTimeout(4, java.util.concurrent.TimeUnit.SECONDS).proceed(request)
            if (response.isSuccessful) return response
            response.close()
        } catch (error: IOException) {
            if (chain.call().isCanceled()) throw error
        }
        return chain.proceed(fallback)
    }
}

internal fun originalPosterRequest(request: Request): Request? {
    val url = request.url
    if (url.scheme != "https" || url.host != "aulama.org" || url.port != 443 || url.encodedPath != "/anime/api/image") return null
    val original = url.queryParameter("url")?.toHttpUrlOrNull() ?: return null
    if (original.host == "aulama.org") return null
    // Build fresh headers so account credentials can never reach an image provider.
    return Request.Builder().url(original).header("Accept", "image/webp,image/*").build()
}
