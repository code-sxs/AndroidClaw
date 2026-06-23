// ReminderSkill.kt
// 提醒/闹钟 Skill - 创建和管理系统提醒和闹钟

package com.androidclaw.app.skills

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 提醒/闹钟 Skill
 * 使用 AlarmManager + NotificationManager 创建和管理系统提醒
 */
class ReminderSkill : SkillDefinition {

    companion object {
        const val TAG = "ReminderSkill"
        const val CHANNEL_ID = "androidclaw_reminders"
        const val CHANNEL_NAME = "AndroidClaw 提醒"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_MESSAGE = "reminder_message"

        // 内存中的提醒列表（持久化方案可选择 DataStore/SharedPreferences）
        private val reminders = ConcurrentHashMap<String, ReminderData>()
    }

    data class ReminderData(
        val id: String,
        val title: String,
        val message: String,
        val triggerTimeMillis: Long,
        val isRecurring: Boolean = false,
        val recurringInterval: Long = 0  // 毫秒
    )

    private var context: Context? = null
    private var alarmManager: AlarmManager? = null
    private var notificationManager: NotificationManager? = null

    override val skillName: String = "reminder"
    override val displayName: String = "提醒"
    override val description: String = "创建和管理系统提醒与闹钟"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.SCHEDULE_EXACT_ALARM,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // 创建通知渠道
        createNotificationChannel()

