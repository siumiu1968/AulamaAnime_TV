package com.jing.sakura.http
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
class PosterFallbackInterceptorTest {
 @Test fun fallbackNeverForwardsAccountCredentials() {
  val source=Request.Builder().url("https://aulama.org/anime/api/image?w=540&url=https%3A%2F%2Fimages.example%2Fa.jpg").header("Authorization","Bearer test-only").header("Cookie","session=test-only").build()
  val result=originalPosterRequest(source)!!
  assertEquals("https://images.example/a.jpg",result.url.toString())
  assertNull(result.header("Authorization"));assertNull(result.header("Cookie"))
 }
 @Test fun unrelatedMediaRequestsCannotBeRewritten() {
  for(url in listOf("https://aulama.org/anime/api/media?url=https://images.example/a.jpg","https://example.com/anime/api/image?url=https://images.example/a.jpg"))assertNull(originalPosterRequest(Request.Builder().url(url).build()))
 }
}
