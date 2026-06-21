// PhoneSkillTest.kt
// PhoneSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhoneSkillTest {

    private lateinit var skill: PhoneSkill

    @Before
    fun setup() {
        skill = PhoneSkill()
    }

    // --- make_call ---

    @Test
    fun `make call requires number`() = runBlocking {
        val result = skill.executeTool("make_call", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("number"))
    }

    @Test
    fun `make call with blank number returns error`() = runBlocking {
        val result = skill.executeTool("make_call", mapOf("number" to ""))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `make call not initialized returns error`() = runBlocking {
        val result = skill.executeTool("make_call", mapOf("number" to "13800138000"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `make call with valid number`() = runBlocking {
        val result = skill.executeTool("make_call", mapOf("number" to "+8613800138000"))
        assertTrue(result is ToolResult.Error) // not initialized
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    // --- get_call_log ---

    @Test
    fun `get call log with limit returns records`() = runBlocking {
        val result = skill.executeTool("get_call_log", mapOf("limit" to 10))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    @Test
    fun `get call log with type filter`() = runBlocking {
        val result = skill.executeTool("get_call_log", mapOf("limit" to 5, "type" to "missed"))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    @Test
    fun `get call log defaults to limit 20`() = runBlocking {
        val result = skill.executeTool("get_call_log", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("make_call", "get_call_log")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has required permissions`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.CALL_PHONE))
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.READ_CALL_LOG))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("phone", skill.skillName)
        assertEquals("电话", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("make_call", mapOf("number" to "10086"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
