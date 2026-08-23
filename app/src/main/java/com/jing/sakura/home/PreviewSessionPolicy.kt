package com.jing.sakura.home

internal const val PREVIEW_DIM_DELAY_MS = 2_000L
internal const val PREVIEW_START_AFTER_DIM_DELAY_MS = 5_000L

internal fun shouldStartPreview(
    scheduledSession: Int,
    currentSession: Int,
    isScreenResumed: Boolean,
    hasFocusedContent: Boolean,
    previewEnabled: Boolean
): Boolean = previewEnabled &&
    scheduledSession == currentSession &&
    isScreenResumed &&
    hasFocusedContent

internal fun isHomePreviewPlaybackActive(
    previewEnabled: Boolean,
    isScreenResumed: Boolean,
    hasFocusedRow: Boolean,
    previewArmed: Boolean,
    firstFrameReady: Boolean,
    readyAnimeId: String?,
    readySourceId: String?,
    focusedAnimeId: String?,
    focusedSourceId: String?
): Boolean = isPreviewPlaybackActive(
    previewEnabled = previewEnabled,
    isScreenResumed = isScreenResumed,
    hasFocusedContent = hasFocusedRow,
    previewArmed = previewArmed,
    firstFrameReady = firstFrameReady,
    readyAnimeId = readyAnimeId,
    readySourceId = readySourceId,
    focusedAnimeId = focusedAnimeId,
    focusedSourceId = focusedSourceId
)

internal fun isPreviewPlaybackActive(
    previewEnabled: Boolean,
    isScreenResumed: Boolean,
    hasFocusedContent: Boolean,
    previewArmed: Boolean,
    firstFrameReady: Boolean,
    readyAnimeId: String?,
    readySourceId: String?,
    focusedAnimeId: String?,
    focusedSourceId: String?
): Boolean = previewEnabled &&
    isScreenResumed &&
    hasFocusedContent &&
    previewArmed &&
    firstFrameReady &&
    !readyAnimeId.isNullOrBlank() &&
    !readySourceId.isNullOrBlank() &&
    readyAnimeId == focusedAnimeId &&
    readySourceId == focusedSourceId

internal fun shouldRetryPreviewLoad(
    failedKey: String?,
    currentKey: String?,
    retryCount: Int,
    maxRetryCount: Int = 1
): Boolean = !failedKey.isNullOrBlank() &&
    failedKey == currentKey &&
    retryCount < maxRetryCount

internal fun previewCardAlpha(
    rowFocused: Boolean,
    selected: Boolean,
    dimUnselected: Boolean,
    previewActive: Boolean,
    previewEnabled: Boolean
): Float = when {
    !rowFocused || selected || !previewEnabled -> 1f
    previewActive -> 0.10f
    dimUnselected -> 0.28f
    else -> 1f
}
