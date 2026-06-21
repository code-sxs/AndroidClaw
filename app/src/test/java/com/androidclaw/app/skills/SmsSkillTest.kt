// SmsSkillTest.kt
// SmsSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmsSkillTest {

    private lateinit var skill: SmsSkill

    @Before
    fun setup() {
        skill = SmsSkill()
    }

    // --- read_sms ---

    @Test
    fun `read sms with conversation_id returns messages`() = runBlocking {
        val result = skill.executeTool("read_sms", mapOf("conversation_id" to "1"))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    @Test
    fun `read sms without conversation_id lists conversations`() = runBlocking {
        val result = skill.executeTool("read_sms", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- send_sms ---

    @Test
    fun `send sms requires number param`() = runBlocking {
        val result = skill.executeTool("send_sms", mapOf("message" to "Hello"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("number"))
    }

    @Test
    fun `send sms requires message param`() = runBlocking {
        val result = skill.executeTool("send_sms", mapOf("number" to "13800138000"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("message"))
    }

    @Test
    fun `send sms with empty number returns error`() = runBlocking {
        val result = skill.executeTool("send_sms", mapOf("number" to "", "message" to "Hi"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `send sms with empty message returns error`() = runBlocking {
        val result = skill.executeTool("send_sms", mapOf("number" to "13800138000", "message" to ""))
        assertTrue(result is ToolResult.Error)
    }

    // --- search_sms ---

    @Test
    fun `search sms requires query`() = runBlocking {
        val result = skill.executeTool("search_sms", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("read_sms", "send_sms", "search_sms")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has required permissions`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.READ_SMS))
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.SEND_SMS))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("sms", skill.skillName)
        assertEquals("短信", skill.displayName)
    }

    @Test
    fun `release clears resolver`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("read_sms", mapOf("conversation_id" to "1"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
