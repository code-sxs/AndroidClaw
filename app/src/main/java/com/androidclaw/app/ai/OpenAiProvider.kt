// OpenAiProvider.kt
// OpenAI 提供商实现
// 支持 GPT-4, GPT-3.5, GPT-4 Vision, 流式响应

package com.androidclaw.app.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.OkHttpSseClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────────────────────────────────────

data class OpenAiChatMessage(
    val role: String,
    val content: Any // String or List<OpenAiContentPart>
)

sealed class OpenAiContentPart {
    data class Text(val text: String) {
        fun toMap() = mapOf("type" to "text", "text" to text)
    }
    data class ImageUrl(
        val url: String,
        val detail: String = "auto"
    ) {
        fun toMap() = mapOf(
            "type" to "image_url",
            "image_url" to mapOf("url" to url, "detail" to detail)
        )
    }
}

data class OpenAiChatRequest(
    val model: String,
    val messages: List<Map<String, Any>>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false,
    val stream_options: Map<String, Any>? = null
)

data class OpenAiChatResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<OpenAiChatChoice>?,
    val usage: OpenAiUsage?
)

data class OpenAiChatChoice(
    val index: Int?,
    val message: OpenAiChatMessage?,
    val finishReason: String?
)

data class OpenAiUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)

// SSE stream chunk
data class OpenAiStreamChoice(
    val index: Int?,
    val delta: OpenAiChatMessage?,
    val finishReason: String?
)

data class OpenAiStreamResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<OpenAiStreamChoice>?
)

// ─────────────────────────────────────────────────────────────────────────────
// Provider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * OpenAI API 提供商
 * 支持: GPT-4o, GPT-4 Turbo, GPT-4 Vision, GPT-3.5 Turbo, 流式响应
 */
