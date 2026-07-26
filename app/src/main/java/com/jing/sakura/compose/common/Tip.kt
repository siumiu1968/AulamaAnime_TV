@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun LoadingOverlay(
    visible: Boolean,
    text: String = ""
) {
    val reducedMotion = rememberReducedMotion()
    var retained by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            retained = true
        } else if (retained) {
            if (!reducedMotion) delay(240L)
            retained = false
        }
    }
    if (retained) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(if (reducedMotion) 0 else 280)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 240))
        ) {
            Loading(text = text)
        }
    }
}

@Composable
fun Loading(text: String = "") {
    val reducedMotion = rememberReducedMotion()
    val breathTransition = rememberInfiniteTransition(label = "aulama-loading-breath")
    val logoScale by breathTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aulama-loading-logo-scale"
    )
    val logoAlpha by breathTransition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aulama-loading-logo-alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .aulamaTvBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.lightweightEntrance(
                transitionKey = Unit,
                reducedMotion = reducedMotion,
                durationMillis = 320,
                offsetY = 0.dp
            ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AulamaAnimeBrandMark(
                height = 64.dp,
                modifier = Modifier.graphicsLayer {
                    val scale = if (reducedMotion) 1f else logoScale
                    scaleX = scale
                    scaleY = scale
                    alpha = if (reducedMotion) 1f else logoAlpha
                }
            )
            Spacer(Modifier.height(22.dp))
            AulamaLoadingPulse(reducedMotion = reducedMotion)
            if (text.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = localizedText(text),
                    color = AulamaTvColors.TextSecondary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Black
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AulamaLoadingPulse(
    modifier: Modifier = Modifier,
    accent: Color = AulamaTvColors.Cyan,
    reducedMotion: Boolean? = null
) {
    val motionReduced = reducedMotion ?: rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "aulama-loading-flow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_050, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aulama-loading-flow-progress"
    )
    val travelPx = with(LocalDensity.current) { 122.dp.toPx() }
    Box(
        modifier = modifier
            .size(width = 178.dp, height = 6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AulamaTvColors.Outline.copy(alpha = 0.42f))
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = if (motionReduced) travelPx / 2f else progress * travelPx
                }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            AulamaTvColors.Cyan,
                            accent,
                            AulamaTvColors.Pink,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun ArtworkLoading(
    title: String,
    imageUrl: String,
    tags: String = "",
    episodeInfo: String = "",
    resumeEpisode: String = "",
    modifier: Modifier = Modifier
) {
    if (title.isBlank() && imageUrl.isBlank()) {
        Loading()
        return
    }

    val reducedMotion = rememberReducedMotion()
    val accent = rememberArtworkAccent(imageUrl)
    val tagItems = remember(tags) { artworkLoadingTagItems(tags) }
    val displayEpisodeInfo = localizedText(episodeInfo.trim())
    val displayResumeEpisode = localizedText(resumeEpisode.trim())
    val showEpisodeInfo = displayEpisodeInfo.isNotBlank() &&
        displayEpisodeInfo != displayResumeEpisode
    val hasMetadata = showEpisodeInfo ||
        displayResumeEpisode.isNotBlank() ||
        tagItems.isNotEmpty()
    val posterRequest = rememberPosterImageRequest(
        imageUrl = imageUrl,
        widthPx = 960,
        heightPx = 1_360
    )
    var posterReady by remember(imageUrl) { mutableStateOf(false) }
    val posterAlpha by animateFloatAsState(
        targetValue = if (posterReady) 0.76f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 420,
            easing = FastOutSlowInEasing
        ),
        label = "artwork-loading-poster-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .aulamaTvBackground()
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                onSuccess = { posterReady = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = posterAlpha
                        scaleX = 1.42f
                        scaleY = 1.42f
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        radius = 900f
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.43f to AulamaTvColors.Background,
                            0.56f to AulamaTvColors.Background.copy(alpha = 0.92f),
                            0.72f to AulamaTvColors.Background.copy(alpha = 0.42f),
                            1f to Color.Transparent
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background.copy(alpha = 0.26f),
                            0.58f to Color.Transparent,
                            1f to AulamaTvColors.Background
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(540.dp)
                .padding(start = 62.dp)
                .lightweightEntrance(
                    transitionKey = "$title|$imageUrl",
                    reducedMotion = reducedMotion,
                    durationMillis = 340,
                    offsetY = 8.dp
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(
                modifier = Modifier
                    .size(width = 42.dp, height = 4.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = localizedText(title),
                color = accent,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 39.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(if (hasMetadata) 18.dp else 24.dp))
            if (hasMetadata) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (displayResumeEpisode.isNotBlank()) {
                        ArtworkLoadingPill(
                            text = localizedText("繼續播放 · $displayResumeEpisode"),
                            accent = accent,
                            emphasized = true
                        )
                    }
                    if (showEpisodeInfo) {
                        ArtworkLoadingPill(
                            text = displayEpisodeInfo,
                            accent = accent
                        )
                    }
                    tagItems
                        .take(if (displayResumeEpisode.isBlank() && !showEpisodeInfo) 3 else 2)
                        .forEach { tag ->
                            ArtworkLoadingPill(
                                text = localizedText(tag),
                                accent = accent
                            )
                        }
                }
                Spacer(Modifier.height(22.dp))
            }
            AulamaLoadingPulse(accent = accent, reducedMotion = reducedMotion)
        }
    }
}

@Composable
private fun ArtworkLoadingPill(
    text: String,
    accent: Color,
    emphasized: Boolean = false
) {
    Text(
        text = text,
        color = if (emphasized) accent else AulamaTvColors.TextPrimary,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (emphasized) accent.copy(alpha = 0.16f) else Color(0xB0121822)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

internal fun artworkLoadingTagItems(tags: String): List<String> = tags
    .split(Regex("""[、,，/|·\\s]+"""))
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

@Composable
fun ErrorTip(message: String, retry: () -> Unit = {}) {
    val focusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .lightweightEntrance(
                    transitionKey = message,
                    reducedMotion = reducedMotion,
                    durationMillis = 200,
                    offsetY = 0.dp
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AulamaAnimeBrandMark(height = 48.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = localizedText("暫時未能載入"),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = localizedText(message),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            AulamaActionButton(
                label = localizedText("重試"),
                onClick = retry,
                modifier = Modifier.focusRequester(focusRequester),
                accent = AulamaTvColors.Cyan
            )
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
