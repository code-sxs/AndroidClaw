// RemoteInferenceWebSocket.kt
// 远程推理 WebSocket 客户端 - 暂时禁用
// TODO: 完整实现需要修复类定义不匹配问题

package com.androidclaw.app.remote

import android.util.Log
import kotlinx.coroutines.flow.Flow

/*
// 暂时禁用的导入
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import com.androidclaw.app.skills.ToolResult
*/

// ─────────────────────────────────────────────────────────────────────────────
// 简化版本 - 只保留基本定义
// ─────────────────────────────────────────────────────────────────────────────

/**
 * RemoteClaw WebSocket 客户端（简化版 - 禁用状态）
 * 提供实时流式响应，支持自动重连
 */
class RemoteInferenceWebSocket(
    private val serverUrl: String,
    private val authToken: String? = null,
    private val timeoutSeconds: Long = 30
) : RemoteInferenceApi {

    companion object {
        private const val TAG = "RemoteWebSocket"
    }

    init {
        Log.w(TAG, "Remote inference WebSocket is disabled (simplified mode)")
    }

    override suspend fun generate(request: GenerateRequest): GenerateResponse {
        throw IllegalStateException("Remote inference WebSocket is disabled")
    }

    override suspend fun generateStream(request: GenerateRequest): Flow<String> {
        return kotlinx.coroutines.flow.flow { }
    }

    override suspend fun analyzeImage(request: VisionRequest): VisionResponse {
        throw IllegalStateException("Remote inference WebSocket is disabled")
    }

    override suspend fun listTools(): List<com.androidclaw.app.skills.ToolDefinition> {
        return emptyList()
    }

    override suspend fun callTool(name: String, params: Map<String, Any>): com.androidclaw.app.skills.ToolResult {
        return com.androidclaw.app.skills.ToolResult.Error("Remote inference WebSocket is disabled")
    }

    override suspend fun testConnection(): Boolean {
        Log.w(TAG, "testConnection called but WebSocket is disabled")
        return false
    }

    override fun close() {
        Log.i(TAG, "close (disabled mode)")
    }
}
