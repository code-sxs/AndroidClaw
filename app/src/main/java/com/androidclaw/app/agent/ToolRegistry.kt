// ToolRegistry.kt
// 工具注册中心
// 管理所有 Skill 和 Tool 的注册、查找、执行

package com.androidclaw.app.agent

import android.content.Context
import android.util.Log
import com.androidclaw.app.skills.SkillDefinition
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工具注册中心 - 单例模式
 * 
 * 功能:
 * 1. 注册/注销 Skill
 * 2. 查找 Tool
 * 3. 执行 Tool
 * 4. 管理权限
 */
class ToolRegistry private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ToolRegistry"

        private var INSTANCE: ToolRegistry? = null

        fun getInstance(context: Context): ToolRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolRegistry(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 注册的 Skill 列表
    private val registeredSkills = mutableMapOf<String, SkillDefinition>()

    // 权限监听器
    private var permissionListener: PermissionListener? = null

    init {
        Log.i(TAG, "ToolRegistry initializing...")
    }

    /**
     * 注册 Skill
     */
    suspend fun registerSkill(skill: SkillDefinition): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Registering skill: ${skill.skillName}")

        try {
            // 1. 检查权限
            val missingPermissions = checkPermissions(skill.requiredPermissions)
            if (missingPermissions.isNotEmpty()) {
                Log.w(TAG, "Missing permissions for skill ${skill.skillName}: $missingPermissions")
                // 请求权限 (需要通过 UI 层处理)
                permissionListener?.onPermissionsRequired(skill.skillName, missingPermissions)
                return@withContext false
            }

            // 2. 初始化 Skill
            skill.initialize(context)

            // 3. 注册到 Map
            registeredSkills[skill.skillName] = skill

            Log.i(TAG, "Skill registered successfully: ${skill.skillName}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register skill: ${skill.skillName}", e)
            false
        }
    }

    /**
     * 注销 Skill
     */
    fun unregisterSkill(skillName: String) {
        Log.i(TAG, "Unregistering skill: $skillName")

        val skill = registeredSkills[skillName]
        if (skill != null) {
            skill.release()
            registeredSkills.remove(skillName)
            Log.i(TAG, "Skill unregistered: $skillName")
        } else {
            Log.w(TAG, "Skill not found: $skillName")
        }
    }

    /**
     * 获取所有已注册的 Skill
     */
    fun getAllSkills(): List<SkillDefinition> {
        return registeredSkills.values.toList()
    }

    /**
     * 查找 Tool
     * @param toolName 工具名称 (格式: "skillName.toolName" 或 "toolName")
     */
    fun findTool(toolName: String): ToolDefinition? {
        // 解析 skillName 和 toolName
        val parts = toolName.split(".")
        return if (parts.size == 2) {
            // 格式: "skillName.toolName"
            val skillName = parts[0]
            val actualToolName = parts[1]
            registeredSkills[skillName]?.getTools()?.find { it.toolName == actualToolName }
        } else {
            // 格式: "toolName" (在所有 Skill 中查找)
            registeredSkills.values.flatMap { it.getTools() }.find { it.toolName == toolName }
        }
    }

    /**
     * 执行 Tool
     * @param toolName 工具名称
     * @param parameters 工具参数
     */
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {

            Log.d(TAG, "Executing tool: $toolName, parameters: $parameters")

            // 1. 查找 Tool 所属的 Skill
            val parts = toolName.split(".")
            if (parts.size != 2) {
                Log.e(TAG, "Invalid tool name format: $toolName (expected: skillName.toolName)")
                return@withContext ToolResult.Error("Invalid tool name format: $toolName")
            }

            val skillName = parts[0]
            val actualToolName = parts[1]

            val skill = registeredSkills[skillName]
            if (skill == null) {
                Log.e(TAG, "Skill not found: $skillName")
                return@withContext ToolResult.Error("Skill not found: $skillName")
            }

            // 2. 执行 Tool
            try {
                val result = skill.executeTool(actualToolName, parameters)
                Log.d(TAG, "Tool executed successfully: $toolName, result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute tool: $toolName", e)
                ToolResult.Error(e.message ?: "Unknown error", e)
            }
        }

    /**
     * 检查权限
     */
    private fun checkPermissions(requiredPermissions: List<String>): List<String> {
        val missingPermissions = mutableListOf<String>()

        for (permission in requiredPermissions) {
            val granted = context.checkSelfPermission(permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                missingPermissions.add(permission)
            }
        }

        return missingPermissions
    }

    /**
     * 设置权限监听器
     */
    fun setPermissionListener(listener: PermissionListener) {
        permissionListener = listener
    }

    /**
     * 释放所有 Skill
     */
    fun releaseAll() {
        Log.i(TAG, "Releasing all skills...")
        registeredSkills.values.forEach { it.release() }
        registeredSkills.clear()
        Log.i(TAG, "All skills released")
    }
}

/**
 * 权限监听器
 */
interface PermissionListener {
    fun onPermissionsRequired(skillName: String, missingPermissions: List<String>)
}
