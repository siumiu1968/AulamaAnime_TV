package com.jing.sakura.compose.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jing.sakura.home.HOME_ENTRANCE_DURATION_MS
import com.jing.sakura.home.homeEntranceFraction

@Composable
internal fun rememberHomeEntrance(key: Any?, reducedMotion: Boolean): State<Float> {
    val progress = remember(key, reducedMotion) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(progress) {
        if (!reducedMotion) {
            withFrameNanos { }
            progress.animateTo(1f, tween(HOME_ENTRANCE_DURATION_MS, easing = LinearEasing))
        }
    }
    return progress.asState()
}

internal fun Modifier.homeEntrance(
    timeline: State<Float>,
    delay: Float = 0f,
    span: Float = 0.62f,
    offsetY: Dp = 10.dp
): Modifier = graphicsLayer {
    val fraction = homeEntranceFraction(timeline.value, delay, span)
    alpha = fraction
    translationY = (1f - fraction) * offsetY.toPx()
}
