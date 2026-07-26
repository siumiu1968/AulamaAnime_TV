package com.jing.sakura.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jing.sakura.repo.SakuraSource

@Database(
    entities = [VideoHistoryEntity::class, SearchHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class SakuraDatabase : RoomDatabase() {

    abstract fun getVideoHistoryDao(): VideoHistoryDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                create table if not exists `search_history` (
                    `keyword` text not null,
                    `searchTime` integer not null,
                    primary key(`keyword`)
                )
            """.trimIndent()
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `video_history_temp` (
                    `episodeId` TEXT NOT NULL, 
                    `sourceId` TEXT NOT NULL, 
                    `animeName` TEXT NOT NULL, 
                    `animeId` TEXT NOT NULL, 
                    `lastEpisodeName` TEXT NOT NULL, 
                    `updateTime` INTEGER NOT NULL, 
                    `lastPlayTime` INTEGER NOT NULL, 
                    `videoDuration` INTEGER NOT NULL, 
                    `coverUrl` TEXT NOT NULL, 
                    PRIMARY KEY(`episodeId`, `sourceId`)
                    )
                """.trimIndent()
                )
                db.execSQL(
                    """
                    insert into video_history_temp(`episodeId`,
                                                    `sourceId`,
                                                    `animeName`,
                                                    `animeId`,
                                                    `lastEpisodeName`,
                                                    `updateTime`,
                                                    `lastPlayTime`,
                                                    `videoDuration`,
                                                    `coverUrl`)
                    select `episodeId`,
                            ?,
                            `animeName`,
                            `animeId`,
                            `lastEpisodeName`,
                            `updateTime`,
                            `lastPlayTime`,
                            `videoDuration`,
                            `coverUrl`
                    from video_history
                """.trimIndent(), arrayOf(SakuraSource.SOURCE_ID)
                )
                db.execSQL("drop table video_history")
                db.execSQL("alter table video_history_temp rename to video_history")
            }

        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 主键添加animeId列
                db.execSQL(
                    """
                    CREATE TABLE `video_history_temp` (
                    `episodeId` TEXT NOT NULL, 
                    `sourceId` TEXT NOT NULL, 
                    `animeName` TEXT NOT NULL, 
                    `animeId` TEXT NOT NULL, 
                    `lastEpisodeName` TEXT NOT NULL, 
                    `updateTime` INTEGER NOT NULL, 
                    `lastPlayTime` INTEGER NOT NULL, 
                    `videoDuration` INTEGER NOT NULL, 
                    `coverUrl` TEXT NOT NULL, 
                    PRIMARY KEY(`animeId`, `episodeId`, `sourceId`)
                    )
                """.trimIndent()
                )
                db.execSQL(
                    """
                    insert into video_history_temp(`episodeId`,
                                                    `sourceId`,
                                                    `animeName`,
                                                    `animeId`,
                                                    `lastEpisodeName`,
                                                    `updateTime`,
                                                    `lastPlayTime`,
                                                    `videoDuration`,
                                                    `coverUrl`)
                    select `episodeId`,
                            `sourceId`,
                            `animeName`,
                            `animeId`,
                            `lastEpisodeName`,
                            `updateTime`,
                            `lastPlayTime`,
                            `videoDuration`,
                            `coverUrl`
                    from video_history
                """.trimIndent()
                )
                db.execSQL("drop table video_history")
                db.execSQL("alter table video_history_temp rename to video_history")
            }

        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `search_history_temp` (
                        `accountKey` TEXT NOT NULL,
                        `keywordKey` TEXT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `searchTime` INTEGER NOT NULL,
                        PRIMARY KEY(`accountKey`, `keywordKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `search_history_temp`(
                        `accountKey`, `keywordKey`, `keyword`, `searchTime`
                    )
                    SELECT 'guest', lower(trim(`keyword`)), trim(`keyword`), `searchTime`
                    FROM `search_history`
                    WHERE trim(`keyword`) <> ''
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `search_history`")
                db.execSQL("ALTER TABLE `search_history_temp` RENAME TO `search_history`")
            }
        }

    }
}
