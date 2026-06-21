// MarketModels.kt
// Skill 市场 - 数据模型定义
// 定义市场源、市场Skill、工具摘要等核心数据结构

package com.androidclaw.app.skills.market

/**
 * 市场源配置
 * 支持多个外部 Skill 市场源
 */
data class MarketSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiEndpoint: String,
    val iconUrl: String? = null,
    val isEnabled: Boolean = true
) {
    companion object {
        /** 预置市场源列表 */
        val DEFAULT_SOURCES = listOf(
            MarketSource(
                id = "clawhub",
                name = "Clawhub",
                baseUrl = "https://clawhub.ai",
                apiEndpoint = "https://clawhub.ai/api/v1",
                iconUrl = "https://clawhub.ai/favicon.ico"
            ),
            MarketSource(
                id = "skillsmp",
                name = "Skillsmp",
                baseUrl = "https://skillsmp.com",
                apiEndpoint = "https://skillsmp.com/api/v1",
                iconUrl = "https://skillsmp.com/favicon.ico"
            ),
            MarketSource(
                id = "skillhub",
                name = "SkillHub",
                baseUrl = "https://skillhub.tencent.com",
                apiEndpoint = "https://skillhub.tencent.com/api/v1",
                iconUrl = "https://skillhub.tencent.com/favicon.ico"
            ),
            MarketSource(
                id = "skillssh",
                name = "skills.sh",
                baseUrl = "https://skills.sh",
                apiEndpoint = "https://skills.sh/api/v1",
                iconUrl = "https://skills.sh/favicon.ico"
            )
        )
    }
}

/**
 * 市场中的 Skill 信息
 */
data class MarketSkill(
    val marketId: String,          // 来源市场 ID
    val skillId: String,           // 在市场中的唯一 ID
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val downloadUrl: String,
    val fileSize: Long,
    val sha256: String? = null,    // 完整性校验
    val permissions: List<String>,
    val tools: List<ToolSummary>,
    val rating: Float? = null,
    val downloadCount: Int = 0,
    val lastUpdated: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val iconUrl: String? = null
)

/**
 * 工具摘要
 */
data class ToolSummary(
    val name: String,
    val description: String,
    val parameters: List<String> = emptyList()
)

/**
 * Skill 清单文件结构 (skill_manifest.json)
 */
data class SkillManifest(
    val name: String,
    val version: String,
    val display_name: String,
    val description: String,
    val author: String,
    val license: String = "Apache-2.0",
    val min_androidclaw_version: String = "1.0.0",
    val permissions: List<String> = emptyList(),
    val entry_point: String,
    val tools: List<ManifestTool> = emptyList(),
    val assets: List<String> = emptyList(),
    val dependencies: List<String> = emptyList()
)

/**
 * 清单中的工具定义
 */
data class ManifestTool(
    val name: String,
    val description: String,
    val parameters: List<ManifestParameter> = emptyList()
)

/**
 * 清单中的参数定义
 */
data class ManifestParameter(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val description: String = ""
)

/**
 * 搜索结果
 */
data class MarketSearchResult(
    val skills: List<MarketSkill>,
    val totalCount: Int,
    val page: Int,
    val hasMore: Boolean
)

/**
 * 安装状态
 */
enum class InstallState {
    NOT_INSTALLED,       // 未安装
    DOWNLOADING,         // 下载中
    VERIFYING,           // 校验中
    INSTALLING,          // 安装中
    INSTALLED,           // 已安装
    UPDATE_AVAILABLE,    // 有更新
    ERROR                // 错误
}

/**
 * 下载进度
 */
data class DownloadProgress(
    val skillId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentage: Int,       // 0-100
    val state: InstallState
)
