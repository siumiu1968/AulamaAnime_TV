package com.jing.sakura.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchHistoryDao {

    @Query("select * from search_history where accountKey = :accountKey order by searchTime desc limit :limit")
    fun queryHistory(accountKey: String, limit: Int): PagingSource<Int, SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHistory(history: SearchHistoryEntity)

    @Query("delete from search_history where accountKey = :accountKey and keywordKey not in (select keywordKey from search_history where accountKey = :accountKey order by searchTime desc limit :limit)")
    fun trimHistory(accountKey: String, limit: Int)

    @Query("delete from search_history where accountKey = :accountKey and keywordKey = :keywordKey")
    fun deleteHistory(accountKey: String, keywordKey: String)

    @Query("delete from search_history where accountKey = :accountKey")
    fun deleteAllHistory(accountKey: String)
}
