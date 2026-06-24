// MarketEntities.kt
// Skill 市场 - Room Entity 定义
// 已安装Skill、搜索缓存、下载任务的数据库实体

package com.androidclaw.app.skills.market.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已安装的 Skill 实体
 */
@Entity(
    tableName = "installed_skills",
    indices = [
        Index(value = ["skillName"], unique = true),
        Index(value = ["marketId"])
    ]
)
data class InstalledSkillEntity(
    @PrimaryKey
    val id: String,                      // marketId + ":" + skillId
    val marketId: String,                // 来源市场 ID
    val skillId: String,                 // 市场中的 Skill ID
    val skillName: String,               // Skill 唯一名称
    val displayName: String,             // 显示名称
    val description: String,             // 描述
    val version: String,                 // 已安装版本
    val author: String,                  // 作者
    val downloadUrl: String,             // 下载地址
    val sha256: String? = null,          // 安装包校验值
    val permissions: List<String>,       // 所需权限列表
    val category: String,                // 分类
    val tags: List<String>,              // 标签
    val installedAt: Long,               // 安装时间戳
    val lastUpdated: String,             // 市场端最后更新时间
    val isEnabled: Boolean = false,      // 是否启用
    val installPath: String              // 安装路径
)

/**
 * 市场搜索缓存实体
 */
@Entity(
    tableName = "market_cache",
    indices = [
        Index(value = ["marketId", "query"]),
        Index(value = ["cachedAt"])
    ]
)
data class MarketCacheEntity(
    @PrimaryKey
    val id: String,                      // marketId + ":" + query 的 hash
    val marketId: String,                // 市场源 ID
    val query: String,                   // 搜索关键词
    val category: String? = null,        // 分类筛选
    val resultJson: String,              // 搜索结果 JSON
    val cachedAt: Long,                  // 缓存时间戳
    val expiresAt: Long                  // 过期时间戳
)

/**
 * 下载任务实体
 */
@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["skillId"]),
        Index(value = ["state"])
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,                      // 唯一任务 ID
    val marketId: String,               // 市场源 ID
    val skillId: String,                // Skill ID
    val skillName: String,              // Skill 名称
    val downloadUrl: String,            // 下载地址
    val targetPath: String,             // 目标路径
    val fileSize: Long,                 // 文件总大小
    val downloadedBytes: Long = 0,      // 已下载字节数
    val sha256: String? = null,         // 校验值
    val state: String = "PENDING",      // PENDING / DOWNLOADING / VERIFYING / INSTALLING / COMPLETED / FAILED
    val errorMessage: String? = null,   // 错误信息
    val createdAt: Long,                // 创建时间
    val updatedAt: Long                 // 更新时间
)
