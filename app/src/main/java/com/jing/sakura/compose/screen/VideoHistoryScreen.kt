@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaSectionHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.LoadingOverlay
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.data.AnimeData
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun VideoHistoryScreen(viewModel: HistoryViewModel) {
    val library by viewModel.library.collectAsState()
    val guestMode by viewModel.guestMode.collectAsState()
    val localHistory = viewModel.pager.collectAsLazyPagingItems()
    val localHistoryItems = localHistory.itemSnapshotList.items
    val localHistoryByAnime = remember(localHistoryItems) {
        localHistoryItems.associateBy {
            historyKey(animeId = it.animeId, sourceId = it.sourceId)
        }
    }
    val historyByAnime = remember(library.historyItems, localHistoryByAnime) {
        val remoteHistory = library.historyItems.associate { item ->
            val history = item.toVideoHistoryEntity()
            historyKey(history.animeId, history.sourceId) to history
        }
        (remoteHistory.keys + localHistoryByAnime.keys).associateWith { key ->
            listOfNotNull(remoteHistory[key], localHistoryByAnime[key])
                .maxBy(VideoHistoryEntity::updateTime)
        }
    }
    val loading by viewModel.libraryLoading.collectAsState()
    val error by viewModel.libraryError.collectAsState()
    val context = LocalContext.current
    val firstFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    val hasContent = library.continueWatching.isNotEmpty() || library.favorites.isNotEmpty()
    val allLibraryAnime = remember(library.continueWatching, library.favorites) {
        (library.continueWatching + library.favorites)
            .distinctBy { historyKey(it.id, it.sourceId) }
    }
    var highlightedAnime by remember { mutableStateOf<AnimeData?>(null) }
    val backdropAccent = rememberArtworkAccent(
        imageUrl = highlightedAnime?.imageUrl.orEmpty(),
        enabled = highlightedAnime != null
    )

    val showInitialLoading = loading && !hasContent
    LoadingOverlay(
        visible = showInitialLoading,
        text = if (guestMode) "載入本機片庫" else "同步片庫"
    )
    if (showInitialLoading) {
        return
    }
    if (error != null && !hasContent) {
        ErrorTip(message = error ?: "未能同步片庫", retry = viewModel::refreshLibrary)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        LibraryBackdrop(anime = highlightedAnime, accent = backdropAccent)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "library-header") {
                AulamaPageHeader(
                    title = "我的片庫",
                    subtitle = if (guestMode) {
                        "本機觀看紀錄與收藏 · 登入後可跨裝置同步"
                    } else {
                        "雲端觀看紀錄與收藏"
                    }
                ) {
                    LibraryRefreshButton(
                        modifier = if (hasContent) {
                            Modifier
                        } else {
                            Modifier.focusRequester(refreshFocusRequester)
                        },
                        onClick = viewModel::refreshLibrary
                    )
                }
            }

            item(key = "continue-watching") {
                LibraryAnimeRow(
                    title = "繼續觀看",
                    videos = library.continueWatching,
                    firstFocusRequester = firstFocusRequester.takeIf {
                        library.continueWatching.isNotEmpty()
                    },
                    onRefresh = viewModel::refreshLibrary,
                    onOpen = { video ->
                        DetailActivity.startActivity(
                            context = context,
                            anime = video,
                            resumeEpisode = historyByAnime[
                                historyKey(video.id, video.sourceId)
                            ]?.lastEpisodeName.orEmpty()
                        )
                    },
                    onFocused = { highlightedAnime = it },
                    historyByAnime = historyByAnime
                )
            }

            item(key = "favorites") {
                LibraryAnimeRow(
                    title = "我的收藏",
                    videos = library.favorites,
                    firstFocusRequester = firstFocusRequester.takeIf {
                        library.continueWatching.isEmpty() && library.favorites.isNotEmpty()
                    },
                    onRefresh = viewModel::refreshLibrary,
                    onOpen = { video ->
                        DetailActivity.startActivity(context, video)
                    },
                    onFocused = { highlightedAnime = it }
                )
            }
        }
    }

    LaunchedEffect(allLibraryAnime) {
        val highlightedKey = highlightedAnime?.let { historyKey(it.id, it.sourceId) }
        highlightedAnime = allLibraryAnime.firstOrNull {
            historyKey(it.id, it.sourceId) == highlightedKey
        } ?: allLibraryAnime.firstOrNull()
        val requester = if (hasContent) firstFocusRequester else refreshFocusRequester
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun LibraryRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        contentPadding = PaddingValues(10.dp),
        shape = ButtonDefaults.shape(shape = CircleShape),
        scale = ButtonDefaults.scale(focusedScale = 1.12f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = AulamaTvColors.TextSecondary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = AulamaTvColors.Cyan,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = AulamaTvColors.Cyan
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color.Transparent)),
            focusedBorder = Border(BorderStroke(1.dp, Color.Transparent)),
            pressedBorder = Border(BorderStroke(1.dp, Color.Transparent))
        )
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "重新整理",
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
private fun LibraryAnimeRow(
    title: String,
    videos: List<AnimeData>,
    firstFocusRequester: FocusRequester?,
    onRefresh: () -> Unit,
    onOpen: (AnimeData) -> Unit,
    onFocused: (AnimeData) -> Unit,
    historyByAnime: Map<String, VideoHistoryEntity> = emptyMap()
) {
    AulamaSectionHeader(title = title, count = videos.size)
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 42.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "暫時未有內容",
                style = MaterialTheme.typography.bodyLarge,
                color = AulamaTvColors.TextSecondary
            )
        }
        return
    }

    val identity = remember(videos) {
        videos.joinToString(separator = "|") { historyKey(it.id, it.sourceId) }
    }
    val loopEnabled = videos.size >= 6
    val loopCopies = 101
    val initialVirtualIndex = remember(identity) {
        if (loopEnabled) videos.size * (loopCopies / 2) else 0
    }
    val virtualItemCount = if (loopEnabled) videos.size * loopCopies else videos.size
    val rowState = remember(identity) {
        LazyListState(firstVisibleItemIndex = initialVirtualIndex)
    }
    var selectedVirtualIndex by remember(identity) { mutableIntStateOf(initialVirtualIndex) }
    var rowFocused by remember(identity) { mutableStateOf(false) }
    val cardWidth = 170.dp
    val cardHeight = cardWidth * (190f / 140f)
    val cardStridePx = with(LocalDensity.current) { 190.dp.toPx() }
    val moveEvents = remember(identity) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val selectedLogicalIndex = Math.floorMod(selectedVirtualIndex, videos.size)
    val selectedVideo = videos[selectedLogicalIndex]
    val focusFrameOffset by animateDpAsState(
        targetValue = if (loopEnabled) 0.dp else (selectedLogicalIndex * 190).dp,
        animationSpec = tween(durationMillis = 165, easing = LinearOutSlowInEasing),
        label = "library-row-focus-frame-offset"
    )
    val selectedAccent = rememberArtworkAccent(selectedVideo.imageUrl, enabled = rowFocused)

    LaunchedEffect(identity, rowState, cardStridePx) {
        moveEvents.collect { delta ->
            val targetIndex = moveLibraryRowIndex(
                currentIndex = selectedVirtualIndex,
                delta = delta,
                itemCount = videos.size,
                loopEnabled = loopEnabled
            )
            val appliedDelta = targetIndex - selectedVirtualIndex
            if (appliedDelta == 0) return@collect
            selectedVirtualIndex = targetIndex
            if (loopEnabled) {
                rowState.animateScrollBy(
                    value = appliedDelta * cardStridePx,
                    animationSpec = tween(
                        durationMillis = 165,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
        }
    }
    LaunchedEffect(rowFocused, selectedVirtualIndex, identity) {
        if (rowFocused) onFocused(selectedVideo)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(cardHeight + 72.dp)
            .run {
                if (firstFocusRequester != null) focusRequester(firstFocusRequester) else this
            }
            .onFocusChanged { state ->
                rowFocused = state.isFocused || state.hasFocus
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        moveEvents.tryEmit(1)
                        true
                    }

                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        moveEvents.tryEmit(-1)
                        true
                    }

                    event.type == KeyEventType.KeyDown && event.key == Key.Menu -> {
                        onRefresh()
                        true
                    }

                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        onOpen(selectedVideo)
                        true
                    }

                    event.key == Key.DirectionCenter || event.key == Key.Enter -> true
                    else -> false
                }
            }
            .focusable()
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 42.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = virtualItemCount,
                key = { virtualIndex ->
                    val video = videos[Math.floorMod(virtualIndex, videos.size)]
                    "$virtualIndex:${video.sourceId}:${video.id}"
                }
            ) { virtualIndex ->
                val video = videos[Math.floorMod(virtualIndex, videos.size)]
                val history = historyByAnime[historyKey(video.id, video.sourceId)]
                val selected = rowFocused && virtualIndex == selectedVirtualIndex
                Column(
                    modifier = Modifier.width(cardWidth),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight),
                        imageUrl = video.imageUrl,
                        title = video.title,
                        subTitle = history?.lastEpisodeName.orEmpty()
                            .ifBlank { video.currentEpisode },
                        focusScale = 1f,
                        isFocusable = false,
                        externallyFocused = selected,
                        showFocusFrame = false,
                        onClick = { onOpen(video) }
                    )
                    if (history != null) {
                        PlaybackProgress(history)
                    }
                }
            }
        }
        if (rowFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 42.dp + focusFrameOffset, top = 16.dp)
                    .size(width = cardWidth, height = cardHeight)
                    .border(BorderStroke(2.5.dp, selectedAccent), AulamaCardShape)
            )
        }
    }
}

