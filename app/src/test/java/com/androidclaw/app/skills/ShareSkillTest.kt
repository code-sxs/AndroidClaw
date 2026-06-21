// ShareSkillTest.kt
// ShareSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ShareSkillTest {

    private lateinit var skill: ShareSkill

    @Before
    fun setup() {
        skill = ShareSkill()
    }

    // --- share_text ---

    @Test
    fun `share text requires text parameter`() = runBlocking {
        val result = skill.executeTool("share_text", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("text"))
    }

    @Test
    fun `share text not initialized returns error`() = runBlocking {
        val result = skill.executeTool("share_text", mapOf("text" to "Hello"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    // --- share_image ---

    @Test
    fun `share image requires image_uri`() = runBlocking {
        val result = skill.executeTool("share_image", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("image_uri"))
    }

    // --- share_file ---

    @Test
    fun `share file requires file_uri`() = runBlocking {
        val result = skill.executeTool("share_file", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("file_uri"))
    }

    @Test
    fun `share file with custom mime type`() = runBlocking {
        val result = skill.executeTool("share_file", mapOf(
            "file_uri" to "/path/to/file.pdf",
            "title" to "Send PDF",
            "mime_type" to "application/pdf"
        ))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tool count`() {
        val tools = skill.getTools()
        assertEquals(3, tools.size)
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("share_text", "share_image", "share_file")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `no permissions required`() {
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("share", skill.skillName)
        assertEquals("分享", skill.displayName)
    }

    @Test
    fun `release clears context`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("share_text", mapOf("text" to "Hello"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
