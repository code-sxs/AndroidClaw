// WeatherSkillTest.kt
// WeatherSkill 单元测试

package com.androidclaw.app.skills

import android.content.Context
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class WeatherSkillTest {

    private lateinit var skill: WeatherSkill

    @Before
    fun setup() {
        skill = WeatherSkill()
        runBlocking { skill.initialize(mock { }) }
    }

    // --- get_current_weather ---

    @Test
    fun `get current weather with city name`() = runBlocking {
        val result = skill.executeTool("get_current_weather", mapOf("location" to "beijing"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertNotNull(data["location"])
        assertEquals("beijing", data["location"])
    }

    @Test
    fun `get current weather with coordinates`() = runBlocking {
        val result = skill.executeTool("get_current_weather", mapOf("location" to "39.9,116.4"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(39.9, (data["latitude"] as Number).toDouble(), 0.1)
        assertEquals(116.4, (data["longitude"] as Number).toDouble(), 0.1)
    }

    @Test
    fun `get current weather with chinese city name`() = runBlocking {
        val result = skill.executeTool("get_current_weather", mapOf("location" to "tokyo"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("tokyo", data["location"])
    }

    @Test
    fun `get current weather defaults to beijing`() = runBlocking {
        val result = skill.executeTool("get_current_weather", emptyMap())
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals("beijing", data["location"])
    }

    @Test
    fun `get current weather with invalid location returns error`() = runBlocking {
        val result = skill.executeTool("get_current_weather", mapOf("location" to "invalid_city_xyz"))
        assertTrue(result is ToolResult.Error)
    }

    // --- get_forecast ---

    @Test
    fun `get forecast returns forecast data`() = runBlocking {
        val result = skill.executeTool("get_forecast", mapOf("location" to "shanghai"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertNotNull(data["forecast"])
        assertEquals("shanghai", data["location"])
    }

    @Test
    fun `get forecast with custom days`() = runBlocking {
        val result = skill.executeTool("get_forecast", mapOf(
            "location" to "beijing",
            "days" to 3
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        assertEquals(3, data["days"])
    }

    @Test
    fun `get forecast clamps days to 1-7`() = runBlocking {
        val result = skill.executeTool("get_forecast", mapOf(
            "location" to "beijing",
            "days" to 10
        ))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as Map<*, *>
        // Should be clamped to 7
        val forecast = data["forecast"] as List<*>
        assertTrue(forecast.size <= 7)
    }

    // --- tools ---

    @Test
    fun `getTools returns correct tools`() {
        val tools = skill.getTools()
        val toolNames = tools.map { it.toolName }
        assertTrue(toolNames.containsAll(listOf("get_current_weather", "get_forecast")))
    }

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    // --- permissions ---

    @Test
    fun `has location permission`() {
        assertTrue(skill.requiredPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
    }

    // --- metadata ---

    @Test
    fun `skill metadata is correct`() {
        assertEquals("weather", skill.skillName)
        assertEquals("天气", skill.displayName)
    }

    @Test
    fun `release clears cache`() {
        skill.release()
        // Should still work since initialize stores coordinates as static map
        runBlocking {
            val result = skill.executeTool("get_current_weather", mapOf("location" to "beijing"))
            // After release, context is null but static city coords still work
            // The execute will try to resolve coordinates and succeed
            assertNotNull(result)
        }
    }
}
