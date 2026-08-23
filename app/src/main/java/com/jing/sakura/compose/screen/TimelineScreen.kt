@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.CinematicArtworkBackdrop
import com.jing.sakura.compose.common.AutoMarqueeText
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.HeroPreviewPlayer
import com.jing.sakura.compose.common.LoadingOverlay
import com.jing.sakura.compose.common.TvPreviewPreferences
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.data.UpdateTimeLine
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.home.PREVIEW_DIM_DELAY_MS
import com.jing.sakura.home.PREVIEW_START_AFTER_DIM_DELAY_MS
import com.jing.sakura.home.isPreviewPlaybackActive
import com.jing.sakura.home.previewCardAlpha
import com.jing.sakura.home.shouldStartPreview
import com.jing.sakura.timeline.TimelineViewModel
import com.jing.sakura.timeline.resolveTimelineSynopsis
import com.jing.sakura.timeline.timelineInitialVirtualIndex
import com.jing.sakura.timeline.timelineLogicalIndex
import com.jing.sakura.timeline.timelineMoveVirtualIndex
import com.jing.sakura.timeline.timelinePosterPrefetchUrls
import com.jing.sakura.timeline.timelineVirtualItemCount
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun TimelineScreen(viewModel: TimelineViewModel) {
    val context = LocalContext.current
    val previewPreferences = remember(context) { TvPreviewPreferences.get(context) }
    val previewEnabled by previewPreferences.previewEnabled.collectAsState()
    val timeline = viewModel.timelines.collectAsState().value
    val synopses = viewModel.synopses.collectAsState().value
    val previewState = viewModel.previewState.collectAsState().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        when (timeline) {
            is Resource.Success -> TimeLine(
                data = timeline.data,
                sourceId = viewModel.sourceId,
                synopses = synopses,
                previewState = previewState,
                previewEnabled = previewEnabled,
                onDaySelected = viewModel::prepareDay,
                onAnimeHighlighted = viewModel::loadSynopsis,
                onPreparePreview = viewModel::preparePreview,
                onCancelPreview = viewModel::cancelPreview
            )

            is Resource.Error -> ErrorTip(message = timeline.message) {
                viewModel.loadData()
            }

            is Resource.Loading -> Unit
        }
        LoadingOverlay(visible = timeline is Resource.Loading)
    }
}

