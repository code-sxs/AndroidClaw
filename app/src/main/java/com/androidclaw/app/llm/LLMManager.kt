// LLMManager.kt
// 三引擎推理层 - 统一抽象接口
// 管理 MediaPipe LLM / MLC-LLM / LiteRT 三个推理引擎

package com.androidclaw.app.llm

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.engine.MediaPipeEngine
import com.androidclaw.app.llm.engine.MLCEngine
import com.androidclaw.app.llm.engine.LiteRTEngine
import com.androidclaw.app.llm.model.HardwareCapability
import com.androidclaw.app.llm.model.InferenceEngine
import com.androidclaw.app.llm.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LLM 管理器 - 单例模式
 * 负责：
 * 1. 硬件检测 (NPU/GPU/CPU)
 * 2. 引擎自动选择
 * 3. 模型下载管理
 * 4. 统一推理接口
 */
class LLMManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"
        private var INSTANCE: LLMManager? = null

        fun getInstance(context: Context): LLMManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LLMManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 推理引擎 (三引擎)
    private var mediaPipeEngine: MediaPipeEngine? = null
    private var mlcEngine: MLCEngine? = null
    private var liteRTEngine: LiteRTEngine? = null

    // 当前活跃的引擎
    private var activeEngine: InferenceEngine = InferenceEngine.NONE

    // 硬件能力
    private var hardwareCapability: HardwareCapability? = null

    // 模型下载器
    private lateinit var modelDownloader: ModelDownloader

    init {
        Log.i(TAG, "LLMManager initializing...")
        modelDownloader = ModelDownloader.getInstance(context)
    }

    /**
     * 检测硬件能力
     * 应该在后台线程调用
     */
    suspend fun detectHardware(): HardwareCapability = withContext(Dispatchers.IO) {
        Log.i(TAG, "Detecting hardware capabilities...")

        val capability = HardwareDetector.detect(context)
        hardwareCapability = capability

        Log.i(TAG, "Hardware detected: $capability")
        capability
    }

    /**
     * 初始化推理引擎
     * 根据硬件能力自动选择最优引擎
     */
    suspend fun initializeEngine(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing inference engine for model: ${modelConfig.modelName}")

        // 1. 检查硬件能力
        val hardware = hardwareCapability ?: detectHardware()

        // 2. 根据优先级选择引擎
        val selectedEngine = selectEngine(hardware, modelConfig)
        Log.i(TAG, "Selected engine: $selectedEngine")

        // 3. 初始化选中的引擎
        val success = when (selectedEngine) {
            InferenceEngine.MEDIAPIPE -> initializeMediaPipe(modelConfig)
            InferenceEngine.MLC_LLM -> initializeMLC(modelConfig)
            InferenceEngine.LITERT -> initializeLiteRT(modelConfig)
            InferenceEngine.NONE -> {
                Log.e(TAG, "No suitable engine found")
                false
            }
        }

        if (success) {
            activeEngine = selectedEngine
            Log.i(TAG, "Engine initialized successfully: $selectedEngine")
        } else {
            Log.e(TAG, "Failed to initialize engine: $selectedEngine")
            // 尝试降级到其他引擎
            val fallbackSuccess = tryFallbackEngine(hardware, modelConfig, selectedEngine)
            if (fallbackSuccess) {
                Log.i(TAG, "Fallback engine initialized successfully")
            }
        }

        success
    }

    /**
     * 选择最优推理引擎
     * 优先级: MediaPipe > MLC-LLM > LiteRT
     */
    private fun selectEngine(hardware: HardwareCapability, modelConfig: ModelConfig): InferenceEngine {
        // 检查 MediaPipe 是否可用
        if (hardware.hasGPU && MediaPipeEngine.isSupported(modelConfig)) {
            return InferenceEngine.MEDIAPIPE
        }

        // 检查 MLC-LLM 是否可用
        if (MLCEngine.isSupported(modelConfig)) {
            return InferenceEngine.MLC_LLM
        }

        // 降级到 LiteRT
        if (LiteRTEngine.isSupported(modelConfig)) {
            return InferenceEngine.LITERT
        }

        return InferenceEngine.NONE
    }

    /**
     * 降级尝试其他引擎
     */
    private suspend fun tryFallbackEngine(
        hardware: HardwareCapability,
        modelConfig: ModelConfig,
        failedEngine: InferenceEngine
    ): Boolean {
        Log.w(TAG, "Trying fallback engine (failed: $failedEngine)")

        val enginesToTry = when (failedEngine) {
            InferenceEngine.MEDIAPIPE -> listOf(InferenceEngine.MLC_LLM, InferenceEngine.LITERT)
            InferenceEngine.MLC_LLM -> listOf(InferenceEngine.LITERT)
            else -> emptyList()
        }

        for (engine in enginesToTry) {
            Log.i(TAG, "Trying fallback: $engine")
            val success = when (engine) {
                InferenceEngine.MLC_LLM -> initializeMLC(modelConfig)
                InferenceEngine.LITERT -> initializeLiteRT(modelConfig)
                else -> false
            }

            if (success) {
                activeEngine = engine
                return true
            }
        }

        return false
    }

    /**
     * 初始化 MediaPipe 引擎
     */
    private suspend fun initializeMediaPipe(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            if (mediaPipeEngine == null) {
                mediaPipeEngine = MediaPipeEngine(context)
            }
            mediaPipeEngine!!.initialize(modelConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe", e)
            false
        }
    }

    /**
     * 初始化 MLC-LLM 引擎
     */
    private suspend fun initializeMLC(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            if (mlcEngine == null) {
                mlcEngine = MLCEngine(context)
            }
            mlcEngine!!.initialize(modelConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MLC-LLM", e)
            false
        }
    }

    /**
     * 初始化 LiteRT 引擎
     */
    private suspend fun initializeLiteRT(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            if (liteRTEngine == null) {
                liteRTEngine = LiteRTEngine(context)
            }
            liteRTEngine!!.initialize(modelConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT", e)
            false
        }
    }

    /**
     * 生成文本 (推理)
     */
    suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        when (activeEngine) {
            InferenceEngine.MEDIAPIPE -> mediaPipeEngine?.generate(prompt) ?: ""
            InferenceEngine.MLC_LLM -> mlcEngine?.generate(prompt) ?: ""
            InferenceEngine.LITERT -> liteRTEngine?.generate(prompt) ?: ""
            InferenceEngine.NONE -> {
                Log.e(TAG, "No active engine")
                ""
            }
        }
    }

    /**
     * 流式生成文本
     */
    suspend fun generateTextStream(prompt: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        when (activeEngine) {
            InferenceEngine.MEDIAPIPE -> mediaPipeEngine?.generateStream(prompt, onToken)
            InferenceEngine.MLC_LLM -> mlcEngine?.generateStream(prompt, onToken)
            InferenceEngine.LITERT -> liteRTEngine?.generateStream(prompt, onToken)
            InferenceEngine.NONE -> {
                Log.e(TAG, "No active engine")
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing LLMManager resources")
        mediaPipeEngine?.release()
        mlcEngine?.release()
        liteRTEngine?.release()
        mediaPipeEngine = null
        mlcEngine = null
        liteRTEngine = null
        activeEngine = InferenceEngine.NONE
    }
}
