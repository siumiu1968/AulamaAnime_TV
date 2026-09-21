package com.jing.sakura.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs

/** Lightweight single-view overlay for the temporary 2x remote hold gesture. */
class SpeedBoostOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 13, 18, 25)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 18f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
    }
    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val bounds = RectF()
    private val triangle = Path()
    private var charge = 0f
    private var locked = false
    private val chargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(83, 217, 233)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val chargePath = Path()
    private val chargeSegment = Path()
    private val pathMeasure = android.graphics.PathMeasure()
    private var phase = 0f
    private var phaseAnimator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density
        bounds.set(inset, inset, width - inset, height - inset)
        val radius = bounds.height() / 2f
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
        canvas.drawRoundRect(bounds, radius, radius, borderPaint)

        chargePath.reset()
        chargePath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        pathMeasure.setPath(chargePath, false)
        chargeSegment.reset()
        pathMeasure.getSegment(0f, pathMeasure.length * charge, chargeSegment, true)
        if (!locked) canvas.drawPath(chargeSegment, chargePaint)

        val centerY = height / 2f
        val textBaseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("2×", width * 0.36f, textBaseline, textPaint)

        val triangleSize = 8f * density
        val gap = 4f * density
        val firstX = width * 0.58f + phase * 3f * density
        drawTriangle(canvas, firstX, centerY, triangleSize, pulseAlpha(phase))
        drawTriangle(
            canvas,
            firstX + triangleSize + gap,
            centerY,
            triangleSize,
            pulseAlpha((phase + 0.56f) % 1f)
        )
    }

    fun updateCharge(value: Float, isLocked: Boolean) {
        charge = value.coerceIn(0f, 1f)
        val wasLocked = locked
        locked = isLocked
        if (isLocked && !wasLocked) {
            animate().cancel()
            animate().alpha(0.55f).translationY(-12f * density).setDuration(260L).start()
        }
        contentDescription = if (locked) "2 倍速已鎖定，再按確認解除" else "2 倍速，長按 3 秒鎖定"
        invalidate()
    }

    fun showBoost() {
        charge = 0f
        locked = false
        translationY = 0f
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(160L).start()
        if (phaseAnimator?.isRunning == true) return
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hideBoost() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(100L)
            .withEndAction { visibility = GONE }
            .start()
    }

    override fun onDetachedFromWindow() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        super.onDetachedFromWindow()
    }

    private fun drawTriangle(canvas: Canvas, left: Float, centerY: Float, size: Float, alpha: Int) {
        triangle.reset()
        triangle.moveTo(left, centerY - size / 2f)
        triangle.lineTo(left + size * 0.82f, centerY)
        triangle.lineTo(left, centerY + size / 2f)
        triangle.close()
        trianglePaint.alpha = alpha
        canvas.drawPath(triangle, trianglePaint)
    }

    private fun pulseAlpha(value: Float): Int {
        val pulse = (1f - abs(value - 0.32f) / 0.34f).coerceIn(0f, 1f)
        return (92 + 163 * pulse).toInt()
    }
}
