package com.jing.sakura.compose.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberPosterImageRequest
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.CategoryGroupWrapper
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.repo.VideoCategoryGroup
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Composable
fun AnimeCategoryScreen(viewModel: CategoryViewModel) {
    val categoriesResource = viewModel.categories.collectAsState().value
    com.jing.sakura.compose.common.LoadingOverlay(
        visible = categoriesResource is Resource.Loading
    )
    if (categoriesResource is Resource.Loading) {
        return
    }
    if (categoriesResource is Resource.Error) {
        com.jing.sakura.compose.common.ErrorTip(message = categoriesResource.message) {
            viewModel.loadCategories()
        }
        return
    }

    val categoryGroups =
        (categoriesResource as Resource.Success<List<CategoryGroupWrapper>>).data
            .filter { it.group.categories.isNotEmpty() }
    val selectedValues by viewModel.userSelectedCategories.collectAsState()
    val appliedValues by viewModel.selectedCategories.collectAsState()
    val context = LocalContext.current
    val repository = remember {
        GlobalContext.get().get<WebPageRepository>()
    }
    val pageSize = remember(repository, viewModel.sourceId) {
        repository.requireAnimationSource(viewModel.sourceId).pageSize.coerceAtLeast(10)
    }
    val appliedFilterSnapshot = remember(appliedValues, categoryGroups) {
        orderedDiscoverFilters(
            categoryKeys = categoryGroups.map { it.group.key },
            selectedValues = appliedValues
        )
    }
    val defaultFilterSnapshot = remember(categoryGroups) {
        orderedDiscoverFilters(
            categoryKeys = categoryGroups.map { it.group.key },
            selectedValues = categoryGroups.associate { it.group.key to it.group.defaultValue }
        )
    }
    val pagerFlow = remember(
        repository,
        viewModel.sourceId,
        appliedFilterSnapshot,
        defaultFilterSnapshot,
        pageSize
    ) {
        Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize,
                prefetchDistance = 10,
                enablePlaceholders = false
            )
        ) {
            DiscoverCatalogPagingSource(
                selectedFilters = appliedFilterSnapshot,
                defaultFilters = defaultFilterSnapshot,
                loader = DiscoverPageLoader { filters, page ->
                    repository.queryByCategory(
                        categories = filters,
                        page = page,
                        sourceId = viewModel.sourceId
                    )
                }
            )
        }.flow
    }
    val pagingItems = pagerFlow.collectAsLazyPagingItems()

    DiscoverGrid(
        pagingItems = pagingItems,
        categoryGroups = categoryGroups,
        selectedValues = selectedValues,
        filterIdentity = appliedFilterSnapshot.entries.joinToString("|") { "${it.key}=${it.value}" },
        hasPendingChanges = selectedValues != appliedValues,
        onSelect = viewModel::onUserSelect,
        onApply = { viewModel.applyUserSelectedCategories() },
        onOpen = { anime ->
            DetailActivity.startActivity(
                context = context,
                animeId = anime.id,
                sourceId = viewModel.sourceId
            )
        }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverGrid(
    pagingItems: LazyPagingItems<AnimeData>,
    categoryGroups: List<CategoryGroupWrapper>,
    selectedValues: Map<String, String>,
    filterIdentity: String,
    hasPendingChanges: Boolean,
    onSelect: (key: String, value: String) -> Unit,
    onApply: () -> Unit,
    onOpen: (AnimeData) -> Unit
) {
    val gridState = rememberLazyGridState()
    val firstCardFocusRequester = remember { FocusRequester() }
    val applyFocusRequester = remember { FocusRequester() }
    val groupKeys = categoryGroups.map { it.group.key }
    val groupFocusRequesters = remember(groupKeys) {
        List(groupKeys.size) { FocusRequester() }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var focusZone by remember { mutableStateOf(DiscoverFocusZone.TOP_NAVIGATION) }
    var initialFocusRequested by remember { mutableStateOf(false) }
    var focusedArtworkUrl by remember { mutableStateOf("") }
    var focusedArtworkTitle by remember { mutableStateOf("") }
    val focusPreviewEvents = remember {
        MutableSharedFlow<AnimeData>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val prefetchedPosterUrls = remember { hashSetOf<String>() }
    val reducedMotion = rememberReducedMotion()
    val initialArtworkUrl = pagingItems.itemSnapshotList.items.firstOrNull()?.imageUrl.orEmpty()
    val initialArtworkTitle = pagingItems.itemSnapshotList.items.firstOrNull()?.title.orEmpty()
    val extractedAccent = rememberArtworkAccent(
        imageUrl = focusedArtworkUrl.ifBlank { initialArtworkUrl },
        enabled = focusedArtworkUrl.isNotBlank() || initialArtworkUrl.isNotBlank()
    )
    val accent = extractedAccent

    LaunchedEffect(focusPreviewEvents, reducedMotion) {
        focusPreviewEvents.collectLatest { anime ->
            if (!reducedMotion) delay(120)
            focusedArtworkUrl = anime.imageUrl
            focusedArtworkTitle = anime.title
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        DiscoverBackdrop(
            imageUrl = focusedArtworkUrl.ifBlank { initialArtworkUrl },
            accent = accent,
            reducedMotion = reducedMotion
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 36.dp,
                end = 36.dp,
                top = 18.dp,
                bottom = 36.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(
                key = "discover-filter-panel",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                DiscoverFilterPanel(
                    groups = categoryGroups,
                    selectedValues = selectedValues,
                    groupFocusRequesters = groupFocusRequesters,
                    applyFocusRequester = applyFocusRequester,
                    firstCardFocusRequester = firstCardFocusRequester,
                    hasPendingChanges = hasPendingChanges,
                    accent = accent,
                    highlightedTitle = focusedArtworkTitle.ifBlank { initialArtworkTitle },
                    onSelect = onSelect,
                    onApply = onApply
                )
            }

            if (pagingItems.itemCount == 0) {
                item(
                    key = "discover-status",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val refreshState = pagingItems.loadState.refresh) {
                            is LoadState.Loading -> CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = accent,
                                strokeWidth = 3.dp
                            )

                            is LoadState.Error -> {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(
                                        R.string.error_category_load_template,
                                        refreshState.error.message ?: refreshState.error.toString()
                                    ),
                                    color = AulamaTvColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(14.dp))
                                AulamaActionButton(
                                    label = "重新載入",
                                    onClick = { pagingItems.retry() },
                                    accent = accent
                                )
                            }

                            is LoadState.NotLoading -> Text(
                                text = androidx.compose.ui.res.stringResource(R.string.grid_no_data_tip),
                                color = AulamaTvColors.TextSecondary,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { "${it.sourceId}:${it.id}" }
            ) { index ->
                val anime = pagingItems[index] ?: return@items
                var focused by remember(anime.id) { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (focused) AulamaFocusScale else 1f,
                    animationSpec = tween(durationMillis = if (reducedMotion) 0 else 110),
                    label = "discover-card-scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(140f / 190f),
                    contentAlignment = Alignment.Center
                ) {
                    VideoCard(
                        modifier = Modifier
                            .fillMaxSize(1f / AulamaFocusScale)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .focusProperties {
                                if (index < 5) up = applyFocusRequester
                            }
                            .run {
                                if (index == 0) focusRequester(firstCardFocusRequester) else this
                            }
                            .onFocusChanged { state ->
                                focused = state.isFocused || state.hasFocus
                                if (focused) {
                                    focusZone = DiscoverFocusZone.RESULTS
                                    focusPreviewEvents.tryEmit(anime)
                                }
                            },
                        imageUrl = anime.imageUrl,
                        title = anime.title,
                        subTitle = anime.currentEpisode,
                        focusScale = 1f,
                        onClick = { onOpen(anime) }
                    )
                }
            }
        }
    }

    LaunchedEffect(pagingItems.itemCount) {
        if (!initialFocusRequested) {
            initialFocusRequested = true
            val requester = groupFocusRequesters.firstOrNull() ?: applyFocusRequester
            kotlin.runCatching { requester.requestFocus() }
        }
    }
    LaunchedEffect(filterIdentity) {
        focusedArtworkUrl = ""
        focusedArtworkTitle = ""
        focusZone = DiscoverFocusZone.TOP_NAVIGATION
        gridState.scrollToItem(0)
        kotlin.runCatching {
            (groupFocusRequesters.firstOrNull() ?: applyFocusRequester).requestFocus()
        }
    }
    LaunchedEffect(gridState, pagingItems.itemCount, filterIdentity) {
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
                .map { it.index - 1 }
                .filter { it >= 0 }
            visible.firstOrNull() to visible.lastOrNull()
        }
            .distinctUntilChanged()
            .collectLatest { (first, last) ->
                val firstIndex = first ?: return@collectLatest
                val lastIndex = last ?: return@collectLatest
                discoverPosterPrefetchIndices(
                    firstVisibleIndex = firstIndex,
                    lastVisibleIndex = lastIndex,
                    itemCount = pagingItems.itemSnapshotList.items.size
                ).forEach { index ->
                    val anime = pagingItems.itemSnapshotList.items.getOrNull(index)
                        ?: return@forEach
                    if (anime.imageUrl.isBlank()) return@forEach
                    if (!prefetchedPosterUrls.add(anime.imageUrl)) return@forEach
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(anime.imageUrl)
                            .size(360, 520)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
    }
    BackHandler(enabled = focusZone == DiscoverFocusZone.RESULTS) {
        focusZone = DiscoverFocusZone.TOP_NAVIGATION
        coroutineScope.launch {
            gridState.animateScrollToItem(0)
            kotlin.runCatching {
                (groupFocusRequesters.firstOrNull() ?: applyFocusRequester).requestFocus()
            }
        }
    }
}

@Composable
private fun DiscoverBackdrop(
    imageUrl: String,
    accent: Color,
    reducedMotion: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = imageUrl,
            transitionSpec = {
                fadeIn(
                    tween(
                        durationMillis = if (reducedMotion) 0 else 180,
                        easing = FastOutSlowInEasing
                    )
                ).togetherWith(
                    fadeOut(
                        tween(
                            durationMillis = if (reducedMotion) 0 else 120,
                            easing = FastOutSlowInEasing
                        )
                    )
                )
            },
            label = "discover-artwork-backdrop"
        ) { artworkUrl ->
            if (artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = rememberPosterImageRequest(
                        imageUrl = artworkUrl,
                        widthPx = 720,
                        heightPx = 1020
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.26f
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

@Composable
private fun DiscoverFilterPanel(
    groups: List<CategoryGroupWrapper>,
    selectedValues: Map<String, String>,
    groupFocusRequesters: List<FocusRequester>,
    applyFocusRequester: FocusRequester,
    firstCardFocusRequester: FocusRequester,
    hasPendingChanges: Boolean,
    accent: Color,
    highlightedTitle: String,
    onSelect: (key: String, value: String) -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_title),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_filter),
                color = accent,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        groups.forEachIndexed { groupIndex, wrapper ->
            val group = wrapper.group
            val selectedIndex = group.categories
                .indexOfFirst { it.value == selectedValues[group.key] }
                .coerceAtLeast(0)
            FilterOptionRow(
                group = group,
                selectedIndex = selectedIndex,
                groupFocusRequester = groupFocusRequesters[groupIndex],
                upRequester = groupFocusRequesters.getOrNull(groupIndex - 1),
                downRequester = groupFocusRequesters.getOrNull(groupIndex + 1)
                    ?: applyFocusRequester,
                accent = accent,
                onSelect = { value -> onSelect(group.key, value) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_results),
                color = AulamaTvColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (highlightedTitle.isNotBlank()) {
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(2.dp)
                        .background(accent.copy(alpha = 0.9f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = localizedText(highlightedTitle),
                    modifier = Modifier.weight(1f),
                    color = AulamaTvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ApplyFilterButton(
                modifier = Modifier
                    .focusRequester(applyFocusRequester)
                    .focusProperties {
                        groupFocusRequesters.lastOrNull()?.let { up = it }
                        down = firstCardFocusRequester
                    },
                accent = accent,
                emphasized = hasPendingChanges,
                onClick = onApply
            )
        }
    }
}

@Composable
private fun FilterOptionRow(
    group: VideoCategoryGroup.NormalCategoryGroup,
    selectedIndex: Int,
    groupFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedText(group.name),
            modifier = Modifier.width(88.dp),
            color = AulamaTvColors.TextSecondary,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.width(4.dp))
        LazyRow(
            state = rowState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                items = group.categories,
                key = { _, category -> category.value }
            ) { index, category ->
                FilterOption(
                    text = localizedText(category.label),
                    selected = index == selectedIndex,
                    accent = accent,
                    modifier = Modifier
                        .run {
                            if (index == selectedIndex) {
                                focusRequester(groupFocusRequester)
                            } else {
                                this
                            }
                        }
                        .focusProperties {
                            upRequester?.let { up = it }
                            down = downRequester
                        },
                    onClick = { onSelect(category.value) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "discover-filter-scale"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            focused -> accent.copy(alpha = if (selected) 0.24f else 0.17f)
            selected -> accent.copy(alpha = 0.13f)
            else -> AulamaTvColors.Surface.copy(alpha = 0.26f)
        },
        animationSpec = tween(durationMillis = 170),
        label = "discover-filter-container"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> accent.copy(alpha = 0.95f)
            selected -> accent.copy(alpha = 0.58f)
            else -> AulamaTvColors.Outline.copy(alpha = 0.34f)
        },
        animationSpec = tween(durationMillis = 170),
        label = "discover-filter-border"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            focused -> AulamaTvColors.TextPrimary
            selected -> accent
            else -> AulamaTvColors.TextSecondary
        },
        animationSpec = tween(durationMillis = 170),
        label = "discover-filter-text"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 54.dp, max = 164.dp)
            .height(34.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(1.dp, borderColor),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(1.5.dp, borderColor),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor,
            contentColor = textColor,
            focusedContentColor = AulamaTvColors.TextPrimary
        )
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ApplyFilterButton(
    accent: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(durationMillis = 170),
        label = "discover-apply-scale"
    )
    val contentColor = if (focused) Color(0xFF05070C) else AulamaTvColors.TextPrimary

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(124.dp)
            .height(42.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (emphasized) 1.5.dp else 1.dp,
                    if (emphasized) accent else AulamaTvColors.Outline
                ),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.5.dp, accent),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (emphasized) accent.copy(alpha = 0.16f)
            else AulamaTvColors.Surface.copy(alpha = 0.72f),
            focusedContainerColor = accent,
            contentColor = contentColor,
            focusedContentColor = Color(0xFF05070C)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_apply),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
