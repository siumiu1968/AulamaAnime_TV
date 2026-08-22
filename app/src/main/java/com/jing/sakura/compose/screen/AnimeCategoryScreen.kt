package com.jing.sakura.compose.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
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
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.VideoCard
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.rememberDpadRepeatGate
import com.jing.sakura.compose.common.rememberArtworkAccent
import com.jing.sakura.compose.common.rememberReducedMotion
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.Resource
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.home.CategoryGroupWrapper
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.repo.VideoCategoryGroup
import com.jing.sakura.repo.WebPageRepository
import kotlinx.coroutines.flow.collectLatest
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
    val pagerFlow = remember(
        repository,
        viewModel.sourceId,
        appliedFilterSnapshot,
        pageSize
    ) {
        Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize.coerceAtMost(15),
                prefetchDistance = 4,
                enablePlaceholders = false
            )
        ) {
            DiscoverCatalogPagingSource(
                selectedFilters = appliedFilterSnapshot,
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
                anime = anime,
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
    val firstCardFocusRequester = remember(filterIdentity) { FocusRequester() }
    val filterButtonFocusRequester = remember { FocusRequester() }
    val applyFocusRequester = remember { FocusRequester() }
    val groupKeys = categoryGroups.map { it.group.key }
    val groupFocusRequesters = remember(groupKeys) {
        List(groupKeys.size) { FocusRequester() }
    }
    val coroutineScope = rememberCoroutineScope()
    val consumeRapidVerticalDpad = rememberDpadRepeatGate(minIntervalMs = 170L)
    var focusZone by remember { mutableStateOf(DiscoverFocusZone.TOP_NAVIGATION) }
    var filtersExpanded by remember { mutableStateOf(false) }
    var focusedFilterIdentity by remember { mutableStateOf<String?>(null) }
    var focusedArtworkUrl by remember { mutableStateOf("") }
    val focusPreviewEvents = remember {
        MutableSharedFlow<AnimeData>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    val reducedMotion = rememberReducedMotion()
    val filterPanelVisibility = remember { MutableTransitionState(false) }
    val filterMotionDurationMs = if (reducedMotion) 0 else 120
    val initialArtworkUrl = pagingItems.itemSnapshotList.items.firstOrNull()?.imageUrl.orEmpty()
    val extractedAccent = rememberArtworkAccent(
        imageUrl = focusedArtworkUrl.ifBlank { initialArtworkUrl },
        enabled = focusedArtworkUrl.isNotBlank() || initialArtworkUrl.isNotBlank()
    )
    val accent = extractedAccent

    LaunchedEffect(focusPreviewEvents, reducedMotion) {
        focusPreviewEvents.collectLatest { anime ->
            if (!reducedMotion) delay(650)
            focusedArtworkUrl = anime.imageUrl
        }
    }
    LaunchedEffect(filtersExpanded) {
        filterPanelVisibility.targetState = filtersExpanded
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        DiscoverColorBackdrop(accent = accent)

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent(consumeRapidVerticalDpad),
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
                key = "discover-header",
                contentType = "discover-header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                DiscoverHeader(
                    filterButtonFocusRequester = filterButtonFocusRequester,
                    expandedDownRequester = groupFocusRequesters.firstOrNull()
                        ?: applyFocusRequester,
                    filtersExpanded = filtersExpanded,
                    accent = accent,
                    onFocused = { focusZone = DiscoverFocusZone.TOP_NAVIGATION },
                    onToggleFilters = {
                        val expand = !filtersExpanded
                        filtersExpanded = expand
                        focusZone = DiscoverFocusZone.TOP_NAVIGATION
                        coroutineScope.launch {
                            gridState.scrollToItem(0)
                            delay(if (expand && !reducedMotion) 140 else 48)
                            val requester = if (expand) {
                                groupFocusRequesters.firstOrNull() ?: applyFocusRequester
                            } else {
                                filterButtonFocusRequester
                            }
                            kotlin.runCatching { requester.requestFocus() }
                        }
                    }
                )
            }

            if (
                filtersExpanded ||
                filterPanelVisibility.currentState ||
                filterPanelVisibility.targetState
            ) {
                item(
                    key = "discover-filter-panel",
                    contentType = "discover-filter-panel",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    AnimatedVisibility(
                        visibleState = filterPanelVisibility,
                        enter = fadeIn(
                            animationSpec = tween(filterMotionDurationMs),
                            initialAlpha = 0.72f
                        ),
                        exit = fadeOut(
                            animationSpec = tween(filterMotionDurationMs.coerceAtMost(90)),
                            targetAlpha = 0.72f
                        )
                    ) {
                        DiscoverFilterPanel(
                            groups = categoryGroups,
                            selectedValues = selectedValues,
                            groupFocusRequesters = groupFocusRequesters,
                            filterButtonFocusRequester = filterButtonFocusRequester,
                            applyFocusRequester = applyFocusRequester,
                            accent = accent,
                            onSelect = onSelect,
                            onFocused = { focusZone = DiscoverFocusZone.TOP_NAVIGATION }
                        )
                    }
                }
            }

            item(
                key = "discover-results-header",
                contentType = "discover-results-header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                DiscoverResultsHeader(
                    filtersExpanded = filtersExpanded,
                    motionDurationMs = filterMotionDurationMs,
                    applyFocusRequester = applyFocusRequester,
                    lastGroupFocusRequester = groupFocusRequesters.lastOrNull(),
                    hasPendingChanges = hasPendingChanges,
                    accent = accent,
                    onFocused = { focusZone = DiscoverFocusZone.TOP_NAVIGATION },
                    onApply = {
                        onApply()
                        filtersExpanded = false
                        focusedFilterIdentity = null
                        focusZone = DiscoverFocusZone.RESULTS
                    }
                )
            }

            if (pagingItems.itemCount == 0) {
                item(
                    key = "discover-status",
                    contentType = "discover-status",
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
                key = pagingItems.itemKey { "${it.sourceId}:${it.id}" },
                contentType = { "discover-poster" }
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
                            .fillMaxSize()
                            .run {
                                if (index == 0) focusRequester(firstCardFocusRequester) else this
                            },
                        imageUrl = anime.imageUrl,
                        title = anime.title,
                        subTitle = anime.currentEpisode,
                        focusScale = 1f,
                        posterWidthPx = 340,
                        posterHeightPx = 480,
                        onFocused = {
                            if (index == 0) focusedFilterIdentity = filterIdentity
                            focusZone = DiscoverFocusZone.RESULTS
                            focusPreviewEvents.tryEmit(anime)
                        },
                        onClick = { onOpen(anime) }
                    )
                }
            }
        }
    }

    LaunchedEffect(filterIdentity) {
        focusedArtworkUrl = ""
        filtersExpanded = false
        focusedFilterIdentity = null
    }
    LaunchedEffect(
        pagingItems.itemCount,
        pagingItems.loadState.refresh,
        filtersExpanded,
        filterIdentity,
        focusedFilterIdentity
    ) {
        if (
            !filtersExpanded &&
            pagingItems.itemCount > 0 &&
            pagingItems.loadState.refresh is LoadState.NotLoading &&
            focusedFilterIdentity != filterIdentity
        ) {
            gridState.scrollToItem(0)
            if (!reducedMotion && filterPanelVisibility.currentState) delay(120)
            repeat(5) { attempt ->
                delay(if (attempt == 0) 64 else 48)
                kotlin.runCatching { firstCardFocusRequester.requestFocus() }
                if (focusedFilterIdentity == filterIdentity) {
                    return@LaunchedEffect
                }
            }
        } else if (
            !filtersExpanded &&
            pagingItems.itemCount == 0 &&
            pagingItems.loadState.refresh !is LoadState.Loading &&
            focusedFilterIdentity != filterIdentity
        ) {
            gridState.scrollToItem(0)
            delay(48)
            kotlin.runCatching { filterButtonFocusRequester.requestFocus() }
        }
    }
    BackHandler(enabled = filtersExpanded || focusZone == DiscoverFocusZone.RESULTS) {
        focusZone = DiscoverFocusZone.TOP_NAVIGATION
        coroutineScope.launch {
            filtersExpanded = false
            gridState.scrollToItem(0)
            delay(if (reducedMotion) 48 else 120)
            kotlin.runCatching { filterButtonFocusRequester.requestFocus() }
        }
    }
}

