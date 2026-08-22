@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.auth.TvHistoryItem
import com.jing.sakura.compose.common.AutoMarqueeText
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.CinematicArtworkBackdrop
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.LoadingOverlay
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.boundedVirtualCarouselMove
import com.jing.sakura.compose.common.virtualCarouselCenterIndex
import com.jing.sakura.compose.common.virtualCarouselIdentity
import com.jing.sakura.compose.common.virtualCarouselItemCount
import com.jing.sakura.compose.common.virtualCarouselLogicalIndex
import com.jing.sakura.data.AnimeData
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow

@Composable
fun VideoHistoryScreen(viewModel: HistoryViewModel) {
    val library by viewModel.library.collectAsState()
    val guestMode by viewModel.guestMode.collectAsState()
    val loading by viewModel.libraryLoading.collectAsState()
    val error by viewModel.libraryError.collectAsState()
    val animeDetails by viewModel.animeDetails.collectAsState()
    val context = LocalContext.current
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
    val rows = remember(library.continueWatching, library.favorites, historyByAnime) {
        buildList {
            if (library.continueWatching.isNotEmpty()) {
                add(
                    LibraryRow(
                        title = "繼續觀看",
                        videos = library.continueWatching,
                        historyByAnime = historyByAnime
                    )
                )
            }
            if (library.favorites.isNotEmpty()) {
                add(LibraryRow(title = "我的收藏", videos = library.favorites))
            }
        }
    }
    val hasContent = rows.isNotEmpty()
    val showInitialLoading = loading && !hasContent
    LoadingOverlay(
        visible = showInitialLoading,
        text = if (guestMode) "載入本機片庫" else "同步片庫"
    )
    if (showInitialLoading) return
    if (error != null && !hasContent) {
        ErrorTip(message = error ?: "未能同步片庫", retry = viewModel::refreshLibrary)
        return
    }

    if (!hasContent) {
        EmptyLibraryScreen(
            guestMode = guestMode,
            onRefresh = viewModel::refreshLibrary
        )
        return
    }

    val rowFocusRequester = remember { FocusRequester() }
    val rowSelectionKeys = remember { mutableStateMapOf<String, String>() }
    var displayedRowTitle by remember { mutableStateOf(rows.first().title) }
    var highlightedAnime by remember { mutableStateOf(rows.first().videos.first()) }
    val detailKey = highlightedAnime.let { historyKey(it.id, it.sourceId) }
    val heroAnime = animeDetails[detailKey] ?: highlightedAnime
    val extractedAccent = rememberArtworkAccent(heroAnime.imageUrl, enabled = true)
    val accent by animateColorAsState(
        targetValue = extractedAccent,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "library-accent"
    )
    val rowTransitionAlpha = remember { Animatable(1f) }
    val rowTransitionOffset = remember { Animatable(0f) }
    val rowMoveEvents = remember {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val displayedRowIndex = restoredLibraryIdentityIndex(
        selectedKey = displayedRowTitle,
        availableKeys = rows.map(LibraryRow::title)
    )
    val activeRow = rows[displayedRowIndex]
    val nextRow = rows.getOrNull(displayedRowIndex + 1)
    val selectedIndex = restoredLibraryIdentityIndex(
        selectedKey = rowSelectionKeys[activeRow.title],
        availableKeys = activeRow.videos.map { historyKey(it.id, it.sourceId) }
    )
    val latestRows by rememberUpdatedState(rows)

    LaunchedEffect(rows) {
        val restoredRowIndex = restoredLibraryIdentityIndex(
            selectedKey = displayedRowTitle,
            availableKeys = rows.map(LibraryRow::title)
        )
        val row = rows[restoredRowIndex]
        displayedRowTitle = row.title
        val preferred = restoredLibraryIdentityIndex(
            selectedKey = rowSelectionKeys[row.title],
            availableKeys = row.videos.map { historyKey(it.id, it.sourceId) }
        )
        highlightedAnime = row.videos[preferred]
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { rowFocusRequester.requestFocus() }
    }
    LaunchedEffect(detailKey) {
        delay(160)
        viewModel.loadAnimeDetail(highlightedAnime)
    }
    DisposableEffect(detailKey) {
        onDispose { viewModel.cancelAnimeDetailLoad(detailKey) }
    }
    LaunchedEffect(rowMoveEvents) {
        rowMoveEvents.collect { delta ->
            val currentRows = latestRows
            val current = restoredLibraryIdentityIndex(
                selectedKey = displayedRowTitle,
                availableKeys = currentRows.map(LibraryRow::title)
            )
            val target = nextLibraryRowIndex(current, delta, currentRows.size) ?: return@collect
            coroutineScope {
                launch {
                    rowTransitionAlpha.animateTo(
                        0.04f,
                        tween(125, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    rowTransitionOffset.animateTo(
                        if (delta > 0) -30f else 30f,
                        tween(125, easing = FastOutSlowInEasing)
                    )
                }
            }
            val targetRow = currentRows[target]
            displayedRowTitle = targetRow.title
            val targetIndex = restoredLibraryIdentityIndex(
                selectedKey = rowSelectionKeys[targetRow.title],
                availableKeys = targetRow.videos.map { historyKey(it.id, it.sourceId) }
            )
            highlightedAnime = targetRow.videos[targetIndex]
            rowTransitionAlpha.snapTo(0.04f)
            rowTransitionOffset.snapTo(if (delta > 0) 30f else -30f)
            coroutineScope {
                launch {
                    rowTransitionAlpha.animateTo(
                        1f,
                        tween(225, easing = LinearOutSlowInEasing)
                    )
                }
                launch {
                    rowTransitionOffset.animateTo(
                        0f,
                        tween(225, easing = LinearOutSlowInEasing)
                    )
                }
            }
            runCatching { rowFocusRequester.requestFocus() }
        }
    }
    val openDetail: (AnimeData) -> Unit = { anime ->
        val resumeEpisode = rows
            .getOrNull(displayedRowIndex)
            ?.takeIf { it.title == "繼續觀看" }
            ?.historyByAnime
            ?.get(historyKey(anime.id, anime.sourceId))
            ?.lastEpisodeName
            .orEmpty()
        DetailActivity.startActivity(
            context = context,
            anime = anime,
            resumeEpisode = resumeEpisode
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        LibraryBackdrop(
            anime = heroAnime,
            accent = accent
        )
        LibraryTopBar(
            guestMode = guestMode,
            loading = loading
        )
        LibraryHeroSummary(
            anime = heroAnime,
            accent = accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 55.dp)
                .height(158.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 218.dp)
                    .graphicsLayer {
                        alpha = rowTransitionAlpha.value
                        translationY = rowTransitionOffset.value.dp.toPx()
                    }
            ) {
                LibraryMediaRow(
                    row = activeRow,
                    initialSelectedIndex = selectedIndex,
                    focusRequester = rowFocusRequester,
                    onSelectionChanged = { _, anime ->
                        rowSelectionKeys[activeRow.title] = historyKey(anime.id, anime.sourceId)
                        highlightedAnime = anime
                    },
                    onMoveRow = { delta ->
                        val canMove = nextLibraryRowIndex(
                            displayedRowIndex,
                            delta,
                            rows.size
                        ) != null
                        if (canMove) rowMoveEvents.tryEmit(delta)
                        canMove
                    },
                    onRefresh = viewModel::refreshLibrary,
                    onOpen = openDetail
                )
                nextRow?.let {
                    LibraryNextRowHeading(title = it.title)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryScreen(guestMode: Boolean, onRefresh: () -> Unit) {
    val refreshFocusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        LibraryTopBar(guestMode = guestMode, loading = false)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = localizedText("片庫暫時未有內容"),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = localizedText(
                    if (guestMode) "觀看或收藏動畫後，內容會保留在這部電視。"
                    else "收藏與觀看紀錄會在此顯示。"
                ),
                color = AulamaTvColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(20.dp))
            LibraryRefreshButton(
                onClick = onRefresh,
                modifier = Modifier.focusRequester(refreshFocusRequester)
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { refreshFocusRequester.requestFocus() } }
}

@Composable
private fun LibraryTopBar(
    guestMode: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .padding(start = 42.dp, end = 38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedText("我的片庫"),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = localizedText(
                    if (guestMode) "本機紀錄與收藏 · 登入後可跨裝置同步"
                    else "雲端紀錄與收藏"
                ),
                color = Color.White.copy(alpha = 0.70f),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
            )
        }
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (loading) 0.42f else 0.76f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = localizedText(if (loading) "正在同步" else "選單鍵重新整理"),
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
        )
    }
}

@Composable
private fun LibraryHeroSummary(
    anime: AnimeData,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 42.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 510.dp),
            verticalArrangement = Arrangement.Center
        ) {
            AutoMarqueeText(
                text = localizedText(anime.title),
                color = accent,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 29.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(5.dp))
            LibraryMetadata(anime = anime, accent = accent)
            if (anime.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = localizedText(anime.description),
                    color = Color.White.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LibraryMetadata(anime: AnimeData, accent: Color) {
    val labels = remember(anime.year, anime.currentEpisode, anime.tags) {
        buildHeroMetadata(
            year = anime.year,
            currentEpisode = anime.currentEpisode,
            tags = anime.tags
        )
    }
    if (labels.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index, label ->
            Text(
                text = localizedText(label),
                color = if (index == 0) accent else Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LibraryMediaRow(
    row: LibraryRow,
    initialSelectedIndex: Int,
    focusRequester: FocusRequester,
    onSelectionChanged: (Int, AnimeData) -> Unit,
    onMoveRow: (Int) -> Boolean,
    onRefresh: () -> Unit,
    onOpen: (AnimeData) -> Unit
) {
    val videos = row.videos
    val identity = remember(row.title, videos) {
        virtualCarouselIdentity(
            rowKey = row.title,
            itemKeys = videos.map { historyKey(it.id, it.sourceId) }
        )
    }
    val loopEnabled = videos.size >= 6
    val loopStart = remember(identity, initialSelectedIndex) {
        if (loopEnabled) {
            virtualCarouselCenterIndex(videos.size, initialSelectedIndex)
        } else {
            initialSelectedIndex
        }
    }
    val virtualItemCount = if (loopEnabled) {
        virtualCarouselItemCount(videos.size)
    } else {
        videos.size
    }
    val rowState = remember(identity) { LazyListState(firstVisibleItemIndex = loopStart) }
    var selectedVirtualIndex by remember(identity) { mutableIntStateOf(loopStart) }
    // Keep the shared row focus state while its content changes. The focus target
    // itself stays in the same composition slot when moving between library rows,
    // so resetting this flag by row identity would hide the selection frame even
    // though Android still considers the target focused.
    var focused by remember { mutableStateOf(false) }
    var dimUnselected by remember { mutableStateOf(false) }
    val moveEvents = remember(identity) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val selectedIndex = virtualCarouselLogicalIndex(selectedVirtualIndex, videos.size)
    val selectedVideo = videos[selectedIndex]
    val focusFrameOffset by animateDpAsState(
        targetValue = if (loopEnabled) 0.dp else (selectedIndex * 166).dp,
        animationSpec = tween(165, easing = LinearOutSlowInEasing),
        label = "library-focus-frame-offset"
    )
    val selectedAccent = rememberArtworkAccent(selectedVideo.imageUrl, enabled = true)
    LaunchedEffect(identity, selectedVirtualIndex) {
        dimUnselected = false
        delay(3_000)
        dimUnselected = true
    }
    LaunchedEffect(identity, rowState) {
        moveEvents.collect { delta ->
            dimUnselected = false
            if (loopEnabled) {
                val move = boundedVirtualCarouselMove(
                    currentIndex = selectedVirtualIndex,
                    delta = delta,
                    itemCount = videos.size
                )
                if (move.targetIndex == selectedVirtualIndex && move.recenterIndex == null) {
                    return@collect
                }
                selectedVirtualIndex = move.targetIndex
                rowState.animateScrollToItem(move.targetIndex)
                move.recenterIndex?.let { recenteredIndex ->
                    rowState.scrollToItem(recenteredIndex)
                    selectedVirtualIndex = recenteredIndex
                }
            } else {
                val target = moveLibraryRowIndex(
                    currentIndex = selectedVirtualIndex,
                    delta = delta,
                    itemCount = videos.size,
                    loopEnabled = false
                )
                if (target == selectedVirtualIndex) return@collect
                selectedVirtualIndex = target
            }
        }
    }
    LaunchedEffect(focused, selectedIndex, identity) {
        if (focused) onSelectionChanged(selectedIndex, selectedVideo)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = localizedText(row.title),
                color = Color.White,
                maxLines = 1,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = localizedText("${videos.size} 套"),
                color = Color.White.copy(alpha = 0.70f),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(220.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    focused = state.isFocused || state.hasFocus
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
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                            onMoveRow(-1)
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            onMoveRow(1)
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
                contentPadding = PaddingValues(horizontal = 42.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = virtualItemCount,
                    key = { virtualIndex ->
                        val anime = videos[virtualCarouselLogicalIndex(virtualIndex, videos.size)]
                        "$virtualIndex:${anime.sourceId}:${anime.id}"
                    }
                ) { virtualIndex ->
                    val anime = videos[virtualCarouselLogicalIndex(virtualIndex, videos.size)]
                    val selected = virtualIndex == selectedVirtualIndex
                    val targetAlpha = if (selected || !dimUnselected) 1f else 0.28f
                    val cardAlpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(
                            durationMillis = if (dimUnselected) 620 else 360,
                            easing = FastOutSlowInEasing
                        ),
                        label = "library-row-card-alpha"
                    )
                    LibraryPosterCard(
                        anime = anime,
                        history = row.historyByAnime[historyKey(anime.id, anime.sourceId)],
                        showLabels = true,
                        modifier = Modifier
                            .requiredSize(width = 148.dp, height = 208.dp)
                            .graphicsLayer { alpha = cardAlpha }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 42.dp + focusFrameOffset, top = 6.dp)
                    .requiredSize(width = 148.dp, height = 208.dp)
                    .border(BorderStroke(3.dp, selectedAccent), LibraryPosterShape)
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.90f)),
                        LibraryPosterShape
                    )
            )
        }
    }
}

@Composable
private fun LibraryPosterCard(
    anime: AnimeData,
    history: VideoHistoryEntity?,
    showLabels: Boolean,
    modifier: Modifier = Modifier
) {
    val title = localizedText(anime.title)
    val subtitle = localizedText(
        history?.lastEpisodeName.orEmpty().ifBlank { anime.currentEpisode }
    )
    val progress = if (history != null && history.videoDuration > 0L) {
        (history.lastPlayTime.toFloat() / history.videoDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .border(
                BorderStroke(1.dp, AulamaTvColors.Outline.copy(alpha = 0.72f)),
                LibraryPosterShape
            )
            .clip(LibraryPosterShape)
    ) {
        AsyncImage(
            model = rememberPosterImageRequest(anime.imageUrl),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showLabels) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.45f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.30f),
                                1f to Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
                if (progress > 0f) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
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
    }
}

@Composable
private fun LibraryNextRowHeading(title: String) {
    Text(
        text = localizedText(title),
        color = Color.White,
        maxLines = 1,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, top = 8.dp, end = 42.dp)
    )
}

@Composable
private fun LibraryBackdrop(anime: AnimeData, accent: Color) {
    CinematicArtworkBackdrop(
        imageUrl = anime.imageUrl,
        imageKey = "${historyKey(anime.id, anime.sourceId)}:${anime.imageUrl}",
        accent = accent,
        previewActive = false
    )
}

@Composable
private fun LibraryRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        shape = ButtonDefaults.shape(shape = AulamaCardShape),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF080A0F)
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))),
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        )
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = localizedText("重新整理"),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

