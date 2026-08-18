package com.jing.sakura.player

import okhttp3.Interceptor
import okhttp3.Response

/** Lets Media3 identify Alipay's byte-range fMP4 objects despite their image MIME type. */
internal object MislabelledHlsMediaInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return if (
            response.request.url.host == "mdn.alipayobjects.com" &&
            response.request.url.encodedPath.endsWith("/original") &&
            response.request.header("Range") != null &&
            response.code == 206 &&
            response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.equals("image/png", ignoreCase = true) == true
        ) {
            response.newBuilder()
                .header("Content-Type", "video/mp4")
                .build()
        } else {
            response
        }
    }
}
