package com.jing.sakura.search

internal const val SEARCH_HISTORY_LIMIT = 5

internal fun searchResultTitle(keyword: String): String =
    "「${keyword.trim()}」搜尋結果"

internal fun normalizeSearchKeyword(keyword: String): String = keyword
    .trim()
    .replace(Regex("\\s+"), " ")
    .take(120)

internal fun searchKeywordKey(keyword: String): String =
    normalizeSearchKeyword(keyword).lowercase()

internal fun latestSearchKeywords(keywords: Iterable<String>): List<String> = keywords
    .map(::normalizeSearchKeyword)
    .filter(String::isNotBlank)
    .distinctBy(::searchKeywordKey)
    .take(SEARCH_HISTORY_LIMIT)
