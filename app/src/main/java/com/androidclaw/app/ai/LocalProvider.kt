// LocalProvider.kt
// 本地模型提供商实现
// 包装 LLMManager，使用本地推理引擎

package com.androidclaw.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 本地模型提供商
 * 使用 LLMManager 进行本地推理
 */
class LocalProvider(
    private val llmManager: LLMManager
) : AiProvider {

    companion object {
        private const val TAG = "LocalProvider"
    }

    override suspend fun generateText(prompt: String, history: List<Message>): String {
        Log.i(TAG, "Generating text with local model")

        return try {
            // 构建完整提示词 (包含历史)
            val fullPrompt = buildPrompt(prompt, history)
            
            // 调用 LLMManager 生成文本
            llmManager.generateText(fullPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text with local model", e)
            throw e
        }
    }

    override suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> {
        Log.i(TAG, "Generating text stream with local model")
        
        // 返回 Flow，但需要适配 LLMManager 的回调接口
        return flow {
            val fullPrompt = buildPrompt(prompt, history)
            
            // TODO: LLMManager 需要添加 Flow 支持
            // 目前使用回调接口，需要适配为 Flow
            var result = ""
            llmManager.generateTextStream(fullPrompt) { token ->
                result += token
                // TODO: 发射每个 token
            }
            
            emit(result)
        }
    }

    override suspend fun supportsVision(): Boolean {
        // 取决于本地模型是否支持视觉
        // 目前默认不支持
        return false
    }

    override suspend fun analyzeImage(image: Bitmap, prompt: String): String {
        Log.i(TAG, "Analyzing image with local model")
        // TODO: 如果本地模型支持视觉，实现图片分析
        throw NotImplementedError("Local vision not implemented yet")
    }

    override suspend fun testConnection(): Boolean {
        Log.i(TAG, "Testing local model connection")
        // 本地模型始终可用（如果已初始化）
        // TODO: 检查 LLMManager 是否已初始化
        return true
    }

    override fun getProviderName(): String {
        return "Local Model"
    }

    override fun release() {
        Log.i(TAG, "Releasing local provider")
        // 不释放 LLMManager，因为它是由 Application 管理的单例
    }

    /**
     * 构建完整提示词 (包含历史)
     */
    private fun buildPrompt(prompt: String, history: List<Message>): String {
        val builder = StringBuilder()

        // 添加历史消息
        history.forEach { msg ->
            builder.append("${msg.role}: ${msg.content}\n")
        }

        // 添加当前提示词
        builder.append("user: $prompt\n")
        builder.append("assistant: ")

        return builder.toString()
    }
}