@Composable
fun TimeLine(
    data: UpdateTimeLine,
    sourceId: String,
    synopses: Map<String, String>,
    previewState: HeroPreviewState,
    previewEnabled: Boolean,
    onDaySelected: (Int) -> Unit,
    onAnimeHighlighted: (AnimeData) -> Unit,
    onPreparePreview: (AnimeData) -> Unit,
    onCancelPreview: () -> Unit
) {
    if (data.timeline.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.grid_no_data_tip),
                style = MaterialTheme.typography.titleLarge,
                color = AulamaTvColors.TextSecondary
            )
        }
        return
    }

    val currentDayIndex = data.current.coerceIn(0, data.timeline.lastIndex)
    var selectedDayIndex by remember(data) { mutableIntStateOf(currentDayIndex) }
    val selectedDay = data.timeline[selectedDayIndex]
    var highlightedAnimeIndex by remember(selectedDayIndex) { mutableIntStateOf(0) }
    val highlightedAnime = selectedDay.second.getOrNull(highlightedAnimeIndex)
        ?: selectedDay.second.firstOrNull()
    val dayFocusRequesters = remember(data.timeline.size) {
        List(data.timeline.size) { FocusRequester() }
    }
    val rowFocusRequesters = remember(data.timeline.size) {
        List(data.timeline.size) { FocusRequester() }
    }
    var rowHasFocus by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var dimUnselected by remember { mutableStateOf(false) }
    var previewArmed by remember { mutableStateOf(false) }
    var previewFirstFrameReady by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var previewSession by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val rawAccent = rememberArtworkAccent(
        imageUrl = highlightedAnime?.imageUrl.orEmpty(),
        enabled = highlightedAnime != null
    )
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "timeline-accent"
    )
    val readyPreview = (previewState as? HeroPreviewState.Ready)?.spec
    val previewActive = isPreviewPlaybackActive(
        previewEnabled = previewEnabled,
        isScreenResumed = isScreenResumed,
        hasFocusedContent = rowHasFocus,
        previewArmed = previewArmed,
        firstFrameReady = previewFirstFrameReady,
        readyAnimeId = readyPreview?.navigateToPlayerArg?.animeId,
        readySourceId = readyPreview?.navigateToPlayerArg?.sourceId,
        focusedAnimeId = highlightedAnime?.id,
        focusedSourceId = highlightedAnime?.sourceId
    )
    val chromeAlpha by animateFloatAsState(
        targetValue = if (previewActive) 0.28f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "timeline-chrome-alpha"
    )
    val posterPrefetchUrls = remember(data, selectedDayIndex) {
        timelinePosterPrefetchUrls(data, selectedDayIndex)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isScreenResumed = true
                    previewSession += 1
                }

                Lifecycle.Event.ON_PAUSE -> {
                    isScreenResumed = false
                    dimUnselected = false
                    previewArmed = false
                    previewFirstFrameReady = false
                    onCancelPreview()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onCancelPreview()
        }
    }

    val openDetail: (AnimeData) -> Unit = { anime ->
        interactionVersion += 1
        dimUnselected = false
        previewArmed = false
        previewFirstFrameReady = false
        onCancelPreview()
        DetailActivity.startActivity(context, anime, sourceId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionVersion += 1
                    dimUnselected = false
                    previewArmed = false
                    previewFirstFrameReady = false
                    onCancelPreview()
                }
                false
            }
    ) {
        readyPreview?.let { spec ->
            HeroPreviewPlayer(
                spec = spec,
                onReady = { previewFirstFrameReady = true },
                onError = {
                    previewArmed = false
                    previewFirstFrameReady = false
                    onCancelPreview()
                },
                onEnded = {
                    dimUnselected = true
                    previewArmed = false
                    previewFirstFrameReady = false
                    onCancelPreview()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (previewActive) 1f else 0f }
            )
        }
        TimelineBackdrop(
            anime = highlightedAnime,
            accent = accent,
            previewActive = previewActive
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 34.dp)
            ) {
                AulamaAnimeBrandMark(
                    height = 34.dp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = chromeAlpha },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    data.timeline.forEachIndexed { index, day ->
                        TimelineDayTab(
                            name = day.first,
                            selected = index == selectedDayIndex,
                            accent = accent,
                            modifier = Modifier
                                .width(92.dp)
                                .focusRequester(dayFocusRequesters[index])
                                .focusProperties {
                                    if (day.second.isNotEmpty()) {
                                        down = rowFocusRequesters[index]
                                    }
                                },
                            onFocused = {
                                rowHasFocus = false
                                selectedDayIndex = index
                            },
                            onClick = { selectedDayIndex = index }
                        )
                    }
                }
            }

            TimelineFocusSummary(
                anime = highlightedAnime,
                dayName = selectedDay.first,
                synopsis = highlightedAnime?.let { synopses[it.id] }.orEmpty(),
                accent = accent,
                marqueeEnabled = rowHasFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            TimelineStripHeader(
                count = selectedDay.second.size,
                isToday = selectedDayIndex == currentDayIndex,
                modifier = Modifier.graphicsLayer { alpha = chromeAlpha }
            )

            AnimatedContent(
                targetState = selectedDayIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (fadeIn(tween(240, delayMillis = 55, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) { width -> direction * width / 18 })
                        .togetherWith(
                            fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) { width -> -direction * width / 22 }
                        )
                },
                label = "timeline-day-content"
            ) { dayIndex ->
                val dayItems = data.timeline[dayIndex].second
                if (dayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.grid_no_data_tip),
                            style = MaterialTheme.typography.titleLarge,
                            color = AulamaTvColors.TextSecondary
                        )
                    }
                } else {
                    TimelinePosterCarousel(
                        items = dayItems,
                        accent = accent,
                        dimUnselected = dimUnselected,
                        previewActive = previewActive,
                        previewEnabled = previewEnabled,
                        rowFocusRequester = rowFocusRequesters[dayIndex],
                        upFocusRequester = dayFocusRequesters[dayIndex],
                        onFocusChanged = { focused ->
                            if (dayIndex == selectedDayIndex) rowHasFocus = focused
                        },
                        onSelected = { index ->
                            if (dayIndex == selectedDayIndex) highlightedAnimeIndex = index
                        },
                        onOpen = { anime ->
                            openDetail(anime)
                        }
                    )
                }
            }
        }
    }

    BackHandler(enabled = rowHasFocus) {
        rowHasFocus = false
        runCatching { dayFocusRequesters[selectedDayIndex].requestFocus() }
    }

    LaunchedEffect(data) {
        runCatching { dayFocusRequesters[currentDayIndex].requestFocus() }
    }

    LaunchedEffect(selectedDayIndex) {
        highlightedAnimeIndex = 0
        onDaySelected(selectedDayIndex)
    }

    LaunchedEffect(highlightedAnime?.sourceId, highlightedAnime?.id) {
        highlightedAnime?.let(onAnimeHighlighted)
    }

    LaunchedEffect(
        highlightedAnime?.sourceId,
        highlightedAnime?.id,
        rowHasFocus,
        interactionVersion,
        isScreenResumed,
        previewSession,
        previewEnabled
    ) {
        val scheduledSession = previewSession
        onCancelPreview()
        dimUnselected = false
        previewArmed = false
        previewFirstFrameReady = false
        val selected = highlightedAnime ?: return@LaunchedEffect
        if (!rowHasFocus || !isScreenResumed || !previewEnabled) return@LaunchedEffect
        delay(PREVIEW_DIM_DELAY_MS)
        dimUnselected = true
        delay(PREVIEW_START_AFTER_DIM_DELAY_MS)
        if (!shouldStartPreview(
                scheduledSession = scheduledSession,
                currentSession = previewSession,
                isScreenResumed = isScreenResumed,
                hasFocusedContent = rowHasFocus,
                previewEnabled = previewEnabled
            )
        ) return@LaunchedEffect
        onPreparePreview(selected)
        previewArmed = true
    }

    LaunchedEffect(previewActive, readyPreview?.key) {
        if (!previewActive) return@LaunchedEffect
        delay(60_000)
        dimUnselected = true
        previewArmed = false
        previewFirstFrameReady = false
        onCancelPreview()
    }

    LaunchedEffect(posterPrefetchUrls) {
        delay(180)
        posterPrefetchUrls.chunked(3).forEach { batch ->
            batch.forEach { imageUrl ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(420, 630)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .build()
                )
            }
            delay(90)
        }
    }
}

