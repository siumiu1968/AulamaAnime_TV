package com.jing.sakura.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.localizedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SegmentEditorDialog(
    animeName: String,
    target: SegmentEditorTarget,
    durationMs: Long,
    currentPosition: () -> Long,
    playing: () -> Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    load: suspend () -> SegmentEditorSnapshot,
    save: suspend (SegmentDraft, Boolean) -> SegmentEditorSnapshot,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<SegmentEditorSnapshot?>(null) }
    var draft by remember { mutableStateOf(SegmentDraft()) }
    var baseline by remember { mutableStateOf(SegmentDraft()) }
    var allProviders by remember { mutableStateOf(true) }
    var intro by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var stepIndex by remember { mutableIntStateOf(1) }
    var position by remember { mutableLongStateOf(currentPosition()) }
    var isPlaying by remember { mutableStateOf(playing()) }
    val initialFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val keepFocus = remember { FocusRequester() }
    val steps = listOf(100L, 1_000L, 5_000L)
    val kind = if (intro) "intro" else "outro"
    val kindLabel = if (intro) "片頭" else "片尾"
    val markers = if (intro) listOf(SegmentMarker.INTRO_START, SegmentMarker.INTRO_END)
        else listOf(SegmentMarker.OUTRO_START, SegmentMarker.OUTRO_END)
    val dirty = draft != baseline

    fun replace(next: SegmentDraft) { draft = next; baseline = next; message = "" }
    fun changeScope() {
        allProviders = !allProviders
        snapshot?.let { replace(if (allProviders) it.all else it.current) }
    }
    fun close() { if (!saving) { if (dirty) confirmation = "close" else onClose() } }

    LaunchedEffect(loadGeneration) {
        loading = true; message = ""
        try {
            val result = load()
            check(result.canEdit) { "只有最高管理員可以調節片頭片尾" }
            snapshot = result
            replace(if (allProviders) result.all else result.current)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            snapshot = null; message = error.message ?: "未能載入片頭片尾，請重試"
        } finally { loading = false }
    }
    LaunchedEffect(Unit) {
        while (true) { position = currentPosition(); isPlaying = playing(); delay(250) }
    }
    LaunchedEffect(loading, snapshot, intro) {
        if (!loading && snapshot != null) initialFocus.requestFocus() else cancelFocus.requestFocus()
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnClickOutside = false
    )) {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (event.type == KeyEventType.KeyUp) onPlayPause()
                true
            } else false
        }, contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.padding(16.dp).widthIn(max = 470.dp).fillMaxHeight()
                .background(AulamaTvColors.Surface, RoundedCornerShape(18.dp)).padding(22.dp)) {
                Text(localizedText("片頭片尾"), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(localizedText(animeName), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = AulamaTvColors.TextSecondary, fontSize = 14.sp)
                Text(localizedText("${target.episodeLabel} · ${target.providerLabel}"),
                    color = AulamaTvColors.Cyan, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(localizedText("目前 ${segmentTimeLabel(position)} / ${segmentTimeLabel(durationMs.takeIf { it > 0 })}"),
                        fontSize = 17.sp, color = AulamaTvColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorButton("−5秒", { onSeek((currentPosition() - 5_000).coerceAtLeast(0)) }, Modifier.weight(1f))
                        EditorButton(if (isPlaying) "暫停" else "播放", onPlayPause, Modifier.weight(1f))
                        EditorButton("+5秒", { onSeek((currentPosition() + 5_000).coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)) }, Modifier.weight(1f))
                    }
                    if (loading) {
                        Text(localizedText("載入現有設定中…"), color = AulamaTvColors.TextSecondary)
                    } else if (snapshot == null) {
                        Text(localizedText(message), color = MaterialTheme.colorScheme.error)
                        EditorButton("重試", { loadGeneration++ })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EditorButton("片頭", { intro = true }, Modifier.weight(1f), selected = intro)
                            EditorButton("片尾", { intro = false }, Modifier.weight(1f), selected = !intro)
                        }
                        Text(localizedText("← → 微調時間 · OK 填入目前位置"), fontSize = 13.sp,
                            color = AulamaTvColors.TextSecondary)
                        markers.forEachIndexed { index, marker ->
                            EditorButton("${if (index == 0) "開始" else "結束"}  ${segmentTimeLabel(draft[marker])}",
                                { draft = draft.set(marker, currentPosition()); message = "" },
                                Modifier.fillMaxWidth().testTag(marker.key)
                                    .then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier)
                                    .onPreviewKeyEvent { event ->
                                        if (!saving && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                                            if (event.type == KeyEventType.KeyDown) {
                                                val delta = steps[stepIndex] * if (event.key == Key.DirectionLeft) -1 else 1
                                                draft = draft.adjusted(marker, delta, durationMs); message = ""
                                            }
                                            true
                                        } else false
                                    }, enabled = !saving)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EditorButton("步幅 ${listOf("0.1秒", "1秒", "5秒")[stepIndex]}",
                                { stepIndex = (stepIndex + 1) % steps.size }, Modifier.weight(1f), enabled = !saving)
                            EditorButton("本集沒有$kindLabel", { draft = draft.clear(kind); message = "已標示，按儲存套用" },
                                Modifier.weight(1.3f), enabled = !saving, selected = kind in draft.cleared)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            markers.forEachIndexed { index, marker ->
                                EditorButton(if (index == 0) "預覽開始" else "預覽結束", {
                                    draft[marker]?.let { onSeek((it - 2_000).coerceAtLeast(0)); if (!playing()) onPlayPause() }
                                }, Modifier.weight(1f), enabled = draft[marker] != null && !saving)
                            }
                        }
                        Text(localizedText("儲存範圍"), fontSize = 14.sp, color = AulamaTvColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EditorButton("全部線路", {
                                if (!allProviders) { if (dirty) confirmation = "scope" else changeScope() }
                            }, Modifier.weight(1f), selected = allProviders, enabled = !saving)
                            EditorButton("目前線路", {
                                if (allProviders) { if (dirty) confirmation = "scope" else changeScope() }
                            }, Modifier.weight(1f), selected = !allProviders, enabled = !saving)
                        }
                    }
                }
                if (snapshot != null && message.isNotBlank()) {
                    Text(localizedText(message), fontSize = 14.sp, color = AulamaTvColors.TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorButton(if (saving) "儲存中…" else if (allProviders) "儲存至全部線路" else "儲存至目前線路", {
                        val invalid = draft.error(durationMs)
                        if (invalid != null) { message = invalid }
                        else scope.launch {
                            saving = true; message = ""
                            try {
                                val saved = save(draft, allProviders)
                                snapshot = saved
                                val next = (if (allProviders) saved.all else saved.current).copy(cleared = draft.cleared)
                                replace(next); message = if (allProviders) "已儲存至全部線路" else "已儲存至${target.providerLabel}"
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                message = error.message ?: "未能完成儲存，請重試"
                            } finally { saving = false }
                        }
                    }, Modifier.weight(2f), enabled = snapshot != null && !loading && !saving)
                    EditorButton("關閉", ::close, Modifier.weight(1f).focusRequester(cancelFocus), enabled = !saving)
                }
            }
        }
        if (confirmation != null) {
            AlertDialog(onDismissRequest = { confirmation = null },
                title = { Text(localizedText("未儲存嘅變更")) },
                text = { Text(localizedText(if (confirmation == "scope") "切換範圍會放棄目前修改。" else "離開會放棄目前修改。")) },
                confirmButton = { TextButton(onClick = {
                    val action = confirmation; confirmation = null
                    if (action == "scope") changeScope() else onClose()
                }) { Text(localizedText("放棄修改")) } },
                dismissButton = { TextButton(onClick = { confirmation = null }, modifier = Modifier.focusRequester(keepFocus)) {
                    Text(localizedText("繼續調節"))
                } })
            LaunchedEffect(confirmation) { keepFocus.requestFocus() }
        }
    }
}

@Composable
private fun EditorButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false) {
    AulamaActionButton(label = if (selected) "✓ $label" else label, onClick = onClick,
        modifier = modifier, enabled = enabled, contentHeight = 44.dp, labelFontSize = 15.sp,
        labelLineHeight = 19.sp, centerLabel = true)
}
