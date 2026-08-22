@file:OptIn(ExperimentalFoundationApi::class, ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.ArtworkLoading
import com.jing.sakura.compose.common.ErrorTip
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.HeroPreviewPlayer
import com.jing.sakura.compose.common.TvPreviewPreferences
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberDpadRepeatGate
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.toDisplayLineName
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.AnimeDetailPageData
import com.jing.sakura.data.AnimePlayList
import com.jing.sakura.data.AnimePlayListEpisode
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.detail.relatedAnimeKey
import com.jing.sakura.home.HeroPreviewState
import com.jing.sakura.home.previewCardAlpha
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showShortToast
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.EpisodePlaybackSequencePolicy
import com.jing.sakura.player.PlaybackActivity
import com.jing.sakura.repo.selectNonJapaneseSynopsis
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val DetailHeroHeight = 346.dp
private val RelatedSectionHeight = 318.dp
private val RelatedRowHeight = 248.dp

private class DetailRelatedExitJobHolder(var job: Job? = null)

private val DetailParentBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float = detailParentBringIntoViewScrollDistance(offset, size, containerSize)
}

@Composable
fun DetailScreen(
    viewModel: DetailPageViewModel,
    initialTitle: String = "",
    initialImageUrl: String = "",
    initialDescription: String = "",
    initialTags: String = "",
    initialEpisodeInfo: String = "",
    initialResumeEpisode: String = ""
) {
    val context = LocalContext.current
    val previewPreferences = remember(context) { TvPreviewPreferences.get(context) }
    val previewEnabled by previewPreferences.previewEnabled.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.favoriteErrors.collect(context::showShortToast)
    }
    val detailResource = viewModel.detailPageData.collectAsState().value
    val loadingHistory = viewModel.latestProgress.collectAsState().value.getOrNull()
    Crossfade(
        targetState = detailResource,
        animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing),
        label = "detail-load-transition"
    ) { state ->
        when (state) {
            is Resource.Loading -> ArtworkLoading(
                title = initialTitle,
                imageUrl = initialImageUrl,
                tags = initialTags,
                episodeInfo = initialEpisodeInfo,
                resumeEpisode = loadingHistory?.lastEpisodeName.orEmpty()
                    .ifBlank { initialResumeEpisode }
            )

            is Resource.Error -> ErrorTip(message = state.message) { viewModel.loadData() }
            is Resource.Success -> DetailContent(
                viewModel = viewModel,
                detail = state.data.copy(
                    description = selectNonJapaneseSynopsis(
                        initialDescription,
                        state.data.description
                    )
                ),
                previewEnabled = previewEnabled
            )
        }
    }
}

