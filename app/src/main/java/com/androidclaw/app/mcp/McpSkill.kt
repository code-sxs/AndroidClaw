// McpSkill.kt
// MCP Skill - 动态 Skill，根据 MCP Server 提供的工具自动生成 ToolDefinition
// 允许用户在 SkillManagementScreen 中添加 MCP Server

package com.androidclaw.app.mcp

import android.content.Context
import android.util.Log
import com.androidclaw.app.agent.ToolResult
import com.androidclaw.app.skills.SecurityProfile
import com.androidclaw.app.skills.SkillDefinition
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MCP Skill - 将 MCP Server 的工具动态包装为 AndroidClaw Skill
 * 
 * @param serverUrl MCP Server 的 URL (HTTP) 或命令 (stdio)
 * @param serverName 服务器名称 (用于生成 skillName)
 */
class McpSkill(
    private val serverUrl: String,
    private val serverName: String
) : SkillDefinition {

    companion object {
        private const val TAG = "McpSkill"
    }

    private var mcpClient: McpClient? = null
    private var tools: List<McpTool> = emptyList()
    private var isInitialized = false

    override val skillName: String
        get() = "mcp_$serverName"

    override val displayName: String
        get() = "MCP: $serverName"

    override val description: String
        get() = "MCP Server at $serverUrl"

    override val requiredPermissions: List<String>
        get() = listOf()  // MCP 本身不需要特殊权限，但工具可能需要

    override val securityProfile: SecurityProfile
        get() = SecurityProfile(
            declaresNetworkAccess = true,  // MCP 通常需要网络
            accessesPersonalData = false,
            declaresExternalStorage = false
        )

    /**
     * 初始化 MCP 连接并获取工具列表
     */
    override suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.w(TAG, "McpSkill already initialized")
            return@withContext
        }

        try {
            Log.i(TAG, "Initializing MCP Skill: $serverName at $serverUrl")

            // 1. 创建 MCP 客户端
            mcpClient = McpClientFactory.create(serverUrl)

            // 2. 初始化连接 (握手)
            mcpClient!!.initialize()

            // 3. 获取工具列表
            tools = mcpClient!!.listTools()

            Log.i(TAG, "Loaded ${tools.size} tools from MCP server: $serverName")
            isInitialized = true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MCP Skill: $serverName", e)
            throw e
        }
    }

    /**
     * 获取此 Skill 提供的所有工具
     * 将 MCP 工具转换为 AndroidClaw 的 ToolDefinition
     */
    override fun getTools(): List<ToolDefinition> {
        if (!isInitialized) {
            Log.w(TAG, "McpSkill not initialized, returning empty tools")
            return emptyList()
        }

        return tools.map { mcpTool ->
            // 将 MCP 工具的 inputSchema 转换为 ToolParameter 列表
            val parameters = parseInputSchema(mcpTool.inputSchema)

            ToolDefinition(
                toolName = "${skillName}_${mcpTool.name}",
                displayName = mcpTool.name,
                description = mcpTool.description,
                parameters = parameters,
                returnType = "String"  // MCP 工具通常返回文本
            )
        }
    }

    /**
     * 执行工具
     * 将 AndroidClaw 的工具调用转发给 MCP Server
     */
    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {

            if (!isInitialized || mcpClient == null) {
                return@withContext ToolResult.Error("MCP Skill not initialized")
            }

            try {
                Log.i(TAG, "Executing MCP tool: $toolName with parameters: $parameters")

                // 从 toolName 中提取 MCP 工具名 (格式: mcp_<serverName>_<toolName>)
                val mcpToolName = toolName.removePrefix("${skillName}_")

                // 调用 MCP Server 的 tools/call
                val result = mcpClient!!.callTool(mcpToolName, parameters)

                Log.i(TAG, "MCP tool executed successfully: $toolName")
                ToolResult.Success(result)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute MCP tool: $toolName", e)
                ToolResult.Error("MCP tool execution failed: ${e.message}", e)
            }
        }

    /**
     * 释放资源
     */
    override fun release() {
        Log.i(TAG, "Releasing MCP Skill: $serverName")
        mcpClient?.close()
        mcpClient = null
        tools = emptyList()
        isInitialized = false
    }

    /**
     * 解析 MCP 工具的 inputSchema (JSON Schema) 转换为 ToolParameter 列表
     */
    private fun parseInputSchema(inputSchema: Map<String, Any>): List<ToolParameter> {
        val parameters = mutableListOf<ToolParameter>()

        try {
            val properties = inputSchema["properties"] as? Map<*, *>
            val required = inputSchema["required"] as? List<*>

            properties?.forEach { (key, value) ->
                val paramName = key as String
                val paramSchema = value as? Map<*, *>

                if (paramSchema != null) {
                    val type = paramSchema["type"] as? String ?: "string"
                    val description = paramSchema["description"] as? String ?: ""
                    val isRequired = required?.contains(paramName) ?: false

                    parameters.add(
                        ToolParameter(
                            name = paramName,
                            type = mapJsonTypeToKotlinType(type),
                            required = isRequired,
                            description = description
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse input schema", e)
        }

        return parameters
    }

    /**
     * 将 JSON Schema 类型映射为 Kotlin 类型
     */
    private fun mapJsonTypeToKotlinType(jsonType: String): String {
        return when (jsonType) {
            "string" -> "string"
            "integer", "number" -> "int"
            "boolean" -> "boolean"
            "array" -> "list"
            "object" -> "map"
            else -> "string"
        }
    }
}

/**
 * MCP Skill 管理器
 * 负责管理用户添加的 MCP Server
 */
object McpSkillManager {

    private const val TAG = "McpSkillManager"

    private val skills = mutableMapOf<String, McpSkill>()

    /**
     * 添加 MCP Server
     * @param serverUrl MCP Server URL
     * @param serverName 服务器名称
     * @return 创建的 McpSkill
     */
    suspend fun addServer(context: Context, serverUrl: String, serverName: String): McpSkill =
        withContext(Dispatchers.IO) {

            Log.i(TAG, "Adding MCP server: $serverName at $serverUrl")

            val skill = McpSkill(serverUrl, serverName)
            skill.initialize(context)

            skills[serverName] = skill

            Log.i(TAG, "MCP server added successfully: $serverName")
            skill
        }

    /**
     * 移除 MCP Server
     */
    fun removeServer(serverName: String) {
        Log.i(TAG, "Removing MCP server: $serverName")
        skills[serverName]?.release()
        skills.remove(serverName)
    }

    /**
     * 获取所有已添加的 MCP Skill
     */
    fun getAllSkills(): List<McpSkill> {
        return skills.values.toList()
    }

    /**
     * 获取指定的 MCP Skill
     */
    fun getSkill(serverName: String): McpSkill? {
        return skills[serverName]
    }

    /**
     * 释放所有 MCP Skill
     */
    fun releaseAll() {
        Log.i(TAG, "Releasing all MCP skills")
        skills.values.forEach { it.release() }
        skills.clear()
    }
}
