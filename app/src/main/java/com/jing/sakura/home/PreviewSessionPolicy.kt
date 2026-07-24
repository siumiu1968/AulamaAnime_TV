package com.jing.sakura.home

internal fun shouldStartPreview(
    scheduledSession: Int,
    currentSession: Int,
    isScreenResumed: Boolean,
    hasFocusedContent: Boolean
): Boolean = scheduledSession == currentSession && isScreenResumed && hasFocusedContent