@Composable
private fun DetailContent(
    viewModel: DetailPageViewModel,
    detail: AnimeDetailPageData,
    previewEnabled: Boolean
) {
    val context = LocalContext.current
    val displayAnimeName = localizedText(detail.animeName)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val history = viewModel.latestProgress.collectAsState().value.getOrNull()
    val detailAccent = rememberArtworkAccent(detail.imageUrl)
    val favoriteUiState = viewModel.favoriteUiState.collectAsState().value
    var reverseEpisodes by remember { mutableStateOf(false) }
    var showLinePicker by remember { mutableStateOf(false) }
    var resumeFocusSignal by remember { mutableStateOf(0) }
    var restoreEpisodePosition by remember { mutableStateOf(-1 to -1) }
    var selectedRelatedAnime by remember(detail.animeId) {
        mutableStateOf(detail.otherAnimeList.firstOrNull())
    }
    val relatedRowFocusState = remember(detail.animeId) { mutableStateOf(false) }
    var relatedPreviewArmed by remember(detail.animeId) { mutableStateOf(false) }
    var relatedPreviewFirstFrameReady by remember(detail.animeId) { mutableStateOf(false) }
    var isScreenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var playlistSectionHasFocus by remember(detail.animeId) { mutableStateOf(false) }
    val relatedExitJobHolder = remember(detail.animeId) { DetailRelatedExitJobHolder() }
    val relatedDescriptions = viewModel.relatedDescriptions.collectAsState().value
    val selectedRelatedDescription = selectedRelatedAnime?.let { anime ->
        relatedDescriptions[relatedAnimeKey(anime, viewModel.sourceId)]
    }
    val selectedRelatedPreviewAnime = remember(
        selectedRelatedAnime,
        selectedRelatedDescription
    ) {
        selectedRelatedAnime?.let { anime ->
            anime.copy(
                description = selectNonJapaneseSynopsis(
                    selectedRelatedDescription,
                    anime.description
                )
            )
        }
    }
    val selectedRelatedImageUrl = detailBackdropImageUrl(
        detailImageUrl = detail.imageUrl,
        relatedImageUrl = selectedRelatedPreviewAnime?.imageUrl
    )
    val selectedRelatedAccent = rememberArtworkAccent(selectedRelatedImageUrl)
    val relatedBackdropAccent = remember(selectedRelatedImageUrl) { selectedRelatedAccent }
    val heroPresentation = detailHeroPresentation(relatedRowFocusState.value)
    val relatedPreviewState = viewModel.relatedPreviewState.collectAsState().value
    val selectedRelatedSourceId = selectedRelatedAnime?.sourceId?.ifBlank { viewModel.sourceId }
    val readyRelatedPreview = (relatedPreviewState as? HeroPreviewState.Ready)?.spec
        ?.takeIf { spec ->
            spec.navigateToPlayerArg.animeId == selectedRelatedAnime?.id &&
                spec.navigateToPlayerArg.sourceId == selectedRelatedSourceId
        }
    val relatedPreviewActive = isDetailRelatedPreviewPlaybackActive(
        previewEnabled = previewEnabled,
        isScreenResumed = isScreenResumed,
        rowFocused = relatedRowFocusState.value,
        previewArmed = relatedPreviewArmed,
        firstFrameReady = relatedPreviewFirstFrameReady,
        readyAnimeId = readyRelatedPreview?.navigateToPlayerArg?.animeId,
        readySourceId = readyRelatedPreview?.navigateToPlayerArg?.sourceId,
        selectedAnimeId = selectedRelatedAnime?.id,
        selectedSourceId = selectedRelatedSourceId
    )

    LaunchedEffect(selectedRelatedAnime?.id, selectedRelatedAnime?.sourceId) {
        selectedRelatedAnime?.let(viewModel::loadRelatedDescription)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.fetchHistory()
            if (event == Lifecycle.Event.ON_RESUME) {
                isScreenResumed = true
                resumeFocusSignal += 1
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                isScreenResumed = false
                relatedPreviewArmed = false
                relatedPreviewFirstFrameReady = false
                viewModel.cancelRelatedPreview()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelRelatedPreview()
        }
    }

    LaunchedEffect(
        selectedRelatedAnime?.id,
        selectedRelatedSourceId,
        relatedRowFocusState.value,
        previewEnabled,
        isScreenResumed
    ) {
        relatedPreviewArmed = false
        relatedPreviewFirstFrameReady = false
        viewModel.cancelRelatedPreview()
        val selected = selectedRelatedAnime ?: return@LaunchedEffect
        if (!previewEnabled || !isScreenResumed || !relatedRowFocusState.value) {
            return@LaunchedEffect
        }
        delay(DETAIL_RELATED_PREVIEW_DWELL_MS)
        viewModel.prepareRelatedPreview(selected)
        relatedPreviewArmed = true
    }

    LaunchedEffect(relatedPreviewActive, readyRelatedPreview?.key) {
        if (!relatedPreviewActive) return@LaunchedEffect
        delay(DETAIL_RELATED_PREVIEW_LIMIT_MS)
        relatedPreviewArmed = false
        relatedPreviewFirstFrameReady = false
        viewModel.cancelRelatedPreview()
    }

    var selectedPlaylistIndex by remember(detail.animeId, history?.episodeId) {
        val historyIndex = history?.episodeId?.let { episodeId ->
            detail.playLists.indexOfFirst { playlist ->
                playlist.episodeList.any { it.episodeId == episodeId }
            }.takeIf { it >= 0 }
        }
        val fallbackIndex = detail.defaultPlayListIndex
            .takeIf {
                it in detail.playLists.indices && detail.playLists[it].episodeList.isNotEmpty()
            }
            ?: detail.playLists.indexOfFirst { it.episodeList.isNotEmpty() }.coerceAtLeast(0)
        mutableStateOf(historyIndex ?: fallbackIndex)
    }
    val canonicalPlaylist = detail.playLists.getOrNull(selectedPlaylistIndex)
    val displayedPlaylist = remember(canonicalPlaylist, reverseEpisodes) {
        canonicalPlaylist?.let { playlist ->
            if (reverseEpisodes) playlist.copy(episodeList = playlist.episodeList.reversed())
            else playlist
        }
    }
    val relatedBrowsePolicy = remember(displayedPlaylist) {
        detailRelatedBrowsePolicy(
            playlistCount = if (displayedPlaylist?.episodeList.isNullOrEmpty()) 0 else 1
        )
    }
    var focusedEpisodeIndex by remember(displayedPlaylist, history?.episodeId) {
        mutableStateOf(
            history?.episodeId?.let { episodeId ->
                displayedPlaylist?.episodeList?.indexOfFirst { it.episodeId == episodeId }
                    ?.takeIf { it >= 0 }
            } ?: 0
        )
    }

    val focusRequesters = remember(detail.animeId, detail.otherAnimeList.isEmpty()) {
        DetailPageRowFocusRequesters(
            hero = FocusRequester(),
            line = FocusRequester(),
            order = FocusRequester(),
            playlist = FocusRequester(),
            related = detail.otherAnimeList.takeIf(List<AnimeData>::isNotEmpty)?.let { FocusRequester() }
        )
    }
    val primaryActionFocusRequester = remember(detail.animeId) { FocusRequester() }
    val restoreEpisodeFocusRequester = remember { FocusRequester() }
    val detailListState = rememberLazyListState()
    val horizontalBringIntoViewSpec = LocalBringIntoViewSpec.current
    val hasEpisodes = !displayedPlaylist?.episodeList.isNullOrEmpty()
    val hasDetailRows = hasEpisodes || detail.otherAnimeList.isNotEmpty()
    val relatedRowIndex = detailRelatedRowIndex(if (hasEpisodes) 1 else 0)
    val upperViewportScrollOffsetPx = with(LocalDensity.current) {
        relatedBrowsePolicy.upperViewportScrollOffsetDp.dp.roundToPx()
    }
    var heroHasFocus by remember(detail.animeId) { mutableStateOf(false) }

    fun requestUpperViewport() {
        if (
            hasDetailRows &&
            (
                detailListState.firstVisibleItemIndex != 0 ||
                    detailListState.firstVisibleItemScrollOffset != upperViewportScrollOffsetPx
                )
        ) {
            detailListState.requestScrollToItem(0, upperViewportScrollOffsetPx)
        }
    }

    suspend fun anchorUpperViewport() {
        if (!hasDetailRows) return
        if (
            detailListState.firstVisibleItemIndex != 0 ||
            detailListState.firstVisibleItemScrollOffset != upperViewportScrollOffsetPx
        ) {
            detailListState.scrollToItem(0, upperViewportScrollOffsetPx)
        }
    }

    val primaryPlayPosition = remember(
        displayedPlaylist,
        history?.episodeId,
        detail.lastPlayEpisodePosition,
        selectedPlaylistIndex
    ) {
        val episodes = displayedPlaylist?.episodeList.orEmpty()
        history?.episodeId?.let { episodeId ->
            episodes.indexOfFirst { it.episodeId == episodeId }.takeIf { it >= 0 }
        } ?: detail.lastPlayEpisodePosition
            .takeIf { it.first == selectedPlaylistIndex }
            ?.second
            ?.takeIf { it in episodes.indices }
        ?: 0.takeIf { episodes.isNotEmpty() }
    }

    val onPrimaryPlay = primaryPlayPosition?.let { episodeIndex ->
        {
            val selectedEpisode = displayedPlaylist!!.episodeList[episodeIndex]
            openPlayback(
                context = context,
                animeName = displayAnimeName,
                detail = detail,
                playlist = canonicalPlaylist!!,
                playlistIndex = selectedPlaylistIndex,
                episodeIndex = EpisodePlaybackSequencePolicy.indexOfEpisode(
                    canonicalPlaylist.episodeList,
                    selectedEpisode.episodeId
                ).coerceAtLeast(0),
                sourceId = viewModel.sourceId
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AulamaTvColors.Background)
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    heroHasFocus &&
                    hasEpisodes
                ) {
                    requestUpperViewport()
                    runCatching { focusRequesters.line.requestFocus() }
                    true
                } else {
                    false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (!heroPresentation.showRelatedHero) drawContent()
                }
                .then(
                    if (heroPresentation.showRelatedHero) Modifier.clearAndSetSemantics { }
                    else Modifier
                )
        ) {
            DetailBackdrop(
                imageUrl = detail.imageUrl,
                accent = detailAccent
            )
        }
        selectedRelatedPreviewAnime?.let {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (heroPresentation.showRelatedHero) drawContent()
                    }
                    .then(
                        if (heroPresentation.showRelatedHero) Modifier
                        else Modifier.clearAndSetSemantics { }
                    )
            ) {
                RelatedDetailBackdrop(
                    imageUrl = selectedRelatedImageUrl,
                    accent = relatedBackdropAccent
                )
            }
        }
        readyRelatedPreview?.let { spec ->
            HeroPreviewPlayer(
                spec = spec,
                onReady = { relatedPreviewFirstFrameReady = true },
                onError = {
                    relatedPreviewArmed = false
                    relatedPreviewFirstFrameReady = false
                    viewModel.cancelRelatedPreview()
                },
                onEnded = {
                    relatedPreviewArmed = false
                    relatedPreviewFirstFrameReady = false
                    viewModel.cancelRelatedPreview()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (relatedPreviewActive) 1f else 0f }
            )
        }
        DetailHero(
            detail = detail,
            history = history,
            onPlayClick = onPrimaryPlay,
            isFavorite = favoriteUiState.isFavorite,
            favoriteEnabled = !favoriteUiState.isLoading && !favoriteUiState.isUpdating,
            onFavoriteClick = { viewModel.toggleFavorite(detail) },
            primaryActionFocusRequester = primaryActionFocusRequester,
            downFocusRequester = focusRequesters.line.takeIf { hasEpisodes },
            isInteractive = heroPresentation.mainHeroInteractive,
            accent = detailAccent,
            height = DetailHeroHeight,
            modifier = Modifier
                .drawWithContent {
                    if (!heroPresentation.showRelatedHero) drawContent()
                }
                .then(
                    if (heroPresentation.showRelatedHero) Modifier.clearAndSetSemantics { }
                    else Modifier
                )
                .focusRequester(focusRequesters.hero)
                .focusProperties {
                    canFocus = heroPresentation.mainHeroInteractive
                    if (
                        DetailPlaybackFocusPolicy.downFromHero(hasEpisodes) ==
                        DetailPlaybackFocusTarget.LINE
                    ) {
                        down = focusRequesters.line
                    }
                }
                .onFocusChanged { state ->
                    heroHasFocus = state.isFocused || state.hasFocus
                    if (state.isFocused || state.hasFocus) {
                        if (hasDetailRows) {
                            requestUpperViewport()
                        }
                    }
                }
        )
        selectedRelatedPreviewAnime?.let { relatedAnime ->
            RelatedPreviewHero(
                title = relatedAnime.title,
                description = relatedAnime.description,
                year = relatedAnime.year,
                tags = relatedAnime.tags,
                currentEpisode = relatedAnime.currentEpisode,
                imageUrl = selectedRelatedImageUrl,
                accent = selectedRelatedAccent,
                modifier = Modifier
                    .drawWithContent {
                        if (heroPresentation.showRelatedHero) drawContent()
                    }
                    .then(
                        if (heroPresentation.showRelatedHero) Modifier
                        else Modifier.clearAndSetSemantics { }
                    )
            )
        }
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides DetailParentBringIntoViewSpec
        ) {
            LazyColumn(
                state = detailListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = relatedBrowsePolicy.listViewportTopDp.dp),
                contentPadding = PaddingValues(bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            item(key = "hero-clearance") {
                Spacer(modifier = Modifier.height(relatedBrowsePolicy.heroClearanceDp.dp))
            }

            if (displayedPlaylist != null && displayedPlaylist.episodeList.isNotEmpty()) {
                item(key = "playlist-$selectedPlaylistIndex-$reverseEpisodes") {
                    val playlist = displayedPlaylist
                    val initialIndex = focusedEpisodeIndex
                        .coerceIn(0, playlist.episodeList.lastIndex.coerceAtLeast(0))
                    EpisodeSection(
                            title = localizedText("播放集數"),
                            lineName = localizedText(playlist.name.toDisplayLineName()),
                            episodes = playlist.episodeList,
                            initiallyFocusedIndex = initialIndex,
                            currentEpisodeId = history?.episodeId,
                            reverseEpisodes = reverseEpisodes,
                            lineFocusRequester = focusRequesters.line,
                            orderFocusRequester = focusRequesters.order,
                            headerUpFocusRequester = primaryActionFocusRequester,
                            horizontalBringIntoViewSpec = horizontalBringIntoViewSpec,
                            modifier = Modifier
                                .focusRequester(focusRequesters.playlist)
                                .onFocusChanged { state ->
                                    playlistSectionHasFocus = state.isFocused || state.hasFocus
                                },
                            restoreFocusRequester = restoreEpisodeFocusRequester,
                            restoreFocusEpisodeIndex = restoreEpisodePosition
                                .takeIf { it.first == selectedPlaylistIndex }
                                ?.second
                                ?: -1,
                            onChooseLine = { showLinePicker = true },
                            onToggleOrder = {
                                focusedEpisodeIndex = 0
                                reverseEpisodes = !reverseEpisodes
                                scope.launch {
                                    delay(90)
                                    runCatching { focusRequesters.order.requestFocus() }
                                }
                            },
                            onNavigateUp = {
                                requestUpperViewport()
                                if (
                                    DetailPlaybackFocusPolicy.upFromEpisode() ==
                                    DetailPlaybackFocusTarget.LINE
                                ) {
                                    runCatching { focusRequesters.line.requestFocus() }
                                }
                            },
                            onNavigateDown = focusRequesters.related?.let {
                                {
                                    detailListState.requestScrollToItem(relatedRowIndex)
                                    runCatching { focusRequesters.related.requestFocus() }
                                }
                            },
                            onEpisodeFocused = { episodeIndex, _ ->
                                focusedEpisodeIndex = episodeIndex
                            },
                            onEpisodeClick = { episodeIndex, _ ->
                                val selectedEpisode = playlist.episodeList[episodeIndex]
                                restoreEpisodePosition = selectedPlaylistIndex to episodeIndex
                                openPlayback(
                                    context = context,
                                    animeName = displayAnimeName,
                                    detail = detail,
                                    playlist = canonicalPlaylist!!,
                                    playlistIndex = selectedPlaylistIndex,
                                    episodeIndex = EpisodePlaybackSequencePolicy.indexOfEpisode(
                                        canonicalPlaylist.episodeList,
                                        selectedEpisode.episodeId
                                    ).coerceAtLeast(0),
                                    sourceId = viewModel.sourceId
                                )
                            }
                        )
                }
            }

            if (detail.otherAnimeList.isNotEmpty()) {
                item(key = "related") {
                    RelatedAnimeSection(
                        videos = detail.otherAnimeList,
                        previewEnabled = previewEnabled,
                        previewActive = relatedPreviewActive,
                        horizontalBringIntoViewSpec = horizontalBringIntoViewSpec,
                        onNavigateUp = {
                            relatedPreviewArmed = false
                            relatedPreviewFirstFrameReady = false
                            viewModel.cancelRelatedPreview()
                            relatedExitJobHolder.job?.cancel()
                            relatedExitJobHolder.job = scope.launch {
                                if (hasEpisodes) {
                                    detailListState.requestScrollToItem(
                                        index = 0,
                                        scrollOffset = upperViewportScrollOffsetPx
                                    )
                                    withFrameNanos { }
                                    runCatching {
                                        focusRequesters.playlist.requestFocus()
                                    }
                                    if (
                                        shouldRetryDetailRelatedExitFocus(
                                            firstRequestSucceeded = playlistSectionHasFocus
                                        )
                                    ) {
                                        withFrameNanos { }
                                        runCatching { focusRequesters.playlist.requestFocus() }
                                    }
                                } else {
                                    relatedRowFocusState.value = false
                                    detailListState.requestScrollToItem(0)
                                    withFrameNanos { }
                                    val primaryFocusResult = runCatching {
                                        primaryActionFocusRequester.requestFocus()
                                    }
                                    if (primaryFocusResult.isFailure) {
                                        runCatching { focusRequesters.hero.requestFocus() }
                                    }
                                }
                            }
                        },
                        onRowFocusChanged = { focused ->
                            val wasFocused = relatedRowFocusState.value
                            relatedRowFocusState.value = focused
                            if (!focused) {
                                relatedPreviewArmed = false
                                relatedPreviewFirstFrameReady = false
                                viewModel.cancelRelatedPreview()
                            }
                            if (shouldCancelDetailRelatedExit(wasFocused, focused)) {
                                relatedExitJobHolder.job?.cancel()
                                relatedExitJobHolder.job = null
                            }
                        },
                        onVideoFocused = {
                            if (
                                selectedRelatedAnime?.id != it.id ||
                                selectedRelatedSourceId != it.sourceId.ifBlank { viewModel.sourceId }
                            ) {
                                relatedPreviewArmed = false
                                relatedPreviewFirstFrameReady = false
                                viewModel.cancelRelatedPreview()
                            }
                            selectedRelatedAnime = it
                        },
                        onVideoClick = { anime ->
                            relatedPreviewArmed = false
                            relatedPreviewFirstFrameReady = false
                            viewModel.cancelRelatedPreview()
                            DetailActivity.startActivity(
                                context = context,
                                anime = anime,
                                sourceId = anime.sourceId.ifBlank { viewModel.sourceId }
                            )
                        },
                        modifier = Modifier.focusRequester(focusRequesters.related!!)
                    )
                }
            }
            }
        }
    }

    if (showLinePicker) {
        PlaybackLinePickerDialog(
            playlists = detail.playLists,
            selectedIndex = selectedPlaylistIndex,
            onSelect = { index ->
                selectedPlaylistIndex = index
                focusedEpisodeIndex = 0
                reverseEpisodes = false
                showLinePicker = false
                scope.launch {
                    delay(80)
                    runCatching { focusRequesters.line.requestFocus() }
                }
            },
            onDismiss = { showLinePicker = false }
        )
    }

    LaunchedEffect(detail.animeId) {
        anchorUpperViewport()
        delay(80)
        runCatching { focusRequesters.hero.requestFocus() }
    }
    LaunchedEffect(resumeFocusSignal) {
        if (restoreEpisodePosition.first >= 0 && restoreEpisodePosition.second >= 0) {
            delay(140)
            if (hasEpisodes) {
                anchorUpperViewport()
            }
            runCatching { restoreEpisodeFocusRequester.requestFocus() }
            restoreEpisodePosition = -1 to -1
        }
    }
}

