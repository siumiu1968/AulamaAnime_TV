package com.jing.sakura.repo

internal fun selectNonJapaneseSynopsis(vararg candidates: String?): String = candidates
    .asSequence()
    .map { it.orEmpty().trim() }
    .firstOrNull { it.isNotBlank() && !it.isLikelyJapaneseSynopsis() }
    .orEmpty()

internal fun shouldFetchSynopsisEnrichment(description: String): Boolean =
    selectNonJapaneseSynopsis(description).isBlank()

private fun String.isLikelyJapaneseSynopsis(): Boolean {
    val kanaCount = count { char ->
        char in '\u3040'..'\u30ff' ||
            char in '\u31f0'..'\u31ff' ||
            char in '\uff66'..'\uff9f'
    }
    if (kanaCount == 0) return false
    val letterCount = count(Char::isLetter).coerceAtLeast(1)
    return kanaCount >= 4 && kanaCount * 5 >= letterCount
}
