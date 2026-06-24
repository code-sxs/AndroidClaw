// GeminiProvider.kt
// Google Gemini 提供商实现
// 支持 Gemini 1.5 Pro, Gemini 1.5 Flash, Gemini 1.0 Pro
// 支持流式响应和图片理解

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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// DTOs
// ─────────────────────────────────────────────────────────────────────────────

/** Gemini contents 请求结构 */
data class GeminiContents(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

/** Gemini generateContent 请求 */
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val safetySettings: List<GeminiSafetySetting>? = null
)

data class GeminiGenerationConfig(
    val temperature: Double? = 0.9,
    val topP: Double? = 1.0,
    val topK: Int? = 40,
    val maxOutputTokens: Int? = 2048,
    val candidateCount: Int? = 1,
    @SerializedName("responseModalities") val responseModalities: List<String>? = null
)

data class GeminiSafetySetting(
    val category: String,
    val threshold: String = "BLOCK_NONE"
)

/** Gemini generateContent 响应 */
data class GeminiGenerateResponse(
    val promptFeedback: GeminiPromptFeedback?,
    val candidates: List<GeminiCandidate>?,
    val usageMetadata: GeminiUsageMetadata?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?,
    val safetyRatings: List<GeminiSafetyRating>?
)

data class GeminiSafetyRating(
    val category: String?,
    val probability: String?
)

data class GeminiPromptFeedback(
    val safetyRatings: List<GeminiSafetyRating>?
)

data class GeminiUsageMetadata(
    @SerializedName("promptTokenCount") val promptTokenCount: Int?,
    @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int?,
    @SerializedName("totalTokenCount") val totalTokenCount: Int?
)

// Streaming chunk
data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate>?,
    val usageMetadata: GeminiUsageMetadata?
)

// ─────────────────────────────────────────────────────────────────────────────
// Provider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Google Gemini API 提供商
 * 支持: Gemini 1.5 Pro, Gemini 1.5 Flash, Gemini 1.0 Pro
 * 支持流式响应和图片理解
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-1.5-pro"
) : AiProvider {

    companion object {
        private const val TAG = "GeminiProvider"
        private val gson = Gson()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MAX_RETRIES = 3
        private val RETRY_DELAY_MS = listOf(500L, 2000L, 8000L)

        // 支持视觉的模型 (Gemini 1.5+ 全系支持)
        private val VISION_MODELS = setOf(
            "gemini-1.5-pro", "gemini-1.5-flash",
            "gemini-1.0-pro-vision", "gemini-pro-vision"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val apiUrl: String get() = "$BASE_URL/models/${model}:generateContent?key=$apiKey"
    private val streamUrl: String get() = "$BASE_URL/models/${model}:streamGenerateContent?key=$apiKey&alt=sse"

    // ─────────────────────────────────────────────────────────────────────────
    // AiProvider 实现
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun generateText(prompt: String, history: List<Message>): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "generateText: model=$model")

            val contents = buildGeminiContents(history, prompt)
            val request = GeminiGenerateRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.9,
                    maxOutputTokens = 4096
                )
            )

            executeWithRetry(request, apiUrl) { response ->
                if (!response.isSuccessful) handleApiError(response)
                val body = response.body?.string()
                    ?: throw Exception("Empty response from Gemini")
                val parsed = gson.fromJson(body, GeminiGenerateResponse::class.java)
                parsed.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No text content in response")
            }
        }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> =
        flow {
            Log.i(TAG, "generateTextStream: model=$model")

            val contents = buildGeminiContents(history, prompt)
            val request = GeminiGenerateRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.9,
                    maxOutputTokens = 4096
                )
            )

            val requestBuilder = Request.Builder()
                .url(streamUrl)
                .post(gson.toJson(request).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")

            var collected = ""

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
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
                                val chunk = gson.fromJson(data, GeminiStreamChunk::class.java)
                                val text = chunk.candidates?.firstOrNull()
                                    ?.content?.parts?.firstOrNull()?.text
                                if (!text.isNullOrBlank()) {
                                    emit(text)
                                    collected += text
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
        return VISION_MODELS.any { model.lowercase().contains(it.lowercase()) }
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "analyzeImage: prompt=$prompt")

            if (!supportsVision()) {
                throw Exception("Model $model does not support vision")
            }

            val base64Image = bitmapToBase64(image)
            val imagePart = GeminiPart(
                inlineData = GeminiInlineData(
                    mimeType = "image/jpeg",
                    data = base64Image
                )
            )

            val textPart = GeminiPart(text = prompt)

            val contents = listOf(
                GeminiContent(parts = listOf(imagePart, textPart))
            )

            val request = GeminiGenerateRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(maxOutputTokens = 4096)
            )

            executeWithRetry(request, apiUrl) { response ->
                if (!response.isSuccessful) handleApiError(response)
                val body = response.body?.string()
                    ?: throw Exception("Empty vision response")
                val parsed = gson.fromJson(body, GeminiGenerateResponse::class.java)
                parsed.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No text content in vision response")
            }
        }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "testConnection")
        try {
            val testRequest = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "Hi")))
                ),
                generationConfig = GeminiGenerationConfig(maxOutputTokens = 10)
            )
            val request = Request.Builder()
                .url(apiUrl)
                .post(gson.toJson(testRequest).toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            false
        }
    }

    override fun getProviderName(): String = "Google Gemini ($model)"

    override fun release() {
        Log.i(TAG, "release")
        try { httpClient.dispatcher.executorService.shutdown() } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 构建 Gemini contents
     * history 中的消息按 role 分组，user/assistant 分别作为 gemini 的 role
     * 当前 prompt 作为最后一条 user 消息
     */
    private fun buildGeminiContents(history: List<Message>, prompt: String): List<GeminiContent> {
        val contents = mutableListOf<GeminiContent>()

        history.forEach { msg ->
            val role = when (msg.role) {
                "assistant" -> "model"
                "user" -> "user"
                else -> return@forEach
            }
            contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.content))))
        }

        // 当前 prompt
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt))))
        return contents
    }

    /**
     * 带重试的请求执行
     */
    private suspend fun <T> executeWithRetry(
        request: GeminiGenerateRequest,
        url: String,
        parse: suspend (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                val httpRequest = Request.Builder()
                    .url(url)
                    .post(gson.toJson(request).toRequestBody(JSON))
                    .addHeader("Content-Type", "application/json")
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
            (json["error"] as? Map<*, *>)?.get("message")?.toString() ?: body
        } catch (_: Exception) {
            body
        }
        Log.e(TAG, "API error $code: $errorMsg")

        when (code) {
            400 -> throw IllegalArgumentException("Gemini bad request: $errorMsg")
            401, 403 -> throw SecurityException("Invalid Google API key")
            429 -> throw QuotaExceededException("Gemini rate limit exceeded")
            else -> throw Exception("Gemini API error ($code): $errorMsg")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
