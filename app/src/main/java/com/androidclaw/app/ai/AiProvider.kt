// AiProvider.kt
// AI 提供商接口定义
// 支持 Local (默认), OpenAI, Anthropic, Google, Custom

package com.androidclaw.app.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * AI 提供商接口
 * 所有 AI 提供商必须实现此接口
 */
interface AiProvider {

    /**
     * 生成文本 (单次)
     * @param prompt 提示词
     * @param history 对话历史
     * @return 生成的文本
     */
    suspend fun generateText(prompt: String, history: List<Message>): String

    /**
     * 生成文本 (流式)
     * @param prompt 提示词
     * @param history 对话历史
     * @return 流式文本 Flow
     */
    suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String>

    /**
     * 是否支持视觉 (图片分析)
     */
    suspend fun supportsVision(): Boolean

    /**
     * 分析图片
     * @param image 图片 Bitmap
     * @param prompt 提示词
     * @return 分析结果
     */
    suspend fun analyzeImage(image: Bitmap, prompt: String): String

    /**
     * 测试连接
     * @return 是否连接成功
     */
    suspend fun testConnection(): Boolean

    /**
     * 获取提供商名称
     */
    fun getProviderName(): String

    /**
     * 释放资源
     */
    fun release()
}

/**
 * 消息数据类
 */
data class Message(
    val role: String,    // "user" / "assistant" / "system"
    val content: String   // 消息内容
)

/**
 * AI 提供商类型
 */
enum class AiProviderType {
    LOCAL,      // 本地模型 (默认)
    OPENAI,     // OpenAI (GPT-4/GPT-3.5)
    ANTHROPIC,  // Anthropic (Claude 3.5/3)
    GOOGLE,     // Google (Gemini 1.5)
    CUSTOM      // 自定义端点 (兼容 OpenAI API)
}
