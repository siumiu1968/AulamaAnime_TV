package com.jing.sakura.compose.common

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

fun Modifier.lightweightEntrance(
    transitionKey: Any?,
    reducedMotion: Boolean,
    delayMillis: Int = 0,
    durationMillis: Int = 240,
    offsetY: Dp = 8.dp
): Modifier = composed {
    var visible by remember(transitionKey, reducedMotion) { mutableStateOf(reducedMotion) }
    LaunchedEffect(transitionKey, reducedMotion) {
        if (!reducedMotion) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            visible = true
        }
    }
    val duration = if (reducedMotion) 0 else durationMillis.coerceIn(180, 320)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = duration),
        label = "lightweight-entrance-alpha"
    )
    val translation by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = duration),
        label = "lightweight-entrance-offset"
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = offsetY.toPx() * translation
    }
}
