// AnthropicProvider.kt
// Anthropic (Claude) 提供商实现
// 支持 Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Sonnet, Claude 3 Haiku
// 支持流式响应 (SSE) 和图片理解

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
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────────────────────────────────────

/** Anthropic messages API 请求 */
data class AnthropicMessageRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = false
)

data class AnthropicMessage(
    val role: String, // "user" or "assistant"
    val content: Any   // String or List<AnthropicContentBlock>
)

sealed class AnthropicContent {
    data class Text(val text: String) {
        fun toMap() = mapOf("type" to "text", "text" to text)
    }
    data class Image(
        val source: AnthropicImageSource
    ) {
        fun toMap() = mapOf("type" to "image", "source" to source.toMap())
    }
}

data class AnthropicImageSource(
    val type: String = "base64",
    val media_type: String,
    val data: String
) {
    fun toMap() = mapOf(
        "type" to type,
        "media_type" to media_type,
        "data" to data
    )
}

/** Anthropic messages API 响应 */
data class AnthropicMessageResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<AnthropicContentBlock>?,
    val model: String?,
    val stop_reason: String?,
    val stop_sequence: Any?,
    val usage: AnthropicUsage?
)

data class AnthropicContentBlock(
    val type: String?,
    val text: String?,
    val id: Any?,
    val name: Any?,
    val input: Any?
)

data class AnthropicUsage(
    @SerializedName("input_tokens") val inputTokens: Int?,
    @SerializedName("output_tokens") val outputTokens: Int?
)

// SSE stream chunk
data class AnthropicStreamEvent(
    val type: String?,       // "content_block_delta" | "message_delta" | "message_start" | etc.
    val index: Int?,
    val content_block: AnthropicContentBlock?,
    val delta: AnthropicDelta?,
    val message: AnthropicMessageResponse?
)

data class AnthropicDelta(
    val type: String?,
    val text: String?,
    val partial_json: Any?
)

