package com.jing.sakura.room

import androidx.room.Entity

@Entity(
    tableName = "search_history",
    primaryKeys = ["accountKey", "keywordKey"]
)
data class SearchHistoryEntity(
    val accountKey: String,
    val keywordKey: String,
    val keyword: String,
    val searchTime: Long
)
