// RemoteInferenceManager.kt
// 远程推理接口完整实现
// 支持 HTTP REST API 和 WebSocket 两种通信方式
// 如果远程不可用，自动降级到本地模型

package com.androidclaw.app.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolParameter
import com.androidclaw.app.skills.ToolResult
import com.androidclaw.app.llm.LLMManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.OkHttpSseClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Retrofit API 接口
// ─────────────────────────────────────────────────────────────────────────────

/** RemoteClaw REST API DTOs */

data class ApiGenerateRequest(
    val prompt: String,
    val history: List<ApiMessage>? = null,
    val tools: List<ApiTool>? = null,
    val stream: Boolean = false,
    @SerializedName("stream_options") val streamOptions: ApiStreamOptions? = null
)

data class ApiStreamOptions(
    val include_usage: Boolean = true
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class ApiTool(
    @SerializedName("tool_name") val toolName: String,
    val description: String,
    val parameters: List<ApiToolParameter>
)

data class ApiToolParameter(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = false,
    val enum_values: List<String>? = null,
    val default: Any? = null
)

data class ApiGenerateResponse(
    val id: String?,
    val text: String?,
    @SerializedName("tool_calls") val toolCalls: List<ApiToolCall>?,
    val done: Boolean = true,
    val usage: ApiUsage?
)

data class ApiToolCall(
    @SerializedName("tool_name") val toolName: String,
    val parameters: Map<String, Any>
)

data class ApiUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)

data class ApiVisionRequest(
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_base64") val imageBase64: String? = null,
    val prompt: String
)

data class ApiVisionResponse(
    val description: String,
    val objects: List<String>? = null,
    val tags: List<String>? = null
)

data class ApiToolListResponse(
    val tools: List<ApiTool>
)

data class ApiToolCallRequest(
    val name: String,
    val parameters: Map<String, Any>
)

data class ApiHealthResponse(
    val status: String?,
    val version: String?,
    val models: List<String>?,
    @SerializedName("server_time") val serverTime: String?
)

// Retrofit Service

interface RemoteClawApiService {

    @POST("v1/generate")
    suspend fun generate(@Body request: ApiGenerateRequest): ApiGenerateResponse

    @POST("v1/generate")
    suspend fun generateStream(@Body request: ApiGenerateRequest): Response

    @POST("v1/vision")
    suspend fun analyzeImage(@Body request: ApiVisionRequest): ApiVisionResponse

    @GET("v1/tools")
    suspend fun listTools(): ApiToolListResponse

    @POST("v1/tools/call")
    suspend fun callTool(@Body request: ApiToolCallRequest): ApiGenerateResponse

    @GET("health")
    suspend fun health(): ApiHealthResponse
}

// ─────────────────────────────────────────────────────────────────────────────
// HTTP API 实现
// ─────────────────────────────────────────────────────────────────────────────

/**
 * HTTP 实现的远程推理 API（使用 Retrofit）
 */
