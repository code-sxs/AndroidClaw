// CalculatorSkillTest.kt
// CalculatorSkill 单元测试

package com.androidclaw.app.skills

import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalculatorSkillTest {

    private lateinit var skill: CalculatorSkill

    @Before
    fun setup() {
        skill = CalculatorSkill()
        runBlocking { skill.initialize(mock { }) }
    }

    // --- calculate ---

    @Test
    fun `calculate simple arithmetic`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to "2 + 3"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(5.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `calculate requires expression`() = runBlocking {
        val result = skill.executeTool("calculate", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("expression"))
    }

    @Test
    fun `calculate empty expression returns error`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to ""))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `calculate complex expression`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to "10 * (3 + 2)"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(50.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `calculate with negative numbers`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to "-5 + 3"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(-2.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `calculate rejects dangerous patterns`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to "exec('rm -rf /')"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `calculate rejects class access`() = runBlocking {
        val result = skill.executeTool("calculate", mapOf("expression" to "Java.lang.Runtime.getRuntime()"))
        assertTrue(result is ToolResult.Error)
    }

    // --- convert_unit ---

    @Test
    fun `convert length units`() = runBlocking {
        val result = skill.executeTool("convert_unit", mapOf(
            "value" to 1.0,
            "from_unit" to "km",
            "to_unit" to "m"
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(1000.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `convert weight units`() = runBlocking {
        val result = skill.executeTool("convert_unit", mapOf(
            "value" to 1.0,
            "from_unit" to "kg",
            "to_unit" to "g"
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(1000.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `convert temperature celsius to fahrenheit`() = runBlocking {
        val result = skill.executeTool("convert_unit", mapOf(
            "value" to 0.0,
            "from_unit" to "c",
            "to_unit" to "f"
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(32.0, (data["result"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `convert incompatible units returns error`() = runBlocking {
        val result = skill.executeTool("convert_unit", mapOf(
            "value" to 1.0,
            "from_unit" to "m",
            "to_unit" to "kg"
        ))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `same unit conversion returns same value`() = runBlocking {
        val result = skill.executeTool("convert_unit", mapOf(
            "value" to 42.0,
            "from_unit" to "m",
            "to_unit" to "m"
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(42.0, (data["result"] as Number).toDouble(), 0.001)
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("calculate", "convert_unit")))
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
        assertEquals("calculator", skill.skillName)
        assertEquals("计算器", skill.displayName)
    }
}