// ─────────────────────────────────────────────────────────────────────────────
// Provider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Anthropic Claude API 提供商
 * 支持: Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Sonnet, Claude 3 Haiku
 * 支持流式响应和图片理解
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val model: String = "claude-3-5-sonnet-20241022",
    private val apiVersion: String = "2023-06-01"
) : AiProvider {

    companion object {
        private const val TAG = "AnthropicProvider"
        private val gson = Gson()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private const val MAX_TOKENS_DEFAULT = 4096
        private const val MAX_RETRIES = 3
        private val RETRY_DELAY_MS = listOf(500L, 2000L, 8000L)

        // 支持视觉的模型
        private val VISION_MODELS = setOf(
            "claude-3-opus", "claude-3-sonnet",
            "claude-3-5-sonnet", "claude-3-5-haiku",
            "claude-3-haiku"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    // AiProvider 实现
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun generateText(prompt: String, history: List<Message>): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "generateText: model=$model")

            val anthropicMessages = buildAnthropicMessages(history, prompt)

            val request = AnthropicMessageRequest(
                model = model,
                max_tokens = MAX_TOKENS_DEFAULT,
                messages = anthropicMessages,
                stream = false
            )

            executeWithRetry(request) { response ->
                if (!response.isSuccessful) handleApiError(response)
                val body = response.body?.string()
                    ?: throw Exception("Empty response from Anthropic")
                val parsed = gson.fromJson(body, AnthropicMessageResponse::class.java)
                parsed.content?.firstOrNull()?.text
                    ?: throw Exception("No text content in response")
            }
        }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> =
        flow {
            Log.i(TAG, "generateTextStream: model=$model")

            val anthropicMessages = buildAnthropicMessages(history, prompt)

            val requestBody = AnthropicMessageRequest(
                model = model,
                max_tokens = MAX_TOKENS_DEFAULT,
                messages = anthropicMessages,
                stream = true
            )

            val request = Request.Builder()
                .url("$baseUrl/messages")
                .post(gson.toJson(requestBody).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", apiVersion)
                .addHeader("anthropic-dangerous-direct-browser-access", "true")
                .build()

            var collected = ""

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    handleApiError(response)
                }

                response.body?.let { body ->
                    val source = body.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data.isBlank()) continue

                            try {
                                val event = gson.fromJson(data, AnthropicStreamEvent::class.java)
                                when (event.type) {
                                    "content_block_delta" -> {
                                        val text = event.delta?.text
                                        if (!text.isNullOrBlank()) {
                                            emit(text)
                                            collected += text
                                        }
                                    }
                                    "message_delta" -> {
                                        // finish
                                        break
                                    }
                                }
                            } catch (_: Exception) {
                                // 忽略解析失败的 chunk
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Stream completed, total=${collected.length} chars")
        }.flowOn(Dispatchers.IO)

    override suspend fun supportsVision(): Boolean {
        return VISION_MODELS.any { model.lowercase().startsWith(it.lowercase()) }
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "analyzeImage: prompt=$prompt")

            if (!supportsVision()) {
                throw Exception("Model $model does not support vision")
            }

            val base64Image = bitmapToBase64(image)
            val imageContent = AnthropicContent.Image(
                AnthropicImageSource(
                    media_type = "image/jpeg",
                    data = base64Image
                )
            )

            val contentBlocks = listOf(
                mapOf("type" to "text", "text" to prompt),
                imageContent.toMap()
            )

            val anthropicMessages = listOf(
                AnthropicMessage("user", contentBlocks)
            )

            val request = AnthropicMessageRequest(
                model = model,
                max_tokens = MAX_TOKENS_DEFAULT,
                messages = anthropicMessages,
                stream = false
            )

            executeWithRetry(request) { response ->
                if (!response.isSuccessful) handleApiError(response)
                val body = response.body?.string()
                    ?: throw Exception("Empty vision response")
                val parsed = gson.fromJson(body, AnthropicMessageResponse::class.java)
                parsed.content?.firstOrNull()?.text
                    ?: throw Exception("No text content in vision response")
            }
        }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "testConnection")
        try {
            val request = AnthropicMessageRequest(
                model = model,
                max_tokens = 10,
                messages = listOf(AnthropicMessage("user", "Hi")),
                stream = false
            )
            val req = Request.Builder()
                .url("$baseUrl/messages")
                .post(gson.toJson(request).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", apiVersion)
                .addHeader("anthropic-dangerous-direct-browser-access", "true")
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            false
        }
    }

    override fun getProviderName(): String = "Anthropic Claude ($model)"

    override fun release() {
        Log.i(TAG, "release")
        try { httpClient.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 构建 Anthropic 格式的消息列表
     * 将历史消息和当前 prompt 转换为 Anthropic API 格式
     */
    private fun buildAnthropicMessages(history: List<Message>, prompt: String): List<AnthropicMessage> {
        val messages = mutableListOf<AnthropicMessage>()

        history.forEach { msg ->
            val role = when {
                msg.role.equals("system", ignoreCase = true) -> null // system prompt handled separately
                msg.role.equals("assistant", ignoreCase = true) -> "assistant"
                else -> "user"
            }
            if (role != null) {
                messages.add(AnthropicMessage(role, msg.content))
            }
        }

        messages.add(AnthropicMessage("user", prompt))
        return messages
    }

    /**
     * 带重试的请求执行
     */
    private suspend fun <T> executeWithRetry(
        request: AnthropicMessageRequest,
        parse: suspend (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                val httpRequest = Request.Builder()
                    .url("$baseUrl/messages")
                    .post(gson.toJson(request).toRequestBody(JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", apiVersion)
                    .addHeader("anthropic-dangerous-direct-browser-access", "true")
                    .build()

                var shouldRetryFlag = false
                var errorCode = 0
                httpClient.newCall(httpRequest).execute().use { response ->
                    if (shouldRetry(response)) {
                        errorCode = response.code
                        lastException = Exception("HTTP ${response.code}")
                        response.close()
                        shouldRetryFlag = true
                        return@use
                    }
                    return@withContext parse(response)
                }
                if (shouldRetryFlag) {
                    Log.w(TAG, "Attempt $attempt failed with $errorCode, retrying")
                    kotlinx.coroutines.delay(RETRY_DELAY_MS.getOrElse(attempt) { 8000L })
                    continue
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

    private fun shouldRetry(response: Response): Boolean {
        val code = response.code
        return code == 429 || code == 500 || code == 502 || code == 503
    }

    private fun handleApiError(response: Response) {
        val code = response.code
        val body = response.body?.string() ?: ""
        val errorMsg = try {
            val json = gson.fromJson(body, Map::class.java)
            (json["error"] as? Map<*, *>)?.get("type")?.toString()
                ?: (json["type"] as? String)
                ?: body
        } catch (_: Exception) {
            body
        }
        Log.e(TAG, "API error $code: $errorMsg")

        when (code) {
            401 -> throw SecurityException("Invalid Anthropic API key")
            403 -> throw SecurityException("API access forbidden")
            429 -> throw QuotaExceededException("Anthropic rate limit exceeded")
            400 -> throw IllegalArgumentException("Bad request: $errorMsg")
            else -> throw Exception("Anthropic API error ($code): $errorMsg")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
