package com.jing.sakura.http

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val reviewedPosterHosts = setOf("img1.cycapp.org", "img2.cycimg.me", "lain.bgm.tv")
private val reviewedPosterPath = Regex(
    "^/pic/cover/[lms]/[a-f0-9]{2}/[a-f0-9]{2}/[0-9]+_[A-Za-z0-9]+\\.(jpg|jpeg|png|webp)$",
    RegexOption.IGNORE_CASE
)

/** Keep the exact cover path; only the reviewed mirrors may change hosts. */
internal fun canonicalPosterImageUrl(imageUrl: String): String =
    imageUrl.toHttpUrlOrNull()?.let(::reviewedPosterImageUrl)
        ?.newBuilder()?.host("lain.bgm.tv")?.build()?.toString() ?: imageUrl

internal fun reviewedPosterImageUrl(imageUrl: HttpUrl): HttpUrl? {
    if (!imageUrl.isHttps || imageUrl.port != 443 || imageUrl.username.isNotEmpty() || imageUrl.password.isNotEmpty()) return null
    var cover = imageUrl
    if (Regex("gimg[0-9]+\\.baidu\\.com").matches(cover.host) && cover.encodedPath.startsWith("/gimg/")) {
        val encoded = Regex("[?&]src=([^&]+)").find(cover.toString())?.groupValues?.get(1) ?: return null
        val nested = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: return null
        cover = (if (nested.startsWith("//")) "https:$nested" else if (nested.startsWith("https://")) nested else "https://$nested")
            .toHttpUrlOrNull() ?: return null
    }
    return cover.takeIf {
        it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
            it.host in reviewedPosterHosts && reviewedPosterPath.matches(it.encodedPath)
    }
}
