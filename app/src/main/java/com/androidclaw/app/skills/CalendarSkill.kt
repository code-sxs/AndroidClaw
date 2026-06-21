// CalendarSkill.kt
// 日历 Skill - 读取/创建/修改/删除日历事件

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import java.util.TimeZone

/**
 * 日历 Skill
 * 提供日历事件的 CRUD 操作
 */
class CalendarSkill : SkillDefinition {

    companion object {
        private const val TAG = "CalendarSkill"

        // Projection: 查询日历事件时返回的列
        private val EVENT_PROJECTION = arrayOf(
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.EVENT_LOCATION,
            Events.DTSTART,
            Events.DTEND,
            Events.ALL_DAY,
            Events.CALENDAR_ID,
            Events.EVENT_TIMEZONE,
            Events.STATUS
        )

        private val CALENDAR_PROJECTION = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME
        )
    }

    private var contentResolver: ContentResolver? = null

    override val skillName: String = "calendar"
    override val displayName: String = "日历"
    override val description: String = "管理日历事件：读取、创建、修改和删除日历事件"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR
    )

    override suspend fun initialize(context: Context) {
        contentResolver = context.contentResolver
        Log.i(TAG, "CalendarSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "read_events",
            displayName = "读取日历事件",
            description = "查询指定时间范围内的日历事件，或查询所有日历列表",
            parameters = listOf(
                ToolParameter("action", "string", true, "操作类型: list_calendars(列出日历), query_events(查询事件)"),
                ToolParameter("calendar_id", "string", false, "日历 ID (查询事件时可选，不填则查所有日历)"),
                ToolParameter("start_time", "long", false, "开始时间戳(毫秒)，action=query_events 时建议提供"),
                ToolParameter("end_time", "long", false, "结束时间戳(毫秒)，action=query_events 时建议提供")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "create_event",
            displayName = "创建日历事件",
            description = "在指定日历中创建新事件",
            parameters = listOf(
                ToolParameter("calendar_id", "long", true, "目标日历 ID"),
                ToolParameter("title", "string", true, "事件标题"),
                ToolParameter("description", "string", false, "事件描述"),
                ToolParameter("location", "string", false, "事件地点"),
                ToolParameter("start_time", "long", true, "开始时间戳(毫秒)"),
                ToolParameter("end_time", "long", true, "结束时间戳(毫秒)"),
                ToolParameter("all_day", "boolean", false, "是否全天事件，默认 false"),
                ToolParameter("timezone", "string", false, "时区，默认系统时区")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "update_event",
            displayName = "修改日历事件",
            description = "修改已有的日历事件，只需传入要修改的字段",
            parameters = listOf(
                ToolParameter("event_id", "long", true, "事件 ID"),
                ToolParameter("title", "string", false, "新标题"),
                ToolParameter("description", "string", false, "新描述"),
                ToolParameter("location", "string", false, "新地点"),
                ToolParameter("start_time", "long", false, "新开始时间戳(毫秒)"),
                ToolParameter("end_time", "long", false, "新结束时间戳(毫秒)")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "delete_event",
            displayName = "删除日历事件",
            description = "删除指定的日历事件",
            parameters = listOf(
                ToolParameter("event_id", "long", true, "事件 ID"),
                ToolParameter("cancel_status", "int", false, "取消状态: 0=仍显示, 1=取消, 2=已修正, 3=已删除，默认 0")
            ),
            returnType = "map"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val resolver = contentResolver
            ?: return ToolResult.Error("CalendarSkill not initialized")

        return try {
            when (toolName) {
                "read_events" -> readEvents(resolver, parameters)
                "create_event" -> createEvent(resolver, parameters)
                "update_event" -> updateEvent(resolver, parameters)
                "delete_event" -> deleteEvent(resolver, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少日历权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    // --- read_events ---

    private fun readEvents(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val action = params["action"] as? String
            ?: return ToolResult.Error("缺少参数: action (list_calendars 或 query_events)")

        return when (action) {
            "list_calendars" -> listCalendars(resolver)
            "query_events" -> queryEvents(resolver, params)
            else -> ToolResult.Error("未知的 action: $action (应为 list_calendars 或 query_events)")
        }
    }

    private fun listCalendars(resolver: ContentResolver): ToolResult {
        val calendars = mutableListOf<Map<String, Any?>>()

        val cursor = resolver.query(
            Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            null, null,
            "${Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(Calendars._ID)
            val nameCol = it.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = it.getColumnIndex(Calendars.ACCOUNT_NAME)

            while (it.moveToNext()) {
                calendars.add(mapOf(
                    "id" to it.getLong(idCol),
                    "name" to it.getString(nameCol),
                    "account" to it.getString(accountCol)
                ))
            }
        }

        Log.i(TAG, "Found ${calendars.size} calendars")
        return ToolResult.Success(calendars)
    }

    private fun queryEvents(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val startTime = (params["start_time"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: start_time")
        val endTime = (params["end_time"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: end_time")
        val calendarId = params["calendar_id"] as? String?

        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startTime)
        ContentUris.appendId(builder, endTime)

        val selection = calendarId?.let { "${Instances.CALENDAR_ID} = ?" }
        val selectionArgs = calendarId?.let { arrayOf(it) }

        val events = mutableListOf<Map<String, Any?>>()

        val cursor = resolver.query(
            builder.build(),
            EVENT_PROJECTION,
            selection, selectionArgs,
            "${Instances.DTSTART} ASC"
        )

        cursor?.use {
            val titleCol = it.getColumnIndex(Events.TITLE)
            val descCol = it.getColumnIndex(Events.DESCRIPTION)
            val locCol = it.getColumnIndex(Events.EVENT_LOCATION)
            val startCol = it.getColumnIndex(Events.DTSTART)
            val endCol = it.getColumnIndex(Events.DTEND)
            val allDayCol = it.getColumnIndex(Events.ALL_DAY)
            val calIdCol = it.getColumnIndex(Events.CALENDAR_ID)

            while (it.moveToNext()) {
                events.add(mapOf(
                    "id" to it.getLong(it.getColumnIndex(Events._ID)),
                    "title" to it.getString(titleCol),
                    "description" to it.getString(descCol),
                    "location" to it.getString(locCol),
                    "start_time" to it.getLong(startCol),
                    "end_time" to it.getLong(endCol),
                    "all_day" to (it.getInt(allDayCol) != 0),
                    "calendar_id" to it.getLong(calIdCol)
                ))
            }
        }

        Log.i(TAG, "Found ${events.size} events between $startTime and $endTime")
        return ToolResult.Success(events)
    }

    // --- create_event ---

    private fun createEvent(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val calendarId = (params["calendar_id"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: calendar_id")
        val title = params["title"] as? String
            ?: return ToolResult.Error("缺少参数: title")
        val startTime = (params["start_time"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: start_time")
        val endTime = (params["end_time"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: end_time")

        val description = params["description"] as? String
        val location = params["location"] as? String
        val allDay = params["all_day"] as? Boolean ?: false
        val timezone = (params["timezone"] as? String) ?: TimeZone.getDefault().id

        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.DTSTART, startTime)
            put(Events.DTEND, endTime)
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            put(Events.EVENT_TIMEZONE, if (allDay) CalendarContract.EVENT_TIMEZONE_UTC else timezone)
            description?.let { put(Events.DESCRIPTION, it) }
            location?.let { put(Events.EVENT_LOCATION, it) }
        }

        val uri = resolver.insert(Events.CONTENT_URI, values)
            ?: return ToolResult.Error("创建事件失败: insert 返回 null")

        val eventId = ContentUris.parseId(uri)
        Log.i(TAG, "Created event: id=$eventId, title=$title")
        return ToolResult.Success(mapOf("event_id" to eventId, "status" to "created"))
    }

    // --- update_event ---

    private fun updateEvent(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val eventId = (params["event_id"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: event_id")

        // 验证事件存在
        val eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val exists = resolver.query(eventUri, arrayOf(Events._ID), null, null, null)?.use {
            it.moveToFirst()
        } ?: false

        if (!exists) {
            return ToolResult.Error("事件不存在: id=$eventId")
        }

        val values = ContentValues()

        (params["title"] as? String)?.let { put(Events.TITLE, it) }
        (params["description"] as? String)?.let { put(Events.DESCRIPTION, it) }
        (params["location"] as? String)?.let { put(Events.EVENT_LOCATION, it) }
        (params["start_time"] as? Number)?.toLong()?.let { put(Events.DTSTART, it) }
        (params["end_time"] as? Number)?.toLong()?.let { put(Events.DTEND, it) }

        if (values.size() == 0) {
            return ToolResult.Error("未提供任何要修改的字段")
        }

        val rowsUpdated = resolver.update(eventUri, values, null, null)

        if (rowsUpdated > 0) {
            Log.i(TAG, "Updated event: id=$eventId")
            ToolResult.Success(mapOf("event_id" to eventId, "rows_updated" to rowsUpdated, "status" to "updated"))
        } else {
            ToolResult.Error("更新失败: 未修改任何行")
        }
    }

    // --- delete_event ---

    private fun deleteEvent(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val eventId = (params["event_id"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: event_id")
        val cancelStatus = (params["cancel_status"] as? Number)?.toInt() ?: 0

        val eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)

        // 软删除: 设置 STATUS 为取消状态
        val values = ContentValues().apply {
            put(Events.STATUS, cancelStatus)
        }

        val rowsDeleted = resolver.delete(eventUri, null, null)

        if (rowsDeleted > 0) {
            Log.i(TAG, "Deleted event: id=$eventId")
            ToolResult.Success(mapOf("event_id" to eventId, "rows_deleted" to rowsDeleted, "status" to "deleted"))
        } else {
            ToolResult.Error("删除失败: 事件不存在或已被删除")
        }
    }

    override fun release() {
        contentResolver = null
        Log.i(TAG, "CalendarSkill released")
    }
}
