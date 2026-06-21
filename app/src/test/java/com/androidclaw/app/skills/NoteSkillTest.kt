// NoteSkillTest.kt
// NoteSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoteSkillTest {

    private lateinit var skill: NoteSkill

    @Before
    fun setup() {
        skill = NoteSkill()
        runBlocking { skill.initialize(mock { }) }
    }

    // --- create_note ---

    @Test
    fun `create note succeeds`() = runBlocking {
        val result = skill.executeTool("create_note", mapOf(
            "title" to "Test Note",
            "content" to "This is a test note"
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertNotNull(data["note_id"])
        assertEquals("Test Note", data["title"])
    }

    @Test
    fun `create note requires title`() = runBlocking {
        val result = skill.executeTool("create_note", mapOf("content" to "Content only"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("title"))
    }

    @Test
    fun `create note requires content`() = runBlocking {
        val result = skill.executeTool("create_note", mapOf("title" to "Title only"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("content"))
    }

    // --- list_notes ---

    @Test
    fun `list notes returns created notes`() = runBlocking {
        skill.executeTool("create_note", mapOf("title" to "Note 1", "content" to "Content 1"))
        skill.executeTool("create_note", mapOf("title" to "Note 2", "content" to "Content 2"))

        val result = skill.executeTool("list_notes", emptyMap())
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        val notes = data["notes"] as List<*>
        assertEquals(2, notes.size)
    }

    @Test
    fun `list notes with query filters results`() = runBlocking {
        skill.executeTool("create_note", mapOf("title" to "Shopping List", "content" to "Milk, Bread"))
        skill.executeTool("create_note", mapOf("title" to "Meeting Notes", "content" to "Q3 review"))

        val result = skill.executeTool("list_notes", mapOf("query" to "Shopping"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        val notes = data["notes"] as List<*>
        assertEquals(1, notes.size)
    }

    // --- update_note ---

    @Test
    fun `update note changes title and content`() = runBlocking {
        val create = skill.executeTool("create_note", mapOf("title" to "Old Title", "content" to "Old content"))
        val noteId = ((create as ToolResult.Success).data as Map<*, *>)["note_id"]

        val result = skill.executeTool("update_note", mapOf(
            "note_id" to noteId,
            "title" to "New Title",
            "content" to "New content"
        ))
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `update note requires note_id`() = runBlocking {
        val result = skill.executeTool("update_note", mapOf("title" to "New"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `update nonexistent note returns error`() = runBlocking {
        val result = skill.executeTool("update_note", mapOf(
            "note_id" to "nonexistent_id",
            "title" to "New"
        ))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("不存在"))
    }

    // --- delete_note ---

    @Test
    fun `delete note removes it`() = runBlocking {
        val create = skill.executeTool("create_note", mapOf("title" to "Delete Me", "content" to "Gone"))
        val noteId = ((create as ToolResult.Success).data as Map<*, *>)["note_id"]

        val deleteResult = skill.executeTool("delete_note", mapOf("note_id" to noteId))
        assertTrue(deleteResult is ToolResult.Success)

        val listResult = skill.executeTool("list_notes", emptyMap())
        val data = (listResult as ToolResult.Success).data as Map<*, *>
        val notes = data["notes"] as List<*>
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `delete nonexistent note returns error`() = runBlocking {
        val result = skill.executeTool("delete_note", mapOf("note_id" to "nonexistent"))
        assertTrue(result is ToolResult.Error)
    }

    // --- search_notes ---

    @Test
    fun `search notes finds matching content`() = runBlocking {
        skill.executeTool("create_note", mapOf("title" to "Meeting", "content" to "Discuss budget plan"))
        skill.executeTool("create_note", mapOf("title" to "Todo", "content" to "Buy groceries"))

        val result = skill.executeTool("search_notes", mapOf("query" to "budget"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(1, data["total"])
    }

    @Test
    fun `search requires query param`() = runBlocking {
        val result = skill.executeTool("search_notes", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    // --- tools ---

    @Test
    fun `getTools returns 5 tools`() {
        val tools = skill.getTools()
        assertEquals(5, tools.size)
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("create_note", "list_notes", "update_note", "delete_note", "search_notes")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- metadata ---

    @Test
    fun `no permissions required`() {
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    @Test
    fun `skill metadata is correct`() {
        assertEquals("note", skill.skillName)
        assertEquals("笔记", skill.displayName)
    }
}
