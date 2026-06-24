// BaseEngine.kt
// 推理引擎抽象基类
// 定义三个引擎 (MediaPipe / MLC-LLM / LiteRT) 的共同接口

package com.androidclaw.app.llm.engine

import com.androidclaw.app.llm.model.InferenceState
import com.androidclaw.app.llm.model.ModelConfig

/**
 * 推理引擎抽象基类
 * 所有推理引擎 (MediaPipe / MLC-LLM / LiteRT) 都必须实现此接口
 */
abstract class BaseEngine(protected val context: android.content.Context) {

    protected var isInitialized = false
    protected var currentModelConfig: ModelConfig? = null

    /**
     * 初始化引擎并加载模型
     * @param modelConfig 模型配置
     * @return 是否初始化成功
     */
    abstract suspend fun initialize(modelConfig: ModelConfig): Boolean

    /**
     * 生成文本 (单次推理)
     * @param prompt 输入提示
     * @return 生成的文本
     */
    abstract suspend fun generate(prompt: String): String

    /**
     * 流式生成文本 (逐 token 输出)
     * @param prompt 输入提示
     * @param onToken 每个 token 的回调
     */
    abstract suspend fun generateStream(prompt: String, onToken: (String) -> Unit)

    /**
     * 获取推理状态
     */
    abstract fun getInferenceState(): InferenceState

    /**
     * 释放资源
     */
    abstract fun release()

    /**
     * 检查是否支持指定模型
     */
    abstract fun isSupported(modelConfig: ModelConfig): Boolean

    /**
     * 获取引擎名称
     */
    abstract fun getEngineName(): String

    /**
     * 获取当前加载的模型信息
     */
    fun getCurrentModel(): ModelConfig? = currentModelConfig

    /**
     * 检查引擎是否已初始化
     */
    fun isReady(): Boolean = isInitialized && getInferenceState() == InferenceState.Ready
}
