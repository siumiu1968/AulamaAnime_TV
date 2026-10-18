package com.jing.sakura.http
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
class PosterFallbackInterceptorTest {
 @Test fun failedProxyAndInvalidCanonicalImageRecoverThroughAnotherReviewedMirror() {
  MockWebServer().use { server ->
   server.start()
   server.enqueue(MockResponse().setResponseCode(503))
   server.enqueue(MockResponse().setHeader("Content-Type","text/html").setBody("not an image"))
   server.enqueue(MockResponse().setHeader("Content-Type","image/jpeg").setBody(okio.Buffer().write(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xe0.toByte(),1,2,3,4))))
   val hosts=mutableListOf<String>()
   val client=OkHttpClient.Builder().addInterceptor(PosterFallbackInterceptor()).addInterceptor { chain ->
    val request=chain.request();hosts+=request.url.host
    if(request.url.host!="aulama.org"){assertNull(request.header("Authorization"));assertNull(request.header("Cookie"))}
    chain.proceed(request.newBuilder().url(server.url("/image")).build())
   }.build()
   val url="https://aulama.org/anime/api/image?url="+java.net.URLEncoder.encode("https://gimg1.baidu.com/gimg/app=2001&src=img1.cycapp.org/pic/cover/l/55/e2/568244_e64dA.jpg","UTF-8")
   client.newCall(Request.Builder().url(url).header("Authorization","Bearer test-only").header("Cookie","test-only").build()).execute().use {
    assertEquals(200,it.code);assertEquals(8,it.body!!.bytes().size)
   }
   assertEquals(listOf("aulama.org","lain.bgm.tv","img2.cycimg.me"),hosts)
  }
 }
 @Test fun unreviewedHostsAndPathsCannotUseACanonicalCoverFromAnotherTitle() {
  for(original in listOf("https://img1.cycapp.org.evil.example/pic/cover/l/55/e2/568244_e64dA.jpg","https://img1.cycapp.org/other/568244_e64dA.jpg","https://gimg1.baidu.com/gimg/app=2001&src=127.0.0.1/private")){
   val url="https://aulama.org/anime/api/image?url="+java.net.URLEncoder.encode(original,"UTF-8")
   assertEquals(listOf(original),posterFallbackRequests(Request.Builder().url(url).build()).map{it.url.toString()})
  }
 }
 @Test fun invalidImageBodiesAreNotReturnedAsSuccessfulCacheEntries() {
  MockWebServer().use { server ->
   server.start();repeat(2){server.enqueue(MockResponse().setHeader("Content-Type","image/jpeg").setBody("HTML disguised as an image"))}
   val client=OkHttpClient.Builder().addInterceptor(PosterFallbackInterceptor()).addInterceptor { chain ->chain.proceed(chain.request().newBuilder().url(server.url("/image")).build())}.build()
   val request=Request.Builder().url("https://aulama.org/anime/api/image?url=https%3A%2F%2Fimages.example%2Fa.jpg").build()
   try{client.newCall(request).execute().close();fail("Invalid image must fail without poisoning cache")}catch(expected:java.io.IOException){assertEquals(2,server.requestCount)}
  }
 }
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