        Log.i(TAG, "ReminderSkill initialized")
    }

    private fun createNotificationChannel() {
        notificationManager?.let { nm ->
            val existingChannel = nm.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "AndroidClaw 提醒通知"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
                Log.i(TAG, "Notification channel created: $CHANNEL_ID")
            }
        }
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "create_reminder",
            displayName = "创建提醒",
            description = "创建一条系统提醒，到指定时间弹出通知",
            parameters = listOf(
                ToolParameter("title", "string", true, "提醒标题"),
                ToolParameter("message", "string", true, "提醒内容"),
                ToolParameter("time", "long", true, "触发时间戳（毫秒）"),
                ToolParameter("is_recurring", "boolean", false, "是否重复提醒，默认 false"),
                ToolParameter("recurring_interval", "long", false, "重复间隔（毫秒），如 86400000 为每天重复")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "list_reminders",
            displayName = "列出提醒",
            description = "列出所有已创建的提醒",
            parameters = emptyList(),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "delete_reminder",
            displayName = "删除提醒",
            description = "删除指定的提醒",
            parameters = listOf(
                ToolParameter("reminder_id", "string", true, "提醒 ID")
            ),
            returnType = "boolean"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        if (context == null) {
            return ToolResult.Error("ReminderSkill not initialized")
        }

        return try {
            when (toolName) {
                "create_reminder" -> createReminder(parameters)
                "list_reminders" -> listReminders()
                "delete_reminder" -> deleteReminder(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    private fun createReminder(params: Map<String, Any>): ToolResult {
        val title = params["title"] as? String
            ?: return ToolResult.Error("缺少参数: title")
        val message = params["message"] as? String
            ?: return ToolResult.Error("缺少参数: message")
        val triggerTime = (params["time"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: time")
        val isRecurring = params["is_recurring"] as? Boolean ?: false
        val recurringInterval = (params["recurring_interval"] as? Number)?.toLong() ?: 0L

        val ctx = context ?: return ToolResult.Error("Context not available")
        val am = alarmManager ?: return ToolResult.Error("AlarmManager not available")

        // 验证触发时间
        val now = System.currentTimeMillis()
        if (triggerTime <= now) {
            return ToolResult.Error("触发时间必须在当前时间之后")
        }

        // 如果是重复提醒，验证间隔
        if (isRecurring && recurringInterval <= 0) {
            return ToolResult.Error("重复提醒需要有效的间隔时间")
        }

        // 生成唯一 ID
        val reminderId = "reminder_${System.currentTimeMillis()}_${Math.random().toString().substring(2, 8)}"

        // 保存提醒数据
        val reminderData = ReminderData(
            id = reminderId,
            title = title,
            message = message,
            triggerTimeMillis = triggerTime,
            isRecurring = isRecurring,
            recurringInterval = if (isRecurring) recurringInterval else 0
        )
        reminders[reminderId] = reminderData

        // 创建 Intent
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_MESSAGE, message)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            reminderId.hashCode(),
            intent,
            flags
        )

        // 设置闹钟
        if (isRecurring) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    recurringInterval,
                    pendingIntent
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                am.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(triggerTime))

        Log.i(TAG, "Reminder created: id=$reminderId, title=$title, time=$timeStr")
        return ToolResult.Success(mapOf(
            "reminder_id" to reminderId,
            "title" to title,
            "message" to message,
            "trigger_time" to triggerTime,
            "trigger_time_display" to timeStr,
            "is_recurring" to isRecurring,
            "recurring_interval" to if (isRecurring) recurringInterval else 0,
            "status" to "created"
        ))
    }

    private fun listReminders(): ToolResult {
        val now = System.currentTimeMillis()
        val reminderList = reminders.values
            .filter { it.triggerTimeMillis > now }  // 只列出未触发的
            .sortedBy { it.triggerTimeMillis }
            .map { reminder ->
                mapOf(
                    "reminder_id" to reminder.id,
                    "title" to reminder.title,
                    "message" to reminder.message,
                    "trigger_time" to reminder.triggerTimeMillis,
                    "trigger_time_display" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(reminder.triggerTimeMillis)),
                    "is_recurring" to reminder.isRecurring,
                    "time_remaining" to (reminder.triggerTimeMillis - now),
                    "time_remaining_display" to formatDuration(reminder.triggerTimeMillis - now)
                )
            }

        Log.d(TAG, "Listed ${reminderList.size} reminders")
        return ToolResult.Success(mapOf(
            "reminders" to reminderList,
            "total" to reminderList.size
        ))
    }

    private fun deleteReminder(params: Map<String, Any>): ToolResult {
        val reminderId = params["reminder_id"] as? String
            ?: return ToolResult.Error("缺少参数: reminder_id")

        val ctx = context ?: return ToolResult.Error("Context not available")
        val am = alarmManager ?: return ToolResult.Error("AlarmManager not available")

        val removed = reminders.remove(reminderId)
        if (removed == null) {
            return ToolResult.Error("提醒不存在: $reminderId")
        }

        // 取消 PendingIntent
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(ctx, reminderId.hashCode(), intent, flags)
        pendingIntent?.let { am.cancel(it) }

        Log.i(TAG, "Reminder deleted: $reminderId")
        return ToolResult.Success(mapOf(
            "reminder_id" to reminderId,
            "title" to removed.title,
            "status" to "deleted"
        ))
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}天 ${hours % 24}小时"
            hours > 0 -> "${hours}小时 ${minutes % 60}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "不到1分钟"
        }
    }

    override fun release() {
        context = null
        alarmManager = null
        notificationManager = null
        reminders.clear()
        Log.i(TAG, "ReminderSkill released")
    }
}

/**
 * 提醒 BroadcastReceiver
 * 收到 AlarmManager 广播时触发通知
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderSkill.EXTRA_REMINDER_ID)
        val title = intent.getStringExtra(ReminderSkill.EXTRA_REMINDER_TITLE)
        val message = intent.getStringExtra(ReminderSkill.EXTRA_REMINDER_MESSAGE)

        Log.i(TAG, "Reminder triggered: id=$reminderId, title=$title")

        // 创建通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ReminderSkill.CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                ReminderSkill.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = android.app.Notification.Builder(context, channelId)
            .setContentTitle(title ?: "提醒")
            .setContentText(message ?: "")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .build()

        val notificationId = reminderId?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
}
