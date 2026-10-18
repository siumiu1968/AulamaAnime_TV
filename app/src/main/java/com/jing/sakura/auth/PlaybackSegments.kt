package com.jing.sakura.auth

import com.google.gson.JsonParser

data class PlaybackSegments(
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null,
    val outroAction: String = "skip",
    val canEdit: Boolean = false
)

object PlaybackSegmentsParser {
    fun parse(body: String): PlaybackSegments? {
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            val item = root.get("item")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: if (root.has("permissions")) com.google.gson.JsonObject() else return null
            PlaybackSegments(
                introStartMs = secondsToMs(item.get("introStart")?.takeUnless { it.isJsonNull }?.asDouble),
                introEndMs = secondsToMs(item.get("introEnd")?.takeUnless { it.isJsonNull }?.asDouble),
                outroStartMs = secondsToMs(item.get("outroStart")?.takeUnless { it.isJsonNull }?.asDouble),
                outroEndMs = secondsToMs(item.get("outroEnd")?.takeUnless { it.isJsonNull }?.asDouble),
                outroAction = item.get("outroAction")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it == "next" }
                    ?: "skip",
                canEdit = root.getAsJsonObject("permissions")?.get("canEdit")?.asBoolean == true
            )
        }.getOrNull()
    }

    private fun secondsToMs(seconds: Double?): Long? = seconds
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { (it * 1000.0).toLong() }
}