@Composable
private fun LibraryBackdrop(anime: AnimeData?, accent: Color) {
    val targetBackdrop = remember(anime?.sourceId, anime?.id, anime?.imageUrl) {
        anime
            ?.takeIf { it.imageUrl.isNotBlank() }
            ?.let {
                LibraryBackdropState(
                    key = "${historyKey(it.id, it.sourceId)}:${it.imageUrl}",
                    imageUrl = it.imageUrl
                )
            }
    }
    var readyBackdrop by remember { mutableStateOf<LibraryBackdropState?>(null) }
    val currentTargetKey by rememberUpdatedState(targetBackdrop?.key)
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "library-backdrop-accent"
    )
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
                        widthPx = 960,
                        heightPx = 1_360
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
                fadeIn(tween(420, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(tween(300, easing = FastOutSlowInEasing)))
            },
            label = "library-backdrop"
        ) { state ->
            if (state != null) {
                val request = rememberPosterImageRequest(
                    imageUrl = state.imageUrl,
                    widthPx = 960,
                    heightPx = 1_360
                )
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.68f
                            scaleX = 1.42f
                            scaleY = 1.42f
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
                        colors = listOf(animatedAccent.copy(alpha = 0.18f), Color.Transparent),
                        radius = 820f
                    )
                )
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.42f to AulamaTvColors.Background,
                            0.54f to AulamaTvColors.Background.copy(alpha = 0.94f),
                            0.66f to AulamaTvColors.Background.copy(alpha = 0.48f),
                            0.80f to AulamaTvColors.Background.copy(alpha = 0.10f),
                            1f to Color.Transparent
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background.copy(alpha = 0.28f),
                            0.56f to Color.Transparent,
                            1f to AulamaTvColors.Background
                        )
                    )
                )
        )
    }
}

