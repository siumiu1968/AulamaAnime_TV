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