@Composable
private fun TimelinePosterCarousel(
    items: List<AnimeData>,
    accent: Color,
    dimUnselected: Boolean,
    previewActive: Boolean,
    previewEnabled: Boolean,
    rowFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
    onOpen: (AnimeData) -> Unit
) {
    val identity = remember(items) {
        items.joinToString(separator = "|") { "${it.sourceId}:${it.id}" }
    }
    val initialVirtualIndex = remember(identity) { timelineInitialVirtualIndex(items.size) }
    val virtualItemCount = remember(identity) { timelineVirtualItemCount(items.size) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialVirtualIndex)
    var selectedVirtualIndex by remember(identity) { mutableIntStateOf(initialVirtualIndex) }
    var focused by remember(identity) { mutableStateOf(false) }
    val cardStridePx = with(LocalDensity.current) { 170.dp.toPx() }
    val moveEvents = remember(identity) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val selectedLogicalIndex = timelineLogicalIndex(selectedVirtualIndex, items.size)
    val selectedAnime = items[selectedLogicalIndex]

    LaunchedEffect(identity, rowState, cardStridePx) {
        moveEvents.collect { delta ->
            val target = timelineMoveVirtualIndex(
                currentIndex = selectedVirtualIndex,
                delta = delta,
                itemCount = items.size
            )
            if (target == selectedVirtualIndex) return@collect
            selectedVirtualIndex = target
            rowState.animateScrollBy(
                value = delta * cardStridePx,
                animationSpec = tween(
                    durationMillis = 165,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rowFocusRequester)
            .focusProperties { up = upFocusRequester }
            .onFocusChanged { state ->
                focused = state.isFocused || state.hasFocus
                onFocusChanged(focused)
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
                        runCatching { upFocusRequester.requestFocus() }
                        true
                    }

                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> true
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        onOpen(selectedAnime)
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 42.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            items(
                count = virtualItemCount,
                key = { virtualIndex ->
                    val anime = items[timelineLogicalIndex(virtualIndex, items.size)]
                    "$virtualIndex:${anime.sourceId}:${anime.id}"
                }
            ) { virtualIndex ->
                val anime = items[timelineLogicalIndex(virtualIndex, items.size)]
                val selected = focused && virtualIndex == selectedVirtualIndex
                val cardAlpha by animateFloatAsState(
                    targetValue = previewCardAlpha(
                        rowFocused = focused,
                        selected = selected,
                        dimUnselected = dimUnselected,
                        previewActive = previewActive,
                        previewEnabled = previewEnabled
                    ),
                    animationSpec = tween(
                        durationMillis = if (previewActive) 320 else 620,
                        easing = FastOutSlowInEasing
                    ),
                    label = "timeline-card-alpha"
                )
                TimelinePosterCard(
                    anime = anime,
                    selected = selected,
                    modifier = Modifier
                        .size(width = 160.dp, height = 240.dp)
                        .graphicsLayer { alpha = cardAlpha }
                )
            }
        }
        if (focused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 42.dp, top = 12.dp)
                    .size(width = 160.dp, height = 240.dp)
                    .border(BorderStroke(2.5.dp, accent), TimelinePosterShape)
            )
        }
    }

    LaunchedEffect(identity) {
        rowState.scrollToItem(initialVirtualIndex)
        onSelected(0)
    }

    LaunchedEffect(selectedLogicalIndex) {
        onSelected(selectedLogicalIndex)
    }
}

