package com.jing.sakura.http
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
class PosterFallbackInterceptorTest {
 @Test fun validatedDiskCacheResponseMustReachCoilWithoutContactingOriginalCdn() {
  MockWebServer().use { server ->
   server.start()
   server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag","\"same-cover\""))
   val original=Request.Builder().url("https://aulama.org/anime/api/image?w=540&url=https%3A%2F%2Fimg1.cycapp.org%2Fpic%2Fcover%2Fl%2F55%2Fe2%2F568244_e64dA.jpg")
    .header("If-None-Match","\"same-cover\"").build()
   var fallbackCalls=0
   val client=OkHttpClient.Builder().addInterceptor(PosterFallbackInterceptor()).addInterceptor { chain ->
    if(chain.request().url.host!="aulama.org"){fallbackCalls++;error("Original CDN must not be requested for a valid cached image")}
    chain.proceed(chain.request().newBuilder().url(server.url("/image")).build())
   }.build()
   client.newCall(original).execute().use { assertEquals(304,it.code);assertEquals("\"same-cover\"",it.header("ETag")) }
   assertEquals(1,server.requestCount);assertEquals(0,fallbackCalls)
   val request=server.takeRequest()
   assertEquals("\"same-cover\"",request.getHeader("If-None-Match"))
   assertEquals("1",request.getHeader("X-Aulama-Image-Cache"))
  }
 }
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
