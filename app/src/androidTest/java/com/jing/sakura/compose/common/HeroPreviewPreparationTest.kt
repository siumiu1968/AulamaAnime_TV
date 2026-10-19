package com.jing.sakura.compose.common

import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.runPreviewFocusDwell
import com.jing.sakura.home.PREVIEW_PREPARE_DELAY_MS
import com.jing.sakura.home.PREVIEW_DIM_DELAY_MS
import com.jing.sakura.home.PREVIEW_START_AFTER_DIM_DELAY_MS
import com.jing.sakura.player.NavigateToPlayerArg
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class HeroPreviewPreparationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun heroRendersFirstFramePausedAndArmsWithoutRecreatingDecoder() = verifyPreparation(retained = false)

    @Test fun retainedPreviewForwardsPrepareAndArmWhileInitiallyHidden() = verifyPreparation(retained = true)

    private fun verifyPreparation(retained: Boolean) {
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("preview-prepared.mp4").use { it.readBytes() }
        FixtureServer(fixture).use { server ->
            val prepared = mutableStateOf(false)
            val hosting = mutableStateOf(true)
            val armed = mutableStateOf(false)
            val ready = mutableStateOf(false)
            val reveal = mutableStateOf(0f)
            val focusAt = AtomicLong(-1)
            val preparedAt = AtomicLong(-1)
            val armedAt = AtomicLong(-1)
            val readyCount = AtomicInteger()
            val error = AtomicReference<String?>(null)
            val earlyPlayback = AtomicBoolean()
            val earlyAudio = AtomicBoolean()
            val audioAdvancing = AtomicInteger()
            val newVideoDecoders = AtomicInteger()
            val releasedVideoDecoders = AtomicInteger()
            val spec = HeroPreviewSpec(
                key = "local-prepared-preview", url = server.url, headers = emptyMap(),
                startPositionMs = 0, posterUrl = "",
                navigateToPlayerArg = NavigateToPlayerArg("Preview", "fixture", "", 0, emptyList(), "fixture")
            )
            compose.mainClock.autoAdvance = false
            compose.setContent {
                if (hosting.value) {
                    LaunchedEffect(Unit) {
                        focusAt.set(compose.mainClock.currentTime)
                        runPreviewFocusDwell(
                            prepare = { preparedAt.set(compose.mainClock.currentTime); prepared.value = true },
                            dim = {},
                            arm = { armedAt.set(compose.mainClock.currentTime); armed.value = true }
                        )
                    }
                    LaunchedEffect(armed.value, ready.value) {
                        reveal.value = if (armed.value && ready.value) 1f else 0f
                    }
                    val onReady = { ready.value = true; readyCount.incrementAndGet(); Unit }
                    val onError: (String) -> Unit = { error.set(it) }
                    if (retained) {
                        RetainedHeroPreview(
                            spec = spec.takeIf { prepared.value }, reveal = reveal, retainWhileFading = true,
                            playbackEnabled = armed.value, onReady = onReady, onError = onError,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (prepared.value) {
                        HeroPreviewPlayer(spec = spec, playbackEnabled = armed.value,
                            onReady = onReady, onError = onError, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertTrue(focusAt.get() >= 0)
            fun advanceTo(elapsed: Long) {
                compose.mainClock.advanceTimeBy(
                    (focusAt.get() + elapsed - compose.mainClock.currentTime).coerceAtLeast(0),
                    ignoreFrameDuration = true
                )
            }
            advanceTo(PREVIEW_PREPARE_DELAY_MS - 1)
            compose.runOnIdle {
                assertFalse(prepared.value)
                assertNull(findPlayerView(compose.activity.window.decorView))
            }
            assertEquals(0, server.requests.get())
            advanceTo(PREVIEW_PREPARE_DELAY_MS)
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.runOnIdle { findPlayerView(compose.activity.window.decorView)?.player != null }
            }
            lateinit var player: ExoPlayer
            lateinit var playerView: PlayerView
            compose.runOnIdle {
                assertEquals(PREVIEW_PREPARE_DELAY_MS, preparedAt.get() - focusAt.get())
                assertFalse(armed.value)
                playerView = checkNotNull(findPlayerView(compose.activity.window.decorView))
                player = playerView.player as ExoPlayer
                player.addAnalyticsListener(object : AnalyticsListener {
                    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
                        if (isPlaying && !armed.value) earlyPlayback.set(true)
                    }
                    override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime,
                        playoutStartSystemTimeMs: Long) {
                        audioAdvancing.incrementAndGet()
                        if (!armed.value) earlyAudio.set(true)
                    }
                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime,
                        decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        newVideoDecoders.incrementAndGet()
                    }
                    override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                        releasedVideoDecoders.incrementAndGet()
                    }
                })
            }
            server.allowResponse.countDown()
            compose.waitUntil(timeoutMillis = 15_000) { readyCount.get() > 0 || error.get() != null }
            compose.mainClock.advanceTimeByFrame()
            assertNull("Preview preparation failed", error.get())
            assertEquals(1, readyCount.get())
            val paused = compose.runOnIdle {
                assertFalse(armed.value)
                assertFalse(player.playWhenReady)
                assertFalse(player.isPlaying)
                assertEquals(Player.STATE_READY, player.playbackState)
                assertTrue(playerView.videoSurfaceView is TextureView)
                assertTrue((playerView.videoSurfaceView as TextureView).isAvailable)
                val counters = checkNotNull(player.videoDecoderCounters)
                counters.ensureUpdated()
                assertTrue(counters.renderedOutputBufferCount > 0)
                Triple(player.currentPosition, counters, counters.renderedOutputBufferCount)
            }
            val initCount = paused.second.decoderInitCount
            assertTrue(initCount > 0)
            val releaseCount = paused.second.decoderReleaseCount
            val observedInitializations = newVideoDecoders.get()
            val observedReleases = releasedVideoDecoders.get()
            SystemClock.sleep(350)
            advanceTo(PREVIEW_DIM_DELAY_MS + PREVIEW_START_AFTER_DIM_DELAY_MS - 1)
            compose.runOnIdle {
                assertFalse(armed.value)
                assertFalse(player.playWhenReady)
                assertFalse(player.isPlaying)
                assertEquals(paused.first, player.currentPosition)
                paused.second.ensureUpdated()
                assertEquals(paused.third, paused.second.renderedOutputBufferCount)
                assertEquals(0, audioAdvancing.get())
            }
            advanceTo(PREVIEW_DIM_DELAY_MS + PREVIEW_START_AFTER_DIM_DELAY_MS)
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertEquals(PREVIEW_DIM_DELAY_MS + PREVIEW_START_AFTER_DIM_DELAY_MS, armedAt.get() - focusAt.get()) }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.runOnIdle { player.currentPosition >= paused.first + 250 } && audioAdvancing.get() > 0
            }
            compose.runOnIdle {
                assertSame(player, findPlayerView(compose.activity.window.decorView)?.player)
                assertTrue(player.playWhenReady)
                assertSame(paused.second, player.videoDecoderCounters)
                paused.second.ensureUpdated()
                assertEquals(initCount, paused.second.decoderInitCount)
                assertEquals(releaseCount, paused.second.decoderReleaseCount)
                assertEquals(observedInitializations, newVideoDecoders.get())
                assertEquals(observedReleases, releasedVideoDecoders.get())
                assertEquals(1, readyCount.get())
                assertFalse(earlyPlayback.get())
                assertFalse(earlyAudio.get())
            }
            compose.runOnIdle { hosting.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun findPlayerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayerView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    /** The production OkHttp source reads a bundled fixture exclusively over device loopback. */
    internal class FixtureServer(private val bytes: ByteArray) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        val allowResponse = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/preview.mp4"
        private val worker = thread(name = "preview-fixture", isDaemon = true) {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        input.readLine() ?: return@use
                        var rangeStart: Int? = null
                        while (true) {
                            val line = input.readLine()?.takeIf { it.isNotEmpty() } ?: break
                            if (line.startsWith("Range: bytes=", ignoreCase = true)) {
                                rangeStart = line.substringAfter('=').substringBefore('-').toIntOrNull()
                            }
                        }
                        requests.incrementAndGet()
                        check(allowResponse.await(10, TimeUnit.SECONDS)) { "Fixture response was not enabled" }
                        val start = (rangeStart ?: 0).coerceIn(0, bytes.lastIndex)
                        val status = if (rangeStart == null) "200 OK" else "206 Partial Content"
                        val headers = buildString {
                            append("HTTP/1.1 $status\r\nContent-Type: video/mp4\r\n")
                            append("Content-Length: ${bytes.size - start}\r\nAccept-Ranges: bytes\r\n")
                            if (rangeStart != null) append("Content-Range: bytes $start-${bytes.lastIndex}/${bytes.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        socket.getOutputStream().apply {
                            write(headers.toByteArray(Charsets.US_ASCII))
                            write(bytes, start, bytes.size - start)
                            flush()
                        }
                    }
                }
            }
        }
        override fun close() { allowResponse.countDown(); server.close(); worker.join(1_000) }
    }
}
