// RemoteInferenceApi.kt
// 远程推理 API 接口定义

package com.androidclaw.app.remote

import kotlinx.coroutines.flow.Flow

/**
 * 远程推理 API 接口
 * 所有远程推理实现（HTTP、WebSocket 等）都需要实现此接口
 */
interface RemoteInferenceApi {
    
    /**
     * 生成文本（非流式）
     */
    suspend fun generate(request: GenerateRequest): GenerateResponse
    
    /**
     * 生成文本（流式）
     */
    suspend fun generateStream(request: GenerateRequest): Flow<String>
    
    /**
     * 分析图片
     */
    suspend fun analyzeImage(request: VisionRequest): VisionResponse
    
    /**
     * 列出可用工具
     */
    suspend fun listTools(): List<com.androidclaw.app.skills.ToolDefinition>
    
    /**
     * 调用工具
     */
    suspend fun callTool(name: String, params: Map<String, Any>): com.androidclaw.app.skills.ToolResult
    
    /**
     * 测试连接
     */
    suspend fun testConnection(): Boolean
    
    /**
     * 关闭连接，释放资源
     */
    fun close()
}

/**
 * 生成请求
 */
data class GenerateRequest(
    val prompt: String,
    val history: List<Message> = emptyList(),
    val tools: List<com.androidclaw.app.skills.ToolDefinition>? = null,
    val stream: Boolean = false
)

/**
 * 生成响应
 */
data class GenerateResponse(
    val text: String,
    val toolCalls: List<ToolCall>? = null,
    val done: Boolean = true
)
