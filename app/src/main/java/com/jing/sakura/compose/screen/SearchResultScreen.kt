@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.LoadingOverlay
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.compose.common.safelyRequestFocus
import com.jing.sakura.data.AnimeData
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.search.SearchResultViewModel
import com.jing.sakura.search.searchResultTitle

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    val context = LocalContext.current
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    val gridState = rememberLazyGridState()
    val firstItemFocusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotion()
    var focusedAnime by remember { mutableStateOf<AnimeData?>(null) }
    val backdropAnime = focusedAnime ?: pagingItems.itemSnapshotList.items.firstOrNull()
    val extractedAccent = rememberArtworkAccent(
        imageUrl = backdropAnime?.imageUrl.orEmpty(),
        enabled = backdropAnime != null
    )
    val accent by animateColorAsState(
        targetValue = extractedAccent,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 360,
            easing = FastOutSlowInEasing
        ),
        label = "search-result-accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        SearchResultBackdrop(
            anime = backdropAnime,
            accent = accent,
            reducedMotion = reducedMotion
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 42.dp,
                end = 42.dp,
                top = 18.dp,
                bottom = 42.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "search-result-header", span = { GridItemSpan(maxLineSpan) }) {
                AulamaPageHeader(
                    title = searchResultTitle(viewModel.keyword),
                    subtitle = if (pagingItems.itemCount > 0) {
                        localizedText("${pagingItems.itemCount} 套作品 · 網站模糊搜尋")
                    } else {
                        localizedText("可輸入別名、繁簡名稱或日文原名")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (refreshState is LoadState.NotLoading && pagingItems.itemCount == 0) {
                item(key = "search-result-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.grid_no_data_tip),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = AulamaTvColors.TextSecondary
                        )
                    }
                }
            }

            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { "${it.sourceId}:${it.id}" }
            ) { index ->
                val anime = pagingItems[index] ?: return@items
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(140f / 190f),
                    contentAlignment = Alignment.Center
                ) {
                    VideoCard(
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .run {
                                if (index == 0) focusRequester(firstItemFocusRequester) else this
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused || state.hasFocus) focusedAnime = anime
                            },
                        imageUrl = anime.imageUrl,
                        title = anime.title,
                        subTitle = anime.currentEpisode,
                        focusScale = AulamaFocusScale,
                        onClick = {
                            DetailActivity.startActivity(context, anime, viewModel.sourceId)
                        }
                    )
                }
            }
        }

        LoadingOverlay(visible = refreshState is LoadState.Loading && pagingItems.itemCount == 0)
        if (refreshState is LoadState.Error) {
            ErrorTip(message = refreshState.error.message ?: refreshState.error.toString()) {
                pagingItems.refresh()
            }
        }
    }

    LaunchedEffect(refreshState, pagingItems.itemCount) {
        if (refreshState is LoadState.NotLoading && pagingItems.itemCount > 0) {
            firstItemFocusRequester.safelyRequestFocus("search-result-first-item")
        }
    }
}

@Composable
private fun SearchResultBackdrop(
    anime: AnimeData?,
    accent: Color,
    reducedMotion: Boolean
) {
    val targetBackdrop = remember(anime?.sourceId, anime?.id, anime?.imageUrl) {
        anime
            ?.takeIf { it.imageUrl.isNotBlank() }
            ?.let {
                SearchResultBackdropState(
                    key = "${it.sourceId}:${it.id}:${it.imageUrl}",
                    imageUrl = it.imageUrl
                )
            }
    }
    var readyBackdrop by remember { mutableStateOf<SearchResultBackdropState?>(null) }
    val currentTargetKey by rememberUpdatedState(targetBackdrop?.key)
    LaunchedEffect(targetBackdrop) {
        if (targetBackdrop == null) readyBackdrop = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        targetBackdrop
            ?.takeIf { it.key != readyBackdrop?.key }
            ?.let { pending ->
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = pending.imageUrl,
                        widthPx = 720,
                        heightPx = 1020
                    ),
                    contentDescription = null,
                    onSuccess = {
                        if (currentTargetKey == pending.key) readyBackdrop = pending
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .graphicsLayer { alpha = 0f }
                )
            }
        AnimatedContent(
            targetState = readyBackdrop,
            transitionSpec = {
                fadeIn(
                    tween(
                        durationMillis = if (reducedMotion) 0 else 420,
                        easing = FastOutSlowInEasing
                    )
                ).togetherWith(
                    fadeOut(
                        tween(
                            durationMillis = if (reducedMotion) 0 else 300,
                            easing = FastOutSlowInEasing
                        )
                    )
                )
            },
            label = "search-result-backdrop"
        ) { state ->
            if (state != null) {
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = state.imageUrl,
                        widthPx = 720,
                        heightPx = 1020
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.30f
                            scaleX = 1.34f
                            scaleY = 1.34f
                            transformOrigin = TransformOrigin(1f, 0f)
                        }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                        radius = 760f
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.48f to AulamaTvColors.Background.copy(alpha = 0.96f),
                            0.70f to AulamaTvColors.Background.copy(alpha = 0.68f),
                            0.88f to AulamaTvColors.Background.copy(alpha = 0.28f),
                            1f to AulamaTvColors.Background.copy(alpha = 0.10f)
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background.copy(alpha = 0.36f),
                            0.50f to Color.Transparent,
                            1f to AulamaTvColors.Background.copy(alpha = 0.90f)
                        )
                    )
                )
        )
    }
}

private data class SearchResultBackdropState(
    val key: String,
    val imageUrl: String
)
