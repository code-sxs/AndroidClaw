// SkillLocalAdapter.kt
// Skill 市场 - 本地化适配器
// 将外部市场的 Skill 格式转换为 AndroidClaw 的 SkillDefinition 接口
// 处理不同来源的格式差异，自动生成权限映射和资源本地化

package com.androidclaw.app.skills.market

import android.content.Context
import android.util.Log
import com.androidclaw.app.skills.SkillDefinition
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolParameter
import com.androidclaw.app.skills.ToolResult
import com.androidclaw.app.skills.market.db.InstalledSkillEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Skill 本地化适配器
 * 
 * 功能：
 * - 将外部市场的 Skill 格式转换为 AndroidClaw 的 SkillDefinition 接口
 * - 处理不同来源的格式差异（Clawhub / Skillsmp / SkillHub）
 * - 自动生成 Android 权限映射
 * - 资源文件本地化（字符串翻译等）
 */
class SkillLocalAdapter(private val context: Context) {

    companion object {
        private const val TAG = "SkillLocalAdapter"
        private const val MANIFEST_FILE = "skill_manifest.json"
        private const val LOCALIZATION_DIR = "locale"
    }

    private val gson = Gson()

    /**
     * 将已安装的 Skill 实体转换为 SkillDefinition
     * 
     * @param entity 已安装的 Skill 数据库实体
     * @return 适配后的 SkillDefinition 实现
     */
    fun adaptToSkillDefinition(entity: InstalledSkillEntity): SkillDefinition {
        val installDir = File(entity.installPath)
        val manifest = loadManifest(installDir)

        return MarketSkillDefinition(
            entity = entity,
            manifest = manifest,
            installDir = installDir
        )
    }

    /**
     * 将市场 Skill 信息转换为 MarketSkill（格式标准化）
     * 处理不同市场源的字段差异
     *
     * @param rawSkill 原始市场 Skill 数据
     * @param marketId 市场来源 ID
     * @return 标准化后的 MarketSkill
     */
    fun normalizeMarketSkill(rawSkill: Map<String, Any?>, marketId: String): MarketSkill {
        return when (marketId) {
            "clawhub" -> normalizeClawhubSkill(rawSkill, marketId)
            "skillhub" -> normalizeSkillHubSkill(rawSkill, marketId)
            else -> normalizeGenericSkill(rawSkill, marketId)
        }
    }

    /**
     * 生成 Android 权限映射
     * 将 Skill 声明的权限需求映射到实际的 Android 权限
     *
     * @param declaredPermissions Skill 清单中声明的权限
     * @return 映射后的 Android 权限列表
     */
    fun mapPermissions(declaredPermissions: List<String>): List<String> {
        return declaredPermissions.mapNotNull { perm ->
            PERMISSION_MAPPING[perm] ?: run {
                // 如果已经是 Android 权限格式 (android.permission.XXX)，直接使用
                if (perm.startsWith("android.permission.")) {
                    perm
                } else {
                    Log.w(TAG, "未知权限映射: $perm")
                    null
                }
            }
        }
    }

    /**
     * 获取本地化字符串
     * 优先加载用户语言对应的字符串资源，回退到默认
     *
     * @param installDir Skill 安装目录
     * @param key 字符串键
     * @return 本地化后的字符串
     */
    fun getLocalizedString(installDir: File, key: String): String? {
        val localeDir = File(installDir, LOCALIZATION_DIR)
        if (!localeDir.exists()) return null

        // 获取当前语言
        val language = context.resources.configuration.locales.get(0).language
        val localeFile = File(localeDir, "$language.json")
        val defaultFile = File(localeDir, "en.json")

        // 优先加载对应语言
        val targetFile = if (localeFile.exists()) localeFile else if (defaultFile.exists()) defaultFile else return null

        try {
            val json = targetFile.readText()
            val type = object : TypeToken<Map<String, String>>() {}.type
            val strings: Map<String, String> = gson.fromJson(json, type)
            return strings[key]
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load localization for key: $key", e)
            return null
        }
    }

    // ===== 格式标准化 =====

