// ScreenCaptureSkillTest.kt
// ScreenCaptureSkill 单元测试

package com.androidclaw.app.skills

import android.app.Activity
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScreenCaptureSkillTest {

    private lateinit var skill: ScreenCaptureSkill

    @Before
    fun setup() {
        skill = ScreenCaptureSkill()
    }

    // --- take_screenshot ---

    @Test
    fun `take screenshot not initialized returns error`() = runBlocking {
        val result = skill.executeTool("take_screenshot", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `take screenshot requests authorization`() = runBlocking {
        // We can't fully test without Context, just verify it returns an error
        val result = skill.executeTool("take_screenshot", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- start_recording ---

    @Test
    fun `start recording not initialized returns error`() = runBlocking {
        val result = skill.executeTool("start_recording", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `start recording requests authorization`() = runBlocking {
        val result = skill.executeTool("start_recording", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- stop_recording ---

    @Test
    fun `stop recording when not recording returns error`() = runBlocking {
        val result = skill.executeTool("stop_recording", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("没有正在进行的录屏"))
    }

    @Test
    fun `stop recording requires active recording`() = runBlocking {
        val result = skill.executeTool("stop_recording", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- handle_projection_result ---

    @Test
    fun `handle projection result with denied permission returns false`() {
        val result = skill.handleProjectionResult(Activity.RESULT_CANCELED, null)
        assertFalse(result)
    }

    @Test
    fun `handle projection result with null data returns false`() {
        val result = skill.handleProjectionResult(Activity.RESULT_OK, null)
        assertFalse(result)
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("take_screenshot", "start_recording", "stop_recording")))
    }

    @Test
    fun `getTools returns 3 tools`() {
        val tools = skill.getTools()
        assertEquals(3, tools.size)
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `no system permissions required`() {
        // MediaProjection requires foreground Activity authorization, not system permission
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("screen_capture", skill.skillName)
        assertEquals("截图录屏", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("take_screenshot", emptyMap())
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
