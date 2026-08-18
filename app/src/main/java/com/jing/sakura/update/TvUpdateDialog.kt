package com.jing.sakura.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaTvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdateDialog(
    update: TvUpdate,
    downloadState: TvUpdateDownloadState,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    val downloadFocus = remember { FocusRequester() }
    val laterFocus = remember { FocusRequester() }
    val notesScrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 132.dp.toPx() }
    val isBusy = downloadState is TvUpdateDownloadState.Starting ||
        downloadState is TvUpdateDownloadState.Downloading ||
        downloadState is TvUpdateDownloadState.PreparingInstall ||
        downloadState is TvUpdateDownloadState.Installing
    Dialog(
        onDismissRequest = { if (!isBusy) onLater() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isBusy,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 460.dp, max = 620.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = when (event.key) {
                        Key.DirectionUp -> -1
                        Key.DirectionDown -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    val delta = releaseNotesScrollDelta(
                        direction = direction,
                        canScrollBackward = notesScrollState.canScrollBackward,
                        canScrollForward = notesScrollState.canScrollForward,
                        stepPx = scrollStepPx
                    )
                    if (delta != 0f) {
                        scrollScope.launch { notesScrollState.scrollBy(delta) }
                    }
                    delta != 0f
                },
            shape = AulamaCardShape,
            color = AulamaTvColors.SurfaceRaised
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_title, update.version),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = AulamaTvColors.TextPrimary
                )
                ReleaseNotes(
                    notes = update.notes.ifBlank { stringResource(R.string.update_body) },
                    scrollState = notesScrollState
                )
                DownloadProgress(downloadState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AulamaActionButton(
                        label = downloadButtonLabel(downloadState),
                        icon = if (isBusy) null else Icons.Default.Download,
                        onClick = onDownload,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(downloadFocus)
                            .focusProperties { right = laterFocus }
                    )
                    AulamaActionButton(
                        label = stringResource(R.string.update_later),
                        onClick = onLater,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(0.7f)
                            .focusRequester(laterFocus)
                            .focusProperties { left = downloadFocus },
                        accent = AulamaTvColors.Blue
                    )
                }
            }
        }
    }
    LaunchedEffect(update.version, isBusy) {
        if (!isBusy) {
            repeat(8) { attempt ->
                delay(if (attempt == 0) 80 else 100)
                runCatching { downloadFocus.requestFocus() }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReleaseNotes(notes: String, scrollState: ScrollState) {
    val items = remember(notes) { parseTvReleaseNotes(notes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (items.none { it.kind == TvReleaseNoteKind.Heading }) {
            Text(
                text = "今次更新",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AulamaTvColors.TextPrimary
            )
        }
        items.forEach { item ->
            when (item.kind) {
                TvReleaseNoteKind.Heading -> Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AulamaTvColors.TextPrimary
                )

                TvReleaseNoteKind.Bullet -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 9.dp)
                            .size(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = CircleShape,
                            color = AulamaTvColors.Cyan
                        ) {}
                    }
                    Text(
                        text = item.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AulamaTvColors.TextSecondary
                    )
                }

                TvReleaseNoteKind.Paragraph -> Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AulamaTvColors.TextSecondary
                )
            }
        }
    }
}

internal fun releaseNotesScrollDelta(
    direction: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    stepPx: Float
): Float = when {
    direction < 0 && canScrollBackward -> -stepPx
    direction > 0 && canScrollForward -> stepPx
    else -> 0f
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadProgress(state: TvUpdateDownloadState) {
    when (state) {
        TvUpdateDownloadState.Idle -> Unit
        TvUpdateDownloadState.Starting -> ProgressBlock(
            message = "正在準備下載",
            progress = null
        )

        is TvUpdateDownloadState.Downloading -> {
            val sizeLabel = if (state.totalBytes > 0L) {
                "${formatMegabytes(state.downloadedBytes)} / ${formatMegabytes(state.totalBytes)}"
            } else {
                "已下載 ${formatMegabytes(state.downloadedBytes)}"
            }
            ProgressBlock(
                message = state.percent?.let { "正在下載 $it%  ·  $sizeLabel" }
                    ?: "正在下載  ·  $sizeLabel",
                progress = state.percent?.div(100f)
            )
        }

        TvUpdateDownloadState.PreparingInstall -> ProgressBlock(
            message = "下載完成，正在準備安裝",
            progress = 1f
        )

        TvUpdateDownloadState.Installing -> ProgressBlock(
            message = "正在開啟系統安裝程式",
            progress = 1f
        )

        is TvUpdateDownloadState.Failed -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFFFF8A80)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgressBlock(message: String, progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AulamaTvColors.Cyan,
                trackColor = AulamaTvColors.Outline
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = AulamaTvColors.Cyan,
                trackColor = AulamaTvColors.Outline
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = AulamaTvColors.TextSecondary
        )
    }
}

@Composable
private fun downloadButtonLabel(state: TvUpdateDownloadState): String = when (state) {
    TvUpdateDownloadState.Idle -> stringResource(R.string.update_download)
    TvUpdateDownloadState.Starting -> "準備下載"
    is TvUpdateDownloadState.Downloading -> state.percent?.let { "下載中 $it%" } ?: "下載中"
    TvUpdateDownloadState.PreparingInstall -> "準備安裝"
    TvUpdateDownloadState.Installing -> "開啟安裝程式"
    is TvUpdateDownloadState.Failed -> "重新下載"
}

private fun formatMegabytes(bytes: Long): String =
    String.format("%.1f MB", bytes.coerceAtLeast(0L) / 1_048_576.0)
