// Model.kt
// 数据模型定义

package com.androidclaw.app.llm.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 推理引擎类型
 */
enum class InferenceEngine(val displayName: String) {
    NONE("未初始化"),
    MEDIAPIPE("MediaPipe"),
    MLC_LLM("MLC-LLM"),
    LITERT("LiteRT")
}

/**
 * 硬件能力
 */
@Parcelize
data class HardwareCapability(
    val hasGPU: Boolean,           // 是否有 GPU (支持 OpenCL / Vulkan)
    val hasNPU: Boolean,           // 是否有 NPU (神经网络加速芯片)
    val gpuRenderer: String,        // GPU 渲染器名称
    val totalRAM: Long,            // 总 RAM (字节)
    val availableRAM: Long,        // 可用 RAM (字节)
    val androidVersion: Int,       // Android 版本 (API level)
    val abi: String                // CPU 架构 (arm64-v8a / armeabi-v7a / x86_64)
) : Parcelable {

    /**
     * 是否可以运行指定大小的模型
     */
    fun canRunModel(modelSizeInBytes: Long): Boolean {
        // 简单 heuristic: 可用 RAM 需要至少是模型大小的 1.5 倍
        val requiredRAM = (modelSizeInBytes * 1.5).toLong()
        return availableRAM >= requiredRAM
    }

    /**
     * 推荐的模型大小
     */
    fun getRecommendedModelSize(): ModelSize {
        return when {
            totalRAM >= 8L * 1024 * 1024 * 1024 -> ModelSize.LARGE   // 8GB+ RAM -> 4B 模型
            totalRAM >= 6L * 1024 * 1024 * 1024 -> ModelSize.MEDIUM // 6GB+ RAM -> 2B 模型
            else -> ModelSize.SMALL  // <6GB RAM -> 轻量模型
        }
    }
}

/**
 * 模型大小分类
 */
enum class ModelSize {
    SMALL,   // 轻量模型 (<2B)
    MEDIUM,  // 中等模型 (2B-4B)
    LARGE    // 大型模型 (>4B)
}

/**
 * 模型配置
 */
@Parcelize
data class ModelConfig(
    val modelId: String,          // 模型 ID (唯一标识)
    val modelName: String,        // 模型名称 (显示用)
    val modelType: ModelType,     // 模型类型 (LLM / VLM)
    val modelSize: ModelSize,     // 模型大小
    val parameterCount: String,   // 参数量 (如 "2B", "4B")
    val downloadUrl: String,      // 下载 URL
    val fileName: String,         // 文件名
    val fileSizeInBytes: Long,    // 文件大小 (字节)
    val md5Checksum: String,      // MD5 校验和
    val preferredEngine: InferenceEngine, // 推荐引擎
    val minRAMRequired: Long,    // 最小 RAM 要求 (字节)
    val supportsGPU: Boolean,     // 是否支持 GPU 加速
    val supportsNPU: Boolean     // 是否支持 NPU 加速
) : Parcelable

/**
 * 模型类型
 */
enum class ModelType {
    LLM,       // 大语言模型 (纯文本)
    VLM,       // 视觉语言模型 (图片 + 文本)
    EMBEDDING, // 嵌入模型
    VISION,    // 视觉模型
    SPEECH,    // 语音模型
    OTHER      // 其他模型
}

/**
 * 下载状态
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Paused(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

/**
 * 推理状态
 */
sealed class InferenceState {
    object Idle : InferenceState()
    object Loading : InferenceState()  // 加载模型
    object Ready : InferenceState()    // 就绪 (可以推理)
    data class Generating(val partialResponse: String) : InferenceState()  // 正在生成
    data class Error(val message: String) : InferenceState()
}
