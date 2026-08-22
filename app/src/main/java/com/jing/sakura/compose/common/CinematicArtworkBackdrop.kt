package com.jing.sakura.compose.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun CinematicArtworkBackdrop(
    imageUrl: String,
    accent: Color,
    modifier: Modifier = Modifier,
    imageKey: String = imageUrl,
    artworkAlpha: Float = 1f,
    previewActive: Boolean = false,
    transitionDurationMillis: Int = 620
) {
    val reducedMotion = rememberReducedMotion()
    val target = remember(imageKey, imageUrl) {
        imageUrl.takeIf(String::isNotBlank)?.let {
            CinematicArtworkState(key = imageKey, imageUrl = it)
        }
    }
    var ready by remember { mutableStateOf<CinematicArtworkState?>(null) }
    val currentTargetKey by rememberUpdatedState(target?.key)

    LaunchedEffect(target) {
        if (target == null) ready = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val duration = if (reducedMotion) 0 else transitionDurationMillis
        val animatedAspectRatio by animateFloatAsState(
            targetValue = ready?.aspectRatio ?: DefaultArtworkAspectRatio,
            animationSpec = tween(duration, easing = FastOutSlowInEasing),
            label = "cinematic-artwork-aspect-ratio"
        )
        val adaptiveArtworkWidth = minOf(maxWidth, maxHeight * animatedAspectRatio)
        val artworkStartFraction = if (maxWidth.value > 0f) {
            (1f - adaptiveArtworkWidth.value / maxWidth.value).coerceIn(0f, 1f)
        } else {
            1f - DefaultArtworkAspectRatio
        }

        // Keep the extracted colour behind the poster so it can bleed into the
        // copy area without tinting or recolouring the original artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.11f),
                                accent.copy(alpha = 0.06f),
                                accent.copy(alpha = 0.025f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.64f, size.height * 0.46f),
                            radius = size.width * 0.72f
                        )
                    )
                }
        )

        target
            ?.takeIf { it.key != ready?.key }
            ?.let { pending ->
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = pending.imageUrl,
                        widthPx = 960,
                        heightPx = 1_360
                    ),
                    contentDescription = null,
                    onSuccess = { success ->
                        if (currentTargetKey == pending.key) {
                            val drawable = success.result.drawable
                            ready = pending.copy(
                                aspectRatio = cinematicArtworkAspectRatio(
                                    width = drawable.intrinsicWidth,
                                    height = drawable.intrinsicHeight
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .graphicsLayer { alpha = 0f }
                )
            }

        AnimatedContent(
            targetState = ready,
            transitionSpec = {
                fadeIn(tween(duration, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(tween(duration, easing = FastOutSlowInEasing)))
            },
            label = "cinematic-artwork-backdrop"
        ) { state ->
            if (state == null) return@AnimatedContent
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val artworkWidth = minOf(maxWidth, maxHeight * state.aspectRatio)
                val featherFraction = if (artworkWidth.value > 0f) {
                    (ArtworkFeatherWidth.value / artworkWidth.value).coerceIn(0.09f, 0.22f)
                } else {
                    0.15f
                }
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = state.imageUrl,
                        widthPx = 960,
                        heightPx = 1_360
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxHeight()
                        .width(artworkWidth)
                        .graphicsLayer {
                            alpha = if (previewActive) 0f else artworkAlpha
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        featherFraction * 0.17f to Color.Black.copy(alpha = 0.06f),
                                        featherFraction * 0.33f to Color.Black.copy(alpha = 0.22f),
                                        featherFraction * 0.53f to Color.Black.copy(alpha = 0.58f),
                                        featherFraction * 0.77f to Color.Black.copy(alpha = 0.86f),
                                        featherFraction to Color.Black,
                                        1f to Color.Black
                                    )
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = if (previewActive) {
                            arrayOf(0f to Color.Transparent, 1f to Color.Transparent)
                        } else {
                            arrayOf(
                                0f to AulamaTvColors.Background,
                                (artworkStartFraction - 0.16f).coerceIn(0f, 0.62f) to AulamaTvColors.Background,
                                (artworkStartFraction - 0.11f).coerceIn(0.01f, 0.67f) to AulamaTvColors.Background.copy(alpha = 0.94f),
                                (artworkStartFraction - 0.05f).coerceIn(0.02f, 0.73f) to AulamaTvColors.Background.copy(alpha = 0.68f),
                                (artworkStartFraction + 0.01f).coerceIn(0.03f, 0.79f) to AulamaTvColors.Background.copy(alpha = 0.34f),
                                (artworkStartFraction + 0.07f).coerceIn(0.04f, 0.85f) to AulamaTvColors.Background.copy(alpha = 0.12f),
                                (artworkStartFraction + 0.12f).coerceIn(0.05f, 0.90f) to AulamaTvColors.Background.copy(alpha = 0.03f),
                                (artworkStartFraction + 0.16f).coerceIn(0.06f, 0.94f) to Color.Transparent,
                                1f to Color.Transparent
                            )
                        }
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background.copy(
                                alpha = if (previewActive) 0.18f else 0.16f
                            ),
                            0.72f to Color.Transparent,
                            1f to AulamaTvColors.Background.copy(
                                alpha = if (previewActive) 0.50f else 0.55f
                            )
                        )
                    )
                )
        )

        // Restore a restrained colour bloom on top of the dark copy-area mask.
        // The earlier bloom sits behind an opaque mask and therefore disappears
        // on most TVs. This narrow band ends inside the poster feather, so the
        // artwork keeps its original colours while its light can travel left.
        if (!previewActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    (artworkStartFraction - 0.32f).coerceIn(0.01f, 0.54f) to Color.Transparent,
                                    (artworkStartFraction - 0.21f).coerceIn(0.02f, 0.65f) to accent.copy(alpha = 0.012f),
                                    (artworkStartFraction - 0.13f).coerceIn(0.03f, 0.73f) to accent.copy(alpha = 0.032f),
                                    (artworkStartFraction - 0.07f).coerceIn(0.04f, 0.79f) to accent.copy(alpha = 0.070f),
                                    (artworkStartFraction - 0.02f).coerceIn(0.05f, 0.84f) to accent.copy(alpha = 0.135f),
                                    (artworkStartFraction + 0.02f).coerceIn(0.06f, 0.88f) to accent.copy(alpha = 0.105f),
                                    (artworkStartFraction + 0.05f).coerceIn(0.07f, 0.91f) to accent.copy(alpha = 0.040f),
                                    (artworkStartFraction + 0.08f).coerceIn(0.08f, 0.94f) to Color.Transparent,
                                    1f to Color.Transparent
                                )
                            )
                        )
                    }
            )
        }
    }
}

private data class CinematicArtworkState(
    val key: String,
    val imageUrl: String,
    val aspectRatio: Float = DefaultArtworkAspectRatio
)

private const val DefaultArtworkAspectRatio = 2f / 3f
private val ArtworkFeatherWidth = 116.dp

internal fun cinematicArtworkAspectRatio(width: Int, height: Int): Float =
    if (width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.45f, 16f / 9f)
    } else {
        DefaultArtworkAspectRatio
    }
