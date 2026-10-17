package com.jing.sakura.auth

import okhttp3.Request

internal fun Request.withAulamaApiSession(session: AuthSession?): Request {
    if (session == null || session.isExpired() || header("Authorization") != null ||
        !url.isHttps || url.host != "aulama.org" || url.port != 443 ||
        !url.encodedPath.startsWith("/anime/api/")
    ) return this
    return newBuilder()
        .header("Authorization", "${session.tokenType} ${session.accessToken}")
        .build()
}
