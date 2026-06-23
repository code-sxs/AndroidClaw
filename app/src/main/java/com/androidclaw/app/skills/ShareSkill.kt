// ShareSkill.kt
// 分享 Skill - 通过 Android Share Sheet 分享内容

package com.androidclaw.app.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * 分享 Skill
 * 通过 Android Share Sheet 分享文本、图片和文件
 */
class ShareSkill : SkillDefinition {

    companion object {
        private const val TAG = "ShareSkill"
        // FILE_PROVIDER_AUTHORITY 模板，运行时通过 context.packageName 替换
        private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

        // 支持的文件类型
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    private var context: Context? = null

    override val skillName: String = "share"
    override val displayName: String = "分享"
    override val description: String = "通过 Android Share Sheet 分享文本、图片和文件到其他应用"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        this.context = context
        Log.i(TAG, "ShareSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "share_text",
            displayName = "分享文本",
            description = "通过系统分享菜单分享文本内容",
            parameters = listOf(
                ToolParameter("text", "string", true, "要分享的文本内容"),
                ToolParameter("title", "string", false, "分享标题，默认 '分享'")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "share_image",
            displayName = "分享图片",
            description = "通过系统分享菜单分享本地图片文件",
            parameters = listOf(
                ToolParameter("image_uri", "string", true, "图片文件的 URI 或文件路径"),
                ToolParameter("title", "string", false, "分享标题，默认 '分享图片'")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "share_file",
            displayName = "分享文件",
            description = "通过系统分享菜单分享任意文件",
            parameters = listOf(
                ToolParameter("file_uri", "string", true, "文件的 URI 或文件路径"),
                ToolParameter("title", "string", false, "分享标题，默认 '分享文件'"),
                ToolParameter("mime_type", "string", false, "文件 MIME 类型，不填则自动推断")
            ),
            returnType = "boolean"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val ctx = context
            ?: return ToolResult.Error("ShareSkill not initialized")

        return try {
            when (toolName) {
                "share_text" -> shareText(ctx, parameters)
                "share_image" -> shareImage(ctx, parameters)
                "share_file" -> shareFile(ctx, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("分享失败: ${e.message}", e)
        }
    }

    private fun shareText(context: Context, params: Map<String, Any>): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Error("缺少参数: text")
        val title = params["title"] as? String ?: "分享"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
        Log.i(TAG, "Shared text: ${text.take(50)}...")
        return ToolResult.Success(mapOf(
            "status" to "shared",
            "type" to "text",
            "length" to text.length
        ))
    }

    private fun shareImage(context: Context, params: Map<String, Any>): ToolResult {
        val imageUriStr = params["image_uri"] as? String
            ?: return ToolResult.Error("缺少参数: image_uri")
        val title = params["title"] as? String ?: "分享图片"

        val imageUri = resolveUri(context, imageUriStr)
        if (imageUri == null) {
            return ToolResult.Error("无法解析图片 URI: $imageUriStr")
        }

        // 验证扩展名是否为图片
        val extension = imageUriStr.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in IMAGE_EXTENSIONS) {
            Log.w(TAG, "File extension '$extension' may not be an image type")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
        Log.i(TAG, "Shared image: $imageUriStr")
        return ToolResult.Success(mapOf(
            "status" to "shared",
            "type" to "image",
            "uri" to imageUri.toString()
        ))
    }

    private fun shareFile(context: Context, params: Map<String, Any>): ToolResult {
        val fileUriStr = params["file_uri"] as? String
            ?: return ToolResult.Error("缺少参数: file_uri")
        val title = params["title"] as? String ?: "分享文件"
        val mimeType = params["mime_type"] as? String ?: "*/*"

        val fileUri = resolveUri(context, fileUriStr)
        if (fileUri == null) {
            return ToolResult.Error("无法解析文件 URI: $fileUriStr")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
        Log.i(TAG, "Shared file: $fileUriStr (type: $mimeType)")
        return ToolResult.Success(mapOf(
            "status" to "shared",
            "type" to "file",
            "uri" to fileUri.toString(),
            "mime_type" to mimeType
        ))
    }

    /**
     * 解析 URI 或文件路径为 Android Uri
     * 如果是文件路径，通过 FileProvider 转换为 content:// URI
     */
    private fun resolveUri(context: Context, uriOrPath: String): Uri? {
        // 如果已经是 URI 格式
        if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            return Uri.parse(uriOrPath)
        }

        // 如果是文件路径，使用 FileProvider
        val file = File(uriOrPath)
        if (file.exists() && file.isFile) {
            return try {
                val authority = context.packageName + FILE_PROVIDER_SUFFIX
                FileProvider.getUriForFile(context, authority, file)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "FileProvider not configured for path: $uriOrPath", e)
                // 降级使用 file:// URI (不推荐，但作为后备)
                Uri.fromFile(file)
            }
        }

        // 尝试直接解析为 URI
        return try {
            Uri.parse(uriOrPath)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URI: $uriOrPath", e)
            null
        }
    }

    override fun release() {
        context = null
        Log.i(TAG, "ShareSkill released")
    }
}
