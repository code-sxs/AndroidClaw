// TranslationSkill.kt
// 翻译 Skill - 使用本地 LLM 进行多语言互译和语言检测

package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.LLMManager

/**
 * 翻译 Skill
 * 通过本地 LLM 模型提供文本翻译和语言检测功能
 * 支持语言：中文、英文、日文、韩文、法文、德文、西班牙文
 */
class TranslationSkill : SkillDefinition {

    companion object {
        private const val TAG = "TranslationSkill"

        // 支持的语言代码及名称
        val SUPPORTED_LANGUAGES = mapOf(
            "zh" to "中文",
            "en" to "英文",
            "ja" to "日文",
            "ko" to "韩文",
            "fr" to "法文",
            "de" to "德文",
            "es" to "西班牙文"
        )
    }

    private var llmManager: LLMManager? = null
    private var isInitialized = false

    override val skillName: String = "translation"
    override val displayName: String = "翻译"
    override val description: String = "文本翻译和语言检测，支持中英日韩法德西多语言互译"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        try {
            llmManager = LLMManager.getInstance(context)
            isInitialized = true
            Log.i(TAG, "TranslationSkill initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TranslationSkill", e)
            isInitialized = false
        }
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "translate",
            displayName = "翻译文本",
            description = "将文本从源语言翻译为目标语言，支持中英日韩法德西互译",
            parameters = listOf(
                ToolParameter("text", "string", true, "要翻译的文本内容"),
                ToolParameter("source_lang", "string", false, "源语言代码 (zh/en/ja/ko/fr/de/es)，不填则自动检测"),
                ToolParameter("target_lang", "string", true, "目标语言代码 (zh/en/ja/ko/fr/de/es)")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "detect_language",
            displayName = "检测语言",
            description = "检测文本的语言类型，返回语言代码",
            parameters = listOf(
                ToolParameter("text", "string", true, "要检测的文本内容")
            ),
            returnType = "string"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        if (!isInitialized) {
            return ToolResult.Error("TranslationSkill not initialized")
        }

        return try {
            when (toolName) {
                "translate" -> translate(parameters)
                "detect_language" -> detectLanguage(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    /**
     * 翻译文本
     */
    private suspend fun translate(params: Map<String, Any>): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Error("缺少参数: text")

        val targetLang = params["target_lang"] as? String
            ?: return ToolResult.Error("缺少参数: target_lang")

        if (targetLang !in SUPPORTED_LANGUAGES) {
            return ToolResult.Error("不支持的目标语言: $targetLang，支持: ${SUPPORTED_LANGUAGES.keys}")
        }

        val sourceLang = params["source_lang"] as? String
        val sourceLangName = sourceLang?.let { SUPPORTED_LANGUAGES[it] } ?: "自动检测"

        val targetLangName = SUPPORTED_LANGUAGES[targetLang]
            ?: return ToolResult.Error("未知目标语言: $targetLang")

        val manager = llmManager
            ?: return ToolResult.Error("LLMManager not available")

        // 构建翻译 prompt
        val prompt = buildString {
            appendLine("你是一个专业翻译。请将以下文本翻译成${targetLangName}。")
            if (sourceLang != null && sourceLang in SUPPORTED_LANGUAGES) {
                appendLine("源语言: ${SUPPORTED_LANGUAGES[sourceLang]}")
            } else {
                appendLine("源语言: 自动检测")
            }
            appendLine("目标语言: ${targetLangName}")
            appendLine()
            appendLine("翻译规则:")
            appendLine("- 只输出翻译结果，不要包含任何解释、引号或额外文字")
            appendLine("- 保持原文的格式和标点")
            appendLine("- 如果是专有名词（人名、地名），直接音译")
            appendLine()
            appendLine("待翻译文本:")
            appendLine(text)
        }

        Log.d(TAG, "Translating: sourceLang=$sourceLangName, targetLang=$targetLangName, textLength=${text.length}")
        val result = manager.generateText(prompt)

        if (result.isBlank()) {
            return ToolResult.Error("翻译结果为空")
        }

        Log.d(TAG, "Translation complete: ${result.length} chars")
        return ToolResult.Success(mapOf(
            "translated_text" to result.trim(),
            "source_lang" to (sourceLang ?: "auto"),
            "target_lang" to targetLang,
            "original_text" to text
        ))
    }

    /**
     * 检测文本的语言
     */
    private suspend fun detectLanguage(params: Map<String, Any>): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Error("缺少参数: text")

        if (text.isBlank()) {
            return ToolResult.Error("文本内容为空")
        }

        val manager = llmManager
            ?: return ToolResult.Error("LLMManager not available")

        // 构建语言检测 prompt
        val prompt = buildString {
            appendLine("请检测以下文本的语言。只输出一个语言代码(${SUPPORTED_LANGUAGES.keys.joinToString("/")})，不要包含其他文字。")
            appendLine()
            appendLine("文本:")
            appendLine(text)
        }

        Log.d(TAG, "Detecting language, textLength=${text.length}")
        val result = manager.generateText(prompt)

        val detectedLang = result.trim().lowercase().take(2)

        // 验证检测结果是否在支持的语言中
        val validLang = if (detectedLang in SUPPORTED_LANGUAGES) {
            detectedLang
        } else {
            Log.w(TAG, "Detected language '$detectedLang' not in supported list, falling back to 'en'")
            "en"
        }

        Log.d(TAG, "Language detected: $validLang")
        return ToolResult.Success(mapOf(
            "language" to validLang,
            "language_name" to (SUPPORTED_LANGUAGES[validLang] ?: "Unknown"),
            "confidence" to "high"
        ))
    }

    override fun release() {
        llmManager = null
        isInitialized = false
        Log.i(TAG, "TranslationSkill released")
    }
}
