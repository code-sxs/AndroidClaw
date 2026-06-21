// MediaPipeEngine.kt
// MediaPipe LLM Inference API 实现 (首选引擎)

package com.androidclaw.app.llm.engine

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.model.InferenceState
import com.androidclaw.app.llm.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaPipe LLM Inference API 引擎
 * 
 * 优势:
 * - Google 官方维护
 * - 支持 GPU 加速 (OpenCL / Vulkan)
 * - 支持 NPU 加速 (通过 NNAPI)
 * - 模型格式: .litertlm / .tflite
 * 
 * 参考:
 * - https://developers.google.com/mediapipe/solutions/genai/llm_inference
 * - https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
 */
class MediaPipeEngine(context: Context) : BaseEngine(context) {

    companion object {
        private const val TAG = "MediaPipeEngine"

        /**
         * 检查是否支持指定模型
         */
        fun isSupported(modelConfig: ModelConfig): Boolean {
            // MediaPipe 支持 .litertlm 和 .tflite 格式
            val supportedFormats = listOf(".litertlm", ".tflite")
            val hasSupportedFormat = supportedFormats.any {
                modelConfig.fileName.endsWith(it)
            }

            // 检查 API level (需要 Android 8.1+ / API 27+)
            val meetsAPIRequirement = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1

            return hasSupportedFormat && meetsAPIRequirement
        }
    }

    // MediaPipe LLM Inference API 对象
    private var llmInference: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null

    override suspend fun initialize(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing MediaPipe engine with model: ${modelConfig.modelName}")

        try {
            // 1. 检查模型文件是否存在
            val modelFile = java.io.File(modelConfig.downloadUrl) // TODO: 实际使用 ModelDownloader 获取文件路径
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                return@withContext false
            }

            // 2. 配置 MediaPipe LLM Inference API
            val options = com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)  // 最大生成 token 数
                .setTopK(40)
                .setTemperature(0.7f)
                .build()

            // 3. 创建 LLM Inference 实例
            llmInference = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(
                context,
                options
            )

            currentModelConfig = modelConfig
            isInitialized = true
            Log.i(TAG, "MediaPipe engine initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe engine", e)
            false
        }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.e(TAG, "Engine not ready")
            return@withContext ""
        }

        try {
            Log.d(TAG, "Generating text with prompt: $prompt")
            val response = llmInference?.generateResponse(prompt) ?: ""
            Log.d(TAG, "Generated response: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text", e)
            ""
        }
    }

    override suspend fun generateStream(prompt: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.e(TAG, "Engine not ready")
            return@withContext
        }

        try {
            Log.d(TAG, "Generating text stream with prompt: $prompt")

            // MediaPipe 支持流式生成
            llmInference?.generateResponseAsync(prompt) { partialResult ->
                Log.d(TAG, "Partial result: $partialResult")
                onToken(partialResult)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text stream", e)
        }
    }

    override fun getInferenceState(): InferenceState {
        return if (isInitialized) {
            InferenceState.Ready
        } else {
            InferenceState.Idle
        }
    }

    override fun release() {
        Log.i(TAG, "Releasing MediaPipe engine")
        llmInference?.close()
        llmInference = null
        isInitialized = false
        currentModelConfig = null
    }

    override fun isSupported(modelConfig: ModelConfig): Boolean {
        return Companion.isSupported(modelConfig)
    }

    override fun getEngineName(): String = "MediaPipe LLM Inference API"
}
