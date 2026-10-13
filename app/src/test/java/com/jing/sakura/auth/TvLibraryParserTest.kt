package com.jing.sakura.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLibraryParserTest {
    @Test
    fun mapsAllPublicScheduleDaysAndKeepsLatestStatus() {
        val payload = TvLibraryParser.parseSchedule(
            body = """
                {
                  "days": [
                    {"day":1,"items":[{"id":"m1","title":"週一新番","currentEpisode":"07 | 週一23:05後"}]},
                    {"day":4,"items":[{"id":"t1","title":"上季作品","currentEpisode":"已完結"}]}
                  ]
                }
            """.trimIndent(),
            currentDayIndex = 2
        )

        assertEquals(2, payload.current)
        assertEquals(7, payload.timeline.size)
        assertEquals("07 | 週一23:05後", payload.timeline[0].second.single().currentEpisode)
        assertTrue(payload.timeline[1].second.isEmpty())
        assertEquals("已完結", payload.timeline[3].second.single().currentEpisode)
    }

    @Test
    fun mapsPublicTheaterCatalog() {
        val items = TvLibraryParser.parseTheaterItems(
            """{"ok":true,"theaterItems":[{"id":"t1","title":"最新劇場版","poster":"t.jpg"}]}"""
        )

        assertEquals(1, items.size)
        assertEquals("最新劇場版", items.single().title)
    }

    @Test
    fun mapsHomeRecommendationsTodayAndTheater() {
        val payload = TvLibraryParser.parseHome(
            body = """
                {
                  "recommendations": [{"id":"r1","title":"推薦一","poster":"r.jpg"}],
                  "days": [
                    {"day":1,"items":[]},
                    {"day":2,"items":[{"id":"d1","title":"今日一","poster":"d.jpg"}]}
                  ],
                  "theaterItems": [{"id":"t1","title":"劇場一","poster":"t.jpg"}]
                }
            """.trimIndent(),
            weekday = 2
        )

        assertEquals("推薦一", payload.recommendations.single().title)
        assertEquals("今日一", payload.todayUpdates.single().title)
        assertEquals("劇場一", payload.theaterItems.single().title)
        assertEquals("d1", payload.schedule?.timeline?.get(1)?.second?.single()?.id)
    }

    @Test
    fun mapsFavoriteItems() {
        val items = TvLibraryParser.parseFavorites(
            """{"items":[{"id":"f1","title":"收藏一","subtitle":"第 3 集","poster":"f.jpg"}]}"""
        )

        assertEquals(1, items.size)
        assertEquals("收藏一", items.single().title)
        assertEquals("第 3 集", items.single().currentEpisode)
    }

    @Test
    fun mapsFavoriteAddedTimeForStableCompletedOrdering() {
        val item = TvLibraryParser.parseFavorites(
            """{"items":[{"id":"f1","title":"收藏一","addedAt":"2026-08-20T10:00:00Z"}]}"""
        ).single()

        assertEquals(
            CloudTimestamp.parseEpochMs("2026-08-20T10:00:00Z"),
            item.favoriteAddedAtEpochMs
        )
    }

    @Test
    fun mapsHistoryUsingAnimeFields() {
        val items = TvLibraryParser.parseHistory(
            """
                {"items":[{
                  "animeId":"h1",
                  "animeTitle":"續播一",
                  "episodeLabel":"第 8 集",
                  "poster":"/anime/api/image?url=https%3A%2F%2Fimg.example.com%2Fh.jpg",
                  "tags":["日常","校園"]
                }]}
            """.trimIndent()
        )

        assertEquals("h1", items.single().id)
        assertEquals("第 8 集", items.single().currentEpisode)
        assertEquals("日常、校園", items.single().tags)
        assertTrue(items.single().imageUrl.startsWith("https://img.example.com/"))
    }

    @Test
    fun mapsTypedHistoryProgressFromNumbersAndNumericStrings() {
        val item = TvLibraryParser.parseHistoryItems(
            """
                {"items":[{
                  "animeId":"h1",
                  "title":"續播一",
                  "poster":"h.jpg",
                  "episodeId":"ep-8",
                  "episodeLabel":"第 8 集",
                  "episodeIndex":"8",
                  "episodeCount":12.0,
                  "currentTime":"125.5",
                  "duration":1500,
                  "completed":"true",
                  "sourceTypeId":7,
                  "updatedAt":"2026-07-14T10:00:00Z",
                  "episodeProgress": {
                    "6": {"episodeIndex":6},
                    "8": {"episodeIndex":8}
                  }
                }]}
            """.trimIndent()
        ).single()

        assertEquals("h1", item.anime.id)
        assertEquals("h1", item.animeId)
        assertEquals("續播一", item.anime.title)
        assertEquals("h.jpg", item.anime.imageUrl)
        assertEquals("ep-8", item.episodeId)
        assertEquals("第 8 集", item.episodeLabel)
        assertEquals(8, item.episodeIndex)
        assertEquals(12, item.episodeCount)
        assertEquals(125.5, item.currentTimeSeconds, 0.0)
        assertEquals(1500.0, item.durationSeconds, 0.0)
        assertTrue(item.completed)
        assertEquals("7", item.sourceTypeId)
        assertEquals("2026-07-14T10:00:00Z", item.updatedAt)
        assertTrue(item.updatedAtEpochMs > 0L)
        assertEquals(setOf(6, 8), item.viewedEpisodeIndexes)
    }

    @Test
    fun toleratesMissingAndMalformedHistoryProgress() {
        val item = TvLibraryParser.parseHistoryItems(
            """
                {"items":[{
                  "animeId":"h1",
                  "title":"續播一",
                  "episodeIndex":"unknown",
                  "episodeCount":null,
                  "currentTime":"NaN",
                  "duration":{},
                  "completed":"unknown"
                }]}
            """.trimIndent()
        ).single()

        assertEquals("", item.episodeId)
        assertEquals(0, item.episodeIndex)
        assertEquals(0, item.episodeCount)
        assertEquals(0.0, item.currentTimeSeconds, 0.0)
        assertEquals(0.0, item.durationSeconds, 0.0)
        assertEquals(false, item.completed)
        assertEquals("", item.updatedAt)
        assertEquals(0L, item.updatedAtEpochMs)
    }

    @Test
    fun keepsFirstHistoryItemForDuplicateAnimeInServerOrder() {
        val items = TvLibraryParser.parseHistoryItems(
            """
                {"items":[
                  {"animeId":"h1","title":"續播一","episodeId":"new","currentTime":300},
                  {"animeId":"h2","title":"續播二","episodeId":"only","currentTime":200},
                  {"animeId":"h1","title":"續播一","episodeId":"old","currentTime":100}
                ]}
            """.trimIndent()
        )

        assertEquals(listOf("h1", "h2"), items.map { it.anime.id })
        assertEquals("new", items.first().episodeId)
        assertEquals(300.0, items.first().currentTimeSeconds, 0.0)
        assertEquals(items.map(TvHistoryItem::anime), TvLibraryParser.parseHistory(
            """
                {"items":[
                  {"animeId":"h1","title":"續播一","episodeId":"new","currentTime":300},
                  {"animeId":"h2","title":"續播二","episodeId":"only","currentTime":200},
                  {"animeId":"h1","title":"續播一","episodeId":"old","currentTime":100}
                ]}
            """.trimIndent()
        ))
    }

    @Test
    fun sortsHistoryFromMostRecentlyViewedToOldest() {
        val items = TvLibraryParser.parseHistoryItems(
            """
                {"items":[
                  {"animeId":"old","title":"較舊","episodeId":"old-1","updatedAt":"2026-08-20T10:00:00Z"},
                  {"animeId":"new","title":"最近","episodeId":"new-1","updatedAt":"2026-08-22T10:00:00Z"},
                  {"animeId":"middle","title":"中間","episodeId":"middle-1","updatedAt":"2026-08-21T10:00:00Z"}
                ]}
            """.trimIndent()
        )

        assertEquals(listOf("new", "middle", "old"), items.map(TvHistoryItem::animeId))
    }

    @Test
    fun mapsWebsiteDetailRecommendations() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {
                  "ok": true,
                  "item": {
                    "related": [
                      {"id":"same-series","title":"同系列","poster":"related.jpg"}
                    ],
                    "recommendations": [
                      {"id":"for-you","title":"為你推介","poster":"recommended.jpg"}
                    ],
                    "personalizedRecommendations": true
                  }
                }
            """.trimIndent()
        )

        assertEquals("同系列", payload.related.single().title)
        assertEquals("為你推介", payload.recommendations.single().title)
        assertTrue(payload.personalizedRecommendations)
    }

    @Test
    fun mapsAuthenticatedGirigiriOnlyDetailForTvPlayback() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {
                  "ok": true,
                  "item": {
                    "id": "gg:GV27102",
                    "title": "只有 GiriGiri 的動畫",
                    "summary": "繁體中文簡介",
                    "poster": "https://img.example/poster.jpg",
                    "year": "2026",
                    "episodes": [
                      {"label":"第01集"},
                      {"label":"第02集"}
                    ],
                    "providerEpisodeCounts": {
                      "girigiri_cht": 2
                    },
                    "playLists": [
                      {"code":"girigiri_cht","name":"主線路B（繁中）","count":2},
                      {"code":"girigiri_chs","name":"主線路B（簡中）","count":1}
                    ],
                    "info": {"area":"日本","director":"測試導演"}
                  }
                }
            """.trimIndent()
        )

        assertEquals("gg:GV27102", checkNotNull(payload.catalogItem).id)
        assertEquals("繁體中文簡介", payload.catalogItem?.description)
        assertEquals(listOf("第01集", "第02集"), payload.episodeLabels)
        assertEquals(
            mapOf("girigiri_cht" to 2, "girigiri_chs" to 1),
            payload.providerEpisodeCounts
        )
        assertEquals(listOf("地區：日本", "導演：測試導演"), payload.infoList)
    }

    @Test
    fun mapsCurrentCycaniDetailWithTheRealSectionIdsForMainPlayback() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {"ok":true,"item":{
                  "id":"cw:3939",
                  "title":"轉生哥布林有問題嗎？",
                  "year":"2026",
                  "episodes":[
                    {"id":"52401","webVideoId":"3939","webSectionId":"52401","webSourceCode":"cychub","label":"第01集","sourceLine":"CYC_Main"},
                    {"id":"52479","webVideoId":"3939","webSectionId":"52479","webSourceCode":"cychub","label":"第02集","sourceLine":"CYC_Main"},
                    {"id":"52516","webVideoId":"3939","webSectionId":"52516","webSourceCode":"cychub","label":"第03集","sourceLine":"CYC_Main"}
                  ],
                  "playLists":[{"code":"cychub","name":"CYC_Main","count":3}],
                  "providerEpisodeCounts":{"cycani":3,"girigiri_cht":3}
                }}
            """.trimIndent()
        )

        assertEquals("cw:3939", payload.catalogItem?.id)
        assertEquals("2026", payload.catalogItem?.year)
        assertEquals(listOf("第01集", "第02集", "第03集"), payload.episodeLabels)
        val main = payload.cycaniPlayLists.single()
        assertEquals("cychub", main.sourceCode)
        assertEquals("CYC_Main", main.name)
        assertEquals(listOf("52401", "52479", "52516"), main.episodes.map { it.sectionId })
        assertEquals(listOf("第01集", "第02集", "第03集"), main.episodes.map { it.label })
        assertEquals(3, payload.providerEpisodeCounts["girigiri_cht"])
    }

    @Test
    fun groupsCurrentSectionsInPlaylistOrderAndKeepsDistinctSectionsWithIdenticalLabels() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {"ok":true,"item":{
                  "id":"cw:3939","title":"測試動畫",
                  "episodes":[
                    {"webSectionId":"61001","webSourceCode":"alt","label":"第01集","sourceLine":"Alternate"},
                    {"webSectionId":"52401","webSourceCode":"cychub","label":"第01集","sourceLine":"Unordered main name"},
                    {"webSectionId":"52479","webSourceCode":"cychub","label":"第01集","sourceLine":"Unordered main name"},
                    {"webSectionId":"52401","webSourceCode":"cychub","label":"重複資料","sourceLine":"Unordered main name"},
                    {"webSectionId":"61002","webSourceCode":"alt","label":"第02集","sourceLine":"Alternate"}
                  ],
                  "playLists":[
                    {"code":"cychub","name":"CYC_Main","count":2},
                    {"code":"alt","name":"另一主線","count":2}
                  ]
                }}
            """.trimIndent()
        )

        assertEquals(listOf("cychub", "alt"), payload.cycaniPlayLists.map { it.sourceCode })
        assertEquals(listOf("CYC_Main", "另一主線"), payload.cycaniPlayLists.map { it.name })
        assertEquals(listOf("52401", "52479"), payload.cycaniPlayLists.first().episodes.map { it.sectionId })
        assertEquals(listOf("第01集", "第01集"), payload.cycaniPlayLists.first().episodes.map { it.label })
        assertEquals(listOf("61001", "61002"), payload.cycaniPlayLists.last().episodes.map { it.sectionId })
    }

    @Test
    fun excludesProviderPlaceholdersAndMalformedSectionsWithoutLosingBackupEpisodeCounts() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {"ok":true,"item":{
                  "id":"cw:3939","title":"測試動畫",
                  "episodes":[
                    {"id":"52401","webSectionId":52401,"webSourceCode":"cychub","label":"第01集"},
                    {"id":"provider-only-2","label":"第02集"},
                    {"id":"52479","webSourceCode":"cychub","label":"有 ID 但沒有 section ID"},
                    {"webSectionId":"-1","webSourceCode":"cychub","label":"負數 ID"},
                    {"webSectionId":"1.5","webSourceCode":"cychub","label":"小數 ID"},
                    {"webSectionId":"1234567890123","webSourceCode":"cychub","label":"過長 ID"},
                    {"webSectionId":"../52479","webSourceCode":"cychub","label":"非法 ID"},
                    {"webSectionId":{},"webSourceCode":"cychub","label":"錯誤 ID 類型"},
                    {"webSectionId":null,"webSourceCode":"cychub","label":"沒有 ID"},
                    {"webSectionId":"52479","webSourceCode":"cychub","label":{},"title":"第02集"},
                    {"webSectionId":"52516","webSourceCode":"cychub","label":"","title":""},
                    null,
                    "malformed episode"
                  ],
                  "playLists":[{"code":"cychub","name":"CYC_Main","count":2}],
                  "providerEpisodeCounts":{"cycani":2,"girigiri_cht":8,"age":5}
                }}
            """.trimIndent()
        )

        val main = payload.cycaniPlayLists.single()
        assertEquals(listOf("52401", "52479", "52516"), main.episodes.map { it.sectionId })
        assertEquals(listOf("第01集", "第02集", "第3集"), main.episodes.map { it.label })
        assertEquals(8, payload.providerEpisodeCounts["girigiri_cht"])
        assertEquals(5, payload.providerEpisodeCounts["age"])
    }

    @Test
    fun usesSourceLineAndNumberedLabelWhenCurrentSectionMetadataIsMissing() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {"ok":true,"item":{
                  "id":"cw:3939","title":"測試動畫",
                  "episodes":[{"webSectionId":"52401","sourceLine":"CYC_Main"}]
                }}
            """.trimIndent()
        )

        val main = payload.cycaniPlayLists.single()
        assertEquals("cycani", main.sourceCode)
        assertEquals("CYC_Main", main.name)
        assertEquals(listOf(TvCycaniEpisode("52401", "第1集")), main.episodes)
    }

    @Test
    fun providerOnlyDetailHasNoMainCycaniPlaylist() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {"ok":true,"item":{
                  "id":"gg:GV27102","title":"只有後備線路的動畫",
                  "episodes":[{"label":"第01集"},{"label":"第02集"}],
                  "providerEpisodeCounts":{"girigiri_cht":2},
                  "playLists":[{"code":"girigiri_cht","name":"主線路B（繁中）","count":2}]
                }}
            """.trimIndent()
        )

        assertTrue(payload.cycaniPlayLists.isEmpty())
        assertEquals(2, payload.providerEpisodeCounts["girigiri_cht"])
    }

    @Test
    fun parsesCloudTimestampsWithFractionAndTimezone() {
        val utc = CloudTimestamp.parseEpochMs("2026-07-14T10:00:00.125Z")
        val hongKong = CloudTimestamp.parseEpochMs("2026-07-14T18:00:00.125+08:00")

        assertEquals(utc, hongKong)
        assertEquals("2026-07-14T10:00:00.125Z", CloudTimestamp.formatEpochMs(utc))
    }
    @Test
    fun mapsSeasonCompletionForTheEndOfSeasonSheet() {
        val payload = TvLibraryParser.parseAnimeDetail(
            """
                {
                  "ok": true,
                  "item": {
                    "id": "cycani:42",
                    "title": "完結作品",
                    "completion": {
                      "sourceStatus": "全12集",
                      "finished": true,
                      "sourceUpdatedAt": "2026-07-14T10:00:00.000Z",
                      "expectedEpisodes": 12
                    }
                  }
                }
            """.trimIndent()
        )

        assertEquals("全12集", payload.completion.sourceStatus)
        assertTrue(payload.completion.finished)
        assertEquals(12, payload.completion.expectedEpisodes)
        assertEquals(CloudTimestamp.parseEpochMs("2026-07-14T10:00:00.000Z"), payload.completion.sourceUpdatedAtEpochMs)
    }

    @Test
    fun readsTheViewersOwnRatingAcrossIdForms() {
        val body = """
            {"ok":true,"items":[
              {"animeId":"cycani:7","rating":4},
              {"animeId":"9","rating":9}
            ]}
        """.trimIndent()

        assertEquals(4, TvLibraryParser.parseRating(body, "7"))
        assertEquals(null, TvLibraryParser.parseRating(body, "9"))
        assertEquals(null, TvLibraryParser.parseRating(body, "missing"))
    }
}
