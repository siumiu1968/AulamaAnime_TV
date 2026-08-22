package com.jing.sakura.auth

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jing.sakura.data.AnimeData
import com.jing.sakura.data.UpdateTimeLine

object TvLibraryParser {
    fun parseTheaterItems(body: String): List<AnimeData> {
        val root = JsonParser.parseString(body).asJsonObject
        return RecommendationParser.parseItems(root.array("theaterItems"))
    }

    fun parseHome(body: String, weekday: Int): TvHomePayload {
        val root = JsonParser.parseString(body).asJsonObject
        val recommendations = RecommendationParser.parseItems(root.array("recommendations"))
        val theaterItems = RecommendationParser.parseItems(root.array("theaterItems"))
        val todayItems = root.array("days")
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .firstOrNull { it.get("day")?.asInt == weekday }
            ?.let { RecommendationParser.parseItems(it.array("items")) }
            .orEmpty()
        return TvHomePayload(
            recommendations = recommendations,
            todayUpdates = todayItems,
            theaterItems = theaterItems
        )
    }

    fun parseSchedule(body: String, currentDayIndex: Int): UpdateTimeLine {
        val root = JsonParser.parseString(body).asJsonObject
        val itemsByDay = root.array("days")
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .associateBy { it.primitiveString("day").toIntOrNull() ?: -1 }
        val labels = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
        return UpdateTimeLine(
            current = currentDayIndex.coerceIn(labels.indices),
            timeline = labels.mapIndexed { index, label ->
                label to RecommendationParser.parseItems(
                    itemsByDay[index + 1]?.array("items") ?: JsonArray()
                )
            }
        )
    }

    fun parseFavorites(body: String): List<AnimeData> {
        val root = JsonParser.parseString(body).asJsonObject
        return RecommendationParser.parseItems(root.array("items"))
    }

    fun parseHistory(body: String): List<AnimeData> {
        return parseHistoryItems(body).map(TvHistoryItem::anime)
    }

    fun parseHistoryItems(body: String): List<TvHistoryItem> {
        val root = runCatching { JsonParser.parseString(body) }
            .getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return emptyList()
        return root.array("items").mapNotNull { element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
            val anime = runCatching {
                RecommendationParser.mapItem(
                    item = item,
                    idKeys = listOf("animeId", "id"),
                    titleKeys = listOf("title", "animeTitle"),
                    episodeKeys = listOf("episodeLabel", "currentEpisode", "subtitle")
                )
            }.getOrNull() ?: return@mapNotNull null
            TvHistoryItem(
                animeId = item.primitiveString("animeId").ifBlank { anime.id },
                anime = anime,
                episodeId = item.primitiveString("episodeId"),
                episodeLabel = item.primitiveString("episodeLabel"),
                episodeIndex = item.nonNegativeInt("episodeIndex"),
                episodeCount = item.nonNegativeInt("episodeCount"),
                currentTimeSeconds = item.nonNegativeDouble("currentTime"),
                durationSeconds = item.nonNegativeDouble("duration"),
                completed = item.boolean("completed"),
                sourceTypeId = item.primitiveString("sourceTypeId"),
                updatedAt = item.primitiveString("updatedAt"),
                updatedAtEpochMs = CloudTimestamp.parseEpochMs(item.primitiveString("updatedAt"))
            )
        }.distinctBy { it.anime.id }
    }

    fun parseAnimeDetail(body: String): TvAnimeDetailPayload {
        val root = runCatching { JsonParser.parseString(body) }
            .getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return TvAnimeDetailPayload()
        val item = root.get("item")
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: root
        val catalogItem = runCatching { RecommendationParser.mapItem(item) }.getOrNull()
        val episodeLabels = item.array("episodes")
            .mapNotNull { element ->
                val episode = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                    ?: return@mapNotNull null
                episode.primitiveString("label")
                    .ifBlank { episode.primitiveString("title") }
                    .takeIf(String::isNotBlank)
            }
            .distinct()
            .take(5_000)
        val providerEpisodeCounts = linkedMapOf<String, Int>()
        item.objectOrNull("providerEpisodeCounts")
            ?.entrySet()
            .orEmpty()
            .forEach { (providerId, count) ->
                if (providerId in DETAIL_PROVIDER_IDS && count.isJsonPrimitive) {
                    providerEpisodeCounts[providerId] =
                        count.asString.toIntOrNull().orZeroEpisodeCount()
                }
            }
        item.array("playLists")
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .forEach { playList ->
                val providerId = playList.primitiveString("code").trim().lowercase()
                if (providerId in DETAIL_PROVIDER_IDS) {
                    val count = playList.primitiveString("count")
                        .toIntOrNull()
                        .orZeroEpisodeCount()
                    providerEpisodeCounts[providerId] = maxOf(
                        providerEpisodeCounts[providerId] ?: 0,
                        count
                    )
                }
            }
        val info = item.objectOrNull("info")
        return TvAnimeDetailPayload(
            catalogItem = catalogItem,
            episodeLabels = episodeLabels,
            providerEpisodeCounts = providerEpisodeCounts,
            infoList = buildList {
                addInfo("地區", info?.primitiveString("area").orEmpty())
                addInfo("演員", info?.primitiveString("actor").orEmpty())
                addInfo("導演", info?.primitiveString("director").orEmpty())
                addInfo("編劇", info?.primitiveString("writer").orEmpty())
            },
            related = RecommendationParser.parseItems(item.array("related")),
            recommendations = RecommendationParser.parseItems(item.array("recommendations")),
            personalizedRecommendations = item.boolean("personalizedRecommendations")
        )
    }

    private fun com.google.gson.JsonObject.array(key: String): JsonArray =
        get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.primitiveString(key: String): String =
        get(key)
            ?.takeIf(JsonElement::isJsonPrimitive)
            ?.asJsonPrimitive
            ?.asString
            .orEmpty()

    private fun JsonObject.nonNegativeDouble(key: String): Double =
        primitiveString(key)
            .toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?.coerceAtLeast(0.0)
            ?: 0.0

    private fun JsonObject.nonNegativeInt(key: String): Int {
        val value = primitiveString(key).toDoubleOrNull() ?: return 0
        if (!value.isFinite() || value < 0.0 || value > Int.MAX_VALUE) return 0
        return value.toInt()
    }

    private fun JsonObject.boolean(key: String): Boolean =
        when (primitiveString(key).trim().lowercase()) {
            "true", "1" -> true
            else -> false
        }

    private fun Int?.orZeroEpisodeCount(): Int = this?.coerceIn(0, 5_000) ?: 0

    private fun MutableList<String>.addInfo(label: String, value: String) {
        if (value.isNotBlank()) add("$label：$value")
    }

    private val DETAIL_PROVIDER_IDS = setOf(
        "cycani",
        "girigiri_cht",
        "girigiri_chs",
        "sakura",
        "age"
    )
}
