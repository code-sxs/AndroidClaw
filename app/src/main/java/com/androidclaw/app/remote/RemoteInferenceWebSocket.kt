// RemoteInferenceWebSocket.kt
// 远程推理 WebSocket 客户端
// 使用 OkHttp WebSocket 实现实时流式响应，支持自动重连

package com.androidclaw.app.remote

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

// ─────────────────────────────────────────────────────────────────────────────
// WebSocket 消息格式
// ─────────────────────────────────────────────────────────────────────────────

/** 客户端 -> 服务器 请求 */
data class WsRequest(
    val type: String,           // "generate" | "generateStream" | "analyzeImage" | "listTools" | "callTool"
    val id: String,             // 请求 ID（用于匹配响应）
    val payload: Any? = null    // 请求数据
)

/** 服务器 -> 客户端 响应 */
data class WsResponse(
    val id: String,             // 对应请求 ID
    val type: String,           // "text" | "chunk" | "done" | "error" | "tools" | "toolResult"
    val data: Any? = null,      // 响应数据
    val error: String? = null   // 错误信息
)

/** 流式文本块 */
data class WsTextChunk(
    val text: String,
    val done: Boolean = false
)

/** 工具调用结果 */
data class WsToolResult(
    val toolName: String,
    val result: Any?,
    val error: String?
)

// ─────────────────────────────────────────────────────────────────────────────
// WebSocket 客户端
// ─────────────────────────────────────────────────────────────────────────────

/**
 * RemoteClaw WebSocket 客户端
 * 提供实时流式响应，支持自动重连
 */
