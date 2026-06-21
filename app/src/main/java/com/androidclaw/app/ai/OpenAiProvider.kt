// OpenAiProvider.kt
// OpenAI 提供商实现
// 支持 GPT-4, GPT-3.5

package com.androidclaw.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI API 请求
 */
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val stream: Boolean = false
)

/**
 * OpenAI 消息
 */
data class OpenAiMessage(
    val role: String,
    val content: String
)

/**
 * OpenAI API 响应
 */
data class OpenAiResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<OpenAiChoice>?,
    val usage: OpenAiUsage?
)

/**
 * OpenAI 选择
 */
data class OpenAiChoice(
    val index: Int?,
    val message: OpenAiMessage?,
    val finishReason: String?
)

/**
 * OpenAI 使用统计
 */
data class OpenAiUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)

/**
 * OpenAI 提供商实现
 */
class OpenAiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4-turbo-preview"
) : AiProvider {

    companion object {
        private const val TAG = "OpenAiProvider"
        private val gson = Gson()
        private val JSON = "application/json".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateText(prompt: String, history: List<Message>): String {
        Log.i(TAG, "Generating text with OpenAI")

        val messages = buildMessages(prompt, history)
        val request = OpenAiRequest(
            model = model,
            messages = messages,
            stream = false
        )

        val json = gson.toJson(request)
        val body = json.toRequestBody(JSON)

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        return try {
            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                throw Exception("OpenAI API error: ${response.code} ${response.message}")
            }

            val openAiResponse = gson.fromJson(responseBody, OpenAiResponse::class.java)
            val content = openAiResponse.choices?.firstOrNull()?.message?.content

            content ?: throw Exception("Empty response from OpenAI")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text", e)
            throw e
        }
    }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> {
        Log.i(TAG, "Generating text stream with OpenAI")
        // TODO: 实现 SSE 流式响应
        return flow {
            val text = generateText(prompt, history)
            emit(text)
        }
    }

    override suspend fun supportsVision(): Boolean {
        // GPT-4 Vision, GPT-4 Turbo 支持视觉
        return model.contains("vision") || model.contains("gpt-4-turbo")
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String {
        Log.i(TAG, "Analyzing image with OpenAI")
        // TODO: 实现图片分析 (需要 Base64 编码图片)
        throw NotImplementedError("Image analysis not implemented yet")
    }

    override suspend fun testConnection(): Boolean {
        Log.i(TAG, "Testing OpenAI connection")

        return try {
            val request = OpenAiRequest(
                model = model,
                messages = listOf(OpenAiMessage("user", "Hello")),
                maxTokens = 10
            )

            val json = gson.toJson(request)
            val body = json.toRequestBody(JSON)

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }

    override fun getProviderName(): String {
        return "OpenAI ($model)"
    }

    override fun release() {
        Log.i(TAG, "Releasing OpenAI provider")
        httpClient.dispatcher.executorService.shutdown()
    }

    /**
     * 构建消息列表
     */
    private fun buildMessages(prompt: String, history: List<Message>): List<OpenAiMessage> {
        val messages = mutableListOf<OpenAiMessage>()

        // 添加历史消息
        history.forEach { msg ->
            messages.add(OpenAiMessage(msg.role, msg.content))
        }

        // 添加当前提示词
        messages.add(OpenAiMessage("user", prompt))

        return messages
    }
}
