// WakeWordDetector.kt
// 唤醒词检测器 - 离线唤醒词检测（"嘿 AndroidClaw" / "小爪"）
// 当前实现为占位版本，输出 TODO 日志

package com.androidclaw.app.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 唤醒词检测状态
 */
enum class WakeWordState {
    IDLE,           // 空闲
    LISTENING,      // 正在监听
    DETECTED,       // 检测到唤醒词
    ERROR           // 错误
}

/**
 * 唤醒词配置
 */
data class WakeWordConfig(
    val wakeWords: List<String> = listOf("小爪", "AndroidClaw"),  // 唤醒词列表
    val sensitivity: Float = 0.5f,  // 灵敏度 (0.0 - 1.0)
    val enableBackgroundListening: Boolean = true  // 是否启用后台监听
)

/**
 * 唤醒词监听器接口
 */
interface WakeWordListener {
    fun onWakeWordDetected(wakeWord: String)  // 检测到唤醒词
    fun onError(error: String)                 // 错误
}

/**
 * 唤醒词检测器
 * 
 * 功能:
 * - 离线唤醒词检测（"嘿 AndroidClaw" / "小爪"）
 * - 使用轻量级模型（如 Snowboy 或 Porcupine 的离线版本）
 * - 持续后台监听（低功耗）
 * - 检测到唤醒词后启动语音输入
 * 
 * 注意：这是一个可选功能，当前实现为占位版本。
 * 完整实现需要集成第三方唤醒词检测引擎（如 Picovoice Porcupine）。
 * 
 * TODO: 集成 Picovoice Porcupine 唤醒词检测引擎
 * - 注册账号获取 Access Key: https://picovoice.ai/
 * - 训练自定义唤醒词 "小爪" 或使用预定义唤醒词
 * - 添加依赖: implementation 'ai.picovoice:porcupine-android:3.0.1'
 * - 实现持续后台监听（考虑电池优化策略）
 * 
 * 使用示例:
 * ```
 * val detector = WakeWordDetector(context)
 * detector.listener = object : WakeWordListener { ... }
 * detector.startListening()
 * ```
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val TODO_MESSAGE = """
            TODO: 唤醒词检测功能尚未实现。
            需要集成第三方唤醒词检测引擎（如 Picovoice Porcupine）。
            请参考 SKILL.md 中的实现指南。
        """.trimIndent()
    }

    // 当前配置
    private var config: WakeWordConfig = WakeWordConfig()

    // 监听器
    var listener: WakeWordListener? = null

    // 状态流
    private val _state = MutableStateFlow(WakeWordState.IDLE)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    // 是否正在监听
    private var isListening = false

    init {
        Log.i(TAG, "WakeWordDetector initializing...")
        Log.w(TAG, TODO_MESSAGE)
    }

    /**
     * 开始监听唤醒词
     */
    fun startListening(config: WakeWordConfig = WakeWordConfig()) {
        Log.i(TAG, "Starting wake word detection...")
        Log.w(TAG, TODO_MESSAGE)

        this.config = config
        this.isListening = true
        _state.value = WakeWordState.LISTENING

        // TODO: 实际实现
        // 1. 检查麦克风权限
        // 2. 初始化 Porcupine 引擎
        // 3. 开始录音循环
        // 4. 持续检测唤醒词
        
        // 占位：输出 TODO 日志
        Log.d(TAG, "Wake word detection started (placeholder mode)")
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        Log.i(TAG, "Stopping wake word detection")

        isListening = false
        _state.value = WakeWordState.IDLE

        // TODO: 实际实现
        // 1. 停止录音
        // 2. 释放 Porcupine 引擎资源
    }

    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean = isListening

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: WakeWordConfig) {
        this.config = newConfig
        Log.d(TAG, "Config updated: $newConfig")

        // 如果正在监听，重启以应用新配置
        if (isListening) {
            stopListening()
            startListening(newConfig)
        }
    }

    /**
     * 模拟检测到唤醒词（用于测试）
     */
    fun simulateWakeWord(wakeWord: String) {
        Log.i(TAG, "Simulating wake word detection: $wakeWord")
        _state.value = WakeWordState.DETECTED
        listener?.onWakeWordDetected(wakeWord)
        _state.value = WakeWordState.LISTENING
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing WakeWordDetector")
        stopListening()
        listener = null
    }
}
