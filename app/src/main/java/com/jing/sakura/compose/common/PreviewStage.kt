package com.jing.sakura.compose.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.jing.sakura.home.HeroPreviewSpec
import kotlinx.coroutines.flow.first

/**
 * How far the cover has dissolved into the playing preview: 0 = artwork, 1 = video.
 *
 * Revealing is a slow, symmetric cross-fade (like the Apple TV app's trailer hand-off); dismissing
 * on any key press is quick so the UI answers immediately.
 */
@Composable
fun rememberPreviewReveal(active: Boolean, reducedMotion: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = AulamaMotion.fade(
            durationMillis = if (active) AulamaMotion.PreviewRevealMillis else AulamaMotion.PreviewDismissMillis,
            reducedMotion = reducedMotion
        ),
        label = "preview-reveal"
    )

/**
 * Slow push-in on the cover while the preview is being prepared, so the wait reads as
 * "something is about to play" rather than a frozen screen. Settles back on interaction.
 */
@Composable
fun rememberPreviewAnticipation(
    waiting: Boolean,
    waitMillis: Long,
    reducedMotion: Boolean
): State<Float> = animateFloatAsState(
    targetValue = if (waiting && !reducedMotion) 1f else 0f,
    animationSpec = if (waiting && !reducedMotion) {
        tween(durationMillis = waitMillis.toInt().coerceAtLeast(600), easing = LinearOutSlowInEasing)
    } else {
        AulamaMotion.settleSpring(reducedMotion)
    },
    label = "preview-anticipation"
)

/**
 * Hosts [HeroPreviewPlayer] so the video dissolves in over the cover and, when the preview is
 * cancelled, keeps the last clip on screen while it fades and its audio ducks out, instead of
 * disappearing in a single frame.
 *
 * @param retainWhileFading false when the screen is leaving, so playback stops at once.
 */
@Composable
fun RetainedHeroPreview(
    spec: HeroPreviewSpec?,
    reveal: State<Float>,
    retainWhileFading: Boolean,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {}
) {
    var fadingSpec by remember { mutableStateOf<HeroPreviewSpec?>(null) }
    LaunchedEffect(spec, retainWhileFading) {
        if (spec != null) {
            fadingSpec = spec
            return@LaunchedEffect
        }
        if (retainWhileFading) {
            snapshotFlow { reveal.value }.first { it <= 0.001f }
        }
        fadingSpec = null
    }
    val displayed = spec ?: fadingSpec?.takeIf { retainWhileFading } ?: return
    HeroPreviewPlayer(
        spec = displayed,
        volume = { AulamaMotion.unit(reveal.value) },
        onReady = onReady,
        onError = onError,
        onEnded = onEnded,
        modifier = modifier.graphicsLayer { alpha = AulamaMotion.unit(reveal.value) }
    )
}

/**
 * Netflix-style vignette that fades in with the preview so copy on the left and the rows at the
 * bottom stay legible over bright video.
 */
@Composable
fun PreviewLegibilityScrim(reveal: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val horizontal = previewHorizontalScrim()
                val vertical = previewVerticalScrim()
                onDrawBehind {
                    val progress = AulamaMotion.unit(reveal())
                    if (progress > 0f) {
                        drawRect(horizontal, alpha = progress)
                        drawRect(vertical, alpha = progress)
                    }
                }
            }
    )
}

internal fun previewHorizontalScrim(): Brush = Brush.horizontalGradient(
    colorStops = arrayOf(
        0f to AulamaTvColors.Background.copy(alpha = 0.74f),
        0.26f to AulamaTvColors.Background.copy(alpha = 0.46f),
        0.5f to Color.Transparent,
        1f to Color.Transparent
    )
)

internal fun previewVerticalScrim(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to AulamaTvColors.Background.copy(alpha = 0.2f),
        0.6f to Color.Transparent,
        1f to AulamaTvColors.Background.copy(alpha = 0.62f)
    )
)
