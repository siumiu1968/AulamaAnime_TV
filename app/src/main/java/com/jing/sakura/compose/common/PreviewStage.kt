package com.jing.sakura.compose.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.jing.sakura.home.HeroPreviewSpec
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.player.NavigateToPlayerArg
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos

@Composable
internal fun PreviewPreparationHint(
    visible: Boolean,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion()
) {
    if (!visible) return
    val phase = if (reducedMotion) null else {
        rememberInfiniteTransition(label = "preview-loading-dots").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
            label = "preview-loading-phase"
        )
    }
    val label = localizedText("預覽載入中")
    Canvas(
        modifier = modifier
            .size(width = 24.dp, height = 8.dp)
            .testTag("preview-loading-dots")
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
    ) {
        repeat(3) { index ->
            val pulse = phase?.value?.let {
                ((cos((it - index / 3f) * 2 * PI) + 1) / 2).toFloat()
            }
            drawCircle(
                color = AulamaTvColors.TextSecondary,
                alpha = pulse?.let { 0.28f + it * 0.67f } ?: 0.72f,
                radius = 2.dp.toPx(),
                center = Offset((3 + index * 9).dp.toPx(), size.height / 2)
            )
        }
    }
}

internal fun isPreviewPreparationPending(
    state: HeroPreviewState,
    focusedAnimeId: String?,
    focusedSourceId: String?,
    firstFrameReady: Boolean
): Boolean {
    if (focusedAnimeId.isNullOrBlank() || focusedSourceId.isNullOrBlank()) return false
    return when (state) {
        is HeroPreviewState.Loading -> state.requestKey == "$focusedSourceId:$focusedAnimeId"
        is HeroPreviewState.Ready -> !firstFrameReady &&
            state.spec.navigateToPlayerArg.animeId == focusedAnimeId &&
            state.spec.navigateToPlayerArg.sourceId == focusedSourceId
        else -> false
    }
}

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
    playbackEnabled: Boolean = true,
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {},
    onContinuePlayback: ((NavigateToPlayerArg) -> Unit)? = null
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
        playbackEnabled = if (spec != null) playbackEnabled else retainWhileFading && reveal.value > 0.001f,
        volume = { AulamaMotion.unit(reveal.value) },
        onReady = { if (spec?.key == displayed.key) onReady() },
        onError = onError,
        onEnded = onEnded,
        onContinuePlayback = onContinuePlayback.takeIf {
            spec?.key == displayed.key && playbackEnabled
        },
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
