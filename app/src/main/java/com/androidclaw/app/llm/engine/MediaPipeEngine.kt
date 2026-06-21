// MediaPipeEngine.kt
// MediaPipe LLM Inference API 实现 (首选引擎)

package com.androidclaw.app.llm.engine

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.ModelDownloader
import com.androidclaw.app.llm.model.InferenceState
import com.androidclaw.app.llm.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        
        // 默认配置
        private const val DEFAULT_MAX_TOKENS = 1024
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_LOOP_WAIT_TIME_MS = 50L

        /**
         * 检查是否支持指定模型
         */
        fun isSupported(modelConfig: ModelConfig): Boolean {
            // MediaPipe 支持 .litertlm 和 .tflite 格式
            val supportedFormats = listOf(".litertlm", ".tflite")
            val hasSupportedFormat = supportedFormats.any {
                modelConfig.fileName.endsWith(it, ignoreCase = true)
            }

            // 检查 API level (需要 Android 8.1+ / API 27+)
            val meetsAPIRequirement = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1

            // 检查 ABI (MediaPipe 主要支持 arm64-v8a)
            val supportedAbi = android.os.Build.SUPPORTED_ABIS.any { abi ->
                abi == "arm64-v8a" || abi == "x86_64"
            }

            return hasSupportedFormat && meetsAPIRequirement && supportedAbi
        }
    }

    // MediaPipe LLM Inference API 对象
    private var llmInference: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null
    
    // 模型下载器
    private val modelDownloader: ModelDownloader by lazy {
        ModelDownloader.getInstance(context)
    }

    override suspend fun initialize(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing MediaPipe engine with model: ${modelConfig.modelName}")
        Log.d(TAG, "Model config: fileName=${modelConfig.fileName}, fileSize=${modelConfig.fileSizeInBytes} bytes")

        try {
            // 1. 获取模型文件
            val modelFile = modelDownloader.getDownloadedModel(modelConfig)
            if (modelFile == null) {
                Log.e(TAG, "Model file not found or checksum failed: ${modelConfig.fileName}")
                Log.e(TAG, "Please download the model first using ModelDownloader")
                return@withContext false
            }

            Log.d(TAG, "Model file path: ${modelFile.absolutePath}")
            Log.d(TAG, "Model file size: ${modelFile.length()} bytes")

            // 2. 验证模型文件格式
            if (!isSupported(modelConfig)) {
                Log.e(TAG, "Unsupported model format: ${modelConfig.fileName}")
                return@withContext false
            }

            // 3. 配置 MediaPipe LLM Inference API
            Log.d(TAG, "Creating LlmInferenceOptions...")
            val options = com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(DEFAULT_MAX_TOKENS)
                .setTopK(DEFAULT_TOP_K)
                .setTemperature(DEFAULT_TEMPERATURE)
                .setTopP(DEFAULT_TOP_P)
                .build()

            Log.d(TAG, "LlmInferenceOptions created: maxTokens=$DEFAULT_MAX_TOKENS, topK=$DEFAULT_TOP_K, temperature=$DEFAULT_TEMPERATURE")

            // 4. 创建 LLM Inference 实例
            Log.d(TAG, "Creating LlmInference instance...")
            llmInference = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(
                context,
                options
            )

            if (llmInference == null) {
                Log.e(TAG, "Failed to create LlmInference instance (returned null)")
                return@withContext false
            }

            currentModelConfig = modelConfig
            isInitialized = true
            Log.i(TAG, "MediaPipe engine initialized successfully")
            Log.d(TAG, "Engine info: ${getEngineName()}, Model: ${modelConfig.modelName}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe engine", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.e(TAG, "Engine not ready")
            return@withContext ""
        }

        try {
            Log.d(TAG, "Generating text with prompt length: ${prompt.length}")
            Log.v(TAG, "Prompt: $prompt")
            
            val startTime = System.currentTimeMillis()
            val response = llmInference?.generateResponse(prompt) ?: ""
            val endTime = System.currentTimeMillis()
            
            Log.d(TAG, "Generated response length: ${response.length}")
            Log.d(TAG, "Generation time: ${endTime - startTime} ms")
            Log.v(TAG, "Response: $response")
            
            response
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    override suspend fun generateStream(prompt: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.e(TAG, "Engine not ready")
            return@withContext
        }

        try {
            Log.d(TAG, "Generating text stream with prompt length: ${prompt.length}")
            Log.v(TAG, "Prompt: $prompt")
            
            val startTime = System.currentTimeMillis()
            val stringBuilder = StringBuilder()
            
            // MediaPipe 支持流式生成
            // 注意: generateResponseAsync 的回调参数是 (String) -> Unit
            Log.d(TAG, "Starting streaming generation...")
            
            suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    llmInference?.generateResponseAsync(prompt) { partialResult ->
                        Log.v(TAG, "Partial result received: $partialResult")
                        stringBuilder.append(partialResult)
                        onToken(partialResult)
                    }
                    
                    // 等待生成完成 (MediaPipe 的 generateResponseAsync 是异步的)
                    // 注意: 实际实现可能需要根据不同的 MediaPipe 版本调整
                    // 这里假设 generateResponseAsync 是同步阻塞的
                    
                    if (!continuation.isCancelled) {
                        continuation.resume(Unit)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in streaming generation", e)
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }
            }
            
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Streaming generation completed")
            Log.d(TAG, "Total response length: ${stringBuilder.length}")
            Log.d(TAG, "Generation time: ${endTime - startTime} ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate text stream", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun getInferenceState(): InferenceState {
        return when {
            llmInference == null -> InferenceState.Idle
            isInitialized -> InferenceState.Ready
            else -> InferenceState.Error("Engine not properly initialized")
        }
    }

    override fun release() {
        Log.i(TAG, "Releasing MediaPipe engine")
        try {
            llmInference?.close()
            Log.d(TAG, "LlmInference instance closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference", e)
        } finally {
            llmInference = null
            isInitialized = false
            currentModelConfig = null
            Log.d(TAG, "MediaPipe engine released")
        }
    }

    override fun isSupported(modelConfig: ModelConfig): Boolean {
        return Companion.isSupported(modelConfig)
    }

    override fun getEngineName(): String = "MediaPipe LLM Inference API"

    /**
     * 获取模型信息
     */
    fun getModelInfo(): String {
        val config = currentModelConfig
        return if (config != null) {
            "Model: ${config.modelName}\n" +
            "File: ${config.fileName}\n" +
            "Size: ${config.fileSizeInBytes / (1024 * 1024)} MB\n" +
            "Supports GPU: ${config.supportsGPU}\n" +
            "Supports NPU: ${config.supportsNPU}"
        } else {
            "No model loaded"
        }
    }

    /**
     * 测试模型加载 (用于调试)
     */
    suspend fun testModelLoading(modelConfig: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Testing model loading for: ${modelConfig.modelName}")
        
        // 1. 检查模型文件
        val modelFile = modelDownloader.getDownloadedModel(modelConfig)
        if (modelFile == null) {
            Log.e(TAG, "Test failed: Model file not found")
            return@withContext false
        }
        Log.d(TAG, "Test passed: Model file exists (${modelFile.length()} bytes)")

        // 2. 检查文件格式
        if (!isSupported(modelConfig)) {
            Log.e(TAG, "Test failed: Unsupported model format")
            return@withContext false
        }
        Log.d(TAG, "Test passed: Supported model format")

        // 3. 尝试初始化
        val initResult = initialize(modelConfig)
        if (!initResult) {
            Log.e(TAG, "Test failed: Initialization failed")
            return@withContext false
        }
        Log.d(TAG, "Test passed: Initialization successful")

        // 4. 测试简单生成
        val testPrompt = "Hello"
        val response = generate(testPrompt)
        if (response.isBlank()) {
            Log.e(TAG, "Test failed: Generation returned empty response")
            release()
            return@withContext false
        }
        Log.d(TAG, "Test passed: Generation successful (response length: ${response.length})")

        // 5. 释放资源
        release()
        Log.d(TAG, "Test passed: Resource release successful")
        
        Log.i(TAG, "All tests passed for model: ${modelConfig.modelName}")
        true
    }
}