class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4o"
) : AiProvider {

    companion object {
        private const val TAG = "OpenAiProvider"
        private val gson = Gson()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // 支持视觉的模型
        private val VISION_MODELS = setOf(
            "gpt-4o", "gpt-4o-mini",
            "gpt-4-turbo", "gpt-4-turbo-2024-04-09",
            "gpt-4-vision-preview"
        )

        // 重试配置
        private const val MAX_RETRIES = 3
        private val RETRY_DELAY_MS = listOf(500L, 2000L, 8000L)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // 自定义重试
        .build()

    private val sseClient by lazy { OkHttpSseClient(httpClient) }

    // ─────────────────────────────────────────────────────────────────────────
    // AiProvider 实现
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun generateText(prompt: String, history: List<Message>): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "generateText: model=$model, promptLen=${prompt.length}")

            val request = buildChatRequest(prompt, history, stream = false)
            executeWithRetry(request) { response ->
                if (!response.isSuccessful) {
                    handleApiError(response)
                }
                val body = response.body?.string()
                    ?: throw Exception("Empty response from OpenAI")
                val parsed = gson.fromJson(body, OpenAiChatResponse::class.java)
                parsed.choices?.firstOrNull()?.message?.content?.toString()
                    ?: throw Exception("No content in response")
            }
        }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> =
        flow {
            Log.i(TAG, "generateTextStream: model=$model")

            val requestBody = buildChatRequestJson(prompt, history, stream = true)
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestBody.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            var collected = ""

            try {
                sseClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        handleApiError(response)
                    }

                    response.body?.let { body ->
                        val source = body.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break

                                try {
                                    val chunk = gson.fromJson(data, OpenAiStreamResponse::class.java)
                                    val token = chunk.choices?.firstOrNull()?.delta?.content
                                    if (!token.isNullOrBlank()) {
                                        emit(token)
                                        collected += token
                                    }
                                } catch (_: Exception) {
                                    // 忽略解析失败的 chunk
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSE stream failed, falling back to non-stream", e)
                // 流式失败时降级到非流式
                if (collected.isEmpty()) {
                    emit(generateText(prompt, history))
                }
            }

            Log.d(TAG, "Stream completed, total=${collected.length} chars")
        }.flowOn(Dispatchers.IO)

    override suspend fun supportsVision(): Boolean {
        return VISION_MODELS.any { model.lowercase().contains(it.lowercase()) }
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "analyzeImage: prompt=$prompt")

            if (!supportsVision()) {
                throw Exception("Model $model does not support vision")
            }

            val base64Image = bitmapToBase64(image)
            val messageContent = listOf(
                mapOf(
                    "type" to "text",
                    "text" to prompt
                ),
                mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf(
                        "url" to "data:image/jpeg;base64,$base64Image",
                        "detail" to "high"
                    )
                )
            )

            val requestBody = mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to messageContent)
                ),
                "max_tokens" to 4096
            )

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(gson.toJson(requestBody).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            executeWithRetry(request) { response ->
                if (!response.isSuccessful) handleApiError(response)
                val body = response.body?.string()
                    ?: throw Exception("Empty vision response")
                val parsed = gson.fromJson(body, OpenAiChatResponse::class.java)
                parsed.choices?.firstOrNull()?.message?.content?.toString()
                    ?: throw Exception("No content in vision response")
            }
        }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "testConnection")
        try {
            val requestBody = mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "Hi")),
                "max_tokens" to 5
            )
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(gson.toJson(requestBody).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            false
        }
    }

    override fun getProviderName(): String = "OpenAI ($model)"

    override fun release() {
        Log.i(TAG, "release")
        try { httpClient.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildChatRequest(
        prompt: String,
        history: List<Message>,
        stream: Boolean
    ): Request.Builder {
        val messages = buildMessages(prompt, history)
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "max_tokens" to 4096,
            "stream" to stream
        )
        if (stream) {
            body["stream_options"] = mapOf("include_usage" to true)
        }
        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(gson.toJson(body).toRequestBody(JSON))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
    }

    private fun buildChatRequestJson(prompt: String, history: List<Message>, stream: Boolean): String {
        val messages = buildMessages(prompt, history)
        val body = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages,
            "max_tokens" to 4096,
            "stream" to stream
        )
        if (stream) {
            body["stream_options"] = mapOf("include_usage" to true)
        }
        return gson.toJson(body)
    }

    private fun buildMessages(prompt: String, history: List<Message>): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        history.forEach { msg ->
            messages.add(mapOf("role" to msg.role, "content" to msg.content))
        }
        messages.add(mapOf("role" to "user", "content" to prompt))
        return messages
    }

    /**
     * 带重试的请求执行
     */
    private suspend fun <T> executeWithRetry(
        requestBuilder: Request.Builder,
        parse: suspend (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val request = requestBuilder.build()
                httpClient.newCall(request).execute().use { response ->
                    // 检查是否需要重试
                    if (shouldRetry(response)) {
                        Log.w(TAG, "Attempt $attempt failed with ${response.code}, retrying in ${RETRY_DELAY_MS[attempt]}ms")
                        kotlinx.coroutines.delay(RETRY_DELAY_MS.getOrElse(attempt) { 8000L })
                        lastException = Exception("HTTP ${response.code}")
                        response.close()
                        continue
                    }
                    return@withContext parse(response)
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt $attempt threw exception, retrying", e)
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS.getOrElse(attempt) { 8000L })
                }
            }
        }
        throw lastException ?: Exception("All retry attempts failed")
    }

    /**
     * 判断是否应该重试
     */
    private fun shouldRetry(response: Response): Boolean {
        val code = response.code
        return code == 429 || // Rate limit
                code == 500 || // Internal server error
                code == 502 || // Bad gateway
                code == 503    // Service unavailable
    }

    /**
     * 处理 API 错误
     */
    private fun handleApiError(response: Response) {
        val code = response.code
        val body = response.body?.string() ?: ""
        val errorMsg = try {
            val json = gson.fromJson(body, Map::class.java)
            (json["error"] as? Map<*, *>)?.get("message")?.toString() ?: body
        } catch (_: Exception) {
            body
        }
        Log.e(TAG, "API error $code: $errorMsg")

        when (code) {
            401 -> throw SecurityException("Invalid API key")
            403 -> throw SecurityException("API access forbidden")
            429 -> throw QuotaExceededException("Rate limit exceeded: $errorMsg")
            400 -> throw IllegalArgumentException("Bad request: $errorMsg")
            else -> throw Exception("OpenAI API error ($code): $errorMsg")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Exceptions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 配额用尽异常
 */
class QuotaExceededException(message: String) : Exception(message)

// 重新导出类型别名，方便外部使用
typealias AiProviderFactory = (String, String, String) -> AiProvider
