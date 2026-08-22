package com.jing.sakura.home

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
): Boolean = previewEnabled &&
    isScreenResumed &&
    hasFocusedRow &&
    previewArmed &&
    firstFrameReady &&
    !readyAnimeId.isNullOrBlank() &&
    !readySourceId.isNullOrBlank() &&
    readyAnimeId == focusedAnimeId &&
    readySourceId == focusedSourceId

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
