// PhoneSkill.kt
// 电话 Skill - 来电显示和拨号（需用户确认）

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.util.Log

/**
 * 电话 Skill
 * 提供拨号和通话记录查询功能（拨号需要用户二次确认）
 */
class PhoneSkill : SkillDefinition {

    companion object {
        private const val TAG = "PhoneSkill"

        private val CALL_LOG_PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.GEOCODED_LOCATION
        )
    }

    private var contentResolver: ContentResolver? = null
    private var context: Context? = null

    override val skillName: String = "phone"
    override val displayName: String = "电话"
    override val description: String = "拨打电话和查询通话记录（拨号需要用户二次确认）"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_CALL_LOG
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        this.contentResolver = context.contentResolver
        Log.i(TAG, "PhoneSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "make_call",
            displayName = "拨打电话",
            description = "拨打电话（会打开拨号界面让用户确认）",
            parameters = listOf(
                ToolParameter("number", "string", true, "电话号码")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "get_call_log",
            displayName = "通话记录",
            description = "查询最近的通话记录",
            parameters = listOf(
                ToolParameter("limit", "int", false, "返回条数上限，默认 20"),
                ToolParameter("type", "string", false, "通话类型过滤: incoming/outgoing/missed/all，默认 all")
            ),
            returnType = "list"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val resolver = contentResolver
            ?: return ToolResult.Error("PhoneSkill not initialized")

        return try {
            when (toolName) {
                "make_call" -> makeCall(parameters)
                "get_call_log" -> getCallLog(resolver, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少电话权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    /**
     * 拨打电话
     * 通过 ACTION_DIAL 打开拨号界面让用户手动确认，而非直接拨号
     */
    private fun makeCall(params: Map<String, Any>): ToolResult {
        val number = params["number"] as? String
            ?: return ToolResult.Error("缺少参数: number")

        if (number.isBlank()) {
            return ToolResult.Error("电话号码不能为空")
        }

        val ctx = context ?: return ToolResult.Error("Context not available")

        // 使用 ACTION_DIAL 让用户手动确认
        // 如需直接拨号（ACTION_CALL），需要 CALL_PHONE 权限
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ctx.startActivity(intent)
        Log.i(TAG, "Dial intent sent for: $number")
        return ToolResult.Success(mapOf(
            "status" to "user_confirmation_required",
            "message" to "已在拨号界面中打开，请手动确认拨号",
            "number" to number,
            "action" to "dial"
        ))
    }

    /**
     * 获取通话记录
     */
    private fun getCallLog(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt() ?: 20
        val typeFilter = params["type"] as? String ?: "all"

        // 构建类型过滤条件
        val selection = when (typeFilter.lowercase()) {
            "incoming" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}"
            "outgoing" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}"
            "missed" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
            else -> null
        }

        val calls = mutableListOf<Map<String, Any?>>()
        val uri = CallLog.Calls.CONTENT_URI

        val cursor = resolver.query(
            uri,
            CALL_LOG_PROJECTION,
            selection, null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberCol = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameCol = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
            val durationCol = it.getColumnIndex(CallLog.Calls.DURATION)
            val geoCol = it.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val callType = it.getInt(typeCol)
                val callTypeName = when (callType) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
                    else -> "unknown"
                }

                calls.add(mapOf(
                    "number" to it.getString(numberCol),
                    "name" to it.getString(nameCol),
                    "type" to callTypeName,
                    "date" to it.getLong(dateCol),
                    "duration" to it.getLong(durationCol),
                    "duration_display" to formatDuration(it.getLong(durationCol)),
                    "location" to it.getString(geoCol)
                ))
                count++
            }
        }

        Log.d(TAG, "Got ${calls.size} call logs (limit=$limit, type=$typeFilter)")
        return ToolResult.Success(mapOf(
            "calls" to calls,
            "total" to calls.size,
            "filter" to typeFilter
        ))
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return when {
            minutes > 0 -> "${minutes}分${secs}秒"
            else -> "${secs}秒"
        }
    }

    override fun release() {
        context = null
        contentResolver = null
        Log.i(TAG, "PhoneSkill released")
    }
}
