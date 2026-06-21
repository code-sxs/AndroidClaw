// VoiceManager.kt
// 语音管理器 - 统一管理语音输入/输出
// 提供便捷的语音交互 API

package com.androidclaw.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音配置
 */
data class VoiceConfig(
    val voiceInputEnabled: Boolean = true,      // 是否启用语音输入
    val voiceOutputEnabled: Boolean = true,     // 是否启用语音输出（TTS 播报）
    val autoSendVoiceInput: Boolean = true,     // 识别完成后是否自动发送
    val language: String = "zh-CN",             // 语言
    val speechRate: Float = 1.0f,               // 语速
    val pitch: Float = 1.0f,                    // 音调
    val offlineMode: Boolean = false            // 是否使用离线模式
)

/**
 * 语音状态
 */
enum class VoiceStatus {
    IDLE,           // 空闲
    LISTENING,      // 正在听
    PROCESSING,     // 正在处理
    SPEAKING,       // 正在播报
    ERROR           // 错误
}

/**
 * 语音管理器监听器
 */
interface VoiceManagerListener {
    fun onStatusChanged(status: VoiceStatus)
    fun onPartialText(text: String)
    fun onFinalText(text: String)
    fun onSpeakStart()
    fun onSpeakDone()
    fun onError(message: String)
}

/**
 * 语音管理器
 * 
 * 统一管理语音输入（STT）和语音输出（TTS）
 * 提供便捷的语音交互 API
 * 
 * 使用示例:
 * ```
 * val voiceManager = VoiceManager.getInstance(context)
 * voiceManager.listener = object : VoiceManagerListener { ... }
 * voiceManager.startListening()
 * ```
 */
class VoiceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
        private const val SOUND_VOICE_START = 1
        private const val SOUND_VOICE_END = 2
        private const val SOUND_TTS_START = 3

        private var INSTANCE: VoiceManager? = null

        fun getInstance(context: Context): VoiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 配置
    private var config: VoiceConfig = VoiceConfig()

    // 监听器
    var listener: VoiceManagerListener? = null

    // 状态
    private val _status = MutableStateFlow(VoiceStatus.IDLE)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    // 部分识别结果
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // 语音输入管理器
    private val voiceInputManager: VoiceInputManager by lazy {
        VoiceInputManager(context).apply {
            this.listener = object : VoiceInputListener {
                override fun onPartialResult(text: String) {
                    Log.d(TAG, "STT partial: $text")
                    _partialText.value = text
                    listener?.onPartialText(text)
                }

                override fun onFinalResult(text: String) {
                    Log.i(TAG, "STT final: $text")
                    _status.value = VoiceStatus.IDLE
                    _partialText.value = ""
                    listener?.onFinalText(text)
                }

                override fun onError(error: VoiceError) {
                    Log.e(TAG, "STT error: $error")
                    _status.value = VoiceStatus.ERROR
                    listener?.onError(getErrorMessage(error))
                }

                override fun onReadyForSpeech() {
                    Log.d(TAG, "Ready for speech")
                    playSound(SOUND_VOICE_START)
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    playSound(SOUND_VOICE_END)
                    _status.value = VoiceStatus.PROCESSING
                }
            }
        }
    }

    // 语音输出管理器
    private val voiceOutputManager: VoiceOutputManager by lazy {
        VoiceOutputManager(context).apply {
            this.listener = object : VoiceOutputListener {
                override fun onInitialized(success: Boolean, availableLanguages: List<String>) {
                    Log.i(TAG, "TTS initialized: $success, languages: $availableLanguages")
                    if (!success) {
                        listener?.onError("TTS 初始化失败")
                    }
                }

                override fun onSpeakStart() {
                    Log.d(TAG, "TTS speak start")
                    _status.value = VoiceStatus.SPEAKING
                    playSound(SOUND_TTS_START)
                    listener?.onSpeakStart()
                }

                override fun onSpeakDone() {
                    Log.d(TAG, "TTS speak done")
                    _status.value = VoiceStatus.IDLE
                    listener?.onSpeakDone()
                }

                override fun onSpeakError(error: String) {
                    Log.e(TAG, "TTS error: $error")
                    listener?.onError(error)
                }
            }
        }
    }

    // 音效播放
    private var soundPool: SoundPool? = null
    private var soundIds = mutableMapOf<Int, Int>()
    private var soundsLoaded = false

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        Log.i(TAG, "VoiceManager initializing...")
        initSounds()
    }

    /**
     * 初始化音效
     */
    private fun initSounds() {
        Log.d(TAG, "Initializing sound effects...")

        soundPool = SoundPool.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setMaxStreams(3)
            .build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        Log.v(TAG, "Sound loaded: $sampleId")
                        checkAllSoundsLoaded()
                    }
                }
            }

        // 尝试加载音效文件，如果不存在则静默失败
        try {
            val res = context.resources
            val packageName = context.packageName

            // voice_start.mp3
            val startId = res.getIdentifier("voice_start", "raw", packageName)
            if (startId != 0) {
                soundIds[SOUND_VOICE_START] = soundPool?.load(context, startId, 1) ?: -1
            }

            // voice_end.mp3
            val endId = res.getIdentifier("voice_end", "raw", packageName)
            if (endId != 0) {
                soundIds[SOUND_VOICE_END] = soundPool?.load(context, endId, 1) ?: -1
            }

            // tts_start.mp3
            val ttsId = res.getIdentifier("tts_start", "raw", packageName)
            if (ttsId != 0) {
                soundIds[SOUND_TTS_START] = soundPool?.load(context, ttsId, 1) ?: -1
            }

            Log.d(TAG, "Sound IDs: $soundIds")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sound effects", e)
        }
    }

    /**
     * 检查所有音效是否已加载
     */
    private fun checkAllSoundsLoaded() {
        soundsLoaded = soundIds.isNotEmpty()
    }

    /**
     * 播放音效
     */
    private fun playSound(soundType: Int) {
        if (!soundsLoaded) return

        val soundId = soundIds[soundType] ?: return
        if (soundId <= 0) return

        try {
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play sound", e)
        }
    }

    /**
     * 开始语音输入
     */
    fun startListening() {
        if (!config.voiceInputEnabled) {
            Log.w(TAG, "Voice input is disabled")
            listener?.onError("语音输入已禁用")
            return
        }

        if (_status.value == VoiceStatus.LISTENING || _status.value == VoiceStatus.SPEAKING) {
            Log.w(TAG, "Already listening or speaking, ignoring start request")
            return
        }

        Log.i(TAG, "Starting voice input...")
        _status.value = VoiceStatus.LISTENING
        _partialText.value = ""

        voiceInputManager.startListening(
            VoiceInputConfig(
                language = config.language,
                useOffline = config.offlineMode,
                enablePartialResults = true
            )
        )
    }

    /**
     * 停止语音输入
     */
    fun stopListening() {
        Log.i(TAG, "Stopping voice input")
        voiceInputManager.stopListening()
        _status.value = VoiceStatus.IDLE
    }

    /**
     * 播报文本
     * @param text 要播报的文本
     * @param interrupt 是否打断当前播报
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (!config.voiceOutputEnabled) {
            Log.w(TAG, "Voice output is disabled")
            return
        }

        if (text.isBlank()) {
            Log.d(TAG, "Empty text, skipping")
            return
        }

        Log.i(TAG, "Speaking: $text (interrupt=$interrupt)")

        if (interrupt) {
            voiceOutputManager.speakNow(text)
        } else {
            voiceOutputManager.speakQueued(text)
        }
    }

    /**
     * 停止播报
     */
    fun stopSpeaking() {
        Log.i(TAG, "Stopping TTS")
        voiceOutputManager.stop()
        _status.value = VoiceStatus.IDLE
    }

    /**
     * 停止所有语音活动
     */
    fun stopAll() {
        Log.i(TAG, "Stopping all voice activities")
        stopListening()
        stopSpeaking()
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: VoiceConfig) {
        Log.i(TAG, "Updating config: $newConfig")
        this.config = newConfig

        // 更新 TTS 配置
        voiceOutputManager.updateConfig(
            TtsConfig(
                language = newConfig.language,
                speechRate = newConfig.speechRate,
                pitch = newConfig.pitch
            )
        )
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): VoiceConfig = config

    /**
     * 检查语音输入是否可用
     */
    fun isVoiceInputAvailable(): Boolean = voiceInputManager.isAvailable()

    /**
     * 检查语音输出是否可用
     */
    fun isVoiceOutputAvailable(): Boolean = voiceOutputManager.isReady()

    /**
     * 获取错误消息
     */
    private fun getErrorMessage(error: VoiceError): String {
        return when (error) {
            VoiceError.NO_PERMISSION -> "缺少录音权限"
            VoiceError.NO_RECOGNITION -> "无法识别，请重试"
            VoiceError.NETWORK_ERROR -> "网络错误"
            VoiceError.BUSY -> "语音服务忙，请稍后重试"
            VoiceError.TIMEOUT -> "未检测到语音"
            VoiceError.UNKNOWN -> "未知错误"
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing VoiceManager")
        stopAll()
        voiceInputManager.release()
        voiceOutputManager.release()
        soundPool?.release()
        soundPool = null
        listener = null
    }
}
