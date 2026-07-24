package com.jing.sakura.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.TextView

/** TV action button with a visible left-to-right auto-advance countdown. */
class CountdownActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val bounds = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        background = null
        setWillNotDraw(false)
    }

    fun startCountdown(durationMs: Long, onComplete: () -> Unit) {
        cancelCountdown()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs.coerceAtLeast(1L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    if (!cancelled) onComplete()
                }
            })
            start()
        }
    }

    fun cancelCountdown() {
        animator?.cancel()
        animator = null
        progress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        surfacePaint.shader = null
        surfacePaint.color = when {
            isPressed -> 0xFFFFFFFF.toInt()
            isFocused -> 0xF2FFFFFF.toInt()
            else -> 0xB3171C25.toInt()
        }
        canvas.drawRoundRect(bounds, radius, radius, surfacePaint)

        if (progress > 0f) {
            val save = canvas.save()
            canvas.clipRect(0f, 0f, width * progress, height.toFloat())
            surfacePaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                0f,
                intArrayOf(
                    0xFF4AD8FF.toInt(),
                    0xFF7A68FF.toInt(),
                    0xFFFF4E91.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(bounds, radius, radius, surfacePaint)
            canvas.restoreToCount(save)
            surfacePaint.shader = null
        }

        strokePaint.color = if (isFocused) 0xFFFFFFFF.toInt() else 0x66FFFFFF
        strokePaint.strokeWidth = dp(if (isFocused) 2f else 1f)
        val inset = strokePaint.strokeWidth / 2f
        bounds.inset(inset, inset)
        canvas.drawRoundRect(bounds, radius, radius, strokePaint)
        bounds.inset(-inset, -inset)

        val label = text.toString()
        val labelPaint = paint
        val x = (width - labelPaint.measureText(label)) / 2f
        val metrics = labelPaint.fontMetrics
        val y = (height - metrics.ascent - metrics.descent) / 2f
        labelPaint.color = if (isFocused && progress <= 0f) 0xFF08090B.toInt() else Color.WHITE
        canvas.drawText(label, x, y, labelPaint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelCountdown()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
