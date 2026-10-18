package com.jing.sakura.player

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.ByteString.Companion.decodeBase64
import java.util.Locale
import kotlin.math.roundToLong

internal fun canEditPlaybackSegments(role: String?, permitted: Boolean): Boolean =
    role?.trim()?.lowercase() == "super_admin" && permitted

internal fun playbackSegmentProvider(episodeId: String): String = runCatching {
    val raw = episodeId.removePrefix("cycapi:").decodeBase64()?.utf8() ?: return@runCatching "cycani"
    JsonParser.parseString(raw).asJsonObject.get("xp")?.asString
        ?.takeIf { it in setOf("girigiri_cht", "girigiri_chs", "sakura", "age") } ?: "cycani"
}.getOrDefault("cycani")

internal enum class SegmentMarker(val key: String, val label: String) {
    INTRO_START("introStart", "片頭開始"), INTRO_END("introEnd", "片頭結束"),
    OUTRO_START("outroStart", "片尾開始"), OUTRO_END("outroEnd", "片尾結束")
}

internal data class SegmentEditorTarget(
    val animeId: String, val episodeId: String, val episodeIndex: Int,
    val episodeLabel: String, val provider: String, val providerLabel: String
)

internal data class SegmentDraft(
    val times: Map<SegmentMarker, Long?> = SegmentMarker.entries.associateWith { null },
    val cleared: Set<String> = emptySet()
) {
    operator fun get(marker: SegmentMarker): Long? = times[marker]
    fun set(marker: SegmentMarker, position: Long) = copy(
        times = times + (marker to (position.coerceAtLeast(0L) / 100 * 100)),
        cleared = cleared - if (marker.key.startsWith("intro")) "intro" else "outro"
    )
    fun clear(kind: String) = copy(
        times = times.mapValues { (marker, value) -> if (marker.key.startsWith(kind)) null else value },
        cleared = cleared + kind
    )
    fun adjusted(marker: SegmentMarker, delta: Long, duration: Long): SegmentDraft =
        set(marker, ((get(marker) ?: 0L) + delta).coerceIn(0L, duration.takeIf { it > 0 } ?: 604_800_000L))

    fun error(duration: Long): String? {
        if (times.values.filterNotNull().any { it < 0 || duration > 0 && it > duration }) return "時間不能超過影片長度"
        for (kind in listOf("intro", "outro")) {
            val markers = SegmentMarker.entries.filter { it.key.startsWith(kind) }
            val start = get(markers[0]); val end = get(markers[1])
            val label = if (kind == "intro") "片頭" else "片尾"
            if ((start == null) != (end == null)) return "請設定${label}嘅開始同結束時間"
            if (start != null && end != null && start >= end) return "${label}結束必須遲過開始"
        }
        val introEnd = get(SegmentMarker.INTRO_END); val outroStart = get(SegmentMarker.OUTRO_START)
        if (introEnd != null && outroStart != null && introEnd > outroStart) return "片頭同片尾不能重疊"
        return null
    }
}

internal data class SegmentEditorSnapshot(val all: SegmentDraft, val current: SegmentDraft, val canEdit: Boolean) {
    companion object {
        fun parse(body: String): SegmentEditorSnapshot {
            val root = JsonParser.parseString(body).asJsonObject
            fun obj(parent: JsonObject?, key: String) = parent?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
            fun draft(value: JsonObject?) = SegmentDraft(SegmentMarker.entries.associateWith { marker ->
                value?.get(marker.key)?.takeUnless { it.isJsonNull }?.asDouble
                    ?.takeIf { it.isFinite() && it >= 0 }?.let { (it * 1000).roundToLong() }
            })
            val raw = obj(root, "rawSegments")
            val item = obj(root, "item")
            val effective = obj(root, "effectiveSegments") ?: item
            return SegmentEditorSnapshot(
                all = draft(obj(raw, "all") ?: obj(item, "baseSegments") ?: item),
                current = draft(obj(raw, "provider") ?: effective),
                canEdit = obj(root, "permissions")?.get("canEdit")?.asBoolean == true
            )
        }
    }
}

internal fun segmentTimeLabel(position: Long?): String {
    if (position == null) return "--:--.-"
    val tenths = position.coerceAtLeast(0L) / 100
    val seconds = tenths / 10
    return String.format(Locale.ROOT, "%02d:%02d.%d", seconds / 60, seconds % 60, tenths % 10)
}
