// ClipboardSkillTest.kt
// ClipboardSkill 单元测试

package com.androidclaw.app.skills

import android.content.ClipData
import android.content.ClipboardManager
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ClipboardSkillTest {

    private lateinit var skill: ClipboardSkill
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setup() {
        skill = ClipboardSkill()
        clipboardManager = mock()
        runBlocking { skill.initialize(mock { 
            whenever(getSystemService("clipboard")) doReturn clipboardManager 
        }) }
    }

    // --- read_clipboard ---

    @Test
    fun `read clipboard with text content`() = runBlocking {
        val clipData = mock<ClipData>()
        val clipItem = mock<ClipData.Item>()
        whenever(clipItem.text) doReturn "Hello World"
        whenever(clipData.getItemAt(0)) doReturn clipItem
        whenever(clipData.itemCount) doReturn 1
        whenever(clipboardManager.primaryClip) doReturn clipData

        val result = skill.executeTool("read_clipboard", emptyMap())

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("Hello World", data["text"])
        assertEquals(true, data["has_content"])
    }

    @Test
    fun `read empty clipboard`() = runBlocking {
        whenever(clipboardManager.primaryClip) doReturn null

        val result = skill.executeTool("read_clipboard", emptyMap())

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(false, data["has_content"])
        assertNull(data["text"])
    }

    @Test
    fun `read clipboard with no items`() = runBlocking {
        val clipData = mock<ClipData>()
        whenever(clipData.itemCount) doReturn 0
        whenever(clipboardManager.primaryClip) doReturn clipData

        val result = skill.executeTool("read_clipboard", emptyMap())

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(false, data["has_content"])
    }

    @Test
    fun `read clipboard merges multiple items`() = runBlocking {
        val clipData = mock<ClipData>()
        val item1 = mock<ClipData.Item>()
        val item2 = mock<ClipData.Item>()
        whenever(item1.text) doReturn "Line 1"
        whenever(item2.text) doReturn "Line 2"
        whenever(clipData.getItemAt(0)) doReturn item1
        whenever(clipData.getItemAt(1)) doReturn item2
        whenever(clipData.itemCount) doReturn 2
        whenever(clipboardManager.primaryClip) doReturn clipData

        val result = skill.executeTool("read_clipboard", emptyMap())

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("Line 1\nLine 2", data["text"])
        assertEquals(2, data["item_count"])
    }

    // --- write_clipboard ---

    @Test
    fun `write clipboard succeeds`() = runBlocking {
        whenever(clipboardManager.primaryClip) doReturn null

        val result = skill.executeTool("write_clipboard", mapOf("text" to "Test content"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("written", data["status"])
        assertEquals(12, data["length"])
        verify(clipboardManager).setPrimaryClip(any())
    }

    @Test
    fun `write clipboard with custom label`() = runBlocking {
        val result = skill.executeTool("write_clipboard", mapOf(
            "text" to "Hello",
            "label" to "CustomLabel"
        ))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("CustomLabel", data["label"])
    }

    @Test
    fun `write clipboard missing text returns error`() = runBlocking {
        val result = skill.executeTool("write_clipboard", emptyMap())

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("text"))
    }

    // --- clear_clipboard ---

    @Test
    fun `clear clipboard succeeds`() = runBlocking {
        val result = skill.executeTool("clear_clipboard", emptyMap())

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("cleared", data["status"])
        assertEquals("AndroidClaw", data["label"])
        verify(clipboardManager).setPrimaryClip(any())
    }

    // --- edge cases ---

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `no required permissions`() {
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    @Test
    fun `release clears clipboard manager`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("read_clipboard", emptyMap())
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
