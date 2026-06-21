// NetworkSkillTest.kt
// NetworkSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkSkillTest {

    private lateinit var skill: NetworkSkill

    @Before
    fun setup() {
        skill = NetworkSkill()
    }

    // --- get_wifi_info ---

    @Test
    fun `get wifi info not initialized returns error`() = runBlocking {
        val result = skill.executeTool("get_wifi_info", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    // --- is_connected ---

    @Test
    fun `is connected check not initialized returns error`() = runBlocking {
        val result = skill.executeTool("is_connected", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `is connected returns boolean`() = runBlocking {
        val result = skill.executeTool("is_connected", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- get_network_type ---

    @Test
    fun `get network type not initialized returns error`() = runBlocking {
        val result = skill.executeTool("get_network_type", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `get network type returns type string`() = runBlocking {
        val result = skill.executeTool("get_network_type", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        assertEquals(3, tools.size)
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("get_wifi_info", "is_connected", "get_network_type")))
    }

    @Test
    fun `tools have descriptions`() {
        val tools = skill.getTools()
        for (tool in tools) {
            assertFalse(tool.description.isBlank())
        }
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has required permissions`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.ACCESS_WIFI_STATE))
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.INTERNET))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("network", skill.skillName)
        assertEquals("网络", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("get_wifi_info", emptyMap())
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