private data class LibraryRow(
    val title: String,
    val videos: List<AnimeData>,
    val historyByAnime: Map<String, VideoHistoryEntity> = emptyMap()
)

private val LibraryPosterShape = RoundedCornerShape(10.dp)

private fun historyKey(animeId: String, sourceId: String): String = "$sourceId:$animeId"

internal fun nextLibraryRowIndex(currentIndex: Int, delta: Int, rowCount: Int): Int? {
    if (rowCount <= 0 || delta == 0) return null
    val target = currentIndex + delta
    return target.takeIf { it in 0 until rowCount }
}

internal fun moveLibraryRowIndex(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
    loopEnabled: Boolean
): Int {
    if (itemCount <= 0) return 0
    return if (loopEnabled) {
        boundedVirtualCarouselMove(
            currentIndex = currentIndex,
            delta = delta,
            itemCount = itemCount
        ).targetIndex
    } else {
        (currentIndex + delta).coerceIn(0, itemCount - 1)
    }
}

internal fun restoredLibraryIdentityIndex(
    selectedKey: String?,
    availableKeys: List<String>
): Int {
    if (availableKeys.isEmpty()) return 0
    return selectedKey
        ?.let(availableKeys::indexOf)
        ?.takeIf { it >= 0 }
        ?: 0
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
