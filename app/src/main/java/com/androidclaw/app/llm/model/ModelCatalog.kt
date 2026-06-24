// ModelCatalog.kt
// 妯″瀷鐩綍 - 鎻愪緵鍙敤妯″瀷鍒楄〃

package com.androidclaw.app.llm.model

/**
 * 妯″瀷鐩綍
 * 鎻愪緵鎵€鏈夊彲鐢ㄧ殑 AI 妯″瀷
 */
object ModelCatalog {
    
    /**
     * 鎵€鏈夊彲鐢ㄦā鍨?     */
    val models: List<ModelConfig> = listOf(
        // 灏忓瀷妯″瀷 - 閫傚悎浣庣璁惧
        ModelConfig(
            modelId = "qwen2-0.5b",
            modelName = "Qwen2 0.5B",
            modelType = ModelType.LLM,
            modelSize = ModelSize.SMALL,
            parameterCount = "0.5B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2-0.5b-instruct-q4_k_m.gguf",
            fileSizeInBytes = 395_000_000L,
            md5Checksum = "",
            preferredEngine = InferenceEngine.MLC_LLM,
            minRAMRequired = 1_000_000_000L,
            supportsGPU = true,
            supportsNPU = false
        ),
        
        // 涓瀷妯″瀷 - 骞宠　鎬ц兘
        ModelConfig(
            modelId = "qwen2-1.5b",
            modelName = "Qwen2 1.5B",
            modelType = ModelType.LLM,
            modelSize = ModelSize.MEDIUM,
            parameterCount = "1.5B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-1.5B-Instruct-GGUF/resolve/main/qwen2-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2-1.5b-instruct-q4_k_m.gguf",
            fileSizeInBytes = 970_000_000L,
            md5Checksum = "",
            preferredEngine = InferenceEngine.MLC_LLM,
            minRAMRequired = 2_000_000_000L,
            supportsGPU = true,
            supportsNPU = false
        ),
        
        // 澶у瀷妯″瀷 - 楂樻€ц兘
        ModelConfig(
            modelId = "qwen2-7b",
            modelName = "Qwen2 7B",
            modelType = ModelType.LLM,
            modelSize = ModelSize.LARGE,
            parameterCount = "7B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-7B-Instruct-GGUF/resolve/main/qwen2-7b-instruct-q4_k_m.gguf",
            fileName = "qwen2-7b-instruct-q4_k_m.gguf",
            fileSizeInBytes = 4_400_000_000L,
            md5Checksum = "",
            preferredEngine = InferenceEngine.MLC_LLM,
            minRAMRequired = 6_000_000_000L,
            supportsGPU = true,
            supportsNPU = false
        ),
        
        // 瑙嗚璇█妯″瀷
        ModelConfig(
            modelId = "qwen2-vl-2b",
            modelName = "Qwen2-VL 2B",
            modelType = ModelType.VLM,
            modelSize = ModelSize.MEDIUM,
            parameterCount = "2B",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/qwen2-vl-2b-instruct-q4_k_m.gguf",
            fileName = "qwen2-vl-2b-instruct-q4_k_m.gguf",
            fileSizeInBytes = 1_500_000_000L,
            md5Checksum = "",
            preferredEngine = InferenceEngine.MLC_LLM,
            minRAMRequired = 3_000_000_000L,
            supportsGPU = true,
            supportsNPU = false
        )
    )
    
    /**
     * 鏍规嵁 ID 鑾峰彇妯″瀷
     */
    fun getModelById(modelId: String): ModelConfig? {
        return models.find { it.modelId == modelId }
    }
    
    /**
     * 鎸夌被鍨嬭繃婊ゆā鍨?     */
    fun getModelsByType(type: ModelType): List<ModelConfig> {
        return models.filter { it.modelType == type }
    }
    
    /**
     * 鎸夊ぇ灏忚繃婊ゆā鍨?     */
    fun getModelsBySize(size: ModelSize): List<ModelConfig> {
        return models.filter { it.modelSize == size }
    }
}
