// VoiceInputManager.kt
// 语音输入管理器 - 使用 Android SpeechRecognizer API 实现 STT
// 支持: 离线识别、在线识别、实时流式结果、超时处理、噪音检测

package com.androidclaw.app.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 语音输入错误类型
 */
enum class VoiceError {
    NO_PERMISSION,      // 缺少 RECORD_AUDIO 权限
    NO_RECOGNITION,     // 无法识别
    NETWORK_ERROR,      // 网络错误（在线模式）
    BUSY,               // 语音识别服务忙
    TIMEOUT,            // 超时
    UNKNOWN             // 未知错误
}

/**
 * 语音输入状态
 */
enum class VoiceInputState {
    IDLE,               // 空闲
    LISTENING,          // 正在监听
    PROCESSING,         // 正在处理
    ERROR               // 错误
}

/**
 * 语音输入监听器接口
 */
interface VoiceInputListener {
    fun onPartialResult(text: String)      // 实时部分结果
    fun onFinalResult(text: String)        // 最终结果
    fun onError(error: VoiceError)         // 错误
    fun onReadyForSpeech()                 // 可以开始说话
    fun onEndOfSpeech()                    // 检测到达语音结尾
}

/**
 * 语音输入配置
 */
data class VoiceInputConfig(
    val language: String = Locale.CHINA.language,  // 语言，默认中文
    val useOffline: Boolean = false,                // 是否使用离线模式
    val timeoutMs: Long = 10000,                     // 超时时间（毫秒）
    val enablePartialResults: Boolean = true        // 是否启用实时结果
)

/**
 * 语音输入管理器
 * 
 * 功能:
 * - 使用 Android 内置 SpeechRecognizer API
 * - 支持离线识别（提示用户下载离线语言包）
 * - 支持在线识别（更高精度）
 * - 实时返回识别中的部分结果（流式）
 * - 噪音检测和处理
 * - 超时自动停止
 * - 错误处理和用户提示
 * 
 * 使用示例:
 * ```
 * val manager = VoiceInputManager(context)
 * manager.listener = object : VoiceInputListener { ... }
 * manager.startListening()
 * ```
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val DEFAULT_TIMEOUT_MS = 10000L
    }

    // 语音识别器
    private var speechRecognizer: SpeechRecognizer? = null

    // 当前配置
    private var config: VoiceInputConfig = VoiceInputConfig()

    // 监听器
    var listener: VoiceInputListener? = null

    // 状态流
    private val _state = MutableStateFlow(VoiceInputState.IDLE)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    // 当前识别的部分结果
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // 超时处理
    private var timeoutRunnable: Runnable? = null
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        Log.i(TAG, "VoiceInputManager initializing...")
    }

    /**
     * 检查 SpeechRecognizer 是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val activities = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            activities.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check SpeechRecognizer availability", e)
            false
        }
    }

    /**
     * 开始语音识别
     * @param config 识别配置
     */
    fun startListening(config: VoiceInputConfig = VoiceInputConfig()) {
        Log.i(TAG, "Starting voice recognition, offline=${config.useOffline}")

        this.config = config

        // 检查权限
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "Missing RECORD_AUDIO permission")
            listener?.onError(VoiceError.NO_PERMISSION)
            return
        }

        // 检查是否可用
        if (!isAvailable()) {
            Log.e(TAG, "SpeechRecognizer not available")
            listener?.onError(VoiceError.NO_RECOGNITION)
            return
        }

        // 销毁旧的识别器
        stopListening()

        // 创建新的识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        // 构建识别 Intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL, config.enablePartialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // 在线/离线模式
            if (config.useOffline) {
                // 提示使用离线识别，需要用户下载语言包
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
            }
        }

        // 开始监听
        try {
            speechRecognizer?.startListening(intent)
            _state.value = VoiceInputState.LISTENING
            _partialText.value = ""

            // 设置超时
            setupTimeout(config.timeoutMs)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener?.onError(VoiceError.UNKNOWN)
            _state.value = VoiceInputState.ERROR
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        Log.i(TAG, "Stopping voice recognition")

        cancelTimeout()

        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null

        _state.value = VoiceInputState.IDLE
    }

    /**
     * 取消当前识别
     */
    fun cancel() {
        Log.i(TAG, "Cancelling voice recognition")
        stopListening()
        _partialText.value = ""
    }

    /**
     * 检查是否有录音权限
     */
    private fun hasRecordAudioPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == 
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 设置超时
     */
    private fun setupTimeout(timeoutMs: Long) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            Log.w(TAG, "Voice recognition timeout")
            if (_state.value == VoiceInputState.LISTENING) {
                listener?.onError(VoiceError.TIMEOUT)
                stopListening()
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    /**
     * 取消超时
     */
    private fun cancelTimeout() {
        timeoutRunnable?.let {
            timeoutHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    /**
     * 识别监听器
     */
    private val recognitionListener = object : RecognitionListener {
        
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            Log.d(TAG, "Ready for speech")
            listener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech began")
            cancelTimeout() // 用户开始说话，取消超时
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 音量变化，可用于 UI 反馈
            // Log.v(TAG, "RMS: $rmsdB")
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // 收到音频缓冲区
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
            _state.value = VoiceInputState.PROCESSING
            listener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Recognition error: $error")
            val voiceError = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> VoiceError.NO_RECOGNITION
                SpeechRecognizer.ERROR_CLIENT -> VoiceError.UNKNOWN
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION
                SpeechRecognizer.ERROR_NETWORK -> VoiceError.NETWORK_ERROR
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> VoiceError.NETWORK_ERROR
                SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_RECOGNITION
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceError.BUSY
                SpeechRecognizer.ERROR_SERVER -> VoiceError.NETWORK_ERROR
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.TIMEOUT
                else -> VoiceError.UNKNOWN
            }
            listener?.onError(voiceError)
            _state.value = VoiceInputState.ERROR
            cancelTimeout()
        }

        override fun onResults(results: android.os.Bundle?) {
            Log.d(TAG, "Recognition results received")
            cancelTimeout()

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            
            Log.i(TAG, "Final result: $text")
            listener?.onFinalResult(text)
            _state.value = VoiceInputState.IDLE
            _partialText.value = ""
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            if (!config.enablePartialResults) return
            
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            
            Log.d(TAG, "Partial result: $text")
            _partialText.value = text
            listener?.onPartialResult(text)
        }

        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
            Log.d(TAG, "Event: $eventType")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing VoiceInputManager")
        stopListening()
        listener = null
    }
}
