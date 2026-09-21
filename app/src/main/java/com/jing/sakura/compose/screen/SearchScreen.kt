package com.jing.sakura.compose.screen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.speech.SpeechRecognizer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Close
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.R
import com.jing.sakura.compose.common.ConfirmDeleteDialog
import com.jing.sakura.compose.common.CustomTextField
import com.jing.sakura.compose.common.FocusGroup
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaFocusScale
import com.jing.sakura.compose.common.AulamaIconButton
import com.jing.sakura.compose.common.AulamaPageHeader
import com.jing.sakura.compose.common.AulamaSectionHeader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.aulamaTvBackground
import com.jing.sakura.compose.common.SpeechToTextParser
import com.jing.sakura.compose.common.customClick
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.safelyRequestFocus
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.http.WebsocketOperation
import com.jing.sakura.http.WebsocketResult
import com.jing.sakura.http.WsMessageHandler
import com.jing.sakura.room.SearchHistoryEntity
import com.jing.sakura.search.SearchResultActivity
import com.jing.sakura.search.SearchViewModel
import com.jing.sakura.compose.theme.SakuraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SearchSectionContentInset = 14.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val context = LocalContext.current
    val historyFocusRequester = remember { FocusRequester() }
    val searchHistory = viewModel.searchHistoryPager.collectAsLazyPagingItems()
    val hasSearchHistory = searchHistory.itemCount > 0
    val onSearch = { keyword: String ->
        keyword.trim().takeIf { it.isNotBlank() }?.let {
            viewModel.saveHistory(it)
            SearchResultActivity.startActivity(context, it, viewModel.sourceId)
        }
        Unit
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { viewModel.refreshHistory(); delay(10_000) }
        }
    }
    Column(Modifier.fillMaxSize().aulamaTvBackground().padding(horizontal = 42.dp, vertical = 28.dp)) {
        InputKeywordRow(onSearch, historyFocusRequester.takeIf { hasSearchHistory })
        Spacer(Modifier.height(30.dp))
        Text(localizedText("想睇邊套？"), color = AulamaTvColors.TextPrimary,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = spacedBy(32.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.size(19.dp), tint = AulamaTvColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(localizedText("最近搜尋"), style = MaterialTheme.typography.titleMedium, color = AulamaTvColors.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(localizedText("長按記錄可刪除"), style = MaterialTheme.typography.labelMedium, color = AulamaTvColors.TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                if (hasSearchHistory) SearchHistoryColumn(searchHistory, viewModel, historyFocusRequester, onSearch)
                else Text(localizedText("搜尋過嘅作品會顯示喺呢度"), color = AulamaTvColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 24.dp))
            }
            val serverUrl by WebServerContext.serverUrl.collectAsState()
            Column(Modifier.width(220.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Color(0x99171D27))
                .border(1.dp, AulamaTvColors.Outline, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Smartphone, null, Modifier.size(24.dp), tint = AulamaTvColors.Cyan)
                Spacer(Modifier.height(10.dp))
                Text(localizedText("用手機輸入"), color = AulamaTvColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(18.dp))
                val img by androidx.compose.runtime.produceState<Bitmap?>(null, serverUrl) {
                    value = if(serverUrl.isBlank()) null else kotlinx.coroutines.withContext(Dispatchers.Default) {
                        val matrix = QRCodeWriter().encode(serverUrl, BarcodeFormat.QR_CODE, 384, 384)
                        val pixels = IntArray(matrix.width * matrix.height) { index -> if(matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
                        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
                    }
                }
                Box(Modifier.size(136.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                    if(img != null) AsyncImage(img, localizedText("手機輸入 QR code"), modifier = Modifier.fillMaxSize().padding(6.dp))
                    else androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(localizedText("手機同電視連接同一個 Wi-Fi，掃碼即可輸入。"), color = AulamaTvColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}


@OptIn(
    ExperimentalPermissionsApi::class,
    ExperimentalTvMaterial3Api::class
)
@Composable
fun InputKeywordRow(
    onSearch: (String) -> Unit,
    historyFocusRequester: FocusRequester?
) {
    val speechFocusRequester = remember {
        FocusRequester()
    }
    val context = LocalContext.current
    val speechToTextParser = remember {
        SpeechToTextParser(context)
    }
    val permissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO) {
        if (it) {
            speechToTextParser.startListening()
        }
    }
    var inputKeyword by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val handler = WsMessageHandler { operation, content ->
            coroutineScope.launch(Dispatchers.Main) {
                when (operation) {
                    WebsocketOperation.INPUT -> inputKeyword = content
                    WebsocketOperation.SUBMIT -> onSearch(inputKeyword)
                    else -> {}
                }
            }
            WebsocketResult.Success
        }
        WebServerContext.registerMessageHandler(handler)

        onDispose {
            WebServerContext.unregisterMessageHandler(handler)
        }

    }

    val searchButtonFocusRequester = remember {
        FocusRequester()
    }
    val inputFocusRequester = remember {
        FocusRequester()
    }
    val sttState by speechToTextParser.state.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacedBy(12.dp)) {
        Row(Modifier.weight(1f).height(60.dp)
            .border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(AulamaTvColors.Cyan.copy(alpha=.65f), AulamaTvColors.Pink.copy(alpha=.5f))), androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color(0xFF141A23), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal=14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement=spacedBy(12.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(24.dp), tint=AulamaTvColors.TextSecondary)
            CustomTextField(value=inputKeyword, onValueChange={inputKeyword=it}, onSubmit={onSearch(inputKeyword.trim())},
                modifier=Modifier.weight(1f).focusRequester(inputFocusRequester).focusProperties {
                    right=searchButtonFocusRequester; historyFocusRequester?.let { down=it }
                }, downFocusRequester=historyFocusRequester, flat=true,
                placeholder={Text(localizedText(if(sttState.isSpeaking) "聆聽中…" else "搜尋動畫、別名或關鍵字"),color=AulamaTvColors.TextSecondary)})
            if(inputKeyword.isNotEmpty()) SearchCircleButton(Icons.Rounded.Close, localizedText("清除搜尋"), {inputKeyword="";inputFocusRequester.safelyRequestFocus()})
            SearchCircleButton(if(sttState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic, localizedText("語音搜尋"), {
                if(sttState.isSpeaking) speechToTextParser.stopListening()
                else if(permissionState.status.isGranted) speechToTextParser.startListening()
                else permissionState.launchPermissionRequest()
            }, Modifier.focusRequester(speechFocusRequester))
            SearchCircleButton(Icons.Default.Search, localizedText("搜尋"), {onSearch(inputKeyword.trim())},
                Modifier.focusRequester(searchButtonFocusRequester).focusProperties {left=inputFocusRequester;historyFocusRequester?.let { down=it }})
        }
        SearchCircleButton(Icons.Rounded.Close, localizedText("關閉搜尋"), {(context as? android.app.Activity)?.finish()})
    }

    LaunchedEffect(sttState) {
        if (!sttState.isSpeaking) {
            val text = sttState.text.trim()
            if (text.isNotEmpty()) {
                inputKeyword = text
                searchButtonFocusRequester.safelyRequestFocus()
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(140)
        inputFocusRequester.safelyRequestFocus("search-keyword-input")
    }
    LaunchedEffect(sttState.isSpeaking) {
        if (sttState.isSpeaking) {
            delay(200)
            speechFocusRequester.requestFocus()
        }
    }
}

@Composable
fun SearchHistoryColumn(
    pagingItems: LazyPagingItems<SearchHistoryEntity>,
    viewModel: SearchViewModel,
    firstItemFocusRequester: FocusRequester,
    onKeywordClick: (keyword: String) -> Unit = {}
) {
    if (pagingItems.loadState.refresh !is LoadState.NotLoading || pagingItems.itemCount == 0) {
        return
    }
    var confirmDeleteHistory by remember {
        mutableStateOf<SearchHistoryEntity?>(null)
    }
    val coroutineScope = rememberCoroutineScope()

    FocusGroup {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            contentPadding = PaddingValues(
                horizontal = 0.dp,
                vertical = 4.dp
            ),
            content = {
                items(pagingItems.itemCount, key = { pagingItems[it]?.keyword ?: it }) { kwIndex ->
                    val history = pagingItems[kwIndex] ?: return@items
                    Keyword(text = history.keyword,
                        modifier = Modifier
                            .run {
                                if (kwIndex == 0) {
                                    initiallyFocused().focusRequester(firstItemFocusRequester)
                                } else {
                                    restorableFocus()
                                }
                            }
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .padding(vertical = 1.dp),
                        onLongClick = {
                            confirmDeleteHistory = history
                        }) {
                        onKeywordClick(history.keyword)
                    }
                }
            }, verticalArrangement = spacedBy(12.dp), horizontalArrangement = spacedBy(12.dp)
        )
    }

    val history = confirmDeleteHistory ?: return

    val confirmText = String.format(
        stringResource(
            id = R.string.confirm_delete_template
        ), confirmDeleteHistory?.keyword
    )
    ConfirmDeleteDialog(
        text = confirmText,
        onDeleteClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteHistory(history.keyword)
                pagingItems.refresh()
            }
        },
        onDeleteAllClick = {
            confirmDeleteHistory = null
            coroutineScope.launch {
                viewModel.deleteAllHistory()
                pagingItems.refresh()
            }
        },
        onCancel = {
            confirmDeleteHistory = null
        }
    )
}


@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Keyword(
    text: String,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var focused by remember {
        mutableStateOf(false)
    }
    KeywordSurface(
        text = text,
        focused = focused,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
            }
            .customClick(onClick, onLongClick)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun KeywordSurface(
    text: String,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) AulamaFocusScale else 1f,
        animationSpec = tween(140),
        label = "search-history-focus-scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AulamaCardShape)
            .background(
                if (focused) androidx.compose.ui.graphics.Color(0xFF173A40)
                else AulamaTvColors.SurfaceRaised
            )
            .then(
                if (focused) {
                    Modifier.border(2.dp, AulamaTvColors.FocusBorder, AulamaCardShape)
                } else {
                    Modifier.border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                }
            )
            .focusable()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SearchSectionContentInset,
                vertical = 8.dp
            ),
            horizontalArrangement = spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (focused) AulamaTvColors.Cyan else AulamaTvColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = AulamaTvColors.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focused) Modifier.basicMarquee() else Modifier),
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(
    name = "搜尋記錄 - 預設與焦點",
    widthDp = 460,
    heightDp = 150,
    showBackground = true,
    backgroundColor = 0xFF05070C
)
@Composable
private fun SearchHistoryKeywordPreview() {
    SakuraTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = spacedBy(10.dp)
        ) {
            KeywordSurface(
                text = "葬送的芙莉蓮",
                focused = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            )
            KeywordSurface(
                text = "進擊的巨人",
                focused = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            )
        }
    }
}
