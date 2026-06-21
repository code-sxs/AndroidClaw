// ScreenCaptureSkill.kt
// 截图/录屏 Skill - 屏幕截图和录制

package com.androidclaw.app.skills

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 截图/录屏 Skill
 * 提供屏幕截图和录屏功能（需要用户通过 MediaProjection 前台授权）
 *
 * 注意：实际的截图和录屏操作需要在前台 Activity 中发起授权请求
 * 此 Skill 仅提供能力定义和授权请求的入口，实际数据处理由系统 MediaProjection 完成
 */
class ScreenCaptureSkill : SkillDefinition {

    companion object {
        private const val TAG = "ScreenCaptureSkill"
        private const val REQUEST_CODE_SCREENSHOT = 10001
        private const val REQUEST_CODE_RECORDING = 10002
    }

    // 录制状态
    private enum class RecordingState {
        IDLE, RECORDING
    }

    private var context: Context? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var recordingState = RecordingState.IDLE
    private var currentRecordingFile: String? = null

    override val skillName: String = "screen_capture"
    override val displayName: String = "截图录屏"
    override val description: String = "屏幕截图和屏幕录制（需要用户在前台授权）"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        this.context = context
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        Log.i(TAG, "ScreenCaptureSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "take_screenshot",
            displayName = "截图",
            description = "对当前屏幕进行截图（需要用户前台授权 MediaProjection）",
            parameters = emptyList(),
            returnType = "string" // 返回图片 URI
        ),
        ToolDefinition(
            toolName = "start_recording",
            displayName = "开始录屏",
            description = "开始屏幕录制（需要用户前台授权 MediaProjection）",
            parameters = emptyList(),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "stop_recording",
            displayName = "停止录屏",
            description = "停止当前屏幕录制并保存视频",
            parameters = emptyList(),
            returnType = "string" // 返回视频 URI
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val ctx = context
            ?: return ToolResult.Error("ScreenCaptureSkill not initialized")

        return try {
            when (toolName) {
                "take_screenshot" -> takeScreenshot(ctx)
                "start_recording" -> startRecording(ctx)
                "stop_recording" -> stopRecording()
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException for $toolName", e)
            ToolResult.Error("缺少屏幕捕获权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("操作失败: ${e.message}", e)
        }
    }

    /**
     * 截图
     * 需要用户通过 MediaProjection 授权
     */
    private fun takeScreenshot(context: Context): ToolResult {
        val mpm = mediaProjectionManager
            ?: return ToolResult.Error("MediaProjectionManager not available")

        // 检查是否已有 MediaProjection 授权
        val intent = mpm.createScreenCaptureIntent()

        try {
            // 尝试通过 Activity 发起授权请求
            // 这里返回授权 Intent，由上层 Activity 处理
            // 实际截图由 Activity 的结果回调处理
            if (context is Activity) {
                context.startActivityForResult(intent, REQUEST_CODE_SCREENSHOT)
                Log.i(TAG, "Screen capture permission requested from Activity")

                return ToolResult.Success(mapOf(
                    "status" to "authorization_required",
                    "action" to "take_screenshot",
                    "message" to "请在弹出的屏幕捕获权限对话框中点击「立即开始」"
                ))
            } else {
                // 非 Activity 上下文，返回授权 Intent 由框架层处理
                Log.i(TAG, "Screen capture intent prepared (context is not Activity)")

                return ToolResult.Success(mapOf(
                    "status" to "authorization_required",
                    "intent" to intent.toUri(Intent.URI_INTENT_SCHEME),
                    "action" to "take_screenshot",
                    "message" to "截图需要用户在前台授权屏幕捕获权限",
                    "image_uri" to generateScreenshotPath()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request screen capture", e)
            return ToolResult.Error("无法请求屏幕捕获权限: ${e.message}")
        }
    }

    /**
     * 开始录屏
     */
    private fun startRecording(context: Context): ToolResult {
        if (recordingState == RecordingState.RECORDING) {
            return ToolResult.Error("正在录制中，请先停止当前录制")
        }

        val mpm = mediaProjectionManager
            ?: return ToolResult.Error("MediaProjectionManager not available")

        val videoPath = generateVideoPath()
        currentRecordingFile = videoPath

        try {
            val intent = mpm.createScreenCaptureIntent()

            if (context is Activity) {
                context.startActivityForResult(intent, REQUEST_CODE_RECORDING)
                recordingState = RecordingState.RECORDING

                Log.i(TAG, "Recording started, output: $videoPath")
                return ToolResult.Success(mapOf(
                    "status" to "recording",
                    "video_path" to videoPath,
                    "message" to "录屏已开始，请使用 stop_recording 停止录制"
                ))
            } else {
                return ToolResult.Success(mapOf(
                    "status" to "authorization_required",
                    "intent" to intent.toUri(Intent.URI_INTENT_SCHEME),
                    "action" to "start_recording",
                    "message" to "录屏需要用户在前台授权屏幕捕获权限"
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            currentRecordingFile = null
            return ToolResult.Error("无法启动录屏: ${e.message}")
        }
    }

    /**
     * 停止录屏
     */
    private fun stopRecording(): ToolResult {
        if (recordingState != RecordingState.RECORDING) {
            return ToolResult.Error("当前没有正在进行的录屏")
        }

        val videoFile = currentRecordingFile
        recordingState = RecordingState.IDLE
        currentRecordingFile = null

        Log.i(TAG, "Recording stopped: $videoFile")
        return ToolResult.Success(mapOf(
            "status" to "stopped",
            "video_uri" to videoFile,
            "message" to "录屏已停止，视频保存在 $videoFile"
        ))
    }

    /**
     * 生成截图文件路径
     */
    private fun generateScreenshotPath(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "screenshot_$timestamp.png"
    }

    /**
     * 生成录屏文件路径
     */
    private fun generateVideoPath(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "recording_$timestamp.mp4"
    }

    /**
     * 处理 MediaProjection 授权结果
     * 由 Activity 在 onActivityResult 中调用
     */
    fun handleProjectionResult(resultCode: Int, data: Intent?): Boolean {
        return try {
            val mpm = mediaProjectionManager
            if (mpm == null) {
                Log.e(TAG, "MediaProjectionManager not available")
                return false
            }

            if (resultCode != Activity.RESULT_OK || data == null) {
                Log.w(TAG, "MediaProjection permission denied")
                return false
            }

            val projection = mpm.getMediaProjection(resultCode, data)
            if (projection == null) {
                Log.e(TAG, "Failed to create MediaProjection")
                return false
            }

            Log.i(TAG, "MediaProjection obtained successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle projection result", e)
            false
        }
    }

    override fun release() {
        // 如果正在录制，停止录制
        if (recordingState == RecordingState.RECORDING) {
            Log.i(TAG, "Force stopping recording during release")
            recordingState = RecordingState.IDLE
            currentRecordingFile = null
        }
        context = null
        mediaProjectionManager = null
        Log.i(TAG, "ScreenCaptureSkill released")
    }
}
