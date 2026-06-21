// GeminiProvider.kt
// Google (Gemini) 提供商实现
// 支持 Gemini 1.5 Pro, Gemini 1.5 Flash

package com.androidclaw.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google Gemini 提供商实现
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-1.5-pro"
) : AiProvider {

    companion object {
        private const val TAG = "GeminiProvider"
        private val gson = Gson()
        private val JSON = "application/json".toMediaType()
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateText(prompt: String, history: List<Message>): String {
        Log.i(TAG, "Generating text with Gemini")

        // TODO: 实现 Gemini API 调用
        // Gemini API 格式与 OpenAI 不同
        throw NotImplementedError("Gemini provider not fully implemented yet")
    }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> {
        Log.i(TAG, "Generating text stream with Gemini")
        // TODO: 实现流式响应
        return flow {
            val text = generateText(prompt, history)
            emit(text)
        }
    }

    override suspend fun supportsVision(): Boolean {
        // Gemini 1.5 Pro/Flash 支持视觉
        return true
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String {
        Log.i(TAG, "Analyzing image with Gemini")
        // TODO: 实现图片分析
        throw NotImplementedError("Image analysis not implemented yet")
    }

    override suspend fun testConnection(): Boolean {
        Log.i(TAG, "Testing Gemini connection")
        // TODO: 实现连接测试
        return false
    }

    override fun getProviderName(): String {
        return "Google Gemini ($model)"
    }

    override fun release() {
        Log.i(TAG, "Releasing Gemini provider")
        httpClient.dispatcher.executorService.shutdown()
    }
}
