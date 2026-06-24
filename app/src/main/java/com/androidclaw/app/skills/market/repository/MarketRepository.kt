// MarketRepository.kt
// Skill 市场 - 数据仓库
// Repository 模式，整合 MarketClient / Installer / LocalAdapter / Database

package com.androidclaw.app.skills.market.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.androidclaw.app.skills.market.InstallResult
import com.androidclaw.app.skills.market.InstallState
import com.androidclaw.app.skills.market.MarketSearchResult
import com.androidclaw.app.skills.market.MarketSkill
import com.androidclaw.app.skills.market.MarketSource
import com.androidclaw.app.skills.market.SkillLocalAdapter
import com.androidclaw.app.skills.market.SkillMarketClient
import com.androidclaw.app.skills.market.SkillInstaller
import com.androidclaw.app.skills.market.db.InstalledSkillEntity
import com.androidclaw.app.skills.market.db.MarketDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Skill 市场数据仓库
 * 整合客户端、安装器、适配器和数据库，提供统一的数据访问接口
 */
class MarketRepository private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MarketRepository"

        @Volatile
        private var instance: MarketRepository? = null

        fun getInstance(context: Context): MarketRepository {
            return instance ?: synchronized(this) {
                instance ?: MarketRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val database: MarketDatabase by lazy {
        Room.databaseBuilder(
            context,
            MarketDatabase::class.java,
            "market_database"
        ).build()
    }

    private val marketClient: SkillMarketClient by lazy {
        SkillMarketClient(okHttpClient, database.marketCacheDao(), gson)
    }

    private val installer: SkillInstaller by lazy {
        SkillInstaller(
            context,
            okHttpClient,
            database.installedSkillDao(),
            database.downloadTaskDao(),
            gson
        )
    }

    private val _localAdapter: SkillLocalAdapter by lazy {
        SkillLocalAdapter(context)
    }

    /** 搜索 Skill */
    suspend fun searchSkills(
        query: String,
        category: String? = null,
        marketId: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): MarketSearchResult {
        return marketClient.searchSkills(query, category, marketId, page, pageSize)
    }

    /** 获取 Skill 详情 */
    suspend fun getSkillDetail(marketId: String, skillId: String): MarketSkill? {
        return marketClient.getSkillDetail(marketId, skillId)
    }

    /** 获取所有分类 */
    suspend fun getCategories(marketId: String? = null): List<String> {
        return marketClient.getCategories(marketId)
    }

    /** 安装 Skill */
    suspend fun installSkill(skill: MarketSkill): InstallResult {
        return installer.installSkill(skill)
    }

    /** 卸载 Skill */
    suspend fun uninstallSkill(skillName: String): Boolean {
        return installer.uninstallSkill(skillName)
    }

    /** 启用/禁用 Skill */
    suspend fun setEnabled(skillName: String, enabled: Boolean) {
        installer.setEnabled(skillName, enabled)
    }

    /** 获取已安装 Skill 列表 */
    fun getInstalledSkillsFlow(): Flow<List<InstalledSkillEntity>> {
        return database.installedSkillDao().getAll()
    }

    /** 获取已安装 Skill（挂起） */
    suspend fun getInstalledSkills(): List<InstalledSkillEntity> {
        return database.installedSkillDao().getEnabled()
    }

    /** 检查 Skill 是否已安装 */
    suspend fun isInstalled(skillName: String): Boolean {
        return database.installedSkillDao().getBySkillName(skillName) != null
    }

    /** 获取市场源列表 */
    fun getMarketSources(): List<MarketSource> {
        return marketClient.sources
    }

    /** 添加市场源 */
    fun addMarketSource(source: MarketSource) {
        marketClient.addSource(source)
    }

    /** 获取安装进度 */
    fun observeInstallProgress(skillId: String): Flow<com.androidclaw.app.skills.market.DownloadProgress?> {
        return installer.observeProgress(skillId)
    }

    /** 获取本地化适配器 */
    fun getLocalAdapter(): SkillLocalAdapter = _localAdapter

    /** 清除搜索缓存 */
    suspend fun clearCache() {
        database.marketCacheDao().deleteAll()
    }
}
