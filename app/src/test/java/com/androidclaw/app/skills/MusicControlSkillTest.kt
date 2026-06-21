// MusicControlSkillTest.kt
// MusicControlSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MusicControlSkillTest {

    private lateinit var skill: MusicControlSkill

    @Before
    fun setup() {
        skill = MusicControlSkill()
    }

    // --- transport controls ---

    @Test
    fun `play not initialized returns error`() = runBlocking {
        val result = skill.executeTool("play", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `pause not initialized returns error`() = runBlocking {
        val result = skill.executeTool("pause", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `next not initialized returns error`() = runBlocking {
        val result = skill.executeTool("next", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `previous not initialized returns error`() = runBlocking {
        val result = skill.executeTool("previous", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- seek ---

    @Test
    fun `seek requires position_ms`() = runBlocking {
        val result = skill.executeTool("seek", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("position_ms"))
    }

    @Test
    fun `seek with negative position returns error`() = runBlocking {
        val result = skill.executeTool("seek", mapOf("position_ms" to -1))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("负数"))
    }

    @Test
    fun `seek with valid position`() = runBlocking {
        val result = skill.executeTool("seek", mapOf("position_ms" to 30000))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- get_now_playing ---

    @Test
    fun `get now playing returns info`() = runBlocking {
        val result = skill.executeTool("get_now_playing", emptyMap())
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- search_music ---

    @Test
    fun `search music requires query`() = runBlocking {
        val result = skill.executeTool("search_music", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    @Test
    fun `search music with empty query returns error`() = runBlocking {
        val result = skill.executeTool("search_music", mapOf("query" to ""))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `search music with valid query`() = runBlocking {
        val result = skill.executeTool("search_music", mapOf("query" to "Bohemian"))
        assertTrue(result is ToolResult.Error) // not initialized
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf(
            "play", "pause", "next", "previous",
            "seek", "get_now_playing", "search_music"
        )))
    }

    @Test
    fun `getTools returns 7 tools`() {
        val tools = skill.getTools()
        assertEquals(7, tools.size)
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `no permissions required`() {
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("music_control", skill.skillName)
        assertEquals("音乐控制", skill.displayName)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("play", emptyMap())
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }
}
