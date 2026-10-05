package com.jing.sakura.compose.common

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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

/**
 * Shared motion tokens, modelled on tvOS: springs for anything that moves or scales, because
 * they can be retargeted mid-flight without losing velocity, and symmetric ease curves for
 * opacity. Everything here is meant to be read inside graphicsLayer or draw lambdas so focus
 * feedback stays smooth on Android 5/6 TV boxes without relayout per frame.
 *
 * Springs can overshoot slightly, so clamp progress with [unit] before using it for colours
 * or alpha.
 */
object AulamaMotion {
    /** Decelerating curve for elements arriving or gaining focus. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard = FastOutSlowInEasing
    /** Symmetric curve for cross-fades, equivalent to UIKit's easeInOut. */
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val PressMillis = 90
    const val BackdropMillis = 720
    const val LabelFadeMillis = 240
    const val HeroSwapMillis = 340
    const val PreviewRevealMillis = 950
    const val PreviewDismissMillis = 360

    const val ChoiceFocusScale = 1.05f
    const val PosterFocusScale = 1.07f
    const val PressedScale = 0.97f

    // Spring constants expressed as tvOS-style response/damping pairs:
    // stiffness = (2π / response)², so FocusResponse 0.34 s ≈ 340 stiffness.
    private const val FocusDamping = 0.78f
    private const val FocusStiffness = 340f
    private const val GlideStiffness = 230f
    private const val SettleDamping = 0.9f
    private const val SettleStiffness = 170f

    /** Focus lift: quick with a barely visible overshoot, which reads as "alive" on a TV. */
    fun <T> focusSpring(reducedMotion: Boolean = false): AnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = FocusDamping, stiffness = FocusStiffness)

    /** Natural frequency (rad/s) of the carousel glide; sqrt(stiffness) for a unit mass. */
    val GlideOmega: Float = kotlin.math.sqrt(GlideStiffness)

    /** Content scrolling: critically damped so rows never bounce past their resting card. */
    fun glideSpring(reducedMotion: Boolean = false): AnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = GlideStiffness,
                visibilityThreshold = 0.5f
            )
        }

    /** Larger surfaces such as panels and frames that should land softly. */
    fun <T> settleSpring(reducedMotion: Boolean = false): AnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = SettleDamping, stiffness = SettleStiffness)

    fun fade(durationMillis: Int, reducedMotion: Boolean = false, delayMillis: Int = 0): AnimationSpec<Float> =
        tween(
            durationMillis = if (reducedMotion) 0 else durationMillis,
            delayMillis = if (reducedMotion) 0 else delayMillis,
            easing = EaseInOut
        )

    fun focusSpec(focused: Boolean, reducedMotion: Boolean): AnimationSpec<Float> =
        focusSpring(reducedMotion)

    /** Clamps spring output (which may overshoot) for colour and alpha use. */
    fun unit(progress: Float): Float = progress.coerceIn(0f, 1f)
}

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
