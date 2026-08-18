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

@Composable
fun SearchScreen(viewModel: SearchViewModel) {

    val context = LocalContext.current
    val historyFocusRequester = remember { FocusRequester() }
    val searchHistory = viewModel.searchHistoryPager.collectAsLazyPagingItems()
    val hasSearchHistory = searchHistory.loadState.refresh is LoadState.NotLoading &&
        searchHistory.itemCount > 0
    val onSearch = { keyword: String ->
        if (keyword.isNotBlank()) {
            keyword.trim().let {
                viewModel.saveHistory(it)
                SearchResultActivity.startActivity(context, it, viewModel.sourceId)
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        AulamaPageHeader(
            title = stringResource(R.string.button_search),
            subtitle = localizedText("支援模糊字詞、別名、繁簡名稱及日文原名")
        )
        InputKeywordRow(
            onSearch = onSearch,
            historyFocusRequester = historyFocusRequester.takeIf { hasSearchHistory }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 36.dp, vertical = 18.dp),
            horizontalArrangement = spacedBy(22.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AulamaCardShape)
                    .background(AulamaTvColors.Surface)
                    .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                AulamaSectionHeader(
                    title = stringResource(R.string.search_history),
                    modifier = Modifier.padding(horizontal = 0.dp),
                    accent = AulamaTvColors.Amber,
                    contentPadding = PaddingValues(vertical = 6.dp)
                )
                Text(
                    text = localizedText("按 OK 再搜尋 · 長按可刪除"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AulamaTvColors.TextSecondary,
                    modifier = Modifier.padding(
                        start = SearchSectionContentInset,
                        bottom = 8.dp
                    )
                )
                if (hasSearchHistory) {
                    SearchHistoryColumn(
                        pagingItems = searchHistory,
                        viewModel = viewModel,
                        firstItemFocusRequester = historyFocusRequester,
                        onKeywordClick = onSearch
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = localizedText("暫時未有搜尋記錄"),
                            style = MaterialTheme.typography.titleMedium,
                            color = AulamaTvColors.TextSecondary
                        )
                    }
                }
            }

            val serverUrl = WebServerContext.serverUrl.collectAsState().value
            if (serverUrl.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(270.dp)
                        .clip(AulamaCardShape)
                        .background(AulamaTvColors.Surface)
                        .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = spacedBy(8.dp)
                ) {
                    AulamaSectionHeader(
                        title = stringResource(R.string.search_mobile_input),
                        modifier = Modifier.padding(horizontal = 0.dp),
                        accent = AulamaTvColors.Pink,
                        contentPadding = PaddingValues(vertical = 6.dp)
                    )
                    Text(
                        text = localizedText("掃描後可用手機輸入搜尋內容"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AulamaTvColors.TextSecondary,
                        modifier = Modifier.padding(start = SearchSectionContentInset)
                    )
                    val img = remember(serverUrl) {
                        val bitMatrix =
                            QRCodeWriter().encode(serverUrl, BarcodeFormat.QR_CODE, 512, 512)
                        val bitmap = Bitmap.createBitmap(
                            bitMatrix.width,
                            bitMatrix.height,
                            Bitmap.Config.RGB_565
                        )

                        for (x in 0 until bitMatrix.width) {
                            for (y in 0 until bitMatrix.height) {
                                bitmap.setPixel(
                                    x,
                                    y,
                                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                                )
                            }
                        }
                        bitmap
                    }

                    AsyncImage(
                        model = img,
                        contentDescription = stringResource(R.string.search_mobile_input),
                        modifier = Modifier
                            .padding(start = SearchSectionContentInset, top = 6.dp)
                            .size(184.dp)
                            .background(androidx.compose.ui.graphics.Color.White, AulamaCardShape)
                            .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                            .padding(10.dp)
                    )
                }
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AulamaIconButton(
            icon = if (sttState.isSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = stringResource(R.string.speak_search_keyword),
            onClick = {
                if (sttState.isSpeaking) {
                    speechToTextParser.stopListening()
                } else {
                    if (permissionState.status.isGranted) {
                        speechToTextParser.startListening()
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                }
            },
            modifier = Modifier
                .focusRequester(speechFocusRequester)
                .focusProperties {
                    right = inputFocusRequester
                    historyFocusRequester?.let { down = it }
                },
            accent = if (sttState.isSpeaking) AulamaTvColors.Pink else AulamaTvColors.Cyan
        )
        Spacer(modifier = Modifier.width(20.dp))
        CustomTextField(
            value = inputKeyword,
            onValueChange = { inputKeyword = it },
            onSubmit = { onSearch(inputKeyword.trim()) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(inputFocusRequester)
                .focusProperties {
                    left = speechFocusRequester
                    right = searchButtonFocusRequester
                    historyFocusRequester?.let { down = it }
                },
            downFocusRequester = historyFocusRequester,
            placeholder = {
                if (sttState.isSpeaking) {
                    Text(text = stringResource(R.string.speak_search_keyword))
                } else {
                    Text(text = stringResource(R.string.input_search_keyword))
                }
            }
        )
        Spacer(modifier = Modifier.width(20.dp))

        AulamaIconButton(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.button_search),
            onClick = {
                onSearch(inputKeyword.trim())
            },
            enabled = inputKeyword.isNotBlank(),
            modifier = Modifier
                .focusRequester(searchButtonFocusRequester)
                .focusProperties {
                    left = inputFocusRequester
                    historyFocusRequester?.let { down = it }
                },
            accent = AulamaTvColors.Green
        )
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

    val listState = rememberLazyListState()
    FocusGroup {
        LazyColumn(
            state = listState,
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
            }, verticalArrangement = spacedBy(10.dp)
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
