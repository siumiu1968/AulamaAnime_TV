package com.jing.sakura.home

import com.jing.sakura.data.AnimeData

internal fun withCurrentScheduleStatus(items: List<AnimeData>, schedule: List<AnimeData>): List<AnimeData> {
    val latest = schedule.associateBy { it.sourceId to it.id.removePrefix("cycani:") }
    return items.map { item ->
        val status = latest[item.sourceId to item.id.removePrefix("cycani:")]?.currentEpisode
        if (status.isNullOrBlank()) item else item.copy(currentEpisode = status)
    }
}
