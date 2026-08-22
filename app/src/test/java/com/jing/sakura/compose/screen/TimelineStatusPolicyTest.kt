package com.jing.sakura.compose.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineStatusPolicyTest {
    @Test
    fun marksBlankAndCompletedAliasesAsCompleted() {
        assertEquals("已完結", timelineDisplayStatus(""))
        assertEquals("已完結", timelineDisplayStatus("已收錄 | 全 12 集"))
        assertEquals("已完結", timelineTimeBadge("全集"))
    }

    @Test
    fun keepsCurrentScheduleAndExtractsTime() {
        assertEquals("07 · 週四24:05後", timelineDisplayStatus("07|週四24:05後", "2026", 2026))
        assertEquals("24:05", timelineTimeBadge("07|週四24:05後", "2026", 2026))
    }

    @Test
    fun treatsOldSeasonOngoingMarkerAsCompleted() {
        assertEquals("已完結", timelineDisplayStatus("更新中", "2025", 2026))
    }

    @Test
    fun treatsEpisodeOnlyStatusWithoutScheduleTimeAsCompleted() {
        assertEquals("已完結", timelineDisplayStatus("07", "2026", 2026))
        assertEquals("已完結", timelineTimeBadge("07", "2026", 2026))
    }

    @Test
    fun keepsCurrentSeasonPendingMarker() {
        assertEquals("即將放送", timelineDisplayStatus("即將放送", "2026", 2026))
        assertEquals("待定", timelineTimeBadge("即將放送", "2026", 2026))
    }
}