@Composable
private fun DiscoverHeader(
    filterButtonFocusRequester: FocusRequester,
    expandedDownRequester: FocusRequester,
    filtersExpanded: Boolean,
    accent: Color,
    onFocused: () -> Unit,
    onToggleFilters: () -> Unit
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
        FilterToggleButton(
            modifier = Modifier
                .focusRequester(filterButtonFocusRequester)
                .focusProperties {
                    if (filtersExpanded) down = expandedDownRequester
                },
            expanded = filtersExpanded,
            accent = accent,
            onFocused = onFocused,
            onClick = onToggleFilters
        )
    }
}

@Composable
private fun DiscoverResultsHeader(
    filtersExpanded: Boolean,
    motionDurationMs: Int,
    applyFocusRequester: FocusRequester,
    lastGroupFocusRequester: FocusRequester?,
    hasPendingChanges: Boolean,
    accent: Color,
    onFocused: () -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
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
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(
            visible = filtersExpanded,
            enter = fadeIn(
                animationSpec = tween(motionDurationMs),
                initialAlpha = 0.72f
            ),
            exit = fadeOut(
                animationSpec = tween(motionDurationMs.coerceAtMost(90)),
                targetAlpha = 0.72f
            )
        ) {
            ApplyFilterButton(
                modifier = Modifier
                    .focusRequester(applyFocusRequester)
                    .focusProperties {
                        lastGroupFocusRequester?.let { up = it }
                    },
                accent = accent,
                emphasized = hasPendingChanges,
                onFocused = onFocused,
                onClick = onApply
            )
        }
    }
}

