// CalendarSkillTest.kt
// CalendarSkill 单元测试

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class CalendarSkillTest {

    private lateinit var skill: CalendarSkill
    private lateinit var resolver: ContentResolver

    @Before
    fun setup() {
        skill = CalendarSkill()
        resolver = mock()
        runBlocking { skill.initialize(mock { whenever(contentResolver) doReturn resolver }) }
    }

    // --- list_calendars ---

    @Test
    fun `list_calendars returns calendars`() = runBlocking {
        val cursor = createCalendarsCursor(
            listOf(
                Triple(1L, "Personal", "user@gmail.com"),
                Triple(2L, "Work", "work@company.com")
            )
        )
        whenever(resolver.query(
            eq(Calendars.CONTENT_URI), any(), isNull(), isNull(), anyString()
        )) doReturn cursor

        val result = skill.executeTool("read_events", mapOf("action" to "list_calendars"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as List<*>
        assertEquals(2, data.size)
    }

    @Test
    fun `list_calendars returns empty list when no calendars`() = runBlocking {
        whenever(resolver.query(
            eq(Calendars.CONTENT_URI), any(), isNull(), isNull(), anyString()
        )) doReturn null

        val result = skill.executeTool("read_events", mapOf("action" to "list_calendars"))

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as List<*>
        assertTrue(data.isEmpty())
    }

    // --- query_events ---

    @Test
    fun `query_events returns events in time range`() = runBlocking {
        whenever(resolver.query(
            any<Uri>(), any(), isNull(), isNull(), anyString()
        )) doReturn createEventsCursor(
            listOf(Triple(100L, "Meeting", "2024-01-01T10:00"))
        )

        val result = skill.executeTool("read_events", mapOf(
            "action" to "query_events",
            "start_time" to 1000L,
            "end_time" to 2000L
        ))

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `query_events requires start_time`() = runBlocking {
        val result = skill.executeTool("read_events", mapOf(
            "action" to "query_events",
            "end_time" to 2000L
        ))

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("start_time"))
    }

    // --- create_event ---

    @Test
    fun `create_event succeeds with valid params`() = runBlocking {
        val eventUri = mock<Uri>()
        whenever(eventUri.lastPathSegment) doReturn "42"
        whenever(resolver.insert(eq(Events.CONTENT_URI), any())) doReturn eventUri

        val result = skill.executeTool("create_event", mapOf(
            "calendar_id" to 1L,
            "title" to "Test Event",
            "start_time" to 1000L,
            "end_time" to 2000L
        ))

        assertTrue(result is ToolResult.Success)
        verify(resolver).insert(eq(Events.CONTENT_URI), any())
    }

    @Test
    fun `create_event fails without title`() = runBlocking {
        val result = skill.executeTool("create_event", mapOf(
            "calendar_id" to 1L,
            "start_time" to 1000L,
            "end_time" to 2000L
        ))

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("title"))
    }

    @Test
    fun `create_event fails when insert returns null`() = runBlocking {
        whenever(resolver.insert(eq(Events.CONTENT_URI), any())) doReturn null

        val result = skill.executeTool("create_event", mapOf(
            "calendar_id" to 1L,
            "title" to "Test",
            "start_time" to 1000L,
            "end_time" to 2000L
        ))

        assertTrue(result is ToolResult.Error)
    }

    // --- update_event ---

    @Test
    fun `update_event succeeds when event exists`() = runBlocking {
        val existCursor = createSimpleCursor(arrayOf(Events._ID), arrayOf(arrayOf("42")))
        whenever(resolver.query(any<Uri>(), any(), isNull(), isNull(), isNull())) doReturn existCursor
        whenever(resolver.update(any<Uri>(), any(), isNull(), isNull())) doReturn 1

        val result = skill.executeTool("update_event", mapOf(
            "event_id" to 42L,
            "title" to "Updated Title"
        ))

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `update_event fails when event not found`() = runBlocking {
        whenever(resolver.query(any<Uri>(), any(), isNull(), isNull(), isNull())) doReturn null

        val result = skill.executeTool("update_event", mapOf(
            "event_id" to 999L,
            "title" to "Ghost Title"
        ))

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("不存在"))
    }

    @Test
    fun `update_event fails with no fields to update`() = runBlocking {
        val existCursor = createSimpleCursor(arrayOf(Events._ID), arrayOf(arrayOf("42")))
        whenever(resolver.query(any<Uri>(), any(), isNull(), isNull(), isNull())) doReturn existCursor

        val result = skill.executeTool("update_event", mapOf("event_id" to 42L))

        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("未提供"))
    }

    // --- delete_event ---

    @Test
    fun `delete_event succeeds`() = runBlocking {
        whenever(resolver.delete(any<Uri>(), isNull(), isNull())) doReturn 1

        val result = skill.executeTool("delete_event", mapOf("event_id" to 42L))

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `delete_event returns error when nothing deleted`() = runBlocking {
        whenever(resolver.delete(any<Uri>(), isNull(), isNull())) doReturn 0

        val result = skill.executeTool("delete_event", mapOf("event_id" to 42L))

        assertTrue(result is ToolResult.Error)
    }

    // --- error handling ---

    @Test
    fun `unknown tool returns error`() = runBlocking {
        val result = skill.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `missing action returns error`() = runBlocking {
        val result = skill.executeTool("read_events", emptyMap())
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun `release clears resolver`() {
        skill.release()
        // After release, executeTool should return error
        runBlocking {
            val result = skill.executeTool("read_events", mapOf("action" to "list_calendars"))
            assertTrue(result is ToolResult.Error)
            assertTrue((result as ToolResult.Error).message.contains("not initialized"))
        }
    }

    // --- Helpers ---

    private fun createCalendarsCursor(data: List<Triple<Long, String, String>>): Cursor {
        return MatrixCursor(arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)).apply {
            data.forEach { (id, name, account) ->
                addRow(arrayOf(id, name, account))
            }
        }
    }

    private fun createEventsCursor(data: List<Triple<Long, String, String>>): Cursor {
        return MatrixCursor(arrayOf(
            Events._ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION,
            Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.CALENDAR_ID
        )).apply {
            data.forEach { (id, title, desc) ->
                addRow(arrayOf(id, title, desc, null, 1000L, 2000L, 0, 1L))
            }
        }
    }

    private fun createSimpleCursor(columns: Array<String>, rows: Array<Array<Any>>): Cursor {
        return MatrixCursor(columns).apply {
            rows.forEach { addRow(it) }
        }
    }
}
