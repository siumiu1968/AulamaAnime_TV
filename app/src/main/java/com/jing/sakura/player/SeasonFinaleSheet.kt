package com.jing.sakura.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.auth.SeriesCompletion
import com.jing.sakura.compose.common.AulamaMotion
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.RatingStar
import com.jing.sakura.compose.common.ratingWord
import com.jing.sakura.compose.common.SpringFocusButton
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.data.AnimeData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the end-of-season sheet needs, prepared while the final episode plays. */
data class SeasonFinaleContent(
    val animeId: String,
    val title: String,
    val posterUrl: String,
    val completion: SeriesCompletion,
    val savedRating: Int,
    val nextWatch: List<AnimeData>,
    val catalogItem: AnimeData?
)

internal enum class SeasonFinaleStage { Rating, NextWatch }

/** Viewers who already rated the title go straight to the recommendations, as on the web. */
internal fun initialSeasonFinaleStage(savedRating: Int): SeasonFinaleStage =
    if (savedRating in 1..5) SeasonFinaleStage.NextWatch else SeasonFinaleStage.Rating

private val SheetShape = RoundedCornerShape(22.dp)
private val PosterShape = RoundedCornerShape(12.dp)
private val ClickKeys = intArrayOf(
    NativeKeyEvent.KEYCODE_DPAD_CENTER,
    NativeKeyEvent.KEYCODE_ENTER,
    NativeKeyEvent.KEYCODE_NUMPAD_ENTER
)

/**
 * End-of-season sheet shown over the paused final frame: first "how many stars?", then "what
 * next?" with the title's sequels and personal recommendations. Back closes the player.
 */
