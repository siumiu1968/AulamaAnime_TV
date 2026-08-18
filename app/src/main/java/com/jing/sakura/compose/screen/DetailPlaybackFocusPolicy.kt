package com.jing.sakura.compose.screen

internal enum class DetailPlaybackFocusTarget { HERO, LINE, EPISODE }

internal object DetailPlaybackFocusPolicy {
    fun downFromHero(hasEpisodes: Boolean): DetailPlaybackFocusTarget =
        if (hasEpisodes) DetailPlaybackFocusTarget.LINE else DetailPlaybackFocusTarget.HERO

    fun upFromEpisode(): DetailPlaybackFocusTarget = DetailPlaybackFocusTarget.LINE

    fun downFromHeader(): DetailPlaybackFocusTarget = DetailPlaybackFocusTarget.EPISODE
}