@Composable
private fun TimelinePosterCard(
    anime: AnimeData,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val title = localizedText(anime.title)
    val subtitle = localizedText(timelineDisplayStatus(anime.currentEpisode, anime.year))
    val completed = subtitle == localizedText("已完結")
    val posterRequest = rememberPosterImageRequest(
        imageUrl = anime.imageUrl,
        widthPx = 420,
        heightPx = 630
    )
    Box(
        modifier = modifier
            .border(
                BorderStroke(1.dp, AulamaTvColors.Outline.copy(alpha = 0.72f)),
                TimelinePosterShape
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(TimelinePosterShape)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x18000000),
                                Color(0x66000000),
                                Color(0xF2050810)
                            )
                        )
                    )
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .align(if (completed) Alignment.TopStart else Alignment.TopEnd)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (completed) Color(0xE00E8F6A) else Color(0xCC080B12))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            AutoMarqueeText(
                text = title,
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Black
                ),
                enabled = selected,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun TimelineBackdrop(
    anime: AnimeData?,
    accent: Color,
    previewActive: Boolean
) {
    CinematicArtworkBackdrop(
        imageUrl = anime?.imageUrl.orEmpty(),
        imageKey = anime?.let { "${it.sourceId}:${it.id}:${it.imageUrl}" }.orEmpty(),
        accent = accent,
        previewActive = previewActive
    )
}

@Composable
private fun TimelineFocusSummary(
    anime: AnimeData?,
    dayName: String,
    synopsis: String,
    accent: Color,
    marqueeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = anime,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(270, delayMillis = 45, easing = FastOutSlowInEasing)) +
                slideInHorizontally(
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) { width -> width / 28 })
                .togetherWith(fadeOut(tween(160, easing = FastOutSlowInEasing)))
        },
        label = "timeline-summary"
    ) { selected ->
        if (selected == null) {
            Spacer(Modifier.fillMaxSize())
        } else {
            val title = localizedText(selected.title)
            val displayStatus = remember(selected.currentEpisode, selected.year) {
                timelineDisplayStatus(selected.currentEpisode, selected.year)
            }
            val completed = displayStatus == "已完結"
            val airInfo = remember(dayName, displayStatus, completed) {
                listOf(if (completed) "" else dayName, displayStatus)
                    .map(::cleanTimelineText)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("  ·  ")
            }
            val readySynopsis = resolveTimelineSynopsis(
                original = cleanTimelineText(selected.description),
                localized = cleanTimelineText(synopsis)
            )
            val description = readySynopsis.ifBlank { localizedText("中文簡介準備中") }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 42.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(
                    modifier = Modifier
                        .size(width = 34.dp, height = 3.dp)
                        .background(accent, AulamaCardShape)
                )
                Spacer(Modifier.height(5.dp))
                AutoMarqueeText(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = accent,
                    enabled = marqueeEnabled,
                    modifier = Modifier.fillMaxWidth(0.52f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = localizedText(
                        if (completed) "播放狀態  ·  $airInfo" else "播出時間  ·  $airInfo"
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = accent,
                    modifier = Modifier.fillMaxWidth(0.54f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = AulamaTvColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(0.56f)
                )
            }
        }
    }
}

@Composable
private fun TimelineStripHeader(
    count: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedText(if (isToday) "今日播送" else "播送節目"),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black
            ),
            color = AulamaTvColors.TextPrimary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = localizedText("$count 套"),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp
            ),
            color = AulamaTvColors.TextSecondary
        )
    }
}

@Composable
private fun TimelineDayTab(
    name: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val containerColor by animateColorAsState(
        targetValue = when {
            focused -> accent
            selected -> accent.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "timeline-day-container"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            focused -> AulamaTvColors.Background
            selected -> accent
            else -> Color.White
        },
        animationSpec = tween(durationMillis = 150),
        label = "timeline-day-content"
    )
    Surface(
        modifier = modifier
            .height(46.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (focused) onFocused()
            },
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
            pressedContainerColor = accent.copy(alpha = 0.82f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = localizedText(name),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black
                ),
                color = contentColor
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 3.dp)
                    .background(
                        color = when {
                            focused -> AulamaTvColors.Background.copy(alpha = 0.64f)
                            selected -> accent
                            else -> Color.Transparent
                        },
                        shape = AulamaCardShape
                    )
            )
        }
    }
}

private val TimelinePosterShape = RoundedCornerShape(12.dp)
private val TimelineHtmlTag = Regex("<[^>]+>")
private val TimelineWhitespace = Regex("\\s+")

private fun cleanTimelineText(value: String): String = value
    .replace(TimelineHtmlTag, " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(TimelineWhitespace, " ")
    .trim()