@Composable
internal fun SeasonFinaleSheet(
    content: SeasonFinaleContent,
    onSaveRating: suspend (Int) -> Boolean,
    onOpenAnime: (AnimeData) -> Unit,
    onHome: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val accent = rememberArtworkAccent(content.posterUrl, enabled = true)
    var stage by remember { mutableStateOf(initialSeasonFinaleStage(content.savedRating)) }
    var savedScore by remember { mutableIntStateOf(content.savedRating) }
    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val backdrop = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { backdrop.animateTo(1f, AulamaMotion.fade(320, reducedMotion)) }
        entrance.animateTo(1f, AulamaMotion.settleSpring(reducedMotion))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = AulamaMotion.unit(backdrop.value) }
            .background(AulamaTvColors.Background.copy(alpha = 0.82f))
    ) {
        AsyncImage(
            model = rememberPosterImageRequest(content.posterUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.2f }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = size.maxDimension * 0.55f
                    )
                    val vignette = Brush.verticalGradient(
                        0f to AulamaTvColors.Background.copy(alpha = 0.55f),
                        0.5f to AulamaTvColors.Background.copy(alpha = 0.35f),
                        1f to AulamaTvColors.Background.copy(alpha = 0.9f)
                    )
                    onDrawBehind {
                        drawRect(vignette)
                        drawRect(glow)
                    }
                }
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 720.dp)
                .graphicsLayer {
                    val progress = entrance.value
                    alpha = AulamaMotion.unit(progress)
                    translationY = (1f - progress) * 28.dp.toPx()
                    val scale = 0.96f + 0.04f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .clip(SheetShape)
                .background(AulamaTvColors.SurfaceRaised.copy(alpha = 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), SheetShape)
                .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(if (reducedMotion) 0 else 260, delayMillis = if (reducedMotion) 0 else 80)) +
                        slideInHorizontally(
                            tween(if (reducedMotion) 0 else 420, easing = AulamaMotion.EmphasizedDecelerate)
                        ) { it / 10 }) togetherWith
                        (fadeOut(tween(if (reducedMotion) 0 else 140)) +
                            slideOutHorizontally(tween(if (reducedMotion) 0 else 200)) { -it / 12 })
                },
                label = "season-finale-stage"
            ) { current ->
                when (current) {
                    SeasonFinaleStage.Rating -> RatingStage(
                        content = content,
                        onSaveRating = onSaveRating,
                        onSaved = { score ->
                            savedScore = score
                            stage = SeasonFinaleStage.NextWatch
                        },
                        onSkip = { stage = SeasonFinaleStage.NextWatch }
                    )
                    SeasonFinaleStage.NextWatch -> NextWatchStage(
                        content = content,
                        savedScore = savedScore,
                        accent = accent,
                        onOpenAnime = onOpenAnime,
                        onHome = onHome
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingStage(
    content: SeasonFinaleContent,
    onSaveRating: suspend (Int) -> Boolean,
    onSaved: (Int) -> Unit,
    onSkip: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var focusedScore by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val initialStar = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { initialStar.requestFocus() }
    }
    val submit: (Int) -> Unit = { score ->
        if (!saving) {
            saving = true
            failed = false
            scope.launch {
                val saved = runCatching { onSaveRating(score) }.getOrDefault(false)
                saving = false
                if (saved) onSaved(score) else failed = true
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = rememberPosterImageRequest(content.posterUrl),
            contentDescription = localizedText(content.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 136.dp, height = 204.dp)
                .clip(PosterShape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), PosterShape)
        )
        Spacer(Modifier.width(30.dp))
        Column(modifier = Modifier.widthIn(max = 470.dp)) {
            FinaleEyebrow(title = content.title)
            Spacer(Modifier.height(8.dp))
            Text(
                text = localizedText("睇完呢套，你俾幾多粒星？"),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 25.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = localizedText("分享你嘅感受，讓之後嘅推薦更合心意。"),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (score in 1..5) {
                    RatingStar(
                        lit = score <= focusedScore,
                        enabled = !saving,
                        onFocused = { focusedScore = score },
                        onSelect = { submit(score) },
                        modifier = if (score == 3) Modifier.focusRequester(initialStar) else Modifier
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = localizedText(
                    when {
                        saving -> "儲存中…"
                        failed -> "評分未能儲存，請再試一次，或暫時略過。"
                        focusedScore > 0 -> "$focusedScore 星 · ${ratingWord(focusedScore)} · 按 OK 儲存"
                        else -> ""
                    }
                ),
                color = if (failed) AulamaTvColors.Pink else AulamaTvColors.Amber,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(16.dp))
            SpringFocusButton(
                onClick = onSkip,
                enabled = !saving,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = localizedText("暫時唔評分"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun NextWatchStage(
    content: SeasonFinaleContent,
    savedScore: Int,
    accent: Color,
    onOpenAnime: (AnimeData) -> Unit,
    onHome: () -> Unit
) {
    val firstTarget = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstTarget.requestFocus() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FinaleEyebrow(title = content.title)
        Spacer(Modifier.height(8.dp))
        Text(
            text = localizedText("下一套，想睇啲咩？"),
            color = AulamaTvColors.TextPrimary,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        if (savedScore in 1..5) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = localizedText("你俾咗 ${"★".repeat(savedScore)} ${ratingWord(savedScore)}，之後嘅推薦會更合口味。"),
                color = AulamaTvColors.Amber,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
            )
        }
        Spacer(Modifier.height(20.dp))
        if (content.nextWatch.isEmpty()) {
            Text(
                text = localizedText("暫時未有相關作品，稍後再探索。"),
                color = AulamaTvColors.TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                modifier = Modifier.padding(vertical = 40.dp)
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                content.nextWatch.forEachIndexed { index, anime ->
                    NextWatchCard(
                        anime = anime,
                        accent = accent,
                        onOpen = { onOpenAnime(anime) },
                        modifier = if (index == 0) Modifier.focusRequester(firstTarget) else Modifier
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpringFocusButton(
                onClick = onHome,
                prominent = true,
                modifier = Modifier
                    .height(42.dp)
                    .then(if (content.nextWatch.isEmpty()) Modifier.focusRequester(firstTarget) else Modifier)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = localizedText("返回首頁"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.width(18.dp))
            Text(
                text = localizedText("按返回鍵回到詳情頁"),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
            )
        }
    }
}

@Composable
private fun FinaleEyebrow(title: String) {
    Text(
        text = localizedText("$title · 已看完"),
        color = AulamaTvColors.Cyan,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun NextWatchCard(
    anime: AnimeData,
    accent: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(focused) { if (!focused) pressed = false }
    val lift = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = AulamaMotion.focusSpring(reducedMotion),
        label = "next-watch-lift"
    )
    Column(
        modifier = modifier
            .width(96.dp)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode !in ClickKeys) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> pressed = true
                    KeyEventType.KeyUp -> if (pressed) {
                        pressed = false
                        currentOnOpen()
                    }
                }
                true
            }
            .focusable(interactionSource = interactionSource),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 144.dp)
                .graphicsLayer {
                    val scale = 1f + (AulamaMotion.PosterFocusScale - 1f) * lift.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .drawWithCache {
                    val radius = CornerRadius(10.dp.toPx())
                    onDrawWithContent {
                        drawContent()
                        val progress = AulamaMotion.unit(lift.value)
                        if (progress > 0f) {
                            val outer = 3.dp.toPx()
                            drawRoundRect(
                                color = accent.copy(alpha = progress),
                                topLeft = Offset(outer / 2f, outer / 2f),
                                size = Size(size.width - outer, size.height - outer),
                                cornerRadius = radius,
                                style = Stroke(outer)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f * progress),
                                cornerRadius = radius,
                                style = Stroke(1.dp.toPx())
                            )
                        }
                    }
                }
                .clip(RoundedCornerShape(10.dp))
                .background(AulamaTvColors.Surface)
        ) {
            AsyncImage(
                model = rememberPosterImageRequest(anime.imageUrl),
                contentDescription = localizedText(anime.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = localizedText(anime.title),
            color = if (focused) AulamaTvColors.TextPrimary else AulamaTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
