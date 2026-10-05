package com.jing.sakura.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.TextView

internal data class CountdownActionVisualStyle(
    val surfaceColor: Int,
    val labelColor: Int,
    val labelShadowColor: Int?
)

internal object CountdownActionVisualPolicy {
    fun resolve(
        isFocused: Boolean,
        isPressed: Boolean,
        countdownActive: Boolean
    ): CountdownActionVisualStyle {
        val usesLightSurface = !countdownActive && (isFocused || isPressed)
        return CountdownActionVisualStyle(
            surfaceColor = when {
                usesLightSurface -> 0xFFFFFFFF.toInt()
                countdownActive -> 0xD9171C25.toInt()
                else -> 0x99171C25.toInt()
            },
            labelColor = when {
                usesLightSurface -> 0xFF08090B.toInt()
                countdownActive -> 0xFFFFFFFF.toInt()
                else -> 0xE8FFFFFF.toInt()
            },
            labelShadowColor = if (countdownActive) 0xB8000000.toInt() else null
        )
    }
}

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
    private val capsulePath = Path()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        background = null
        clipToOutline = false
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

    /** Stops the countdown without emptying the bar, for while the prompt fades away. */
    fun freezeCountdown() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    /** Empties a bar left over from a frozen countdown when no countdown is running. */
    fun clearIdleProgress() {
        if (animator == null && progress != 0f) {
            progress = 0f
            invalidate()
        }
    }

    fun pauseCountdown() {
        animator?.takeIf { it.isStarted && !it.isPaused }?.pause()
    }

    fun resumeCountdown() {
        animator?.takeIf { it.isPaused }?.resume()
    }

    override fun onDraw(canvas: Canvas) {
        val focusedStrokeWidth = dp(if (isFocused) 2f else 1f)
        val inset = focusedStrokeWidth / 2f
        val countdownActive = animator != null || progress > 0f
        val visualStyle = CountdownActionVisualPolicy.resolve(
            isFocused = isFocused,
            isPressed = isPressed,
            countdownActive = countdownActive
        )
        bounds.set(inset, inset, width - inset, height - inset)
        val radius = bounds.height() / 2f
        capsulePath.reset()
        capsulePath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        surfacePaint.shader = null
        surfacePaint.color = visualStyle.surfaceColor
        canvas.drawPath(capsulePath, surfacePaint)

        if (progress > 0f) {
            val fillEdge = bounds.left + bounds.width() * progress
            val save = canvas.save()
            canvas.clipPath(capsulePath)
            canvas.clipRect(bounds.left, bounds.top, fillEdge, bounds.bottom)
            surfacePaint.shader = fillShader()
            canvas.drawPath(capsulePath, surfacePaint)
            if (progress < 1f) {
                // A soft glow riding the leading edge makes the bar read as charging.
                val glowWidth = dp(GLOW_WIDTH_DP)
                canvas.translate(fillEdge - glowWidth, 0f)
                surfacePaint.shader = glowShader(glowWidth)
                canvas.drawRect(0f, bounds.top, glowWidth, bounds.bottom, surfacePaint)
            }
            canvas.restoreToCount(save)
            surfacePaint.shader = null
        }

        strokePaint.color = if (isFocused) 0xFFFFFFFF.toInt() else 0x52FFFFFF
        strokePaint.strokeWidth = focusedStrokeWidth
        canvas.drawPath(capsulePath, strokePaint)

        val label = text.toString()
        val labelPaint = paint
        val x = (width - labelPaint.measureText(label)) / 2f
        val metrics = labelPaint.fontMetrics
        val y = (height - metrics.ascent - metrics.descent) / 2f
        labelPaint.color = visualStyle.labelColor
        visualStyle.labelShadowColor?.let { shadowColor ->
            labelPaint.setShadowLayer(dp(2f), 0f, dp(1f), shadowColor)
        }
        canvas.drawText(label, x, y, labelPaint)
        labelPaint.clearShadowLayer()
    }

    private var cachedFillShader: Shader? = null
    private var cachedFillWidth = -1
    private var cachedGlowShader: Shader? = null
    private var cachedGlowWidth = -1f

    // Shaders are cached so the countdown does not allocate on every frame on older TV boxes.
    private fun fillShader(): Shader {
        val cached = cachedFillShader
        if (cached != null && cachedFillWidth == width) return cached
        return LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(0xFF4AD8FF.toInt(), 0xFF7A68FF.toInt(), 0xFFFF4E91.toInt()),
            null,
            Shader.TileMode.CLAMP
        ).also {
            cachedFillShader = it
            cachedFillWidth = width
        }
    }

    private fun glowShader(glowWidth: Float): Shader {
        val cached = cachedGlowShader
        if (cached != null && cachedGlowWidth == glowWidth) return cached
        return LinearGradient(
            0f,
            0f,
            glowWidth,
            0f,
            intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        ).also {
            cachedGlowShader = it
            cachedGlowWidth = glowWidth
        }
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

    private companion object {
        const val GLOW_WIDTH_DP = 26f
    }
}
