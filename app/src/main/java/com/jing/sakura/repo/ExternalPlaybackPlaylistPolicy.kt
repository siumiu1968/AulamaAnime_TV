package com.jing.sakura.repo

import com.jing.sakura.auth.AulamaPlaybackProvider

internal data class ExternalPlaybackEpisodeSpec(
    val catalogAnimeId: String,
    val index: Int,
    val label: String
)

internal data class ExternalPlaybackPlaylistSpec(
    val providerId: String,
    val displayName: String,
    val episodes: List<ExternalPlaybackEpisodeSpec>
)

internal object ExternalPlaybackPlaylistPolicy {
    private val orderedProviders = listOf(
        "girigiri_cht" to "主線路B（繁中）",
        "girigiri_chs" to "主線路B（簡中）",
        "sakura" to "後備 A",
        "age" to "後備 B"
    )

    fun playlists(
        catalogAnimeId: String,
        providers: List<AulamaPlaybackProvider>,
        canonicalEpisodeLabels: List<String>
    ): List<ExternalPlaybackPlaylistSpec> {
        if (catalogAnimeId.isBlank()) return emptyList()
        val providersById = providers.associateBy { it.id }
        return orderedProviders.mapNotNull { (providerId, displayName) ->
            val provider = providersById[providerId]?.takeIf { it.available }
                ?: return@mapNotNull null
            val episodeCount = provider.episodeCount.takeIf { it > 0 } ?: when {
                providerId.startsWith("girigiri_") -> return@mapNotNull null
                else -> canonicalEpisodeLabels.size
            }
            ExternalPlaybackPlaylistSpec(
                providerId = providerId,
                displayName = displayName,
                episodes = (0 until episodeCount).map { episodeIndex ->
                    ExternalPlaybackEpisodeSpec(
                        catalogAnimeId = catalogAnimeId,
                        index = episodeIndex,
                        label = canonicalEpisodeLabels.getOrNull(episodeIndex)
                            ?: "第${episodeIndex + 1}集"
                    )
                }
            )
        }
    }
}
