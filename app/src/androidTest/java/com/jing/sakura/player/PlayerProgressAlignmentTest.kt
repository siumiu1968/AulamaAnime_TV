package com.jing.sakura.player

import android.graphics.Rect
import android.graphics.RectF
import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.leanback.widget.SeekBar
import androidx.leanback.widget.PlaybackSeekUi
import androidx.leanback.widget.PlaybackTransportRowPresenter
import androidx.leanback.media.PlaybackGlueHost
import androidx.leanback.media.PlayerAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerProgressAlignmentTest {
    private class SeekAdapter : PlayerAdapter() {
        var positionMs = 100_000L
        val seeks = mutableListOf<Long>()
        override fun play() = Unit
        override fun pause() = Unit
        override fun isPrepared() = true
        override fun getDuration() = 24 * 60_000L
        override fun getCurrentPosition() = positionMs
        override fun seekTo(positionInMs: Long) { positionMs = positionInMs; seeks += positionInMs }
    }

    private class SeekHost : PlaybackGlueHost(), PlaybackSeekUi {
        var seekClient: PlaybackSeekUi.Client? = null
        override fun setPlaybackSeekUiClient(client: PlaybackSeekUi.Client?) { seekClient = client }
    }

    @Test fun durationStaysAlignedWithVisibleTrackAfterFocusAndRepeatedStyling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.leanback.R.style.Theme_Leanback)
            for (width in listOf(1280, 1920)) {
                val host = FrameLayout(context)
                val row = LayoutInflater.from(context).inflate(
                    androidx.leanback.R.layout.lb_playback_transport_controls_row, host, false)
                host.addView(row)
                val seek = row.findViewById<SeekBar>(androidx.leanback.R.id.playback_progress)
                val time = row.findViewById<TextView>(androidx.leanback.R.id.total_time)
                time.text = "1:23:45"
                repeat(2) {
                    PlayerProgressStyler.apply(row)
                    host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
                    host.layout(0, 0, width, 720)
                    seek.requestFocus()
                    val seekBounds = Rect(0, 0, seek.width, seek.height)
                    val timeBounds = Rect(0, 0, time.width, time.height)
                    host.offsetDescendantRectToMyCoords(seek, seekBounds)
                    host.offsetDescendantRectToMyCoords(time, timeBounds)
                    val field = SeekBar::class.java.getDeclaredField("mBackgroundRect").apply { isAccessible = true }
                    val track = field.get(seek) as RectF
                    assertEquals((seekBounds.left + track.right).toDouble(), timeBounds.right.toDouble(), 1.0)
                }
            }
        }
    }

    @Test fun heldDirectionsUseOneNativeSeekAndBackCancelsWithoutTakingMenuKeys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, androidx.leanback.R.style.Theme_Leanback)
            val adapter = SeekAdapter()
            val glue = ProgressTransportControlGlue(context, Activity(), adapter,
                updateProgress = {}, chooseEpisode = {}, switchSourceFallback = {},
                playPreviousEpisode = {}, playNextEpisode = {}, open4kModePicker = {}, openSpeedPicker = {})
            val seekHost = SeekHost()
            glue.isSeekEnabled = true
            glue.host = seekHost
            val presenter = glue.playbackRowPresenter as PlaybackTransportRowPresenter
            val parent = FrameLayout(context)
            val holder = presenter.onCreateViewHolder(parent) as PlaybackTransportRowPresenter.ViewHolder
            parent.addView(holder.view)
            presenter.onBindViewHolder(holder, glue.controlsRow)
            holder.setPlaybackSeekUiClient(checkNotNull(seekHost.seekClient))
            val seek = holder.view.findViewById<SeekBar>(androidx.leanback.R.id.playback_progress)
            seek.requestFocus()

            fun send(action: Int, key: Int, time: Long, repeat: Int = 0, down: Long = 1_000L) {
                val event = KeyEvent(down, time, action, key, repeat)
                if (!glue.prepareProgressSeekKeyEvent(event, progressFocused = true) &&
                    !glue.onKey(seek, key, event)) seek.dispatchKeyEvent(event)
            }
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1_000)
            assertEquals(1, adapter.seeks.size)
            assertEquals(105_000.0, adapter.positionMs.toDouble(), 1.0)
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1_050, 1)
            assertEquals(1, adapter.seeks.size)
            var previous = adapter.positionMs
            for ((time, step) in listOf(1_800L to 10_000L, 3_000L to 20_000L, 7_000L to 28_800L)) {
                send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, time, 2)
                assertEquals(step.toDouble(), (adapter.positionMs - previous).toDouble(), 1.0)
                previous = adapter.positionMs
            }
            assertEquals(4, adapter.seeks.size)
            send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 7_001)
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 7_200, 6)
            assertEquals(5_000.0, (previous - adapter.positionMs).toDouble(), 1.0)
            send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 7_201)
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 7_300)
            send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 7_301)
            assertEquals(100_000L, adapter.positionMs)
            val writes = adapter.seeks.size
            val oldHold = KeyEvent(1_000, 8_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 99)
            assertFalse(glue.prepareProgressSeekKeyEvent(oldHold, progressFocused = false))
            assertEquals(writes, adapter.seeks.size)
            assertTrue(seek.hasFocus())
            presenter.onUnbindViewHolder(holder)
            glue.host = null
        }
    }
}