@Composable
private fun DiscoverColorBackdrop(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val radial = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.24f),
                        accent.copy(alpha = 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.30f),
                    radius = size.width * 0.92f
                )
                val vertical = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.66f to AulamaTvColors.Background.copy(alpha = 0.22f),
                        1f to AulamaTvColors.Background.copy(alpha = 0.78f)
                    )
                )
                onDrawBehind {
                    drawRect(brush = radial)
                    drawRect(brush = vertical)
                }
            }
    )
}

@Composable
private fun DiscoverFilterPanel(
    groups: List<CategoryGroupWrapper>,
    selectedValues: Map<String, String>,
    groupFocusRequesters: List<FocusRequester>,
    filterButtonFocusRequester: FocusRequester,
    applyFocusRequester: FocusRequester,
    accent: Color,
    onSelect: (key: String, value: String) -> Unit,
    onFocused: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        groups.forEachIndexed { groupIndex, wrapper ->
            val group = wrapper.group
            val selectedIndex = group.categories
                .indexOfFirst { it.value == selectedValues[group.key] }
                .coerceAtLeast(0)
            FilterOptionRow(
                group = group,
                selectedIndex = selectedIndex,
                groupFocusRequester = groupFocusRequesters[groupIndex],
                upRequester = groupFocusRequesters.getOrNull(groupIndex - 1)
                    ?: filterButtonFocusRequester,
                downRequester = groupFocusRequesters.getOrNull(groupIndex + 1)
                    ?: applyFocusRequester,
                accent = accent,
                onFocused = onFocused,
                onSelect = { value -> onSelect(group.key, value) }
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
    onFocused: () -> Unit,
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
                    onFocused = onFocused,
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
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor = when {
        focused -> accent.copy(alpha = if (selected) 0.24f else 0.17f)
        selected -> accent.copy(alpha = 0.13f)
        else -> AulamaTvColors.Surface.copy(alpha = 0.26f)
    }
    val borderColor = when {
        focused -> accent.copy(alpha = 0.95f)
        selected -> accent.copy(alpha = 0.58f)
        else -> AulamaTvColors.Outline.copy(alpha = 0.34f)
    }
    val textColor = when {
        focused -> AulamaTvColors.TextPrimary
        selected -> accent
        else -> AulamaTvColors.TextSecondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 54.dp, max = 164.dp)
            .height(34.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (focused) onFocused()
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
private fun FilterToggleButton(
    expanded: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) Color(0xFF05070C) else {
        if (expanded) accent else AulamaTvColors.TextPrimary
    }
    val containerColor = when {
        focused -> accent
        expanded -> accent.copy(alpha = 0.16f)
        else -> AulamaTvColors.Surface.copy(alpha = 0.50f)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(112.dp)
            .height(40.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (focused) onFocused()
            },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = AulamaCardShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (expanded) 1.5.dp else 1.dp,
                    if (expanded) accent else AulamaTvColors.Outline.copy(alpha = 0.68f)
                ),
                shape = AulamaCardShape
            ),
            focusedBorder = Border(
                BorderStroke(2.5.dp, accent),
                shape = AulamaCardShape
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = accent,
            contentColor = contentColor,
            focusedContentColor = Color(0xFF05070C)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.category_filter),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ApplyFilterButton(
    accent: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) Color(0xFF05070C) else AulamaTvColors.TextPrimary

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(124.dp)
            .height(42.dp)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (focused) onFocused()
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
