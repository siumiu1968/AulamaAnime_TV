package com.jing.sakura.compose.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val StarClickKeys = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)

/** A short word under the stars so the score reads as a feeling rather than a number. */
fun ratingWord(score: Int): String = when (score) {
    1 -> "唔啱我"
    2 -> "一般"
    3 -> "不錯"
    4 -> "好睇"
    5 -> "神作"
    else -> ""
}

/** One focusable star. Focus previews the score; OK saves it. */
@Composable
fun RatingStar(
    lit: Boolean,
    enabled: Boolean,
    onFocused: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val currentOnFocused by rememberUpdatedState(onFocused)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(focused) {
        if (focused) currentOnFocused() else pressed = false
    }
    val lift = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = AulamaMotion.focusSpring(reducedMotion),
        label = "rating-star-lift"
    )
    val press = animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else AulamaMotion.PressMillis),
        label = "rating-star-press"
    )
    val fill by animateColorAsState(
        targetValue = if (lit) AulamaTvColors.Amber else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(if (reducedMotion) 0 else 160, easing = AulamaMotion.EaseInOut),
        label = "rating-star-fill"
    )
    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                val scale = (1f + 0.16f * lift.value) * press.value
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val progress = AulamaMotion.unit(lift.value)
                if (progress > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.1f * progress),
                        cornerRadius = CornerRadius(14.dp.toPx())
                    )
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = AulamaTvColors.FocusBorder.copy(alpha = progress),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(stroke)
                    )
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode !in StarClickKeys) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> if (currentEnabled) pressed = true
                    KeyEventType.KeyUp -> if (pressed) {
                        pressed = false
                        if (currentEnabled) currentOnSelect()
                    }
                }
                true
            }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .drawWithCache {
                    val star = starPath(size)
                    onDrawBehind { drawPath(star, fill) }
                }
        )
    }
}

private fun starPath(size: Size): Path {
    val outer = min(size.width, size.height) / 2f
    val inner = outer * 0.47f
    val centerX = size.width / 2f
    val centerY = size.height / 2f + outer * 0.06f
    return Path().apply {
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = -PI / 2 + point * PI / 5
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            if (point == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
