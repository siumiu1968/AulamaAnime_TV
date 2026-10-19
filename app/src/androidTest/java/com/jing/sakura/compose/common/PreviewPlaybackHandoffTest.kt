package com.jing.sakura.compose.common

import android.graphics.Bitmap
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.PreviewPlaybackHandoff
import com.jing.sakura.player.PreviewPlaybackTransfer
import com.jing.sakura.player.PreviewHandoffLoadControl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PreviewPlaybackHandoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun transfersBufferedPlayerOnceAndPreviewCleanupDoesNotReleaseIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "preview-handoff-fixture.mp4")
        instrumentation.context.assets.open("preview-prepared.mp4").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val continued = AtomicReference<NavigateToPlayerArg?>()
        val error = AtomicReference<String?>()
        lateinit var controller: HeroPreviewController
        lateinit var view: PlayerView
        lateinit var initialPlayer: ExoPlayer
        lateinit var transfer: PreviewPlaybackTransfer
        lateinit var counters: DecoderCounters
        var decoderStarts = 0
        var positionBefore = 0L
        val fullPlayerVisible = mutableStateOf(false)
        val fullFrameReady = AtomicBoolean()
        val focused = AtomicBoolean(true)
        var initialized = false
        var transferred = false
        val episode = AnimePlayListEpisode("第 1 集", "fixture-episode")
        val arg = NavigateToPlayerArg("Preview", "fixture", "", 0, listOf(episode), "fixture")
        val spec = HeroPreviewSpec("fixture-preview", file.toURI().toString(), emptyMap(), 0L, "", arg)
        try {
            compose.runOnUiThread {
                controller = HeroPreviewController(compose.activity.applicationContext, OkHttpClient(), {},
                    { error.set(it) }, {}, { continued.set(it) }, { focused.get() })
                initialized = true
                view = controller.createPlayerView(compose.activity)
            }
            compose.setContent {
                if (fullPlayerVisible.value) {
                    AndroidView(factory = { context ->
                        SurfaceView(context).also { transfer.player.setVideoSurfaceView(it) }
                    }, modifier = Modifier.fillMaxSize())
                } else {
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                }
            }
            compose.runOnIdle {
                controller.setContinuationEnabled(true)
                controller.load(spec)
                controller.start()
                initialPlayer = view.player as ExoPlayer
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.runOnIdle { initialPlayer.isPlaying && initialPlayer.currentPosition > 150L } || error.get() != null
            }
            assertNull(error.get())
            compose.runOnIdle {
                val bufferedBefore = initialPlayer.bufferedPosition
                positionBefore = initialPlayer.currentPosition
                counters = checkNotNull(initialPlayer.videoDecoderCounters)
                counters.ensureUpdated()
                decoderStarts = counters.decoderInitCount
                focused.set(false)
                controller.continuePlayback()
                assertNull(continued.get())
                focused.set(true)
                controller.setContinuationEnabled(false)
                controller.continuePlayback()
                assertNull(continued.get())
                controller.setContinuationEnabled(true)
                controller.continuePlayback()
                val handoffArg = checkNotNull(continued.get())
                assertTrue(handoffArg.resumePositionMs >= positionBefore)
                assertEquals(spec.url, PreviewPlaybackHandoff.source(handoffArg, episode.episodeId)?.url)
                assertNull(PreviewPlaybackHandoff.source(handoffArg, "another-episode"))
                assertNull(PreviewPlaybackHandoff.claim(handoffArg.copy(animeId = "another-anime")))
                controller.release()
                transfer = checkNotNull(PreviewPlaybackHandoff.claim(handoffArg))
                transferred = true
                assertSame(initialPlayer, transfer.player)
                assertSame(counters, transfer.player.videoDecoderCounters)
                counters.ensureUpdated()
                assertEquals(decoderStarts, counters.decoderInitCount)
                assertTrue(transfer.player.bufferedPosition >= bufferedBefore)
                assertTrue(transfer.player.currentPosition >= positionBefore)
                assertEquals(Player.STATE_READY, transfer.player.playbackState)
                assertNull(PreviewPlaybackHandoff.claim(handoffArg))
                assertNotNull(transfer.snapshot)
                transfer.player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { fullFrameReady.set(true) }
                })
                fullPlayerVisible.value = true
            }
            compose.waitUntil(timeoutMillis = 5_000) { fullFrameReady.get() }
            compose.runOnIdle {
                assertTrue(transfer.player.isPlaying)
                assertTrue(transfer.player.currentPosition >= positionBefore)
                assertSame(counters, transfer.player.videoDecoderCounters)
                counters.ensureUpdated()
                assertEquals(decoderStarts, counters.decoderInitCount)
            }
        } finally {
            if (initialized) compose.runOnUiThread {
                controller.release()
                if (transferred) {
                    transfer.player.release()
                    transfer.snapshot?.recycle()
                }
                PreviewPlaybackHandoff.expire(continued.get()?.previewHandoffId)
            }
            file.delete()
        }
    }

    @Test fun abandonedHandoffExpiresAndReleasesItsDecoderAndSnapshot() {
        val releases = AtomicInteger()
        compose.runOnUiThread {
            val context = compose.activity.applicationContext
            val selector = DefaultTrackSelector(context)
            val player = ExoPlayer.Builder(context).setTrackSelector(selector).build()
            val detached = AtomicInteger()
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) { releases.incrementAndGet() }
            })
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val arg = NavigateToPlayerArg("Fixture", "fixture", "", 0,
                listOf(AnimePlayListEpisode("第 1 集", "fixture-episode")), "fixture")
            val ticket = PreviewPlaybackHandoff.register(arg, "file:///fixture.mp4", emptyMap(),
                player, selector, bitmap) { detached.incrementAndGet() }
            PreviewPlaybackHandoff.expire(ticket.previewHandoffId)
            PreviewPlaybackHandoff.expire(ticket.previewHandoffId)
            assertEquals(1, detached.get())
            assertTrue(bitmap.isRecycled)
            assertNull(PreviewPlaybackHandoff.claim(ticket))
            assertNull(PreviewPlaybackHandoff.source(ticket, "fixture-episode"))
        }
        compose.waitUntil(timeoutMillis = 5_000) { releases.get() == 1 }
    }

    @Test fun unhealthyPlayerIsRejectedTogetherWithItsPreparedSource() {
        val releases = AtomicInteger()
        compose.runOnUiThread {
            val context = compose.activity.applicationContext
            val selector = DefaultTrackSelector(context)
            val player = ExoPlayer.Builder(context).setTrackSelector(selector).build()
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) { releases.incrementAndGet() }
            })
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val arg = NavigateToPlayerArg("Fixture", "fixture", "", 0,
                listOf(AnimePlayListEpisode("第 1 集", "fixture-episode")), "fixture")
            val ticket = PreviewPlaybackHandoff.register(arg, "file:///expired.mp4", emptyMap(),
                player, selector, bitmap) {}
            assertNotNull(PreviewPlaybackHandoff.source(ticket, "fixture-episode"))
            assertNull(PreviewPlaybackHandoff.claim(ticket)) // IDLE never belongs in the full player.
            assertNull(PreviewPlaybackHandoff.source(ticket, "fixture-episode"))
            assertTrue(bitmap.isRecycled)
        }
        compose.waitUntil(timeoutMillis = 5_000) { releases.get() == 1 }
    }

    @Test fun promotionRestoresNormalBufferPolicyWithoutDiscardingAllocations() {
        compose.runOnUiThread {
            val control = PreviewHandoffLoadControl.create()
            val playerId = PlayerId.UNSET
            val parameters = LoadControl.Parameters(playerId, Timeline.EMPTY, MediaPeriodId(Any()),
                60_000_000L, 6_000_000L, 1f, true, false, C.TIME_UNSET)
            control.onPrepared(playerId)
            val allocator = control.allocator
            val allocation = allocator.allocate()
            val allocated = allocator.totalBytesAllocated
            assertFalse(control.shouldContinueLoading(parameters)) // Preview stops above five seconds.
            control.promote()
            assertSame(allocator, control.allocator)
            assertEquals(allocated, allocator.totalBytesAllocated)
            assertTrue(control.shouldContinueLoading(parameters)) // Full playback fills its usual buffer.
            allocator.release(allocation)
            control.onReleased(playerId)
        }
    }
}
