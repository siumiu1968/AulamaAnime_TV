package com.jing.sakura.player

internal enum class CenterKeyAction {
    NONE,
    SHORT_PRESS,
    START_BOOST,
    STOP_BOOST
}

/** Separates a normal OK click from a deliberate temporary 2x hold. */
internal class PlaybackCenterKeyController(
    private val longPressThresholdMs: Long = DEFAULT_LONG_PRESS_THRESHOLD_MS
) {
    private var downAtMs: Long? = null
    private var boosting = false

    fun onKeyDown(eventTimeMs: Long, repeatCount: Int): CenterKeyAction {
        val startedAt = downAtMs
        if (startedAt == null) {
            downAtMs = eventTimeMs
            return CenterKeyAction.NONE
        }
        return if (
            !boosting &&
            repeatCount > 0 &&
            eventTimeMs - startedAt >= longPressThresholdMs
        ) {
            boosting = true
            CenterKeyAction.START_BOOST
        } else {
            CenterKeyAction.NONE
        }
    }

    fun onLongPressTimeout(nowMs: Long): CenterKeyAction {
        val startedAt = downAtMs ?: return CenterKeyAction.NONE
        if (boosting || nowMs - startedAt < longPressThresholdMs) return CenterKeyAction.NONE
        boosting = true
        return CenterKeyAction.START_BOOST
    }

    fun onKeyUp(): CenterKeyAction {
        if (downAtMs == null) return CenterKeyAction.NONE
        downAtMs = null
        return if (boosting) {
            boosting = false
            CenterKeyAction.STOP_BOOST
        } else {
            CenterKeyAction.SHORT_PRESS
        }
    }

    fun cancel(): CenterKeyAction {
        downAtMs = null
        return if (boosting) {
            boosting = false
            CenterKeyAction.STOP_BOOST
        } else {
            CenterKeyAction.NONE
        }
    }

    companion object {
        const val DEFAULT_LONG_PRESS_THRESHOLD_MS = 500L
    }
}
