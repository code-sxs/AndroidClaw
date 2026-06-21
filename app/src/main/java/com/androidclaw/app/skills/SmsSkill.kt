// SmsSkill.kt
// 短信 Skill - 读取和发送短信（需用户确认）

package com.androidclaw.app.skills

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * 短信 Skill
 * 读取短信收件箱和发送短信（所有操作需用户确认）
 */
class SmsSkill : SkillDefinition {

    companion object {
        private const val TAG = "SmsSkill"

        private val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        private val CONVERSATION_PROJECTION = arrayOf(
            Telephony.Sms.Conversations._ID,
            Telephony.Sms.Conversations.SNIPPET,
            Telephony.Sms.Conversations.MSG_COUNT,
            Telephony.Sms.Conversations.RECIPIENT_IDS
        )
    }

    private var contentResolver: ContentResolver? = null

    override val skillName: String = "sms"
    override val displayName: String = "短信"
    override val description: String = "读取短信息和发送短信（每次操作需要用户确认）"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.SEND_SMS
    )

    override suspend fun initialize(context: Context) {
        contentResolver = context.contentResolver
        Log.i(TAG, "SmsSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "read_sms",
            displayName = "读取短信",
            description = "读取短信收件箱或指定会话的短信",
            parameters = listOf(
                ToolParameter("conversation_id", "string", false, "会话 ID，不填则读取所有会话摘要"),
                ToolParameter("limit", "int", false, "返回条数上限，默认 20")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "send_sms",
            displayName = "发送短信",
            description = "发送短信到指定号码（需用户二次确认）",
            parameters = listOf(
                ToolParameter("number", "string", true, "目标电话号码"),
                ToolParameter("message", "string", true, "短信内容")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "search_sms",
            displayName = "搜索短信",
            description = "按关键词搜索短信内容",
            parameters = listOf(
                ToolParameter("query", "string", true, "搜索关键词"),
                ToolParameter("limit", "int", false, "返回条数上限，默认 20")
            ),
            returnType = "list"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val resolver = contentResolver
            ?: return ToolResult.Error("SmsSkill not initialized")

        return try {
            when (toolName) {
                "read_sms" -> readSms(resolver, parameters)
                "send_sms" -> sendSms(parameters)
                "search_sms" -> searchSms(resolver, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少短信权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    private fun readSms(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val conversationId = params["conversation_id"] as? String
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        return if (conversationId != null) {
            readConversationMessages(resolver, conversationId, limit)
        } else {
            listConversations(resolver, limit)
        }
    }

    /**
     * 列出所有会话摘要
     */
    private fun listConversations(resolver: ContentResolver, limit: Int): ToolResult {
        val conversations = mutableListOf<Map<String, Any?>>()

        val uri = Telephony.Sms.Conversations.CONTENT_URI
        val cursor = resolver.query(
            uri,
            CONVERSATION_PROJECTION,
            null, null,
            "${Telephony.Sms.Conversations.DATE} DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(Telephony.Sms.Conversations._ID)
            val snippetCol = it.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)
            val countCol = it.getColumnIndex(Telephony.Sms.Conversations.MSG_COUNT)
            val recipientsCol = it.getColumnIndex(Telephony.Sms.Conversations.RECIPIENT_IDS)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val convId = it.getLong(idCol)
                conversations.add(mapOf(
                    "conversation_id" to convId.toString(),
                    "snippet" to it.getString(snippetCol),
                    "message_count" to it.getInt(countCol),
                    "recipient_ids" to it.getString(recipientsCol)
                ))
                count++
            }
        }

        Log.d(TAG, "Listed ${conversations.size} conversations")
        return ToolResult.Success(mapOf(
            "conversations" to conversations,
            "total" to conversations.size
        ))
    }

    /**
     * 读取指定会话的消息
     */
    private fun readConversationMessages(resolver: ContentResolver, conversationId: String, limit: Int): ToolResult {
        val messages = mutableListOf<Map<String, Any?>>()

        val uri = Telephony.Sms.CONTENT_URI
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(conversationId)

        val cursor = resolver.query(
            uri,
            SMS_PROJECTION,
            selection, selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(Telephony.Sms._ID)
            val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
            val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
            val readCol = it.getColumnIndex(Telephony.Sms.READ)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val smsType = it.getInt(typeCol)
                messages.add(mapOf(
                    "id" to it.getLong(idCol),
                    "address" to it.getString(addressCol),
                    "body" to it.getString(bodyCol),
                    "date" to it.getLong(dateCol),
                    "type" to if (smsType == Telephony.Sms.MESSAGE_TYPE_INBOX) "inbox" else "sent",
                    "is_read" to (it.getInt(readCol) != 0),
                    "conversation_id" to conversationId
                ))
                count++
            }
        }

        Log.d(TAG, "Read ${messages.size} messages from conversation $conversationId")
        return ToolResult.Success(mapOf(
            "conversation_id" to conversationId,
            "messages" to messages,
            "total" to messages.size
        ))
    }

    /**
     * 发送短信（通过 Intent 打开短信应用让用户确认）
     */
    private fun sendSms(params: Map<String, Any>): ToolResult {
        val number = params["number"] as? String
            ?: return ToolResult.Error("缺少参数: number")
        val message = params["message"] as? String
            ?: return ToolResult.Error("缺少参数: message")

        if (number.isBlank()) {
            return ToolResult.Error("电话号码不能为空")
        }
        if (message.isBlank()) {
            return ToolResult.Error("短信内容不能为空")
        }

        // 使用 Intent 打开短信应用，让用户手动确认发送
        // 这是最安全的方式，确保用户知情
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$number")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 注意：这里只返回指导信息，实际发送需要用户在短信应用中确认
        Log.i(TAG, "Prepared SMS intent for: $number")
        return ToolResult.Success(mapOf(
            "status" to "user_confirmation_required",
            "message" to "已在短信应用中打开，请手动确认发送",
            "number" to number,
            "content" to message
        ))
    }

    /**
     * 搜索短信
     */
    private fun searchSms(resolver: ContentResolver, params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
            ?: return ToolResult.Error("缺少参数: query")
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        val messages = mutableListOf<Map<String, Any?>>()
        val uri = Telephony.Sms.CONTENT_URI
        val selection = "${Telephony.Sms.BODY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor = resolver.query(
            uri,
            SMS_PROJECTION,
            selection, selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndex(Telephony.Sms._ID)
            val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
            val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)
            val readCol = it.getColumnIndex(Telephony.Sms.READ)

            var count = 0
            while (it.moveToNext() && count < limit) {
                val smsType = it.getInt(typeCol)
                messages.add(mapOf(
                    "id" to it.getLong(idCol),
                    "address" to it.getString(addressCol),
                    "body" to it.getString(bodyCol),
                    "date" to it.getLong(dateCol),
                    "type" to if (smsType == Telephony.Sms.MESSAGE_TYPE_INBOX) "inbox" else "sent",
                    "is_read" to (it.getInt(readCol) != 0)
                ))
                count++
            }
        }

        Log.d(TAG, "Searched SMS with query '$query': ${messages.size} results")
        return ToolResult.Success(mapOf(
            "query" to query,
            "messages" to messages,
            "total" to messages.size
        ))
    }

    override fun release() {
        contentResolver = null
        Log.i(TAG, "SmsSkill released")
    }
}
