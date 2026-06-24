// MarketDatabase.kt
// Skill 市场 - Room 数据库定义
// 管理已安装Skill、搜索缓存、下载任务等持久化数据

package com.androidclaw.app.skills.market.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Skill 市场 Room 数据库
 */
@Database(
    entities = [
        InstalledSkillEntity::class,
        MarketCacheEntity::class,
        DownloadTaskEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(MarketConverters::class)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun installedSkillDao(): InstalledSkillDao
    abstract fun marketCacheDao(): MarketCacheDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}

/**
 * Room 类型转换器
 * 处理 List<String> 与 JSON 字符串之间的转换
 */
class MarketConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(separator = "|||")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("|||")
}
