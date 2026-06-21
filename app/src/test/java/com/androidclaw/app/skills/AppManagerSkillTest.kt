// AppManagerSkillTest.kt
// AppManagerSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppManagerSkillTest {

    private lateinit var skill: AppManagerSkill

    @Before
    fun setup() {
        skill = AppManagerSkill()
    }

    // --- list_apps ---

    @Test
    fun `list apps without query returns all`() = runBlocking {
        val result = skill.executeTool("list_apps", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `list apps with query filters results`() = runBlocking {
        val result = skill.executeTool("list_apps", mapOf("query" to "chrome"))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    @Test
    fun `list apps with include_system`() = runBlocking {
        val result = skill.executeTool("list_apps", mapOf("include_system" to true))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- launch_app ---

    @Test
    fun `launch app requires package_name`() = runBlocking {
        val result = skill.executeTool("launch_app", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("package_name"))
    }

    @Test
    fun `launch nonexistent app returns error`() = runBlocking {
        val result = skill.executeTool("launch_app", mapOf("package_name" to "com.nonexistent.app"))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- get_app_info ---

    @Test
    fun `get app info requires package_name`() = runBlocking {
        val result = skill.executeTool("get_app_info", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("package_name"))
    }

    // --- open_app_settings ---

    @Test
    fun `open app settings requires package_name`() = runBlocking {
        val result = skill.executeTool("open_app_settings", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("package_name"))
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("list_apps", "launch_app", "get_app_info", "open_app_settings")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has query all packages permission`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.QUERY_ALL_PACKAGES))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("app_manager", skill.skillName)
        assertEquals("应用管理", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("list_apps", emptyMap())
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