@Composable
private fun DetailBackdrop(
    imageUrl: String,
    accent: Color
) {
    val targetBackdrop = remember(imageUrl) {
        imageUrl.takeIf(String::isNotBlank)?.let {
            DetailBackdropState(
                key = it,
                imageUrl = it
            )
        }
    }
    var readyBackdrop by remember { mutableStateOf<DetailBackdropState?>(null) }
    val currentTargetKey by rememberUpdatedState(targetBackdrop?.key)
    val backdropAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "detail-backdrop-accent"
    )
    LaunchedEffect(targetBackdrop) {
        if (targetBackdrop == null) readyBackdrop = null
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            backdropAccent.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        radius = 980f
                    )
                )
        )
        targetBackdrop
            ?.takeIf { it.key != readyBackdrop?.key }
            ?.let { pending ->
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = pending.imageUrl,
                        widthPx = 900,
                        heightPx = 900
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
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(420, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(tween(300, easing = FastOutSlowInEasing)))
            },
            label = "detail-backdrop"
        ) { state ->
            DetailBackdropArtwork(state)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.42f to AulamaTvColors.Background.copy(alpha = 0.98f),
                            0.72f to AulamaTvColors.Background.copy(alpha = 0.62f),
                            1f to AulamaTvColors.Background.copy(alpha = 0.18f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
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

private data class DetailBackdropState(
    val key: String,
    val imageUrl: String
)

@Composable
private fun DetailBackdropArtwork(state: DetailBackdropState?) {
    if (state == null) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        AsyncImage(
            model = rememberPosterImageRequest(
                imageUrl = state.imageUrl,
                widthPx = 900,
                heightPx = 900
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
            alpha = 0.38f,
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun RelatedDetailBackdrop(
    imageUrl: String,
    accent: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.2f), Color.Transparent),
                        radius = 980f
                    )
                )
        )
        AsyncImage(
            model = rememberPosterImageRequest(
                imageUrl = imageUrl,
                widthPx = 300,
                heightPx = 450
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopEnd,
            alpha = 0.38f,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.58f)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to AulamaTvColors.Background,
                            0.42f to AulamaTvColors.Background.copy(alpha = 0.98f),
                            0.72f to AulamaTvColors.Background.copy(alpha = 0.62f),
                            1f to AulamaTvColors.Background.copy(alpha = 0.18f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
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
private fun DetailHero(
    detail: AnimeDetailPageData,
    history: VideoHistoryEntity?,
    onPlayClick: (() -> Unit)?,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester?,
    isInteractive: Boolean,
    accent: Color,
    height: Dp = DetailHeroHeight,
    modifier: Modifier = Modifier
) {
    val displayTitle = localizedText(detail.animeName)
    val displayDescription = localizedText(detail.description).trim()
    val episodeCount = detail.playLists.maxOfOrNull { it.episodeList.size } ?: 0
    val metadata = compactDetailMetadata(detail.infoList, episodeCount)
    val titleLayout = remember(displayTitle) { DetailTitleLayoutPolicy.forTitle(displayTitle) }
    val onNavigateDown: (() -> Unit)? = downFocusRequester?.let { requester ->
        {
            runCatching { requester.requestFocus() }
            Unit
        }
    }
    var showDescription by remember { mutableStateOf(false) }

    FocusGroup(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (
                event.type == KeyEventType.KeyDown &&
                event.key == Key.DirectionDown &&
                isInteractive &&
                onNavigateDown != null
            ) {
                onNavigateDown()
                true
            } else {
                false
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(start = 44.dp, end = 44.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailPoster(
                imageUrl = detail.imageUrl,
                title = displayTitle,
                accent = accent,
            )
            Spacer(modifier = Modifier.width(32.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = displayTitle,
                    maxLines = titleLayout.maxLines,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = titleLayout.fontSizeSp.sp,
                        lineHeight = titleLayout.lineHeightSp.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = accent
                )
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = AulamaTvColors.TextSecondary
                    )
                }
                if (displayDescription.isNotBlank()) {
                    Text(
                        text = displayDescription,
                        maxLines = titleLayout.descriptionMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 23.sp
                        ),
                        color = AulamaTvColors.TextPrimary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onPlayClick?.let { play ->
                        DetailActionButton(
                            label = if (history == null) "立即播放" else "繼續播放",
                            icon = Icons.Default.PlayArrow,
                            accent = accent,
                            enabled = isInteractive,
                            onClick = play,
                            onNavigateDown = onNavigateDown,
                            modifier = Modifier
                                .width(142.dp)
                                .focusRequester(primaryActionFocusRequester)
                                .focusProperties {
                                    canFocus = isInteractive
                                    downFocusRequester?.let { down = it }
                                }
                                .initiallyFocused()
                        )
                    }
                    if (displayDescription.isNotBlank()) {
                        DetailActionButton(
                            label = "完整簡介",
                            icon = Icons.Default.Info,
                            accent = AulamaTvColors.Pink,
                            enabled = isInteractive,
                            onClick = { showDescription = true },
                            onNavigateDown = onNavigateDown,
                            modifier = Modifier
                                .width(132.dp)
                                .focusProperties {
                                    canFocus = isInteractive
                                    downFocusRequester?.let { down = it }
                                }
                                .restorableFocus()
                        )
                    }
                    DetailActionButton(
                        label = if (isFavorite) "已收藏" else "收藏",
                        icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        accent = if (isFavorite) AulamaTvColors.Cyan else AulamaTvColors.Amber,
                        enabled = isInteractive && favoriteEnabled,
                        onClick = onFavoriteClick,
                        onNavigateDown = onNavigateDown,
                        modifier = Modifier
                            .width(116.dp)
                            .focusProperties {
                                canFocus = isInteractive
                                downFocusRequester?.let { down = it }
                            }
                            .restorableFocus()
                    )
                }
                history?.let {
                    PlaybackProgressLine(history = it, accent = accent)
                }
            }
        }
    }

    if (showDescription && isInteractive) {
        DescriptionDialog(
            title = displayTitle,
            description = displayDescription,
            accent = accent,
            onDismiss = { showDescription = false }
        )
    }
}

@Composable
private fun RelatedPreviewHero(
    title: String,
    description: String,
    year: String,
    tags: String,
    currentEpisode: String,
    imageUrl: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val displayTitle = localizedText(title)
    val displayDescription = localizedText(selectNonJapaneseSynopsis(description)).trim()
    val metadata = listOf(year, tags, currentEpisode)
        .map { localizedText(it) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("  •  ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(226.dp)
            .padding(start = 44.dp, end = 44.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(8.dp)
        AsyncImage(
            model = rememberPosterImageRequest(imageUrl, widthPx = 300, heightPx = 450),
            contentDescription = displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 126.dp, height = 180.dp)
                .clip(shape)
                .border(1.5.dp, accent.copy(alpha = 0.76f), shape)
        )
        Spacer(modifier = Modifier.width(26.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = displayTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 31.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = accent
            )
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = AulamaTvColors.TextSecondary
                )
            }
            if (displayDescription.isNotBlank()) {
                Text(
                    text = displayDescription,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 21.sp
                    ),
                    color = AulamaTvColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onNavigateDown: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(7.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionDown &&
                    onNavigateDown != null
                ) {
                    onNavigateDown()
                    true
                } else {
                    false
                }
            }
            .height(46.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = accent,
            focusedContentColor = Color(0xFF061014),
            pressedContainerColor = accent.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AulamaTvColors.Outline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AulamaTvColors.FocusBorder), shape = shape)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = localizedText(label),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailPoster(
    imageUrl: String,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(width = 218.dp, height = 310.dp)
            .clip(shape)
            .border(1.5.dp, accent.copy(alpha = 0.7f), shape)
            .background(Color(0xFF0A0E16))
    ) {
        AsyncImage(
            model = rememberPosterImageRequest(imageUrl = imageUrl, widthPx = 440, heightPx = 620),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun compactDetailMetadata(infoList: List<String>, episodeCount: Int): String {
    val localized = mutableListOf<String>()
    for (rawInfo in infoList) {
        for (rawPart in rawInfo.split('•', '・', '|')) {
            val part = rawPart.trim()
            if (part.isNotBlank()) localized += localizedText(part)
        }
    }

    val preferredKeys = listOf(
        "地區", "地区", "年份", "年", "類型", "类型", "狀態", "状态"
    )
    val selected = localized
        .filter { part -> preferredKeys.any(part::startsWith) }
        .map { part ->
            if (part.startsWith("類型") || part.startsWith("类型")) {
                val separator = if ('：' in part) '：' else ':'
                val pieces = part.substringAfter(separator, part)
                    .split(',', '，', '/', '、')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .take(3)
                if (pieces.isNotEmpty()) pieces.joinToString(" / ") else part
            } else {
                part.substringAfter('：', part.substringAfter(':', part)).trim()
            }
        }
        .filter(String::isNotBlank)
        .distinct()
        .take(4)
        .toMutableList()

    if (selected.isEmpty()) {
        selected += localized.take(3)
    }
    if (episodeCount > 0) selected += localizedText("$episodeCount 集")
    return selected.distinct().joinToString("  •  ")
}

@Composable
private fun PlaybackProgressLine(history: VideoHistoryEntity, accent: Color) {
    val progress = if (history.videoDuration > 0L) {
        (history.lastPlayTime.toFloat() / history.videoDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = localizedText(
                "上次看到 ${history.lastEpisodeName}  " +
                    "${(history.lastPlayTime / 1000).secondsToMinuteAndSecondText()} / " +
                    (history.videoDuration / 1000).secondsToMinuteAndSecondText()
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = AulamaTvColors.TextSecondary
        )
        Box(
            modifier = Modifier
                .width(340.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AulamaTvColors.Outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(accent)
            )
        }
    }
}

@Composable
private fun EpisodeSection(
    title: String,
    lineName: String,
    episodes: List<AnimePlayListEpisode>,
    initiallyFocusedIndex: Int,
    currentEpisodeId: String?,
    reverseEpisodes: Boolean,
    lineFocusRequester: FocusRequester,
    orderFocusRequester: FocusRequester,
    headerUpFocusRequester: FocusRequester,
    horizontalBringIntoViewSpec: BringIntoViewSpec,
    modifier: Modifier = Modifier,
    restoreFocusRequester: FocusRequester,
    restoreFocusEpisodeIndex: Int,
    onChooseLine: () -> Unit,
    onToggleOrder: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateDown: (() -> Unit)?,
    onEpisodeFocused: (Int, AnimePlayListEpisode) -> Unit,
    onEpisodeClick: (Int, AnimePlayListEpisode) -> Unit
) {
    if (episodes.isEmpty()) return
    val consumeRapidDpad = rememberDpadRepeatGate()
    val rangeSize = when {
        episodes.size >= 40 -> 20
        episodes.size >= 20 -> 10
        else -> episodes.size
    }
    val rangeCount = (episodes.size + rangeSize - 1) / rangeSize
    val safeInitialIndex = initiallyFocusedIndex.coerceIn(0, episodes.lastIndex)
    val initialRangeIndex = (safeInitialIndex / rangeSize).coerceIn(0, rangeCount - 1)
    var selectedRangeIndex by remember(episodes, reverseEpisodes) {
        mutableStateOf(initialRangeIndex)
    }
    val rangeStart = selectedRangeIndex * rangeSize
    val rangeEnd = (rangeStart + rangeSize).coerceAtMost(episodes.size)
    val entryEpisodeIndex = safeInitialIndex.takeIf { it in rangeStart until rangeEnd } ?: rangeStart
    val entryEpisodeFocusRequester = remember(
        episodes,
        reverseEpisodes,
        selectedRangeIndex
    ) { FocusRequester() }
    val initialVisibleIndex = if (selectedRangeIndex == initialRangeIndex) {
        safeInitialIndex - rangeStart
    } else {
        0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialVisibleIndex)
    LaunchedEffect(episodes, reverseEpisodes, selectedRangeIndex) {
        listState.scrollToItem(initialVisibleIndex)
    }
    val firstRangeLabel = localizedText(episodes[rangeStart].episode)
    val lastRangeLabel = localizedText(episodes[rangeEnd - 1].episode)
    val rangeLabel = buildString {
        append(selectedRangeIndex + 1)
        append('/')
        append(rangeCount)
        append("  ")
        append(firstRangeLabel)
        if (firstRangeLabel != lastRangeLabel) {
            append(" - ")
            append(lastRangeLabel)
        }
    }
    FocusGroup(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AulamaTvColors.TextPrimary,
                    modifier = Modifier.widthIn(min = 112.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                PlaybackLineControl(
                    lineName = lineName,
                    onClick = onChooseLine,
                    modifier = Modifier
                        .focusRequester(lineFocusRequester)
                        .focusProperties {
                            up = headerUpFocusRequester
                            right = orderFocusRequester
                            down = entryEpisodeFocusRequester
                        }
                        .initiallyFocused()
                )
                Spacer(modifier = Modifier.width(10.dp))
                OrderControl(
                    reverse = reverseEpisodes,
                    onClick = onToggleOrder,
                    modifier = Modifier
                        .focusRequester(orderFocusRequester)
                        .focusProperties {
                            up = headerUpFocusRequester
                            left = lineFocusRequester
                            down = entryEpisodeFocusRequester
                        }
                        .restorableFocus()
                )
                if (rangeCount > 1) {
                    Spacer(modifier = Modifier.width(10.dp))
                    EpisodeRangeControl(
                        label = rangeLabel,
                        canGoPrevious = selectedRangeIndex > 0,
                        canGoNext = selectedRangeIndex < rangeCount - 1,
                        onPrevious = { selectedRangeIndex -= 1 },
                        onNext = { selectedRangeIndex += 1 },
                        onClick = {
                            selectedRangeIndex = (selectedRangeIndex + 1) % rangeCount
                        },
                        modifier = Modifier
                            .focusProperties { down = entryEpisodeFocusRequester }
                            .restorableFocus()
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = localizedText("${episodes.size} 集"),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = AulamaTvColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides horizontalBringIntoViewSpec
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            onNavigateUp()
                            return@onPreviewKeyEvent true
                        }
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown &&
                            onNavigateDown != null
                        ) {
                            onNavigateDown()
                            return@onPreviewKeyEvent true
                        }
                        if (consumeRapidDpad(event)) return@onPreviewKeyEvent true
                        false
                    },
                    contentPadding = PaddingValues(horizontal = 44.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = rangeEnd - rangeStart,
                        key = { episodes[rangeStart + it].episodeId }
                    ) { visibleEpisodeIndex ->
                        val episodeIndex = rangeStart + visibleEpisodeIndex
                        val episode = episodes[episodeIndex]
                        var episodeModifier = Modifier
                            .onFocusChanged {
                                if (it.isFocused || it.hasFocus) {
                                    onEpisodeFocused(episodeIndex, episode)
                                }
                            }
                        episodeModifier = if (episodeIndex == entryEpisodeIndex) {
                            episodeModifier
                                .focusRequester(entryEpisodeFocusRequester)
                                .restorableFocus()
                        } else {
                            episodeModifier.restorableFocus()
                        }
                        if (restoreFocusEpisodeIndex == episodeIndex) {
                            episodeModifier = episodeModifier.focusRequester(restoreFocusRequester)
                        }
                        EpisodeTile(
                            label = episode.episode,
                            isCurrent = episode.episodeId == currentEpisodeId,
                            modifier = episodeModifier,
                            onClick = { onEpisodeClick(episodeIndex, episode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRangeControl(
    label: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .widthIn(min = 190.dp, max = 252.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (canGoPrevious) onPrevious() else return@onPreviewKeyEvent false
                        true
                    }

                    Key.DirectionRight -> {
                        if (canGoNext) onNext() else return@onPreviewKeyEvent false
                        true
                    }

                    else -> false
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AulamaTvColors.Cyan,
            focusedContentColor = Color(0xFF041014),
            pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AulamaTvColors.Outline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AulamaTvColors.FocusBorder), shape = shape)
        )
    ) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = localizedText("上一組"),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = if (canGoPrevious) 1f else 0.32f }
            )
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = localizedText("下一組"),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = if (canGoNext) 1f else 0.32f }
            )
        }
    }
}

@Composable
private fun PlaybackLineControl(
    lineName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .widthIn(max = 210.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AulamaTvColors.Cyan,
            focusedContentColor = Color(0xFF041014),
            pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AulamaTvColors.Outline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AulamaTvColors.FocusBorder), shape = shape)
        )
    ) {
        Text(
            text = localizedText("線路：$lineName ▾"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun OrderControl(
    reverse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AulamaTvColors.Cyan,
            focusedContentColor = Color(0xFF041014),
            pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AulamaTvColors.Outline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AulamaTvColors.FocusBorder), shape = shape)
        )
    ) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = localizedText(if (reverse) "倒序" else "正序"),
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun EpisodeTile(
    label: String,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(5.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(42.dp)
            .widthIn(min = 78.dp, max = 132.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x520A0E16),
            focusedContainerColor = AulamaTvColors.Cyan,
            pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, AulamaTvColors.Outline.copy(alpha = 0.72f)),
                shape = shape
            ),
            focusedBorder = Border(BorderStroke(2.5.dp, AulamaTvColors.FocusBorder), shape = shape)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = localizedText(label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black
                ),
                color = if (focused) Color(0xFF041014) else AulamaTvColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isCurrent) 4.dp else 0.dp)
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .size(width = 22.dp, height = 3.dp)
                        .background(
                            if (focused) Color(0xFF041014) else AulamaTvColors.Cyan,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun RelatedAnimeSection(
    videos: List<AnimeData>,
    previewEnabled: Boolean,
    previewActive: Boolean,
    horizontalBringIntoViewSpec: BringIntoViewSpec,
    onNavigateUp: () -> Unit,
    onRowFocusChanged: (Boolean) -> Unit,
    onVideoFocused: (AnimeData) -> Unit,
    onVideoClick: (AnimeData) -> Unit,
    modifier: Modifier = Modifier
) {
    val identity = remember(videos) {
        videos.joinToString(separator = "|") { "${it.sourceId}:${it.id}" }
    }
    val virtualItemCount = remember(identity) { detailRelatedVirtualItemCount(videos.size) }
    val initialVirtualIndex = remember(identity) { detailRelatedInitialVirtualIndex(videos.size) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = initialVirtualIndex)
    var selectedVirtualIndex by remember(identity) { mutableStateOf(initialVirtualIndex) }
    var rowFocused by remember(identity) { mutableStateOf(false) }
    var dimUnselected by remember(identity) { mutableStateOf(false) }
    val cardStridePx = with(LocalDensity.current) { 164.dp.toPx() }
    val moveEvents = remember(identity) {
        MutableSharedFlow<Int>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val selectedLogicalIndex = detailRelatedLogicalIndex(selectedVirtualIndex, videos.size)
    val selectedVideo = videos[selectedLogicalIndex]
    val selectedAccent = rememberArtworkAccent(selectedVideo.imageUrl, enabled = rowFocused)
    val publishFocusedVideo by rememberUpdatedState(onVideoFocused)

    LaunchedEffect(identity, rowState, cardStridePx) {
        moveEvents.collect { delta ->
            val target = detailRelatedMoveVirtualIndex(
                currentIndex = selectedVirtualIndex,
                delta = delta,
                itemCount = videos.size
            )
            if (target == selectedVirtualIndex) return@collect
            selectedVirtualIndex = target
            dimUnselected = false
            publishFocusedVideo(
                videos[detailRelatedLogicalIndex(target, videos.size)]
            )
            rowState.animateScrollBy(
                value = delta * cardStridePx,
                animationSpec = tween(
                    durationMillis = 165,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    LaunchedEffect(rowFocused, selectedVirtualIndex, identity, previewEnabled) {
        dimUnselected = false
        if (!shouldDimDetailRelatedSelection(rowFocused, previewEnabled)) {
            return@LaunchedEffect
        }
        delay(3_000)
        dimUnselected = true
    }

    FocusGroup(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(RelatedSectionHeight)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp)
                    .graphicsLayer { alpha = if (previewActive) 0.10f else 1f },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.related_videos),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = localizedText("${videos.size} 套"),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = AulamaTvColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RelatedRowHeight)
                    .onFocusChanged { state ->
                        val focused = state.isFocused || state.hasFocus
                        val wasFocused = rowFocused
                        if (focused == wasFocused) return@onFocusChanged
                        if (shouldPublishDetailRelatedSelection(wasFocused, focused)) {
                            publishFocusedVideo(selectedVideo)
                        }
                        rowFocused = focused
                        onRowFocusChanged(focused)
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
                                onNavigateUp()
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> true
                            event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                onVideoClick(selectedVideo)
                                true
                            }

                            event.key == Key.DirectionCenter || event.key == Key.Enter -> true
                            else -> false
                        }
                    }
                    .initiallyFocused()
                    .focusable()
            ) {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides horizontalBringIntoViewSpec
                ) {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 38.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            count = virtualItemCount,
                            key = { virtualIndex ->
                                val video = videos[
                                    detailRelatedLogicalIndex(virtualIndex, videos.size)
                                ]
                                "$virtualIndex:${video.sourceId}:${video.id}"
                            }
                        ) { virtualIndex ->
                            val video = videos[
                                detailRelatedLogicalIndex(virtualIndex, videos.size)
                            ]
                            val selected = rowFocused && virtualIndex == selectedVirtualIndex
                            val cardAlpha by animateFloatAsState(
                                targetValue = previewCardAlpha(
                                    rowFocused = rowFocused,
                                    selected = selected,
                                    dimUnselected = dimUnselected,
                                    previewActive = previewActive,
                                    previewEnabled = previewEnabled
                                ),
                                animationSpec = tween(durationMillis = 420),
                                label = "detail-related-card-alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 238.dp)
                                    .graphicsLayer { alpha = cardAlpha },
                                contentAlignment = Alignment.Center
                            ) {
                                RelatedPosterCard(
                                    anime = video,
                                    selected = selected,
                                    selectedAccent = selectedAccent,
                                    modifier = Modifier.size(width = 148.dp, height = 222.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedPosterCard(
    anime: AnimeData,
    selected: Boolean,
    selectedAccent: Color,
    modifier: Modifier = Modifier
) {
    val title = localizedText(anime.title)
    val subtitle = localizedText(anime.currentEpisode)
    val posterRequest = rememberPosterImageRequest(
        imageUrl = anime.imageUrl,
        widthPx = 300,
        heightPx = 450
    )
    Box(
        modifier = modifier
            .border(
                BorderStroke(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        selectedAccent
                    } else {
                        AulamaTvColors.Outline.copy(alpha = 0.72f)
                    }
                ),
                AulamaCardShape
            )
            .clip(AulamaCardShape)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xCC080B12))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        com.jing.sakura.compose.common.AutoMarqueeText(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Black
            ),
            color = AulamaTvColors.TextPrimary,
            enabled = selected,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun DescriptionDialog(
    title: String,
    description: String,
    accent: Color,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD905070C)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AulamaTvColors.Surface)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 26.dp)
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 29.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 27.sp
                    ),
                    color = AulamaTvColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val offset = when (event.key) {
                                Key.DirectionUp -> -82f
                                Key.DirectionDown -> 82f
                                else -> return@onPreviewKeyEvent false
                            }
                            scope.launch { scrollState.animateScrollBy(offset) }
                            true
                        }
                )
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun PlaybackLinePickerDialog(
    playlists: List<AnimePlayList>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedFocusRequester = remember(selectedIndex) { FocusRequester() }
    val cardShape = RoundedCornerShape(20.dp)
    val optionShape = RoundedCornerShape(12.dp)
    val columns = 3
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogBlurBehind(radius = 24)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x8F02050A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(548.dp)
                    .shadow(28.dp, cardShape, clip = false)
                    .clip(cardShape)
                    .background(AulamaTvColors.Surface.copy(alpha = 0.72f))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.09f),
                                AulamaTvColors.Cyan.copy(alpha = 0.055f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        cardShape
                    )
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = localizedText("選擇播放線路"),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 25.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    playlists.withIndex().chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (index, playlist) ->
                                val available = playlist.episodeList.isNotEmpty()
                                val selected = index == selectedIndex
                                Surface(
                                    onClick = { if (available) onSelect(index) },
                                    enabled = available,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(82.dp)
                                        .then(
                                            if (selected) {
                                                Modifier.focusRequester(selectedFocusRequester)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (selected) {
                                            AulamaTvColors.Cyan.copy(alpha = 0.16f)
                                        } else {
                                            Color.White.copy(alpha = 0.055f)
                                        },
                                        contentColor = AulamaTvColors.TextPrimary,
                                        focusedContainerColor = AulamaTvColors.Cyan,
                                        focusedContentColor = Color(0xFF041014),
                                        pressedContainerColor = AulamaTvColors.Cyan.copy(alpha = 0.84f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.025f),
                                        disabledContentColor = AulamaTvColors.TextSecondary.copy(alpha = 0.48f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(
                                        focusedScale = AulamaFocusScale
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(optionShape),
                                    border = ClickableSurfaceDefaults.border(
                                        border = Border(
                                            BorderStroke(
                                                1.dp,
                                                if (selected) {
                                                    AulamaTvColors.Cyan.copy(alpha = 0.58f)
                                                } else {
                                                    Color.White.copy(alpha = 0.11f)
                                                }
                                            ),
                                            shape = optionShape
                                        ),
                                        focusedBorder = Border(
                                            BorderStroke(2.dp, AulamaTvColors.FocusBorder),
                                            shape = optionShape
                                        )
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = localizedText(playlist.name.toDisplayLineName()),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 16.sp,
                                                lineHeight = 21.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = localizedText(playbackLineAvailabilityText(playlist)),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(selectedIndex) {
            runCatching { selectedFocusRequester.requestFocus() }
        }
    }
}

@Composable
private fun DialogBlurBehind(radius: Int) {
    val view = LocalView.current
    DisposableEffect(view, radius) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window?.attributes = window?.attributes?.apply {
                blurBehindRadius = radius
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}

internal fun playbackLineAvailabilityText(playlist: AnimePlayList): String =
    if (playlist.episodeList.isEmpty()) "不可用 · 未有可播放集數"
    else "可用 · ${playlist.episodeList.size} 集"

private fun openPlayback(
    context: android.content.Context,
    animeName: String,
    detail: AnimeDetailPageData,
    playlist: AnimePlayList,
    playlistIndex: Int,
    episodeIndex: Int,
    sourceId: String
) {
    PlaybackActivity.startActivity(
        context,
        NavigateToPlayerArg(
            animeName = animeName,
            animeId = detail.animeId,
            coverUrl = detail.imageUrl,
            playIndex = episodeIndex,
            playlist = playlist.episodeList,
            sourceId = sourceId,
            playlists = detail.playLists,
            playlistIndex = playlistIndex
        )
    )
}

private data class DetailPageRowFocusRequesters(
    val hero: FocusRequester,
    val line: FocusRequester,
    val order: FocusRequester,
    val playlist: FocusRequester,
    val related: FocusRequester?
)
