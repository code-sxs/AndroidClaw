// McpSkillManager.kt
// MCP Skill 管理器 - 管理所有 MCP Server 连接

package com.androidclaw.app.mcp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MCP 服务器信息
 */
data class McpServerInfo(
    val name: String,
    val url: String,
    val isConnected: Boolean = false,
    val toolCount: Int = 0,
    val lastError: String? = null
)

/**
 * MCP Skill 管理器
 * 管理所有 MCP Server 的连接和工具
 */
class McpSkillManager(private val context: Context) {

    companion object {
        private const val TAG = "McpSkillManager"
        private const val PREFS_NAME = "mcp_servers"
        private const val KEY_SERVERS = "servers_json"

        @Volatile
        private var instance: McpSkillManager? = null

        fun getInstance(context: Context): McpSkillManager {
            return instance ?: synchronized(this) {
                instance ?: McpSkillManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _servers = MutableStateFlow<List<McpServerInfo>>(emptyList())
    val servers: StateFlow<List<McpServerInfo>> = _servers.asStateFlow()

    private val _skills = MutableStateFlow<List<McpSkill>>(emptyList())
    val skills: StateFlow<List<McpSkill>> = _skills.asStateFlow()

    init {
        loadServers()
    }

    /**
     * 获取所有 MCP Skill
     */
    fun getAllSkills(): List<McpSkill> = _skills.value

    /**
     * 添加 MCP Server
     */
    suspend fun addServer(name: String, url: String): Result<Unit> {
        return try {
            val skill = McpSkill(url, name)
            skill.initialize(context)

            val serverInfo = McpServerInfo(
                name = name,
                url = url,
                isConnected = true,
                toolCount = skill.getTools().size
            )

            val currentServers = _servers.value.toMutableList()
            currentServers.add(serverInfo)
            _servers.value = currentServers

            val currentSkills = _skills.value.toMutableList()
            currentSkills.add(skill)
            _skills.value = currentSkills

            saveServers()
            Log.i(TAG, "Added MCP server: $name ($url)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add MCP server: $name", e)
            Result.failure(e)
        }
    }

    /**
     * 移除 MCP Server
     */
    fun removeServer(index: Int) {
        val currentServers = _servers.value.toMutableList()
        val currentSkills = _skills.value.toMutableList()

        if (index in currentServers.indices) {
            val server = currentServers[index]
            currentSkills.getOrNull(index)?.release()
            currentServers.removeAt(index)
            currentSkills.removeAt(index)
            _servers.value = currentServers
            _skills.value = currentSkills
            saveServers()
            Log.i(TAG, "Removed MCP server: ${server.name}")
        }
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(url: String): Result<String> {
        return try {
            val skill = McpSkill(url, "test")
            skill.initialize(context)
            val tools = skill.getTools()
            skill.release()
            Result.success("连接成功，发现 ${tools.size} 个工具")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存服务器列表
     */
    private fun saveServers() {
        try {
            val jsonArray = org.json.JSONArray()
            _servers.value.forEach { server ->
                val obj = org.json.JSONObject()
                obj.put("name", server.name)
                obj.put("url", server.url)
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVERS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save servers", e)
        }
    }

    /**
     * 加载服务器列表
     */
    private fun loadServers() {
        try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SERVERS, null) ?: return

            val jsonArray = org.json.JSONArray(json)
            val serverList = mutableListOf<McpServerInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                serverList.add(McpServerInfo(
                    name = obj.getString("name"),
                    url = obj.getString("url")
                ))
            }
            _servers.value = serverList
            Log.i(TAG, "Loaded ${serverList.size} MCP servers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load servers", e)
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        _skills.value.forEach { it.release() }
        _skills.value = emptyList()
        _servers.value = emptyList()
    }
}
