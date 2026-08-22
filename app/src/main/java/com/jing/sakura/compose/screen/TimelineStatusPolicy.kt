package com.jing.sakura.compose.screen

import java.util.Calendar

private val completedTimelineTokens = setOf(
    "已收錄",
    "已收录",
    "已完結",
    "已完结",
    "完結",
    "完结",
    "全集"
)
private val ongoingTimelineStatuses = setOf("更新中", "連載中", "连载中")
private val pendingTimelineStatuses = setOf("即將放送", "即将放送", "尚未播出", "待定")
private val timelineTokenSeparator = Regex("[|·・,，/／\\s]+")
private val timelineTimePattern = Regex("(\\d{1,2}):(\\d{2})")

internal fun timelineDisplayStatus(
    value: String,
    year: String = "",
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
): String {
    val raw = value.trim()
    val completed = raw.isBlank() || raw
        .split(timelineTokenSeparator)
        .any(completedTimelineTokens::contains)
    if (completed) return "已完結"

    val parsedYear = Regex("(?:19|20)\\d{2}").find(year)?.value?.toIntOrNull()
    if (parsedYear != null && parsedYear < currentYear && raw in ongoingTimelineStatuses) {
        return "已完結"
    }
    if (
        timelineTimePattern.find(raw) == null &&
        raw !in ongoingTimelineStatuses &&
        raw !in pendingTimelineStatuses
    ) {
        return "已完結"
    }
    return raw.replace(Regex("\\s*\\|\\s*"), " · ")
}

internal fun timelineTimeBadge(
    value: String,
    year: String = "",
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
): String {
    val display = timelineDisplayStatus(value, year, currentYear)
    if (display == "已完結") return display
    val match = timelineTimePattern.find(value) ?: return "待定"
    return "${match.groupValues[1].padStart(2, '0')}:${match.groupValues[2]}"
}
