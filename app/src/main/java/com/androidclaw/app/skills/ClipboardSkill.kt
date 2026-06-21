// ClipboardSkill.kt
// 剪贴板 Skill - 读取/写入剪贴板内容

package com.androidclaw.app.skills

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * 剪贴板 Skill
 * 提供读取和写入系统剪贴板的能力
 */
class ClipboardSkill : SkillDefinition {

    companion object {
        private const val TAG = "ClipboardSkill"
    }

    private var clipboardManager: ClipboardManager? = null

    override val skillName: String = "clipboard"
    override val displayName: String = "剪贴板"
    override val description: String = "管理系统剪贴板：读取和写入剪贴板内容"
    override val requiredPermissions: List<String> = emptyList() // 剪贴板不需要额外权限

    override suspend fun initialize(context: Context) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager == null) {
            Log.w(TAG, "ClipboardManager not available on this device")
        } else {
            Log.i(TAG, "ClipboardSkill initialized")
        }
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "read_clipboard",
            displayName = "读取剪贴板",
            description = "读取系统剪贴板中的文本内容",
            parameters = listOf(
                ToolParameter("label", "string", false, "剪贴板标签 (可选，用于标识来源)")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "write_clipboard",
            displayName = "写入剪贴板",
            description = "将文本写入系统剪贴板",
            parameters = listOf(
                ToolParameter("text", "string", true, "要写入的文本内容"),
                ToolParameter("label", "string", false, "剪贴板标签，默认 'AndroidClaw'")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "clear_clipboard",
            displayName = "清空剪贴板",
            description = "清空系统剪贴板",
            parameters = listOf(
                ToolParameter("label", "string", false, "剪贴板标签，默认 'AndroidClaw'")
            ),
            returnType = "map"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val manager = clipboardManager
            ?: return ToolResult.Error("ClipboardSkill not initialized or ClipboardManager unavailable")

        return try {
            when (toolName) {
                "read_clipboard" -> readClipboard(manager, parameters)
                "write_clipboard" -> writeClipboard(manager, parameters)
                "clear_clipboard" -> clearClipboard(manager, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException for $toolName (possible secure clipboard restriction)", e)
            ToolResult.Error("无法访问剪贴板（安全限制）: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    // --- read_clipboard ---

    private fun readClipboard(manager: ClipboardManager, params: Map<String, Any>): ToolResult {
        val label = params["label"] as? String

        val clip = manager.primaryClip
            ?: return ToolResult.Success(mapOf(
                "text" to null,
                "has_content" to false,
                "label" to label,
                "message" to "剪贴板为空"
            ))

        if (clip.itemCount <= 0) {
            return ToolResult.Success(mapOf(
                "text" to null,
                "has_content" to false,
                "label" to label,
                "message" to "剪贴板无内容"
            ))
        }

        // 提取文本 (合并多个 clip item)
        val textBuilder = StringBuilder()
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val text = item.text?.toString()
            if (text != null) {
                if (i > 0) textBuilder.append("\n")
                textBuilder.append(text)
            }
        }

        val text = textBuilder.toString()
        Log.i(TAG, "Read clipboard: ${text.length} chars")

        return ToolResult.Success(mapOf(
            "text" to text,
            "has_content" to true,
            "length" to text.length,
            "item_count" to clip.itemCount,
            "label" to label
        ))
    }

    // --- write_clipboard ---

    private fun writeClipboard(manager: ClipboardManager, params: Map<String, Any>): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Error("缺少参数: text")
        val label = params["label"] as? String ?: "AndroidClaw"

        val clip = ClipData.newPlainText(label, text)
        manager.setPrimaryClip(clip)

        Log.i(TAG, "Wrote clipboard: ${text.length} chars, label=$label")
        return ToolResult.Success(mapOf(
            "status" to "written",
            "length" to text.length,
            "label" to label
        ))
    }

    // --- clear_clipboard ---

    private fun clearClipboard(manager: ClipboardManager, params: Map<String, Any>): ToolResult {
        val label = params["label"] as? String ?: "AndroidClaw"

        val clip = ClipData.newPlainText(label, "")
        manager.setPrimaryClip(clip)

        Log.i(TAG, "Clipboard cleared")
        return ToolResult.Success(mapOf(
            "status" to "cleared",
            "label" to label
        ))
    }

    override fun release() {
        clipboardManager = null
        Log.i(TAG, "ClipboardSkill released")
    }
}