@Composable
private fun PlaybackProgress(history: VideoHistoryEntity) {
    val watchedSeconds = (history.lastPlayTime / 1000L).coerceAtLeast(0L)
    val episode = history.lastEpisodeName.trim()
    val progress = if (history.videoDuration > 0L) {
        (history.lastPlayTime.toFloat() / history.videoDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressText = buildList {
        if (episode.isNotEmpty()) add("看到 $episode")
        add("已看 ${watchedSeconds.secondsToMinuteAndSecondText()}")
    }.joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = progressText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp
            ),
            color = AulamaTvColors.TextSecondary
        )
        if (history.videoDuration > 0L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AulamaTvColors.Outline.copy(alpha = 0.7f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(AulamaTvColors.Cyan)
                )
            }
        }
    }
}

private fun historyKey(animeId: String, sourceId: String): String = "$sourceId:$animeId"

internal fun moveLibraryRowIndex(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
    loopEnabled: Boolean
): Int {
    if (itemCount <= 0) return 0
    return if (loopEnabled) {
        currentIndex + delta
    } else {
        (currentIndex + delta).coerceIn(0, itemCount - 1)
    }
}

private fun TvHistoryItem.toVideoHistoryEntity(): VideoHistoryEntity = VideoHistoryEntity(
    animeId = animeId.ifBlank { anime.id },
    animeName = anime.title,
    episodeId = episodeId.ifBlank { "cloud:$episodeIndex" },
    lastEpisodeName = episodeLabel.ifBlank { anime.currentEpisode },
    updateTime = updatedAtEpochMs.coerceAtLeast(1L),
    lastPlayTime = currentTimeSeconds.toPositionMs(),
    videoDuration = durationSeconds.toPositionMs(),
    coverUrl = anime.imageUrl,
    sourceId = sourceTypeId.ifBlank { anime.sourceId }
)

private fun Double.toPositionMs(): Long =
    takeIf { it.isFinite() && it > 0.0 }
        ?.times(1_000.0)
        ?.coerceAtMost(Long.MAX_VALUE.toDouble())
        ?.toLong()
        ?: 0L

private data class LibraryBackdropState(val key: String, val imageUrl: String)
