@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.jing.sakura.home.previewCardAlpha
import com.jing.sakura.extend.secondsToMinuteAndSecondText
import com.jing.sakura.extend.showShortToast
import com.jing.sakura.player.NavigateToPlayerArg
import com.jing.sakura.player.EpisodePlaybackSequencePolicy
import com.jing.sakura.player.PlaybackActivity
import com.jing.sakura.room.VideoHistoryEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val DetailHeroHeight = 346.dp
private val RelatedSectionHeight = 318.dp
private val RelatedRowHeight = 248.dp

@Composable
fun DetailScreen(
    viewModel: DetailPageViewModel,
    initialTitle: String = "",
    initialImageUrl: String = "",
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
                detail = state.data,
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
    var resumeFocusSignal by remember { mutableStateOf(0) }
    var restoreEpisodePosition by remember { mutableStateOf(-1 to -1) }
    var focusedRelatedAnime by remember(detail.animeId) { mutableStateOf<AnimeData?>(null) }
    val relatedDescriptions = viewModel.relatedDescriptions.collectAsState().value
    val activeBackdropAnime = focusedRelatedAnime?.let { anime ->
        relatedDescriptions[relatedAnimeKey(anime, viewModel.sourceId)]
            ?.takeIf(String::isNotBlank)
            ?.let { description -> anime.copy(description = description) }
            ?: anime
    }
    val activeBackdropImageUrl = detailBackdropImageUrl(
        detailImageUrl = detail.imageUrl,
        relatedImageUrl = activeBackdropAnime?.imageUrl
    )
    val activeBackdropAccent = rememberArtworkAccent(activeBackdropImageUrl)

    LaunchedEffect(focusedRelatedAnime?.id, focusedRelatedAnime?.sourceId) {
        focusedRelatedAnime?.let(viewModel::loadRelatedDescription)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.fetchHistory()
            if (event == Lifecycle.Event.ON_RESUME) resumeFocusSignal += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playlists = remember(detail.playLists, reverseEpisodes) {
        detail.playLists.mapIndexed { index, playlist ->
            PlayListWrapper(
                id = index,
                playlist = if (reverseEpisodes) {
                    playlist.copy(episodeList = playlist.episodeList.reversed())
                } else {
                    playlist
                }
            )
        }
    }
    val relatedBrowsePolicy = remember(playlists.size, activeBackdropAnime?.id) {
        detailRelatedBrowsePolicy(
            playlistCount = playlists.size,
            focusedRelatedAnimeId = activeBackdropAnime?.id
        )
    }
    val heroHeight by animateDpAsState(
        targetValue = relatedBrowsePolicy.heroHeightDp.dp,
        animationSpec = tween(durationMillis = 260),
        label = "detail-hero-height"
    )
    var focusedEpisodeIndexes by remember(playlists, history?.episodeId) {
        val focused = MutableList(playlists.size) { 0 }
        history?.episodeId?.let { episodeId ->
            playlists.forEachIndexed { playlistIndex, wrapper ->
                wrapper.playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
                    .takeIf { it >= 0 }
                    ?.let { focused[playlistIndex] = it }
            }
        }
        mutableStateOf(focused)
    }

    val focusRequesters = remember(playlists.size, detail.otherAnimeList.isEmpty()) {
        DetailPageRowFocusRequesters(
            hero = FocusRequester(),
            order = playlists.firstOrNull()?.let { FocusRequester() },
            playlists = List(playlists.size) { FocusRequester() },
            related = detail.otherAnimeList.takeIf(List<AnimeData>::isNotEmpty)?.let { FocusRequester() }
        )
    }
    val primaryActionFocusRequester = remember(detail.animeId) { FocusRequester() }
    val restoreEpisodeFocusRequester = remember { FocusRequester() }
    val detailListState = rememberLazyListState()
    val hasDetailRows = playlists.isNotEmpty() || detail.otherAnimeList.isNotEmpty()

    val primaryPlayPosition = remember(
        playlists,
        history?.episodeId,
        detail.defaultPlayListIndex,
        detail.lastPlayEpisodePosition
    ) {
        fun valid(position: Pair<Int, Int>): Boolean =
            position.first in playlists.indices &&
                position.second in playlists[position.first].playlist.episodeList.indices

        val historyPosition = history?.episodeId?.let { episodeId ->
            playlists.forEachIndexed { playlistIndex, wrapper ->
                val episodeIndex = wrapper.playlist.episodeList.indexOfFirst { it.episodeId == episodeId }
                if (episodeIndex >= 0) return@let playlistIndex to episodeIndex
            }
            null
        }
        val defaultPosition = detail.defaultPlayListIndex to 0
        listOfNotNull(historyPosition, detail.lastPlayEpisodePosition, defaultPosition)
            .firstOrNull(::valid)
            ?: playlists.indexOfFirst { it.playlist.episodeList.isNotEmpty() }
                .takeIf { it >= 0 }
                ?.let { it to 0 }
    }

    val onPrimaryPlay = primaryPlayPosition?.let { (playlistIndex, episodeIndex) ->
        {
            val selectedEpisode = playlists[playlistIndex].playlist.episodeList[episodeIndex]
            val canonicalPlaylist = detail.playLists[playlistIndex]
            openPlayback(
                context = context,
                animeName = displayAnimeName,
                detail = detail,
                playlist = canonicalPlaylist,
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
    ) {
        DetailBackdrop(
            imageUrl = activeBackdropImageUrl,
            accent = activeBackdropAccent
        )
        Crossfade(
            targetState = activeBackdropAnime,
            animationSpec = tween(durationMillis = 240),
            label = "detail-related-preview"
        ) { relatedAnime ->
            if (relatedAnime == null) {
                DetailHero(
                    detail = detail,
                    history = history,
                    onPlayClick = onPrimaryPlay,
                    isFavorite = favoriteUiState.isFavorite,
                    favoriteEnabled = !favoriteUiState.isLoading && !favoriteUiState.isUpdating,
                    onFavoriteClick = { viewModel.toggleFavorite(detail) },
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    accent = detailAccent,
                    height = heroHeight,
                    modifier = Modifier
                        .focusRequester(focusRequesters.hero)
                        .onFocusChanged { state ->
                            if (state.isFocused || state.hasFocus) {
                                focusedRelatedAnime = null
                                if (hasDetailRows) {
                                    scope.launch { detailListState.animateScrollToItem(0) }
                                }
                            }
                        }
                )
            } else {
                RelatedPreviewHero(
                    anime = relatedAnime,
                    imageUrl = activeBackdropImageUrl,
                    accent = activeBackdropAccent,
                    height = heroHeight,
                    descriptionMaxLines = relatedBrowsePolicy.descriptionMaxLines
                )
            }
        }
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = heroHeight),
            contentPadding = PaddingValues(bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                count = playlists.size,
                key = { "playlist-${playlists[it].id}-$reverseEpisodes" }
            ) { playlistIndex ->
                val playlist = playlists[playlistIndex].playlist
                val initialIndex = focusedEpisodeIndexes.getOrElse(playlistIndex) { 0 }
                    .coerceIn(0, (playlist.episodeList.lastIndex).coerceAtLeast(0))
                EpisodeSection(
                    title = localizedText(playlist.name.toDisplayLineName()),
                    episodes = playlist.episodeList,
                    initiallyFocusedIndex = initialIndex,
                    currentEpisodeId = history?.episodeId,
                    showOrderControl = playlistIndex == 0,
                    reverseEpisodes = reverseEpisodes,
                    orderFocusRequester = focusRequesters.order,
                    modifier = Modifier.focusRequester(focusRequesters.playlists[playlistIndex]),
                    restoreFocusRequester = restoreEpisodeFocusRequester,
                    restoreFocusEpisodeIndex = restoreEpisodePosition
                        .takeIf { it.first == playlistIndex }
                        ?.second
                        ?: -1,
                    onToggleOrder = {
                        focusedEpisodeIndexes = MutableList(playlists.size) { 0 }
                        reverseEpisodes = !reverseEpisodes
                        scope.launch {
                            delay(90)
                            runCatching { focusRequesters.order?.requestFocus() }
                        }
                    },
                    onNavigateUp = {
                        scope.launch {
                            if (playlistIndex > 0) {
                                detailListState.scrollToItem(playlistIndex - 1)
                                delay(40)
                                runCatching {
                                    focusRequesters.playlists[playlistIndex - 1].requestFocus()
                                }
                            } else {
                                detailListState.scrollToItem(0)
                                val primaryFocusResult = runCatching {
                                    primaryActionFocusRequester.requestFocus()
                                }
                                if (primaryFocusResult.isFailure) {
                                    runCatching { focusRequesters.hero.requestFocus() }
                                }
                            }
                        }
                    },
                    onNavigateDown = when {
                        playlistIndex < playlists.lastIndex -> {
                            {
                                scope.launch {
                                    detailListState.scrollToItem(playlistIndex + 1)
                                    delay(40)
                                    runCatching {
                                        focusRequesters.playlists[playlistIndex + 1].requestFocus()
                                    }
                                }
                            }
                        }

                        focusRequesters.related != null -> {
                            {
                                scope.launch {
                                    detailListState.scrollToItem(playlists.size)
                                    delay(40)
                                    runCatching { focusRequesters.related.requestFocus() }
                                }
                            }
                        }

                        else -> null
                    },
                    onEpisodeFocused = { episodeIndex, _ ->
                        focusedRelatedAnime = null
                        focusedEpisodeIndexes = focusedEpisodeIndexes.toMutableList().also {
                            it[playlistIndex] = episodeIndex
                        }
                    },
                    onEpisodeClick = { episodeIndex, _ ->
                        val selectedEpisode = playlist.episodeList[episodeIndex]
                        val canonicalPlaylist = detail.playLists[playlistIndex]
                        restoreEpisodePosition = playlistIndex to episodeIndex
                        openPlayback(
                            context = context,
                            animeName = displayAnimeName,
                            detail = detail,
                            playlist = canonicalPlaylist,
                            episodeIndex = EpisodePlaybackSequencePolicy.indexOfEpisode(
                                canonicalPlaylist.episodeList,
                                selectedEpisode.episodeId
                            ).coerceAtLeast(0),
                            sourceId = viewModel.sourceId
                        )
                    }
                )
            }

            if (detail.otherAnimeList.isNotEmpty()) {
                item(key = "related") {
                    RelatedAnimeSection(
                        videos = detail.otherAnimeList,
                        sourceId = viewModel.sourceId,
                        previewEnabled = previewEnabled,
                        onNavigateUp = {
                            focusedRelatedAnime = null
                            scope.launch {
                                if (playlists.isNotEmpty()) {
                                    detailListState.scrollToItem(playlists.lastIndex)
                                    delay(40)
                                    runCatching { focusRequesters.playlists.last().requestFocus() }
                                } else {
                                    detailListState.scrollToItem(0)
                                    val primaryFocusResult = runCatching {
                                        primaryActionFocusRequester.requestFocus()
                                    }
                                    if (primaryFocusResult.isFailure) {
                                        runCatching { focusRequesters.hero.requestFocus() }
                                    }
                                }
                            }
                        },
                        onVideoFocused = { focusedRelatedAnime = it },
                        modifier = Modifier.focusRequester(focusRequesters.related!!)
                    )
                }
            }
        }
    }

    LaunchedEffect(detail.animeId) {
        if (hasDetailRows) detailListState.scrollToItem(0)
        delay(80)
        runCatching { focusRequesters.hero.requestFocus() }
        delay(90)
        if (hasDetailRows) detailListState.scrollToItem(0)
    }
    LaunchedEffect(resumeFocusSignal) {
        if (restoreEpisodePosition.first >= 0 && restoreEpisodePosition.second >= 0) {
            delay(140)
            runCatching { restoreEpisodeFocusRequester.requestFocus() }
            restoreEpisodePosition = -1 to -1
        }
    }
    LaunchedEffect(
        relatedBrowsePolicy.isBrowsingRelated,
        relatedBrowsePolicy.relatedRowIndex
    ) {
        val relatedRowIndex = relatedBrowsePolicy.relatedRowIndex ?: return@LaunchedEffect
        // Anchor once when entering the row; horizontal selection must not move the parent viewport.
        detailListState.scrollToItem(relatedRowIndex)
    }
}

@Composable
private fun DetailBackdrop(imageUrl: String, accent: Color) {
    val targetBackdrop = remember(imageUrl) {
        imageUrl.takeIf(String::isNotBlank)?.let {
            DetailBackdropState(key = it, imageUrl = it)
        }
    }
    var readyBackdrop by remember { mutableStateOf<DetailBackdropState?>(null) }
    val currentTargetKey by rememberUpdatedState(targetBackdrop?.key)
    val animatedAccent by animateColorAsState(
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
                            animatedAccent.copy(alpha = 0.2f),
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
                        widthPx = 1040,
                        heightPx = 1040
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
            if (state == null) return@AnimatedContent
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = state.imageUrl,
                        widthPx = 1040,
                        heightPx = 1040
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
private fun DetailHero(
    detail: AnimeDetailPageData,
    history: VideoHistoryEntity?,
    onPlayClick: (() -> Unit)?,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    accent: Color,
    height: Dp = DetailHeroHeight,
    modifier: Modifier = Modifier
) {
    val displayTitle = localizedText(detail.animeName)
    val displayDescription = localizedText(detail.description).trim()
    val episodeCount = detail.playLists.maxOfOrNull { it.episodeList.size } ?: 0
    val metadata = compactDetailMetadata(detail.infoList, episodeCount)
    var showDescription by remember { mutableStateOf(false) }

    FocusGroup(modifier) {
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 37.sp,
                        lineHeight = 43.sp,
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
                        maxLines = 3,
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
                            onClick = play,
                            modifier = Modifier
                                .width(142.dp)
                                .focusRequester(primaryActionFocusRequester)
                                .initiallyFocused()
                        )
                    }
                    if (displayDescription.isNotBlank()) {
                        DetailActionButton(
                            label = "完整簡介",
                            icon = Icons.Default.Info,
                            accent = AulamaTvColors.Pink,
                            onClick = { showDescription = true },
                            modifier = Modifier
                                .width(132.dp)
                                .restorableFocus()
                        )
                    }
                    DetailActionButton(
                        label = if (isFavorite) "已收藏" else "收藏",
                        icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        accent = if (isFavorite) AulamaTvColors.Cyan else AulamaTvColors.Amber,
                        enabled = favoriteEnabled,
                        onClick = onFavoriteClick,
                        modifier = Modifier
                            .width(116.dp)
                            .restorableFocus()
                    )
                }
                history?.let {
                    PlaybackProgressLine(history = it, accent = accent)
                }
            }
        }
    }

    if (showDescription) {
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
    anime: AnimeData,
    imageUrl: String,
    accent: Color,
    height: Dp,
    descriptionMaxLines: Int
) {
    val title = localizedText(anime.title)
    val description = localizedText(anime.description).trim()
    val metadata = listOf(anime.year, anime.tags, anime.currentEpisode)
        .map { localizedText(it) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("  •  ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = 44.dp, end = 44.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(8.dp)
        AsyncImage(
            model = rememberPosterImageRequest(imageUrl, widthPx = 360, heightPx = 520),
            contentDescription = title,
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
                text = title,
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
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    maxLines = descriptionMaxLines,
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
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(7.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AulamaTvColors.SurfaceRaised,
            contentColor = AulamaTvColors.TextPrimary,
            focusedContainerColor = accent,
            focusedContentColor = Color(0xFF061014),
            pressedContainerColor = accent.copy(alpha = 0.82f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
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
            model = rememberPosterImageRequest(imageUrl = imageUrl, widthPx = 600, heightPx = 860),
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
    episodes: List<AnimePlayListEpisode>,
    initiallyFocusedIndex: Int,
    currentEpisodeId: String?,
    showOrderControl: Boolean,
    reverseEpisodes: Boolean,
    orderFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    restoreFocusRequester: FocusRequester,
    restoreFocusEpisodeIndex: Int,
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
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = localizedText("${episodes.size} 集"),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = AulamaTvColors.TextSecondary
                )
                if (rangeCount > 1 || (showOrderControl && orderFocusRequester != null)) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (rangeCount > 1) {
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
                    if (showOrderControl && orderFocusRequester != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                if (showOrderControl && orderFocusRequester != null) {
                    OrderControl(
                        reverse = reverseEpisodes,
                        onClick = onToggleOrder,
                        modifier = Modifier
                            .focusRequester(orderFocusRequester)
                            .focusProperties { down = entryEpisodeFocusRequester }
                            .restorableFocus()
                    )
                }
            }
            Spacer(modifier = Modifier.height(7.dp))
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
                            .initiallyFocused()
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = AulamaFocusScale),
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
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
    sourceId: String,
    previewEnabled: Boolean,
    onNavigateUp: () -> Unit,
    onVideoFocused: (AnimeData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        if (!rowFocused || !previewEnabled) {
            dimUnselected = false
            return@LaunchedEffect
        }
        dimUnselected = false
        onVideoFocused(selectedVideo)
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
                    .padding(horizontal = 44.dp),
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

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> {
                                onNavigateUp()
                                true
                            }

                            event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> true
                            event.type == KeyEventType.KeyUp &&
                                (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                                DetailActivity.startActivity(
                                    context = context,
                                    anime = selectedVideo,
                                    sourceId = selectedVideo.sourceId.ifBlank { sourceId }
                                )
                                true
                            }

                            event.key == Key.DirectionCenter || event.key == Key.Enter -> true
                            else -> false
                        }
                    }
                    .initiallyFocused()
                    .focusable()
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
                            val video = videos[detailRelatedLogicalIndex(virtualIndex, videos.size)]
                            "$virtualIndex:${video.sourceId}:${video.id}"
                        }
                    ) { virtualIndex ->
                        val video = videos[detailRelatedLogicalIndex(virtualIndex, videos.size)]
                        val selected = rowFocused && virtualIndex == selectedVirtualIndex
                        val cardAlpha by animateFloatAsState(
                            targetValue = previewCardAlpha(
                                rowFocused = rowFocused,
                                selected = selected,
                                dimUnselected = dimUnselected,
                                previewActive = false,
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

@Composable
private fun RelatedPosterCard(
    anime: AnimeData,
    selected: Boolean,
    selectedAccent: Color,
    modifier: Modifier = Modifier
) {
    val title = localizedText(anime.title)
    val subtitle = localizedText(anime.currentEpisode)
    val posterRequest = rememberPosterImageRequest(anime.imageUrl)
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

private fun openPlayback(
    context: android.content.Context,
    animeName: String,
    detail: AnimeDetailPageData,
    playlist: AnimePlayList,
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
            sourceId = sourceId
        )
    )
}

private data class PlayListWrapper(
    val id: Int,
    val playlist: AnimePlayList
)

private data class DetailPageRowFocusRequesters(
    val hero: FocusRequester,
    val order: FocusRequester?,
    val playlists: List<FocusRequester>,
    val related: FocusRequester?
)
