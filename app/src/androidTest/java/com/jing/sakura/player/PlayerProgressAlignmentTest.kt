package com.jing.sakura.player

import android.graphics.Rect
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.leanback.widget.SeekBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerProgressAlignmentTest {
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
}