class HttpRemoteInferenceApi(
    private val baseUrl: String,
    private val authToken: String? = null,
    private val timeoutSeconds: Long = 30
) : RemoteInferenceApi {

    companion object {
        private const val TAG = "HttpRemoteInferenceApi"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val gson = Gson()
        private const val MAX_RETRIES = 3
        private val RETRY_DELAY_MS = listOf(500L, 2000L, 8000L)
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
        authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .retryOnConnectionFailure(false)
        .build()

    private val sseClient by lazy { OkHttpSseClient(okHttpClient) }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ensureTrailingSlash(baseUrl))
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val api: RemoteClawApiService = retrofit.create(RemoteClawApiService::class.java)

    override suspend fun generate(request: GenerateRequest): GenerateResponse =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "generate: promptLen=${request.prompt.length}")

            val apiRequest = ApiGenerateRequest(
                prompt = request.prompt,
                history = request.history.map { ApiMessage(it.role, it.content) },
                tools = request.tools?.map { it.toApiTool() },
                stream = false
            )

            executeWithRetry { api.generate(apiRequest) }.let { resp ->
                GenerateResponse(
                    text = resp.text ?: "",
                    toolCalls = resp.toolCalls?.map {
                        ToolCall(it.toolName, it.parameters)
                    },
                    done = resp.done
                )
            }
        }

    override suspend fun generateStream(request: GenerateRequest): Flow<String> = flow {
        val apiRequest = ApiGenerateRequest(
            prompt = request.prompt,
            history = request.history.map { ApiMessage(it.role, it.content) },
            tools = request.tools?.map { it.toApiTool() },
            stream = true,
            streamOptions = ApiStreamOptions()
        )

        val httpRequest = Request.Builder()
            .url("${ensureTrailingSlash(baseUrl)}v1/generate")
            .post(gson.toJson(apiRequest).toRequestBody(JSON))
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .header("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

            response.body?.let { body ->
                val source = body.source()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val chunk = gson.fromJson(data, ApiGenerateResponse::class.java)
                            chunk.text?.let { emit(it) }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun analyzeImage(request: VisionRequest): VisionResponse =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "analyzeImage")
            val apiRequest = ApiVisionRequest(
                imageUrl = request.imageUrl,
                imageBase64 = request.imageBase64,
                prompt = request.prompt
            )
            val resp = executeWithRetry { api.analyzeImage(apiRequest) }
            VisionResponse(resp.description, resp.objects)
        }

    override suspend fun listTools(): List<ToolDefinition> =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "listTools")
            val resp = executeWithRetry { api.listTools() }
            resp.tools.map { it.toToolDefinition() }
        }

    override suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "callTool: $name")
            val resp = executeWithRetry {
                api.callTool(ApiToolCallRequest(name, params))
            }
            if (resp.text.isNullOrBlank()) {
                ToolResult.Success(name, null)
            } else {
                ToolResult.Success(name, resp.text)
            }
        }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            api.health().let { health ->
                Log.i(TAG, "Health check: ${health.status}, version=${health.version}")
                health.status == "ok" || health.status == "healthy"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            false
        }
    }

    override fun close() {
        try { okHttpClient.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun <T> executeWithRetry(call: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return call()
            } catch (e: Exception) {
                lastError = e
                if (isRetryable(e) && attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS.getOrElse(attempt) { 8000L })
                } else break
            }
        }
        throw lastError ?: Exception("Request failed")
    }

    private fun isRetryable(e: Exception): Boolean {
        val msg = e.message ?: ""
        return e is java.net.SocketTimeoutException ||
                e is java.net.ConnectException ||
                e is java.io.IOException ||
                msg.contains("429") || msg.contains("502") || msg.contains("503")
    }

    private fun ensureTrailingSlash(url: String) =
        if (url.endsWith("/")) url else "$url/"

    private fun ToolDefinition.toApiTool() = ApiTool(
        toolName = toolName,
        description = description,
        parameters = parameters.map { param ->
            ApiToolParameter(
                name = param.name,
                description = param.description,
                type = param.type,
                required = param.required,
                enumValues = param.enumValues,
                default = param.defaultValue
            )
        }
    )

    private fun ApiTool.toToolDefinition() = ToolDefinition(
        toolName = toolName,
        description = description,
        parameters = parameters.map { param ->
            ToolParameter(
                name = param.name,
                description = param.description,
                type = param.type,
                required = param.required,
                enumValues = param.enumValues ?: emptyList(),
                defaultValue = param.default
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 消息 / 请求 / 响应（与 RemoteInferenceApi 共用）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 消息
 */
data class Message(
    val role: String,
    val content: String
)

/**
 * 工具调用
 */
data class ToolCall(
    val toolName: String,
    val parameters: Map<String, Any>
)

/**
 * 视觉请求
 */
data class VisionRequest(
    val imageUrl: String? = null,
    val imageBase64: String? = null,
    val prompt: String
)

/**
 * 视觉响应
 */
data class VisionResponse(
    val description: String,
    val objects: List<String>? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Remote Inference Manager（单例）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 远程推理管理器
 * 单例模式，统一管理 HTTP/WebSocket 连接，支持自动降级到本地模型
 */
class RemoteInferenceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RemoteInferenceManager"
        private const val DEFAULT_TIMEOUT = 30
        private const val DEFAULT_WS_RECONNECT_MAX = 5

        private var INSTANCE: RemoteInferenceManager? = null

        fun getInstance(context: Context): RemoteInferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteInferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remote_inference_prefs")
    private val dataStore = context.dataStore

    // Remote API（HTTP 或 WebSocket）
    private var remoteApi: RemoteInferenceApi? = null

    // 本地 LLM 管理器（降级时使用）
    private val llmManager: LLMManager by lazy {
        LLMManager.getInstance(context)
    }

    // 配置
    private var serverAddress: String? = null
    private var authToken: String? = null
    private var enabled: Boolean = false
    private var useWebSocket: Boolean = false
    private var timeoutSeconds: Int = DEFAULT_TIMEOUT

    init {
        Log.i(TAG, "RemoteInferenceManager initializing...")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            loadConfiguration()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 配置 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 配置服务器地址
     */
    suspend fun setServerAddress(address: String) {
        Log.i(TAG, "setServerAddress: $address")
        serverAddress = address
        saveConfiguration()
        if (enabled) reconnect()
    }

    /**
     * 配置认证 Token
     */
    suspend fun setAuthToken(token: String) {
        Log.i(TAG, "setAuthToken: [REDACTED]")
        authToken = token
        saveConfiguration()
        if (enabled) reconnect()
    }

    /**
     * 启用/禁用远程推理
     */
    suspend fun setEnabled(enabled: Boolean) {
        Log.i(TAG, "setEnabled: $enabled")
        this.enabled = enabled
        saveConfiguration()
        if (enabled) connect() else disconnect()
    }

    /**
     * 设置连接超时（秒）
     */
    suspend fun setTimeoutSeconds(timeout: Int) {
        timeoutSeconds = timeout
        saveConfiguration()
    }

    /**
     * 设置是否使用 WebSocket
     */
    suspend fun setUseWebSocket(use: Boolean) {
        useWebSocket = use
        saveConfiguration()
        if (enabled) reconnect()
    }

    /**
     * 连接到远程服务器
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "connect: server=$serverAddress, ws=$useWebSocket")

        if (serverAddress.isNullOrBlank()) {
            Log.e(TAG, "Server address not configured")
            return@withContext false
        }

        return@withContext try {
            // 关闭旧连接
            remoteApi?.close()
            remoteApi = null

            // 选择连接方式
            val api: RemoteInferenceApi = if (useWebSocket ||
                serverAddress!!.startsWith("ws://") || serverAddress!!.startsWith("wss://")
            ) {
                val wsUrl = serverAddress!!.let {
                    if (it.startsWith("ws://") || it.startsWith("wss://")) it
                    else "ws://$it"
                }
                RemoteInferenceWebSocket(wsUrl, authToken, timeoutSeconds.toLong())
            } else {
                HttpRemoteInferenceApi(serverAddress!!, authToken, timeoutSeconds.toLong())
            }

            // 测试连接
            val connected = api.testConnection()
            if (connected) {
                remoteApi = api
                Log.i(TAG, "Connected to remote server successfully")
            } else {
                api.close()
                Log.w(TAG, "Failed to connect to remote server")
            }
            connected
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to remote server", e)
            false
        }
    }

    /**
     * 重新连接
     */
    private suspend fun reconnect() {
        disconnect()
        connect()
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        Log.i(TAG, "disconnect")
        remoteApi?.close()
        remoteApi = null
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(): Boolean {
        return remoteApi?.testConnection() ?: connect()
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = remoteApi != null

    /**
     * 是否启用
     */
    fun isEnabled(): Boolean = enabled

    // ─────────────────────────────────────────────────────────────────────────
    // 推理 API（自动降级到本地模型）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 生成文本
     * 如果远程不可用，自动降级到本地模型
     */
    suspend fun generateText(prompt: String, history: List<Message>): String =
        withContext(Dispatchers.IO) {
            if (!enabled || remoteApi == null) {
                Log.d(TAG, "Remote disabled, using local model")
                return@withContext generateTextLocal(prompt)
            }

            try {
                val request = GenerateRequest(prompt, history, stream = false)
                remoteApi!!.generate(request).text
            } catch (e: Exception) {
                Log.e(TAG, "Remote inference failed, falling back to local", e)
                generateTextLocal(prompt)
            }
        }

    /**
     * 生成文本（流式）
     * 如果远程不可用，降级到本地
     */
    suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> =
        flow {
            if (!enabled || remoteApi == null) {
                Log.d(TAG, "Remote disabled, using local model (stream)")
                emit(generateTextLocal(prompt))
                return@flow
            }

            try {
                val request = GenerateRequest(prompt, history, stream = true)
                remoteApi!!.generateStream(request).collect { emit(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Remote stream failed, falling back to local", e)
                emit(generateTextLocal(prompt))
            }
        }.flowOn(Dispatchers.IO)

    /**
     * 分析图片
     */
    suspend fun analyzeImage(imageBytes: ByteArray, prompt: String): String =
        withContext(Dispatchers.IO) {
            if (!enabled || remoteApi == null) {
                Log.d(TAG, "Remote disabled, cannot analyze image")
                throw Exception("Remote inference not available for image analysis")
            }

            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val request = VisionRequest(imageBase64 = base64, prompt = prompt)
            remoteApi!!.analyzeImage(request).description
        }

    /**
     * 获取可用工具列表
     */
    suspend fun listTools(): List<ToolDefinition> = withContext(Dispatchers.IO) {
        remoteApi?.listTools() ?: emptyList()
    }

    /**
     * 调用工具
     */
    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {
            if (remoteApi == null) {
                ToolResult.Error("Remote inference not connected: $name")
            } else {
                remoteApi!!.callTool(name, params)
            }
        }

    /**
     * 使用本地模型生成文本
     */
    private suspend fun generateTextLocal(prompt: String): String {
        Log.i(TAG, "generateTextLocal")
        return llmManager.generateText(prompt)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 配置持久化
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadConfiguration() {
        try {
            val prefs = dataStore.data.first()
            serverAddress = prefs[stringPreferencesKey("remote_server_address")]
            authToken = prefs[stringPreferencesKey("remote_auth_token")]
            enabled = prefs[booleanPreferencesKey("remote_enabled")] ?: false
            useWebSocket = prefs[booleanPreferencesKey("remote_use_websocket")] ?: false
            timeoutSeconds = prefs[intPreferencesKey("remote_timeout")] ?: DEFAULT_TIMEOUT
            Log.i(TAG, "Configuration loaded: enabled=$enabled, ws=$useWebSocket")

            if (enabled && !serverAddress.isNullOrBlank()) {
                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load configuration", e)
        }
    }

    private suspend fun saveConfiguration() {
        try {
            dataStore.edit { prefs ->
                serverAddress?.let { prefs[stringPreferencesKey("remote_server_address")] = it }
                authToken?.let { prefs[stringPreferencesKey("remote_auth_token")] = it }
                prefs[booleanPreferencesKey("remote_enabled")] = enabled
                prefs[booleanPreferencesKey("remote_use_websocket")] = useWebSocket
                prefs[intPreferencesKey("remote_timeout")] = timeoutSeconds
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save configuration", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "release")
        disconnect()
    }

}
