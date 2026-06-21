// ReminderSkillTest.kt
// ReminderSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReminderSkillTest {

    private lateinit var skill: ReminderSkill

    @Before
    fun setup() {
        skill = ReminderSkill()
    }

    // --- create_reminder ---

    @Test
    fun `create reminder requires title`() = runBlocking {
        val result = skill.executeTool("create_reminder", mapOf(
            "message" to "Test",
            "time" to (System.currentTimeMillis() + 60000)
        ))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("title"))
    }

    @Test
    fun `create reminder requires message`() = runBlocking {
        val result = skill.executeTool("create_reminder", mapOf(
            "title" to "Test",
            "time" to (System.currentTimeMillis() + 60000)
        ))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `create reminder requires future time`() = runBlocking {
        val result = skill.executeTool("create_reminder", mapOf(
            "title" to "Test",
            "message" to "Test message",
            "time" to (System.currentTimeMillis() - 1000)
        ))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `create reminder with past time returns error`() = runBlocking {
        val result = skill.executeTool("create_reminder", mapOf(
            "title" to "Test",
            "message" to "Past reminder",
            "time" to 1000L
        ))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `create reminder not initialized returns error`() = runBlocking {
        val result = skill.executeTool("create_reminder", mapOf(
            "title" to "Test",
            "message" to "Message",
            "time" to (System.currentTimeMillis() + 60000)
        ))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    // --- list_reminders ---

    @Test
    fun `list reminders returns empty list when none`() = runBlocking {
        val result = skill.executeTool("list_reminders", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    // --- delete_reminder ---

    @Test
    fun `delete reminder requires reminder_id`() = runBlocking {
        val result = skill.executeTool("delete_reminder", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("create_reminder", "list_reminders", "delete_reminder")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has required permissions`() {
        assertTrue(skill.requiredPermissions.isNotEmpty())
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.SCHEDULE_EXACT_ALARM))
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.POST_NOTIFICATIONS))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("reminder", skill.skillName)
        assertEquals("提醒", skill.displayName)
    }
}
