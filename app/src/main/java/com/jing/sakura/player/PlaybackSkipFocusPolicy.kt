package com.jing.sakura.player

internal enum class PlaybackSkipFocusZone {
    TRANSPORT,
    PRIMARY_ACTION,
    SECONDARY_ACTION
}

internal enum class PlaybackSkipDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

internal enum class PlaybackSkipFocusAction {
    KEEP_CURRENT,
    ENTER_PRIMARY,
    ENTER_SECONDARY,
    RETURN_TO_TRANSPORT
}

/** Keeps skip visibility independent from explicit D-pad focus navigation. */
internal object PlaybackSkipFocusPolicy {
    fun action(
        zone: PlaybackSkipFocusZone,
        direction: PlaybackSkipDirection,
        actionsVisible: Boolean,
        secondaryVisible: Boolean
    ): PlaybackSkipFocusAction = when (zone) {
        PlaybackSkipFocusZone.TRANSPORT -> {
            if (direction == PlaybackSkipDirection.UP && actionsVisible) {
                PlaybackSkipFocusAction.ENTER_PRIMARY
            } else {
                PlaybackSkipFocusAction.KEEP_CURRENT
            }
        }
        PlaybackSkipFocusZone.PRIMARY_ACTION -> when {
            direction == PlaybackSkipDirection.DOWN -> {
                PlaybackSkipFocusAction.RETURN_TO_TRANSPORT
            }
            direction == PlaybackSkipDirection.RIGHT && secondaryVisible -> {
                PlaybackSkipFocusAction.ENTER_SECONDARY
            }
            else -> PlaybackSkipFocusAction.KEEP_CURRENT
        }
        PlaybackSkipFocusZone.SECONDARY_ACTION -> when (direction) {
            PlaybackSkipDirection.DOWN -> PlaybackSkipFocusAction.RETURN_TO_TRANSPORT
            PlaybackSkipDirection.LEFT -> PlaybackSkipFocusAction.ENTER_PRIMARY
            else -> PlaybackSkipFocusAction.KEEP_CURRENT
        }
    }
}

internal object PlaybackSkipLayoutPolicy {
    const val FOCUS_SCALE = 1.025f
    const val OVERFLOW_PADDING_DP = 8f

    fun requiredOverflowDp(sizeDp: Float): Float =
        (sizeDp * (FOCUS_SCALE - 1f) / 2f).coerceAtLeast(0f)
}
