package com.jing.sakura.player

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.SeekBar
import java.util.WeakHashMap

internal object PlayerProgressStyleSpec {
    const val BAR_HEIGHT_DP = 5
    const val ACTIVE_BAR_HEIGHT_DP = 9
    const val ACTIVE_RADIUS_DP = 7
    const val TRANSLATION_Y_DP = 6
    const val SIDE_INSET_DP = 8

    val gradientColors = intArrayOf(
        0xFF49D6FF.toInt(),
        0xFF6A74FF.toInt(),
        0xFFD753FF.toInt(),
        0xFFFF4E8B.toInt()
    )
}

/** Applies Aulama branding to Leanback's final SeekBar without replacing its seek behavior. */
internal object PlayerProgressStyler {
    private val styledSeekBars = WeakHashMap<SeekBar, Boolean>()

    fun apply(root: View) {
        val seekBar = root.findViewById<SeekBar>(androidx.leanback.R.id.playback_progress) ?: return
        val density = root.resources.displayMetrics.density
        seekBar.setBarHeight((PlayerProgressStyleSpec.BAR_HEIGHT_DP * density).toInt())
        seekBar.setActiveBarHeight((PlayerProgressStyleSpec.ACTIVE_BAR_HEIGHT_DP * density).toInt())
        seekBar.setActiveRadius((PlayerProgressStyleSpec.ACTIVE_RADIUS_DP * density).toInt())
        seekBar.translationY = PlayerProgressStyleSpec.TRANSLATION_Y_DP * density
        seekBar.setSecondaryProgressColor(0x55FFFFFF)
        (seekBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            val inset = (PlayerProgressStyleSpec.SIDE_INSET_DP * density).toInt()
            params.marginStart = maxOf(params.marginStart, inset)
            params.marginEnd = maxOf(params.marginEnd, inset)
            seekBar.layoutParams = params
        }
        if (styledSeekBars.put(seekBar, true) == null) {
            seekBar.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
                applyGradient(view as SeekBar, (right - left).toFloat())
            }
        }
        seekBar.post { applyGradient(seekBar, seekBar.width.toFloat()) }

        root.findViewById<View>(androidx.leanback.R.id.secondary_controls_dock)?.translationY =
            PlayerProgressStyleSpec.TRANSLATION_Y_DP * density
    }

    private fun applyGradient(seekBar: SeekBar, width: Float) {
        if (width <= 0f) return
        runCatching {
            val field = SeekBar::class.java.getDeclaredField("mProgressPaint").apply {
                isAccessible = true
            }
            val paint = field.get(seekBar) as Paint
            paint.shader = LinearGradient(
                0f,
                0f,
                width,
                0f,
                PlayerProgressStyleSpec.gradientColors,
                null,
                Shader.TileMode.CLAMP
            )
            seekBar.invalidate()
        }.onFailure {
            seekBar.setProgressColor(PlayerProgressStyleSpec.gradientColors.last())
        }
    }
}