    /**
     * 标准化 Clawhub 格式
     */
    @Suppress("UNCHECKED_CAST")
    private fun normalizeClawhubSkill(raw: Map<String, Any?>, marketId: String): MarketSkill {
        val tools = (raw["tools"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
            ToolSummary(
                name = t["name"] as? String ?: "",
                description = t["description"] as? String ?: "",
                parameters = (t["parameters"] as? List<String>) ?: emptyList()
            )
        }

        return MarketSkill(
            marketId = marketId,
            skillId = raw["id"] as? String ?: "",
            name = raw["name"] as? String ?: "",
            version = raw["version"] as? String ?: "1.0.0",
            description = raw["description"] as? String ?: "",
            author = raw["author"] as? String ?: "",
            downloadUrl = raw["download_url"] as? String ?: "",
            fileSize = (raw["file_size"] as? Number)?.toLong() ?: 0L,
            sha256 = raw["sha256"] as? String,
            permissions = (raw["permissions"] as? List<String>) ?: emptyList(),
            tools = tools,
            rating = (raw["rating"] as? Number)?.toFloat(),
            downloadCount = (raw["download_count"] as? Number)?.toInt() ?: 0,
            lastUpdated = raw["updated_at"] as? String ?: "",
            category = raw["category"] as? String ?: "general",
            tags = (raw["tags"] as? List<String>) ?: emptyList()
        )
    }

    /**
     * 标准化 SkillHub 格式
     * SkillHub 使用不同的字段命名约定
     */
    @Suppress("UNCHECKED_CAST")
    private fun normalizeSkillHubSkill(raw: Map<String, Any?>, marketId: String): MarketSkill {
        val tools = (raw["tool_list"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
            ToolSummary(
                name = t["tool_name"] as? String ?: "",
                description = t["tool_desc"] as? String ?: "",
                parameters = (t["param_names"] as? List<String>) ?: emptyList()
            )
        }

        return MarketSkill(
            marketId = marketId,
            skillId = raw["skill_id"] as? String ?: "",
            name = raw["skill_name"] as? String ?: "",
            version = raw["ver"] as? String ?: "1.0.0",
            description = raw["desc"] as? String ?: "",
            author = raw["creator"] as? String ?: "",
            downloadUrl = raw["pkg_url"] as? String ?: "",
            fileSize = (raw["pkg_size"] as? Number)?.toLong() ?: 0L,
            sha256 = raw["checksum"] as? String,
            permissions = (raw["perm_list"] as? List<String>) ?: emptyList(),
            tools = tools,
            rating = (raw["score"] as? Number)?.toFloat(),
            downloadCount = (raw["dl_count"] as? Number)?.toInt() ?: 0,
            lastUpdated = raw["update_time"] as? String ?: "",
            category = raw["cat"] as? String ?: "general",
            tags = (raw["tag_list"] as? List<String>) ?: emptyList()
        )
    }

    /**
     * 标准化通用格式
     */
    @Suppress("UNCHECKED_CAST")
    private fun normalizeGenericSkill(raw: Map<String, Any?>, marketId: String): MarketSkill {
        val tools = (raw["tools"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
            ToolSummary(
                name = t["name"] as? String ?: "",
                description = t["description"] as? String ?: "",
                parameters = (t["parameters"] as? List<String>) ?: emptyList()
            )
        }

        return MarketSkill(
            marketId = marketId,
            skillId = raw["skill_id"] as? String ?: (raw["id"] as? String) ?: "",
            name = raw["name"] as? String ?: "",
            version = raw["version"] as? String ?: "1.0.0",
            description = raw["description"] as? String ?: "",
            author = raw["author"] as? String ?: "",
            downloadUrl = raw["download_url"] as? String ?: "",
            fileSize = (raw["file_size"] as? Number)?.toLong() ?: 0L,
            sha256 = raw["sha256"] as? String,
            permissions = (raw["permissions"] as? List<String>) ?: emptyList(),
            tools = tools,
            rating = (raw["rating"] as? Number)?.toFloat(),
            downloadCount = (raw["download_count"] as? Number)?.toInt() ?: 0,
            lastUpdated = raw["last_updated"] as? String ?: "",
            category = raw["category"] as? String ?: "general",
            tags = (raw["tags"] as? List<String>) ?: emptyList()
        )
    }

    // ===== 内部方法 =====

    private fun loadManifest(installDir: File): SkillManifest? {
        val manifestFile = File(installDir, MANIFEST_FILE)
        if (!manifestFile.exists()) {
            Log.w(TAG, "Manifest not found in ${installDir.absolutePath}")
            return null
        }
        return try {
            gson.fromJson(manifestFile.readText(), SkillManifest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse manifest", e)
            null
        }
    }

    /**
     * 权限映射表
     * 将简写权限名映射到 Android 完整权限名
     */
    private val PERMISSION_MAPPING = mapOf(
        "camera" to "android.permission.CAMERA",
        "contacts" to "android.permission.READ_CONTACTS",
        "contacts.write" to "android.permission.WRITE_CONTACTS",
        "calendar" to "android.permission.READ_CALENDAR",
        "calendar.write" to "android.permission.WRITE_CALENDAR",
        "location" to "android.permission.ACCESS_FINE_LOCATION",
        "location.coarse" to "android.permission.ACCESS_COARSE_LOCATION",
        "storage" to "android.permission.READ_EXTERNAL_STORAGE",
        "storage.write" to "android.permission.WRITE_EXTERNAL_STORAGE",
        "microphone" to "android.permission.RECORD_AUDIO",
        "phone" to "android.permission.READ_PHONE_STATE",
        "sms" to "android.permission.READ_SMS",
        "notifications" to "android.permission.POST_NOTIFICATIONS",
        "bluetooth" to "android.permission.BLUETOOTH",
        "wifi" to "android.permission.ACCESS_WIFI_STATE",
        "network" to "android.permission.INTERNET"
    )
}

/**
 * 基于 Market Skill 的 SkillDefinition 实现
 * 将已安装的市场 Skill 适配为 AndroidClaw 可运行的接口
 */
private class MarketSkillDefinition(
    private val entity: InstalledSkillEntity,
    private val manifest: SkillManifest?,
    private val installDir: File
) : SkillDefinition {

    override val skillName: String = entity.skillName
    override val displayName: String = entity.displayName
    override val description: String = entity.description
    override val requiredPermissions: List<String> = entity.permissions

    override suspend fun initialize(context: android.content.Context) {
        // TODO: 执行 Skill 特定的初始化逻辑
        // 例如加载配置、建立连接等
    }

    override fun getTools(): List<ToolDefinition> {
        return manifest?.tools?.map { mt ->
            ToolDefinition(
                toolName = mt.name,
                displayName = mt.name,
                description = mt.description,
                parameters = mt.parameters.map { mp ->
                    ToolParameter(
                        name = mp.name,
                        type = mp.type,
                        required = mp.required,
                        description = mp.description
                    )
                },
                returnType = "string"
            )
        } ?: emptyList()
    }

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        // TODO: 实现动态 Skill 的工具执行
        // 当前返回占位结果
        return ToolResult.Error("Skill 工具执行尚未实现: $toolName")
    }

    override fun release() {
        // 释放资源
    }
}
