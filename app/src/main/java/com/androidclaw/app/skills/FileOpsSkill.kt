// FileOpsSkill.kt
// 文件操作 Skill - 安全沙箱内的文件读写操作

package com.androidclaw.app.skills

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件操作 Skill
 * 在安全沙箱内提供文件读写、目录列表和文件信息查询
 * 安全限制：只能访问应用私有目录和用户明确授权的目录
 */
class FileOpsSkill : SkillDefinition {

    companion object {
        private const val TAG = "FileOpsSkill"

        // 允许访问的基础目录
        private val ALLOWED_BASE_DIRS = listOf(
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_MOVIES
        )

        // 禁止访问的系统目录
        private val FORBIDDEN_DIRS = listOf(
            "/system", "/data", "/proc", "/sys", "/etc",
            "/vendor", "/dev", "/cache", "/storage/emulated/0/Android"
        )
    }

    private var context: Context? = null
    private var appPrivateDir: File? = null

    override val skillName: String = "file_ops"
    override val displayName: String = "文件操作"
    override val description: String = "安全沙箱内的文件读写、目录管理和文件信息查询"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        appPrivateDir = context.filesDir
        Log.i(TAG, "FileOpsSkill initialized, private dir: ${context.filesDir.absolutePath}")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "read_file",
            displayName = "读取文件",
            description = "读取指定文件的文本内容",
            parameters = listOf(
                ToolParameter("path", "string", true, "文件路径（绝对路径或相对于应用私有目录的相对路径）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "write_file",
            displayName = "写入文件",
            description = "将文本内容写入指定文件（自动创建父目录）",
            parameters = listOf(
                ToolParameter("path", "string", true, "文件路径"),
                ToolParameter("content", "string", true, "文件内容"),
                ToolParameter("append", "boolean", false, "是否追加到文件末尾，默认 false")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "list_directory",
            displayName = "列出目录",
            description = "列出指定目录下的所有文件和子目录",
            parameters = listOf(
                ToolParameter("path", "string", true, "目录路径"),
                ToolParameter("show_hidden", "boolean", false, "是否显示隐藏文件，默认 false")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "delete_file",
            displayName = "删除文件",
            description = "删除指定文件或空目录",
            parameters = listOf(
                ToolParameter("path", "string", true, "要删除的文件或目录路径"),
                ToolParameter("recursive", "boolean", false, "是否递归删除目录，默认 false")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "get_file_info",
            displayName = "获取文件信息",
            description = "获取文件或目录的详细信息（大小、类型、修改时间等）",
            parameters = listOf(
                ToolParameter("path", "string", true, "文件或目录路径")
            ),
            returnType = "map"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        if (context == null) {
            return ToolResult.Error("FileOpsSkill not initialized")
        }

        return try {
            when (toolName) {
                "read_file" -> readFile(parameters)
                "write_file" -> writeFile(parameters)
                "list_directory" -> listDirectory(parameters)
                "delete_file" -> deleteFile(parameters)
                "get_file_info" -> getFileInfo(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error for $toolName", e)
            ToolResult.Error("权限不足或目录受限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    /**
     * 解析并验证路径安全性
     */
    private fun resolveAndValidatePath(path: String): ToolResult.Error? {
        if (path.isBlank()) {
            return ToolResult.Error("路径不能为空")
        }

        // 检查是否在禁止目录中
        for (forbidden in FORBIDDEN_DIRS) {
            if (path.startsWith(forbidden)) {
                return ToolResult.Error("禁止访问系统目录: $forbidden")
            }
        }

        // 检查路径穿越
        val normalizedPath = File(path).canonicalPath
        if (normalizedPath.contains("..")) {
            return ToolResult.Error("路径包含非法字符（..）")
        }

        return null
    }

    /**
     * 检查文件是否在允许的目录范围内
     */
    private fun isPathAllowed(file: File): Boolean {
        return try {
            val canonicalPath = file.canonicalPath
            val appDir = appPrivateDir?.canonicalPath ?: ""

            // 应用私有目录始终允许
            if (canonicalPath.startsWith(appDir)) {
                return true
            }

            // 检查外部存储中的允许目录
            for (dirName in ALLOWED_BASE_DIRS) {
                val baseDir = Environment.getExternalStoragePublicDirectory(dirName)
                if (baseDir != null && canonicalPath.startsWith(baseDir.canonicalPath)) {
                    return true
                }
            }

            // 根级别的允许目录
            val allowedPaths = listOf(
                Environment.getExternalStorageDirectory().absolutePath + "/Documents",
                Environment.getExternalStorageDirectory().absolutePath + "/Download",
                Environment.getExternalStorageDirectory().absolutePath + "/DCIM",
                Environment.getExternalStorageDirectory().absolutePath + "/Pictures",
                Environment.getExternalStorageDirectory().absolutePath + "/Music",
                Environment.getExternalStorageDirectory().absolutePath + "/Movies"
            )

            allowedPaths.any { canonicalPath.startsWith(it) }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to check path permissions", e)
            false
        }
    }

    private fun readFile(params: Map<String, Any>): ToolResult {
        val path = params["path"] as? String
            ?: return ToolResult.Error("缺少参数: path")

        val validationError = resolveAndValidatePath(path)
        if (validationError != null) return validationError

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.Error("文件不存在: $path")
        }
        if (!file.isFile) {
            return ToolResult.Error("不是文件: $path")
        }
        if (!file.canRead()) {
            return ToolResult.Error("文件不可读: $path")
        }
        if (!isPathAllowed(file)) {
            return ToolResult.Error("无权访问该目录: $path")
        }

        val content = file.readText()
        Log.d(TAG, "Read file: $path, ${content.length} chars")
        return ToolResult.Success(mapOf(
            "content" to content,
            "path" to file.absolutePath,
            "size" to file.length(),
            "encoding" to "UTF-8"
        ))
    }

    private fun writeFile(params: Map<String, Any>): ToolResult {
        val path = params["path"] as? String
            ?: return ToolResult.Error("缺少参数: path")
        val content = params["content"] as? String
            ?: return ToolResult.Error("缺少参数: content")
        val append = params["append"] as? Boolean ?: false

        val validationError = resolveAndValidatePath(path)
        if (validationError != null) return validationError

        val file = File(path)
        if (!isPathAllowed(file)) {
            return ToolResult.Error("无权访问该目录: $path")
        }

        // 创建父目录
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
            if (!parent.canWrite()) {
                return ToolResult.Error("目录不可写: ${parent.absolutePath}")
            }
        }

        if (append) {
            file.appendText(content)
        } else {
            file.writeText(content)
        }

        Log.d(TAG, "Written file: $path, ${content.length} chars, append=$append")
        return ToolResult.Success(mapOf(
            "path" to file.absolutePath,
            "size" to file.length(),
            "append" to append,
            "status" to "written"
        ))
    }

    private fun listDirectory(params: Map<String, Any>): ToolResult {
        val path = params["path"] as? String
            ?: return ToolResult.Error("缺少参数: path")
        val showHidden = params["show_hidden"] as? Boolean ?: false

        val validationError = resolveAndValidatePath(path)
        if (validationError != null) return validationError

        val dir = File(path)
        if (!dir.exists()) {
            return ToolResult.Error("目录不存在: $path")
        }
        if (!dir.isDirectory) {
            return ToolResult.Error("不是目录: $path")
        }
        if (!isPathAllowed(dir)) {
            return ToolResult.Error("无权访问该目录: $path")
        }

        val files = dir.listFiles()
        if (files == null) {
            return ToolResult.Error("无法列出目录内容: $path")
        }

        val fileList = files
            .filter { showHidden || !it.isHidden }
            .sortedBy { it.name.lowercase() }
            .map { file ->
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "type" to if (file.isDirectory) "directory" else "file",
                    "size" to file.length(),
                    "is_hidden" to file.isHidden,
                    "last_modified" to formatTimestamp(file.lastModified())
                )
            }

        Log.d(TAG, "Listed directory: $path, ${fileList.size} items")
        return ToolResult.Success(mapOf(
            "path" to dir.absolutePath,
            "items" to fileList,
            "total" to fileList.size
        ))
    }

    private fun deleteFile(params: Map<String, Any>): ToolResult {
        val path = params["path"] as? String
            ?: return ToolResult.Error("缺少参数: path")
        val recursive = params["recursive"] as? Boolean ?: false

        val validationError = resolveAndValidatePath(path)
        if (validationError != null) return validationError

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.Error("文件不存在: $path")
        }
        if (!isPathAllowed(file)) {
            return ToolResult.Error("无权删除该路径: $path")
        }

        val deleted = if (file.isDirectory && recursive) {
            file.deleteRecursively()
        } else {
            file.delete()
        }

        return if (deleted) {
            Log.d(TAG, "Deleted: $path")
            ToolResult.Success(mapOf(
                "path" to path,
                "status" to "deleted",
                "recursive" to recursive
            ))
        } else {
            if (file.isDirectory && !recursive) {
                ToolResult.Error("目录非空，如需删除请使用 recursive=true")
            } else {
                ToolResult.Error("删除失败: $path")
            }
        }
    }

    private fun getFileInfo(params: Map<String, Any>): ToolResult {
        val path = params["path"] as? String
            ?: return ToolResult.Error("缺少参数: path")

        val validationError = resolveAndValidatePath(path)
        if (validationError != null) return validationError

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.Error("文件不存在: $path")
        }
        if (!isPathAllowed(file)) {
            return ToolResult.Error("无权访问该路径: $path")
        }

        val mimeType = if (file.isDirectory) "directory" else guessMimeType(file.name)

        return ToolResult.Success(mapOf(
            "name" to file.name,
            "path" to file.absolutePath,
            "type" to mimeType,
            "size" to file.length(),
            "size_display" to formatFileSize(file.length()),
            "is_directory" to file.isDirectory,
            "is_file" to file.isFile,
            "is_hidden" to file.isHidden,
            "can_read" to file.canRead(),
            "can_write" to file.canWrite(),
            "last_modified" to formatTimestamp(file.lastModified()),
            "parent" to file.parent
        ))
    }

    // --- Helper methods ---

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "kt", "java" -> "text/x-kotlin"
            "py" -> "text/x-python"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "csv" -> "text/csv"
            "yaml", "yml" -> "text/yaml"
            "sh" -> "application/x-sh"
            "bat" -> "application/x-msdos-program"
            else -> "application/octet-stream"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun formatTimestamp(millis: Long): String {
        if (millis <= 0) return "unknown"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    override fun release() {
        context = null
        appPrivateDir = null
        Log.i(TAG, "FileOpsSkill released")
    }
}
