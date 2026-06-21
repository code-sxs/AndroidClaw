// ChatViewModel.kt
// 聊天界面的 ViewModel - 管理聊天状态和语音交互

package com.androidclaw.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.app.voice.*
import kotlinx.coroutines.flow.*

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: String,  // "user" / "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 聊天界面的 ViewModel
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // 语音管理器
    private val voiceManager: VoiceManager by lazy {
        VoiceManager.getInstance(application).apply {
            this.listener = object : VoiceManagerListener {
                override fun onStatusChanged(status: VoiceStatus) {
                    Log.d(TAG, "Voice status changed: $status")
                    _voiceStatus.value = status
                }

                override fun onPartialText(text: String) {
                    Log.d(TAG, "Partial text: $text")
                    _partialText.value = text
                    _inputText.value = text
                }

                override fun onFinalText(text: String) {
                    Log.i(TAG, "Final text: $text")
                    _partialText.value = ""
                    
                    // 如果配置了自动发送，则发送消息
                    if (_voiceConfig.value.autoSendVoiceInput && text.isNotBlank()) {
                        sendMessage(text)
                    } else {
                        _inputText.value = text
                    }
                }

                override fun onSpeakStart() {
                    Log.d(TAG, "Speak start")
                }

                override fun onSpeakDone() {
                    Log.d(TAG, "Speak done")
                }

                override fun onError(message: String) {
                    Log.e(TAG, "Error: $message")
                    _voiceStatus.value = VoiceStatus.ERROR
                }
            }
        }
    }

    // 消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 输入文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 语音配置
    private val _voiceConfig = MutableStateFlow(VoiceConfig())
    val voiceConfig: StateFlow<VoiceConfig> = _voiceConfig.asStateFlow()

    // 语音状态
    private val _voiceStatus = MutableStateFlow(VoiceStatus.IDLE)
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    // 部分识别结果
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    init {
        Log.i(TAG, "ChatViewModel initializing...")
        
        // 添加欢迎消息
        val welcomeMessage = ChatMessage(
            role = "assistant",
            content = "你好！我是 AndroidClaw，你的本地 AI 助手。有什么可以帮你的吗？"
        )
        _messages.value = listOf(welcomeMessage)
    }

    /**
     * 更新输入文本
     */
    fun updateInputText(text: String) {
        _inputText.value = text
    }

    /**
     * 发送消息
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) {
            return
        }

        Log.i(TAG, "Sending message: $text")

        // 添加用户消息
        val userMessage = ChatMessage(
            role = "user",
            content = text
        )
        _messages.value = _messages.value + userMessage
        _inputText.value = ""
        _isLoading.value = true

        // TODO: 实际调用 AgentManager 发送消息
        // 这里模拟回复
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = "收到你的消息：$text\n\n这是一个模拟回复。实际使用时会调用本地 LLM 进行推理。"
            )
            _messages.value = _messages.value + assistantMessage
            _isLoading.value = false

            // 如果启用了语音输出，则播报回复
            if (_voiceConfig.value.voiceOutputEnabled) {
                speakMessage(assistantMessage)
            }
        }
    }

    /**
     * 播报消息
     */
    fun speakMessage(message: ChatMessage) {
        if (message.role != "assistant") return
        
        Log.i(TAG, "Speaking message: ${message.id}")
        voiceManager.speak(message.content)
    }

    /**
     * 开始语音输入
     */
    fun startListening() {
        Log.i(TAG, "Starting voice listening")
        voiceManager.startListening()
    }

    /**
     * 停止语音输入
     */
    fun stopListening() {
        Log.i(TAG, "Stopping voice listening")
        voiceManager.stopListening()
    }

    /**
     * 停止语音输出
     */
    fun stopSpeaking() {
        Log.i(TAG, "Stopping voice speaking")
        voiceManager.stopSpeaking()
    }

    /**
     * 更新语音配置
     */
    fun updateVoiceConfig(config: VoiceConfig) {
        Log.i(TAG, "Updating voice config: $config")
        _voiceConfig.value = config
        voiceManager.updateConfig(config)
    }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        Log.i(TAG, "Clearing chat history")
        _messages.value = emptyList()
        
        // 重新添加欢迎消息
        val welcomeMessage = ChatMessage(
            role = "assistant",
            content = "对话已清除。有什么新话题吗？"
        )
        _messages.value = listOf(welcomeMessage)
    }

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "ChatViewModel cleared")
        // 不要在这里释放 VoiceManager，因为它是单例
    }
}
