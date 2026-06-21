// TranslationSkillTest.kt
// TranslationSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.llm.LLMManager
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class TranslationSkillTest {

    private lateinit var skill: TranslationSkill

    @Before
    fun setup() {
        skill = TranslationSkill()
    }

    // --- translate ---

    @Test
    fun `translate requires text parameter`() = runBlocking {
        val result = skill.executeTool("translate", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("text"))
    }

    @Test
    fun `translate requires target_lang`() = runBlocking {
        val result = skill.executeTool("translate", mapOf("text" to "Hello"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("target_lang"))
    }

    @Test
    fun `translate validates target language`() = runBlocking {
        val result = skill.executeTool("translate", mapOf(
            "text" to "Hello",
            "target_lang" to "xx"
        ))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `translate not initialized returns error`() = runBlocking {
        val result = skill.executeTool("translate", mapOf(
            "text" to "Hello",
            "target_lang" to "zh"
        ))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("not initialized"))
    }

    @Test
    fun `translate supports all target languages`() = runBlocking {
        val supported = setOf("zh", "en", "ja", "ko", "fr", "de", "es")
        for (lang in supported) {
            val result = skill.executeTool("translate", mapOf(
                "text" to "test",
                "target_lang" to lang
            ))
            // Should fail with "not initialized" not "不支持"
            assertTrue(result is ToolResult.Error)
            val msg = (result as ToolResult.Error).message
            assertFalse(msg.contains("不支持"), "Language $lang should be supported")
        }
    }

    // --- detect_language ---

    @Test
    fun `detect language requires text`() = runBlocking {
        val result = skill.executeTool("detect_language", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("text"))
    }

    @Test
    fun `detect empty text returns error`() = runBlocking {
        val result = skill.executeTool("detect_language", mapOf("text" to ""))
        assertTrue(result is ToolResult.Error)
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tool count`() {
        val tools = skill.getTools()
        assertEquals(2, tools.size)
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("translate", "detect_language")))
    }

    @Test
    fun `no permissions required`() {
        assertTrue(skill.requiredPermissions.isEmpty())
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `release clears state`() {
        skill.release()
        runBlocking {
            val result = skill.executeTool("translate", mapOf(
                "text" to "Hello",
                "target_lang" to "zh"
            ))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }

    // --- skill metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("translation", skill.skillName)
        assertEquals("翻译", skill.displayName)
        assertTrue(skill.description.isNotBlank())
    }
}
