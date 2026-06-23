// AgentManager.kt
// Agent 框架核心
// 负责: 对话管理、工具调用、推理协调、语音集成

package com.androidclaw.app.agent

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import com.androidclaw.app.skills.ToolResult
import com.androidclaw.app.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Agent 管理器 - 单例模式
 * 
 * 功能:
 * 1. 管理对话历史
 * 2. 协调 LLM 推理和 Tool 调用
 * 3. 处理多轮对话
 * 4. 格式化 Prompt (包含 Tool 定义)
 */
class AgentManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AgentManager"

        private var INSTANCE: AgentManager? = null

        fun getInstance(context: Context): AgentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val DEFAULT_SYSTEM_PROMPT = "You are AndroidClaw, a helpful AI assistant running locally on Android. You can help users with various tasks using the available tools. Always be concise and helpful. If you don't know something, just say so."
    }

    // LLM 管理器
    private val llmManager: LLMManager by lazy {
        (context.applicationContext as com.androidclaw.app.AndroidClawApplication).llmManager
    }

    // 工具注册中心
    private val toolRegistry: ToolRegistry by lazy {
        ToolRegistry.getInstance(context)
    }

    // 对话历史
    private val conversationHistory = mutableListOf<Message>()

    // 系统提示 (包含 Tool 定义)
    private var systemPrompt: String = DEFAULT_SYSTEM_PROMPT

    // 语音管理器
    private val voiceManager: VoiceManager by lazy {
        VoiceManager.getInstance(context)
    }

    // 语音配置
    private var voiceConfig: VoiceConfig = VoiceConfig()

    init {
        Log.i(TAG, "AgentManager initializing...")
    }

    /**
     * 发送消息并获取回复 (流式)
     * @param userMessage 用户消息
     * @return 流式回复 Flow
     */
    fun sendMessageStream(userMessage: String): Flow<String> = callbackFlow {
        Log.i(TAG, "Sending message (stream): $userMessage")

        try {
            // 1. 添加用户消息到历史
            val userMsg = Message(role = "user", content = userMessage)
            conversationHistory.add(userMsg)

            // 2. 构建 Prompt (包含对话历史和 Tool 定义)
            val prompt = buildPrompt()

            // 3. 调用 LLM (流式)
            llmManager.generateTextStream(prompt) { token ->
                // 转发 token
                trySend(token)
            }

            // TODO: 4. 解析 LLM 输出，检查是否需要调用 Tool
            // 如果 LLM 输出包含 Tool 调用请求，则执行 Tool 并继续推理

            // 5. 添加 LLM 回复到历史 (TODO: 需要实现完整的流式接收)
            // val assistantMsg = Message(role = "assistant", content = fullResponse)
            // conversationHistory.add(assistantMsg)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message (stream)", e)
            trySend("Error: ${e.message}")
        }

        close()
    }

    /**
     * 发送消息并获取回复 (单次)
     * @param userMessage 用户消息
     * @return 完整回复
     */
    suspend fun sendMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Sending message: $userMessage")

        try {
            // 1. 添加用户消息到历史
            val userMsg = Message(role = "user", content = userMessage)
            conversationHistory.add(userMsg)

            // 2. 构建 Prompt
            val prompt = buildPrompt()

            // 3. 调用 LLM
            val response = llmManager.generateText(prompt)

            // 4. 添加 LLM 回复到历史
            val assistantMsg = Message(role = "assistant", content = response)
            conversationHistory.add(assistantMsg)

            // 5. 如果启用了语音输出，播报回复
            if (voiceConfig.voiceOutputEnabled) {
                voiceManager.speak(response)
            }

            Log.i(TAG, "Message sent successfully, response length: ${response.length}")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 发送语音消息
     * @param voiceText 语音识别结果
     */
    suspend fun sendVoiceMessage(voiceText: String): String {
        Log.i(TAG, "Sending voice message: $voiceText")
        return sendMessage(voiceText)
    }

    /**
     * 构建 Prompt
     * 包含: 系统提示 + 对话历史 + Tool 定义
     */
    private fun buildPrompt(): String {
        val builder = StringBuilder()

        // 1. 系统提示
        builder.append(systemPrompt)
        builder.append("\n\n")

        // 2. Tool 定义 (如果有)
        val tools = toolRegistry.getAllSkills().flatMap { it.getTools() }
        if (tools.isNotEmpty()) {
            builder.append("You have access to the following tools:\n")
            for (tool in tools) {
                builder.append("- ${tool.toolName}: ${tool.description}\n")
            }
            builder.append("\n")
        }

        // 3. 对话历史
        builder.append("Conversation history:\n")
        for (message in conversationHistory) {
            builder.append("${message.role}: ${message.content}\n")
        }

        return builder.toString()
    }

    /**
     * 调用 Tool
     * @param toolName 工具名称
     * @param parameters 工具参数
     */
    suspend fun callTool(toolName: String, parameters: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {

            Log.i(TAG, "Calling tool: $toolName, parameters: $parameters")
            toolRegistry.executeTool(toolName, parameters)
        }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        Log.i(TAG, "Clearing conversation history")
        conversationHistory.clear()
    }

    /**
     * 获取对话历史
     */
    fun getHistory(): List<Message> {
        return conversationHistory.toList()
    }

    /**
     * 设置系统提示
     */
    fun setSystemPrompt(prompt: String) {
        Log.i(TAG, "Setting system prompt")
        systemPrompt = prompt
    }

    /**
     * 更新语音配置
     */
    fun updateVoiceConfig(config: VoiceConfig) {
        Log.i(TAG, "Updating voice config: $config")
        this.voiceConfig = config
        voiceManager.updateConfig(config)
    }

    /**
     * 开始语音输入
     */
    fun startVoiceInput(listener: VoiceManagerListener? = null) {
        Log.i(TAG, "Starting voice input")
        listener?.let { voiceManager.listener = it }
        voiceManager.startListening()
    }

    /**
     * 停止语音输入
     */
    fun stopVoiceInput() {
        Log.i(TAG, "Stopping voice input")
        voiceManager.stopListening()
    }

    /**
     * 播报文本
     */
    fun speak(text: String) {
        Log.i(TAG, "Speaking: $text")
        voiceManager.speak(text)
    }

    /**
     * 停止播报
     */
    fun stopSpeaking() {
        Log.i(TAG, "Stopping speech")
        voiceManager.stopSpeaking()
    }

    /**
     * 重置系统提示为默认
     */
    fun resetSystemPrompt() {
        systemPrompt = DEFAULT_SYSTEM_PROMPT
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing AgentManager")
        clearHistory()
    }

    /**
     * 消息数据类
     */
    data class Message(
        val role: String,    // "user" / "assistant" / "system"
        val content: String,  // 消息内容
        val timestamp: Long = System.currentTimeMillis()
    )
}