class RemoteInferenceWebSocket(
    private val serverUrl: String,
    private val authToken: String? = null,
    private val timeoutSeconds: Long = 30
) : RemoteInferenceApi {

    companion object {
        private const val TAG = "RemoteWebSocket"
        private val gson = Gson()

        // 重连配置
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val INITIAL_RECONNECT_DELAY_MS = listOf(1000L, 2000L, 5000L, 10000L, 30000L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // WebSocket 需要长连接
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // 保活
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    // 待处理的响应（请求 ID -> 协程续延）
    private val pendingRequests = mutableMapOf<String, PendingRequest>()

    // 流式响应的 SharedFlow
    private val streamFlows = mutableMapOf<String, MutableSharedFlow<String>>()

    // 重连状态
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var shouldReconnect = true

    private data class PendingRequest(
        val onResponse: (WsResponse) -> Unit,
        val onChunk: ((String) -> Unit)? = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // RemoteInferenceApi 实现
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun generate(request: GenerateRequest): GenerateResponse {
        Log.i(TAG, "generate: prompt=${request.prompt.take(50)}")

        val requestId = generateId()
        val wsRequest = WsRequest(
            type = "generate",
            id = requestId,
            payload = mapOf(
                "prompt" to request.prompt,
                "history" to request.history,
                "tools" to request.tools
            )
        )

        return sendRequest(requestId, wsRequest) as? GenerateResponse
            ?: throw Exception("Invalid response type")
    }

    override suspend fun generateStream(request: GenerateRequest): Flow<String> {
        Log.i(TAG, "generateStream: prompt=${request.prompt.take(50)}")

        val requestId = generateId()
        val flow = MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        streamFlows[requestId] = flow

        val wsRequest = WsRequest(
            type = "generateStream",
            id = requestId,
            payload = mapOf(
                "prompt" to request.prompt,
                "history" to request.history,
                "tools" to request.tools
            )
        )

        scope.launch {
            ensureConnected()
            webSocket?.send(gson.toJson(wsRequest))
        }

        return flow
    }

    override suspend fun analyzeImage(request: VisionRequest): VisionResponse {
        Log.i(TAG, "analyzeImage")

        val requestId = generateId()
        val wsRequest = WsRequest(
            type = "analyzeImage",
            id = requestId,
            payload = request
        )

        return sendRequest(requestId, wsRequest) as? VisionResponse
            ?: throw Exception("Invalid response type")
    }

    override suspend fun listTools(): List<ToolDefinition> {
        Log.i(TAG, "listTools")

        val requestId = generateId()
        val wsRequest = WsRequest(
            type = "listTools",
            id = requestId
        )

        @Suppress("UNCHECKED_CAST")
        return sendRequest(requestId, wsRequest) as? List<ToolDefinition>
            ?: emptyList()
    }

    override suspend fun callTool(name: String, params: Map<String, Any>): ToolResult {
        Log.i(TAG, "callTool: $name")

        val requestId = generateId()
        val wsRequest = WsRequest(
            type = "callTool",
            id = requestId,
            payload = mapOf("name" to name, "parameters" to params)
        )

        return sendRequest(requestId, wsRequest) as? ToolResult
            ?: ToolResult.Error("Invalid response for tool: $name")
    }

    override suspend fun testConnection(): Boolean {
        Log.i(TAG, "testConnection")
        return try {
            ensureConnected()
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }

    override fun close() {
        Log.i(TAG, "close")
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        isConnected = false
        try { httpClient.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 确保已连接
     */
    @Synchronized
    private fun ensureConnected() {
        if (!isConnected || webSocket == null) {
            connect()
        }
    }

    /**
     * 连接 WebSocket
     */
    @Synchronized
    private fun connect() {
        val url = if (authToken != null) {
            "$serverUrl?token=$authToken"
        } else {
            serverUrl
        }

        Log.i(TAG, "Connecting to $url")

        val request = Request.Builder()
            .url(url)
            .apply {
                authToken?.let { addHeader("Authorization", "Bearer $it") }
            }
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                isConnected = true
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                scheduleReconnect()
            }
        })
    }

    /**
     * 处理接收到的消息
     */
    private fun handleMessage(text: String) {
        try {
            val response = gson.fromJson(text, WsResponse::class.java)
            val requestId = response.id

            // 检查是否是流式响应
            val streamFlow = streamFlows[requestId]
            if (streamFlow != null) {
                when (response.type) {
                    "chunk" -> {
                        val chunk = response.data as? String
                        if (!chunk.isNullOrBlank()) {
                            scope.launch {
                                streamFlow.emit(chunk)
                            }
                        }
                    }
                    "done" -> {
                        streamFlows.remove(requestId)
                    }
                    "error" -> {
                        Log.e(TAG, "Stream error: ${response.error}")
                        streamFlows.remove(requestId)
                    }
                }
                return
            }

            // 非流式响应
            val pending = pendingRequests.remove(requestId)
            if (pending != null) {
                pending.onResponse(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle message: ${e.message}", e)
        }
    }

    /**
     * 发送请求并等待响应
     */
    private suspend fun sendRequest(requestId: String, wsRequest: WsRequest): Any? {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            pendingRequests[requestId] = PendingRequest(
                onResponse = { response ->
                    if (continuation.isActive) {
                        val result = parseResponse(response)
                        continuation.resume(result) {}
                    }
                },
                onChunk = null
            )

            continuation.invokeOnCancellation {
                pendingRequests.remove(requestId)
            }

            scope.launch {
                try {
                    ensureConnected()
                    val sent = webSocket?.send(gson.toJson(wsRequest)) ?: false
                    if (!sent) {
                        pendingRequests.remove(requestId)
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(Exception("WebSocket not connected"))) {}
                        }
                    }
                } catch (e: Exception) {
                    pendingRequests.remove(requestId)
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(e)) {}
                    }
                }
            }
        }
    }

    /**
     * 解析 WsResponse 为具体类型
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(response: WsResponse): Any {
        if (response.error != null) {
            throw Exception(response.error)
        }

        return when (response.type) {
            "text" -> {
                GenerateResponse(
                    text = response.data?.toString() ?: "",
                    toolCalls = null,
                    done = true
                )
            }
            "tools" -> {
                val list = response.data as? List<*>
                list?.filterIsInstance<Map<String, Any?>>()?.map { map ->
                    ToolDefinition(
                        toolName = map["toolName"]?.toString() ?: "",
                        description = map["description"]?.toString() ?: "",
                        parameters = (map["parameters"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { param ->
                            ToolParameter(
                                name = param["name"]?.toString() ?: "",
                                description = param["description"]?.toString() ?: "",
                                type = param["type"]?.toString() ?: "string",
                                required = param["required"] as? Boolean ?: false
                            )
                        } ?: emptyList()
                    )
                } ?: emptyList()
            }
            "toolResult" -> {
                val map = response.data as? Map<String, Any?>
                ToolResult.Success(
                    toolName = map?.get("toolName")?.toString() ?: "",
                    data = map?.get("result")
                )
            }
            else -> {
                response.data ?: Unit
            }
        }
    }

    /**
     * 计划重连
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = INITIAL_RECONNECT_DELAY_MS.getOrElse(reconnectAttempts) { 30000L }
            Log.i(TAG, "Scheduling reconnect in ${delayMs}ms (attempt ${reconnectAttempts + 1})")
            delay(delayMs)
            reconnectAttempts++
            connect()
        }
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
