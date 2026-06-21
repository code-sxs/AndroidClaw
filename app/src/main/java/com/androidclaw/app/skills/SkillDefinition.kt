// SkillDefinition.kt
// Skill 系统 - 接口定义
// 参考 PhoneClaw 的基于文件的 Skill 系统

package com.androidclaw.app.skills

import android.content.Context
import com.androidclaw.app.agent.ToolResult

/**
 * Skill 接口
 * 所有 Skill 必须实现此接口
 * 
 * Skill 是 AndroidClaw 的功能扩展单元，类似于 PhoneClaw 的 Skill 系统
 * 每个 Skill 提供一组相关的工具 (Tool)
 */
interface SkillDefinition {

    /**
     * Skill 名称 (唯一标识)
     */
    val skillName: String

    /**
     * Skill 显示名称
     */
    val displayName: String

    /**
     * Skill 描述
     */
    val description: String

    /**
     * 所需的 Android 权限
     */
    val requiredPermissions: List<String>

    /**
     * Skill 自报的安全信息
     * 用于安装时与安全扫描结果对比，检测声明不一致
     */
    val securityProfile: SecurityProfile
        get() = SecurityProfile()

    /**
     * 初始化 Skill
     */
    suspend fun initialize(context: Context)

    /**
     * 获取此 Skill 提供的所有工具
     */
    fun getTools(): List<ToolDefinition>

    /**
     * 执行工具
     * @param toolName 工具名称
     * @param parameters 工具参数 (JSON 格式)
     * @return 执行结果
     */
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult

    /**
     * 释放资源
     */
    fun release()
}

/**
 * 工具定义
 */
data class ToolDefinition(
    val toolName: String,          // 工具名称 (唯一标识)
    val displayName: String,       // 显示名称
    val description: String,       // 工具描述
    val parameters: List<ToolParameter>,  // 参数列表
    val returnType: String         // 返回值类型
)

/**
 * 工具参数
 */
data class ToolParameter(
    val name: String,             // 参数名称
    val type: String,             // 参数类型 (string / int / boolean / etc.)
    val required: Boolean,        // 是否必需
    val description: String       // 参数描述
)

/**
 * Skill 安全配置文件
 * Skill 开发者声明此 Skill 的安全行为
 * 用于与安全扫描结果进行对比验证
 */
data class SecurityProfile(
    /**
     * 声明需要网络访问权限（如调用 API、下载资源等）
     */
    val declaresNetworkAccess: Boolean = false,
    /**
     * 声明访问个人数据（联系人、短信、位置等）
     */
    val accessesPersonalData: Boolean = false,
    /**
     * 声明需要访问外部存储
     */
    val declaresExternalStorage: Boolean = false,
    /**
     * 开发者联系方式（邮箱、网站等）
     */
    val authorContact: String? = null,
    /**
     * 隐私政策 URL
     */
    val privacyPolicyUrl: String? = null
)

/**
 * 工具执行结果
 */
sealed class ToolResult {
    data class Success(val data: Any?) : ToolResult()
    data class Error(val message: String, val exception: Exception? = null) : ToolResult()
    object Cancelled : ToolResult()
}
