// MarketDaos.kt
// Skill 市场 - Room DAO 定义
// 已安装Skill、搜索缓存、下载任务的数据访问对象

package com.androidclaw.app.skills.market.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 已安装 Skill DAO
 */
@Dao
interface InstalledSkillDao {

    @Query("SELECT * FROM installed_skills ORDER BY installedAt DESC")
    fun getAll(): Flow<List<InstalledSkillEntity>>

    @Query("SELECT * FROM installed_skills WHERE skillName = :skillName LIMIT 1")
    suspend fun getBySkillName(skillName: String): InstalledSkillEntity?

    @Query("SELECT * FROM installed_skills WHERE marketId = :marketId")
    suspend fun getByMarket(marketId: String): List<InstalledSkillEntity>

    @Query("SELECT * FROM installed_skills WHERE isEnabled = 1")
    suspend fun getEnabled(): List<InstalledSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstalledSkillEntity)

    @Update
    suspend fun update(entity: InstalledSkillEntity)

    @Query("DELETE FROM installed_skills WHERE skillName = :skillName")
    suspend fun deleteBySkillName(skillName: String)

    @Query("UPDATE installed_skills SET isEnabled = :enabled WHERE skillName = :skillName")
    suspend fun setEnabled(skillName: String, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM installed_skills")
    suspend fun count(): Int
}

/**
 * 市场搜索缓存 DAO
 */
@Dao
interface MarketCacheDao {

    @Query("SELECT * FROM market_cache WHERE marketId = :marketId AND query = :query AND expiresAt > :now LIMIT 1")
    suspend fun getValidCache(marketId: String, query: String, now: Long = System.currentTimeMillis()): MarketCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MarketCacheEntity)

    @Query("DELETE FROM market_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM market_cache WHERE marketId = :marketId")
    suspend fun deleteByMarket(marketId: String)

    @Query("DELETE FROM market_cache")
    suspend fun deleteAll()
}

/**
 * 下载任务 DAO
 */
@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE skillId = :skillId LIMIT 1")
    suspend fun getBySkillId(skillId: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE state = 'DOWNLOADING' OR state = 'PENDING'")
    suspend fun getActiveTasks(): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadTaskEntity)

    @Update
    suspend fun update(entity: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_tasks WHERE state = 'COMPLETED' OR state = 'FAILED'")
    suspend fun deleteFinished()
}
