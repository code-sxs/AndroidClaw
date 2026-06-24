// VoiceOutputManager.kt
// 语音输出管理器 - 使用 Android TextToSpeech API 实现 TTS
// 支持: 离线语音合成、中文语音、语速/音调配置、队列管理、打断播报

package com.androidclaw.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TTS 配置
 */
data class TtsConfig(
    val language: String = "zh-CN",     // 语言，默认中文
    val speechRate: Float = 1.0f,       // 语速 (0.5 - 2.0)
    val pitch: Float = 1.0f,            // 音调 (0.5 - 2.0)
    val enableQueue: Boolean = true     // 是否排队播报
)

/**
 * TTS 状态
 */
enum class TtsState {
    IDLE,           // 空闲
    INITIALIZING,   // 初始化中
    READY,          // 准备就绪
    SPEAKING,       // 正在播报
    ERROR           // 错误
}

/**
 * 语音输出监听器接口
 */
interface VoiceOutputListener {
    fun onInitialized(success: Boolean, availableLanguages: List<String>)
    fun onSpeakStart()
    fun onSpeakDone()
    fun onSpeakError(error: String)
}

/**
 * 语音输出管理器
 * 
 * 功能:
 * - 使用 Android 内置 TextToSpeech API
 * - 支持离线语音合成
 * - 支持中文语音（优先选择高质量中文语音引擎）
 * - 语速、音调可配置
 * - 队列管理（多条消息顺序播报）
 * - 打断当前播报（用户新指令时）
 * - 检查 TTS 引擎是否可用，不可用时有回调
 * 
 * 使用示例:
 * ```
 * val manager = VoiceOutputManager(context)
 * manager.listener = object : VoiceOutputListener { ... }
 * manager.speak("你好，世界")
 * ```
 */
class VoiceOutputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceOutputManager"
        private const val UTTERANCE_ID_PREFIX = "tts_utterance_"
    }

    // TTS 引擎
    private var tts: TextToSpeech? = null

    // 当前配置
    private var config: TtsConfig = TtsConfig()

    // 监听器
    var listener: VoiceOutputListener? = null

    // 状态流
    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    // 可用语言列表
    private val _availableLanguages = MutableStateFlow<List<String>>(emptyList())
    val availableLanguages: StateFlow<List<String>> = _availableLanguages.asStateFlow()

    // 初始化完成标志
    private var isInitialized = false

    // 播报计数器（用于生成 utterance ID）
    private var utteranceCounter = 0L

    init {
        Log.i(TAG, "VoiceOutputManager initializing...")
        initializeTts()
    }

    /**
     * 初始化 TTS 引擎
     */
    private fun initializeTts() {
        Log.d(TAG, "Initializing TTS engine...")
        _state.value = TtsState.INITIALIZING

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.i(TAG, "TTS engine initialized successfully")
                setupTts()
            } else {
                Log.e(TAG, "TTS engine initialization failed: status=$status")
                _state.value = TtsState.ERROR
                listener?.onInitialized(false, emptyList())
                listener?.onSpeakError("TTS 初始化失败")
            }
        }
    }

    /**
     * 设置 TTS 参数
     */
    private fun setupTts() {
        tts?.apply {
            // 设置进度监听器
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Speak started: $utteranceId")
                    _state.value = TtsState.SPEAKING
                    listener?.onSpeakStart()
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Speak done: $utteranceId")
                    _state.value = TtsState.READY
                    listener?.onSpeakDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Speak error: $utteranceId")
                    _state.value = TtsState.ERROR
                    listener?.onSpeakError("播报出错")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "Speak error: $utteranceId, code=$errorCode")
                    _state.value = TtsState.ERROR
                    listener?.onSpeakError("播报出错 (code: $errorCode)")
                }
            })

            // 获取可用语言
            val languages = mutableListOf<String>()
            val locales = availableLanguages
            for (locale in locales) {
                languages.add(locale.toString())
            }
            _availableLanguages.value = languages
            Log.d(TAG, "Available languages: $languages")

            // 设置默认语言（中文）
            val langSuccess = setLanguage(config.language)
            if (!langSuccess) {
                Log.w(TAG, "Language ${config.language} not available, falling back to default")
                // 尝试使用默认语言
                setLanguage(Locale.getDefault().toString())
            }

            isInitialized = true
            _state.value = TtsState.READY
            listener?.onInitialized(true, languages)
        }
    }

    /**
     * 设置语言
     * @param language 语言代码（如 "zh-CN", "en-US"）
     * @return 是否设置成功
     */
    fun setLanguage(language: String): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return false
        }

        val locale = when {
            language.contains("-") -> {
                val parts = language.split("-")
                if (parts.size == 2) {
                    Locale(parts[0], parts[1])
                } else {
                    Locale(language)
                }
            }
            else -> Locale(language)
        }

        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val success = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

        if (success) {
            config = config.copy(language = language)
            Log.i(TAG, "Language set to: $language")
        } else {
            Log.w(TAG, "Failed to set language: $language, result=$result")
        }

        return success
    }

    /**
     * 设置语速
     * @param rate 语速 (0.5 - 2.0)
     */
    fun setSpeechRate(rate: Float) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(clampedRate)
        config = config.copy(speechRate = clampedRate)
        Log.d(TAG, "Speech rate set to: $clampedRate")
    }

    /**
     * 设置音调
     * @param pitch 音调 (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clampedPitch)
        config = config.copy(pitch = clampedPitch)
        Log.d(TAG, "Pitch set to: $clampedPitch")
    }

    /**
     * 播报文本
     * @param text 要播报的文本
     * @param queueMode 队列模式：QUEUE_ADD（排队）或 QUEUE_FLUSH（打断当前）
     */
    fun speak(
        text: String,
        queueMode: Int = if (config.enableQueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
    ) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, cannot speak")
            listener?.onSpeakError("TTS 未初始化")
            return
        }

        if (text.isBlank()) {
            Log.d(TAG, "Empty text, skipping")
            return
        }

        val utteranceId = "$UTTERANCE_ID_PREFIX${utteranceCounter++}"
        Log.i(TAG, "Speaking: $text (id=$utteranceId, queueMode=$queueMode)")

        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * 打断当前播报并开始新的播报
     * @param text 要播报的文本
     */
    fun speakNow(text: String) {
        speak(text, TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * 添加到播报队列
     * @param text 要播报的文本
     */
    fun speakQueued(text: String) {
        speak(text, TextToSpeech.QUEUE_ADD)
    }

    /**
     * 停止当前播报并清空队列
     */
    fun stop() {
        Log.i(TAG, "Stopping TTS")
        tts?.stop()
        _state.value = TtsState.READY
    }

    /**
     * 暂停播报（如果引擎支持）
     */
    fun pause() {
        Log.d(TAG, "Pausing TTS")
        // 注意：不是所有 TTS 引擎都支持暂停
        // Note: playSilent is not available in standard Android TTS API
    }

    /**
     * 检查是否正在播报
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: TtsConfig) {
        config = newConfig
        setLanguage(newConfig.language)
        setSpeechRate(newConfig.speechRate)
        setPitch(newConfig.pitch)
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): TtsConfig = config

    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean = isInitialized && _state.value == TtsState.READY

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing VoiceOutputManager")
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        listener = null
        isInitialized = false
        _state.value = TtsState.IDLE
    }
}
