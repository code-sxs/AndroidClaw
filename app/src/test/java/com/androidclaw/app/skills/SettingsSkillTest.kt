// SettingsSkillTest.kt
// SettingsSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsSkillTest {

    private lateinit var skill: SettingsSkill

    @Before
    fun setup() {
        skill = SettingsSkill()
    }

    // --- get_setting ---

    @Test
    fun `get setting requires type param`() = runBlocking {
        val result = skill.executeTool("get_setting", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("type"))
    }

    @Test
    fun `get setting with unsupported type returns error`() = runBlocking {
        val result = skill.executeTool("get_setting", mapOf("type" to "invalid"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("不支持"))
    }

    @Test
    fun `get brightness not initialized returns error`() = runBlocking {
        val result = skill.executeTool("get_setting", mapOf("type" to "brightness"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `get volume not initialized returns error`() = runBlocking {
        val result = skill.executeTool("get_setting", mapOf("type" to "volume"))
        assertTrue(result is ToolResult.Error)
    }

    // --- set_setting ---

    @Test
    fun `set setting requires type`() = runBlocking {
        val result = skill.executeTool("set_setting", mapOf("value" to "100"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `set setting requires value`() = runBlocking {
        val result = skill.executeTool("set_setting", mapOf("type" to "brightness"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `set brightness with invalid value returns error`() = runBlocking {
        val result = skill.executeTool("set_setting", mapOf(
            "type" to "brightness",
            "value" to "invalid"
        ))
        assertTrue(result is ToolResult.Error)
    }

    // --- toggle_bluetooth ---

    @Test
    fun `toggle bluetooth requires enable param`() = runBlocking {
        val result = skill.executeTool("toggle_bluetooth", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("enable"))
    }

    // --- toggle_flashlight ---

    @Test
    fun `toggle flashlight requires enable param`() = runBlocking {
        val result = skill.executeTool("toggle_flashlight", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("enable"))
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("get_setting", "set_setting", "toggle_bluetooth", "toggle_flashlight")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has write settings permission`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.WRITE_SETTINGS))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("settings", skill.skillName)
        assertEquals("设置", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("get_setting", mapOf("type" to "brightness"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
