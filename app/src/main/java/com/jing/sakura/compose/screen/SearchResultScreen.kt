@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.*
import com.jing.sakura.compose.common.*
import com.jing.sakura.data.AnimeData
import com.jing.sakura.detail.DetailActivity
import com.jing.sakura.search.SearchResultViewModel

private var skipSearchRemovalConfirmationUntil = 0L

@Composable
fun SearchResultScreen(viewModel: SearchResultViewModel) {
    val context = LocalContext.current
    val pagingItems = viewModel.pager.collectAsLazyPagingItems()
    val refreshState = pagingItems.loadState.refresh
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val busyIds by viewModel.busyIds.collectAsState()
    val favoritesReady by viewModel.favoritesReady.collectAsState()
    val favoriteError by viewModel.favoriteError.collectAsState()
    val firstItem = remember { FocusRequester() }
    var initialFocusSet by remember { mutableStateOf(false) }
    var removal by remember { mutableStateOf<AnimeData?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refreshFavorites() }
    }
    Box(Modifier.fillMaxSize().aulamaTvBackground()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).height(60.dp).background(Color(0xFF141A23), CircleShape)
                    .border(1.dp, AulamaTvColors.Cyan.copy(alpha = .5f), CircleShape).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = AulamaTvColors.TextSecondary)
                    Text(viewModel.keyword, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                }
                SearchCircleButton(Icons.Rounded.Close, localizedText("返回搜尋"), { (context as? Activity)?.finish() })
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(localizedText("搜尋結果"), color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.weight(1f))
                Text(localizedText("${pagingItems.itemCount} 套作品"), color = AulamaTvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (favoriteError.isNotBlank()) Text(localizedText(favoriteError), color = AulamaTvColors.Pink, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(5), state = rememberLazyGridState(),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (refreshState is LoadState.NotLoading && pagingItems.itemCount == 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text(localizedText("未搵到相關作品，試吓其他名稱或關鍵字。"), color = AulamaTvColors.TextSecondary)
                        }
                    }
                }
                items(pagingItems.itemCount, key = pagingItems.itemKey { "${it.sourceId}:${it.id}" }) { index ->
                    val anime = pagingItems[index] ?: return@items
                    val posterFocus = remember(anime.id) { if (index == 0) firstItem else FocusRequester() }
                    val bookmarkFocus = remember(anime.id) { FocusRequester() }
                    var focused by remember(anime.id) { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                            VideoCard(imageUrl = anime.imageUrl, title = "", focusScale = 1f, showScrim = false,
                                modifier = Modifier.fillMaxSize().focusRequester(posterFocus).focusProperties { right = bookmarkFocus },
                                onClick = { DetailActivity.startActivity(context, anime, viewModel.sourceId) })
                            SearchCircleButton(
                                if (anime.id in favoriteIds) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                localizedText(if (anime.id in favoriteIds) "取消收藏 ${anime.title}" else "收藏 ${anime.title}"),
                                onClick = {
                                    if (favoritesReady && anime.id !in busyIds) {
                                        if (anime.id in favoriteIds && SystemClock.elapsedRealtime() >= skipSearchRemovalConfirmationUntil) removal = anime
                                        else viewModel.toggleFavorite(anime)
                                    }
                                }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).focusRequester(bookmarkFocus)
                                    .focusProperties { left = posterFocus }, selected = anime.id in favoriteIds, compact = true)
                        }
                        Spacer(Modifier.height(10.dp))
                        AutoMarqueeText(localizedText(anime.title), color = Color.White,
                            style = MaterialTheme.typography.titleMedium, enabled = focused, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(5.dp))
                        Text(localizedText(listOf(anime.year, anime.currentEpisode).filter { it.isNotBlank() }.joinToString(" · ")),
                            color = AulamaTvColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                if (pagingItems.loadState.append is LoadState.Loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(localizedText("載入更多作品…"), color = AulamaTvColors.TextSecondary)
                }
                if (pagingItems.loadState.append is LoadState.Error) item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = { pagingItems.retry() }) { Text(localizedText("重新載入")) }
                }
            }
        }
        LoadingOverlay(visible = refreshState is LoadState.Loading && pagingItems.itemCount == 0)
        if (refreshState is LoadState.Error) ErrorTip(message = refreshState.error.message ?: "搜尋失敗") { pagingItems.retry() }
    }
    LaunchedEffect(refreshState, pagingItems.itemCount) {
        if (!initialFocusSet && refreshState is LoadState.NotLoading && pagingItems.itemCount > 0) {
            firstItem.safelyRequestFocus("search-result-first-item")
            initialFocusSet = true
        }
    }
    removal?.let { anime ->
        var skip by remember(anime.id) { mutableStateOf(false) }
        val cancelFocus = remember { FocusRequester() }
        androidx.compose.material3.AlertDialog(onDismissRequest = { removal = null },
            containerColor = AulamaTvColors.Surface,
            title = { Text(localizedText("確定取消收藏？"), color = Color.White) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(localizedText(anime.title), color = AulamaTvColors.TextSecondary)
                Button(onClick = { skip = !skip }) {
                    Icon(if (skip) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(localizedText("未來 5 分鐘不再顯示"))
                }
            } },
            confirmButton = { Button(onClick = {
                if (skip) skipSearchRemovalConfirmationUntil = SystemClock.elapsedRealtime() + 300_000
                viewModel.toggleFavorite(anime); removal = null
            }) { Text(localizedText("取消收藏")) } },
            dismissButton = { Button(onClick = { removal = null }, modifier = Modifier.focusRequester(cancelFocus)) { Text(localizedText("保留收藏")) } })
        LaunchedEffect(anime.id) { cancelFocus.safelyRequestFocus() }
    }
}
