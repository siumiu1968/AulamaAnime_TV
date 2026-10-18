package com.jing.sakura.http

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class PosterUrlPolicyTest {
    private val coverPath = "/pic/cover/l/55/e2/568244_e64dA.jpg"
    private val canonical = "https://lain.bgm.tv$coverPath"

    @Test fun guestCoverUsesExactCanonicalPathWithoutAProtectedProxy() {
        for (host in listOf("img1.cycapp.org", "img2.cycimg.me", "lain.bgm.tv")) {
            assertEquals(canonical, canonicalPosterImageUrl("https://$host$coverPath"))
        }
        assertEquals(canonical, canonicalPosterImageUrl("https://gimg1.baidu.com/gimg/app=2001&src=img1.cycapp.org$coverPath&refer=test"))
        val encoded = URLEncoder.encode("https://img2.cycimg.me$coverPath", "UTF-8")
        assertEquals(canonical, canonicalPosterImageUrl("https://gimg2.baidu.com/gimg/app=2001&src=$encoded"))
        assertEquals(canonical, canonicalPosterImageUrl("https://gimg1.baidu.com/gimg/app=2001&src=//img1.cycapp.org$coverPath"))
    }

    @Test fun guestCoverNeverGuessesAnotherPathOrRewritesUnreviewedOrigins() {
        for (url in listOf(
            "https://img1.cycapp.org.evil.example$coverPath",
            "https://img1.cycapp.org/other/568244_e64dA.jpg",
            "https://img1.cycapp.org/pic/cover/l/55/e2/other-title.jpg",
            "https://user:password@img1.cycapp.org$coverPath",
            "https://img1.cycapp.org:8443$coverPath",
            "http://img1.cycapp.org$coverPath",
            "https://gimg1.baidu.com/gimg/app=2001&src=127.0.0.1/private",
            "https://gimg1.baidu.com/gimg/app=2001&src=img1.cycapp.org.evil.example$coverPath",
            "https://gimg1.baidu.com/gimg/app=2001&src=https://user:password@img1.cycapp.org$coverPath",
            "https://gimg1.baidu.com/gimg/app=2001&src=https://img1.cycapp.org:8443$coverPath",
            "https://gimg1.baidu.com/gimg/app=2001&src=http://img1.cycapp.org$coverPath",
            "https://gimg1.baidu.com:8443/gimg/app=2001&src=img1.cycapp.org$coverPath",
            "https://gimg1.baidu.com/not-gimg/app=2001&src=img1.cycapp.org$coverPath",
            "https://images.example/cover.jpg",
            "https://aulama.org/anime/api/image?url=https%3A%2F%2Fimages.example%2Fcover.jpg",
            "file:///local/cover.jpg",
            ""
        )) assertEquals(url, canonicalPosterImageUrl(url))
    }
}
