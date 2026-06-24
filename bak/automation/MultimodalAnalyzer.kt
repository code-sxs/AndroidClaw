// MultimodalAnalyzer.kt
// 多模态屏幕分析 - 使用多模态 LLM 分析截图
// 结合 MiniCPM-V 或其他视觉语言模型

package com.androidclaw.app.automation

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 多模态分析器
 * 
 * 功能：
 * - 分析截图理解当前页面
 * - 识别可操作元素
 * - 提供下一步操作建议
 * - 判断任务完成状态
 */
class MultimodalAnalyzer(
    private val context: Context,
    private val llmManager: LLMManager
) {
    companion object {
        private const val TAG = "MultimodalAnalyzer"
        
        // 提示词模板
        private const val TEMPLATE_GENERAL = """
你是一个手机自动化助手。分析这张截图，回答以下问题：

1. 当前在哪个 App 的哪个页面？（如果不确定，请说明）
2. 页面上有哪些可操作的元素？（列出按钮、输入框、链接等）
3. 用户的目标是"{goal}"，根据当前页面状态，下一步应该做什么？
4. 请输出一个 JSON 格式的操作建议。

JSON 格式示例：
```json
{
  "app": "淘宝",
  "page": "首页",
  "elements": [
    {"type": "input", "description": "搜索框", "action": "点击后输入文字"},
    {"type": "button", "description": "我的淘宝", "action": "点击进入个人中心"}
  ],
  "next_action": {
    "type": "click",
    "target": "搜索框",
    "reason": "需要先点击搜索框才能输入搜索内容"
  },
  "task_status": "进行中"
}
```

请严格遵循 JSON 格式输出。
"""

        private const val TEMPLATE_SHOPPING = """
你是一个购物助手。分析这张电商 App 截图：

用户目标：{goal}

请识别：
1. 当前页面类型（首页、搜索结果、商品详情、购物车等）
2. 商品信息（名称、价格、销量、店铺）
3. 可操作元素（搜索框、筛选、排序、商品卡片、加购按钮等）
4. 下一步操作建议

输出 JSON 格式：
```json
{
  "page_type": "搜索结果页",
  "products": [
    {"name": "...", "price": "...", "sales": "..."}
  ],
  "actions": ["搜索框", "筛选", "排序", "商品1", "..."],
  "next_action": {
    "type": "click",
    "target": "商品1",
    "reason": "..."
  }
}
```
"""

        private const val TEMPLATE_FORM_FILLING = """
你是一个表单填写助手。分析这张截图：

用户目标：{goal}

请识别：
1. 表单字段及其类型（文本输入、下拉选择、复选框等）
2. 当前已填写的内容
3. 还需要填写的字段
4. 下一步应该填写哪个字段，填写什么内容

输出 JSON 格式：
```json
{
  "form_fields": [
    {"name": "姓名", "type": "text", "filled": true, "value": "张三"},
    {"name": "手机号", "type": "text", "filled": false}
  ],
  "next_action": {
    "type": "input",
    "target": "手机号输入框",
    "value": "13800138000"
  }
}
```
"""
    }

    /**
     * 分析截图
     * 
     * @param bitmap 截图
     * @param goal 用户目标
     * @param scenario 场景类型
     * @return 分析结果
     */
    suspend fun analyze(
        bitmap: Bitmap,
        goal: String,
        scenario: AnalysisScenario = AnalysisScenario.GENERAL
    ): AnalysisResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Analyzing screenshot, goal: $goal, scenario: $scenario")

        try {
            // 1. 构建提示词
            val prompt = buildPrompt(goal, scenario)

            // 2. 将 Bitmap 转为 Base64
            val imageBase64 = bitmapToBase64(bitmap)

            // 3. 调用多模态 LLM
            val response = callMultimodalLLM(prompt, imageBase64)

            // 4. 解析响应
            parseResponse(response, goal)

        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            AnalysisResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 分析当前屏幕（结合截图和 UI 树）
     */
    suspend fun analyzeWithUiTree(
        bitmap: Bitmap,
        uiTree: UiTree,
        goal: String
    ): AnalysisResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Analyzing with UI tree, nodes: ${uiTree.nodes.size}")

        try {
            // 获取 UI 树的文本描述
            val uiDescription = UiParser.generateTextDescription(uiTree)

            // 构建增强提示词
            val prompt = """
你是一个手机自动化助手。我提供了一张截图和界面元素的文本描述。

界面元素描述：
$uiDescription

用户目标：$goal

请综合截图和文本信息，分析：
1. 当前页面状态
2. 可操作的元素
3. 下一步应该做什么

输出 JSON 格式的操作建议。
"""

            val imageBase64 = bitmapToBase64(bitmap)
            val response = callMultimodalLLM(prompt, imageBase64)

            parseResponse(response, goal)

        } catch (e: Exception) {
            Log.e(TAG, "Analysis with UI tree failed", e)
            AnalysisResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 构建提示词
     */
    private fun buildPrompt(goal: String, scenario: AnalysisScenario): String {
        val template = when (scenario) {
            AnalysisScenario.SHOPPING -> TEMPLATE_SHOPPING
            AnalysisScenario.FORM_FILLING -> TEMPLATE_FORM_FILLING
            else -> TEMPLATE_GENERAL
        }

        return template.replace("{goal}", goal)
    }

    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    /**
     * 调用多模态 LLM
     */
    private suspend fun callMultimodalLLM(prompt: String, imageBase64: String): String {
        // TODO: 与 LLMManager 集成，调用多模态模型
        // 这里假设 LLMManager 已有相关接口

        // 模拟响应（实际实现时替换）
        return """
{
  "app": "淘宝",
  "page": "首页",
  "elements": [
    {"type": "input", "description": "搜索框"},
    {"type": "button", "description": "我的淘宝"}
  ],
  "next_action": {
    "type": "click",
    "target": "搜索框",
    "reason": "需要先点击搜索框"
  },
  "task_status": "进行中"
}
"""
    }

    /**
     * 解析 LLM 响应
     */
    private fun parseResponse(response: String, goal: String): AnalysisResult {
        return try {
            // 提取 JSON（可能被 markdown 包裹）
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)

            val app = json.optString("app", "未知")
            val page = json.optString("page", "未知")
            val taskStatus = json.optString("task_status", "进行中")

            val elements = mutableListOf<DetectedElement>()
            val elementsArray = json.optJSONArray("elements")
            if (elementsArray != null) {
                for (i in 0 until elementsArray.length()) {
                    val elemJson = elementsArray.getJSONObject(i)
                    elements.add(DetectedElement(
                        type = elemJson.optString("type"),
                        description = elemJson.optString("description"),
                        action = elemJson.optString("action", "")
                    ))
                }
            }

            var nextAction: ActionSuggestion? = null
            val actionJson = json.optJSONObject("next_action")
            if (actionJson != null) {
                nextAction = ActionSuggestion(
                    type = actionJson.optString("type"),
                    target = actionJson.optString("target"),
                    value = actionJson.optString("value", ""),
                    reason = actionJson.optString("reason", "")
                )
            }

            AnalysisResult.Success(
                appName = app,
                pageName = page,
                elements = elements,
                nextAction = nextAction,
                taskStatus = taskStatus,
                goal = goal
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            AnalysisResult.Error("Failed to parse LLM response: ${e.message}")
        }
    }

    /**
     * 从响应中提取 JSON
     */
    private fun extractJson(response: String): String {
        // 去除可能的 markdown 代码块标记
        var jsonStr = response.trim()
        
        if (jsonStr.contains("```json")) {
            jsonStr = jsonStr.substringAfter("```json")
                .substringBefore("```")
                .trim()
        } else if (jsonStr.contains("```")) {
            jsonStr = jsonStr.substringAfter("```")
                .substringBefore("```")
                .trim()
        }

        return jsonStr
    }

    /**
     * 降级分析（当多模态不可用时）
     */
    suspend fun fallbackAnalyze(uiTree: UiTree, goal: String): AnalysisResult =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Using fallback analysis (UI tree only)")

            val description = UiParser.generateTextDescription(uiTree)
            val summary = UiParser.getActionableSummary(uiTree)

            // 简单的规则匹配
            val nextAction = determineNextAction(uiTree, goal)

            AnalysisResult.Success(
                appName = uiTree.packageName,
                pageName = uiTree.activityName,
                elements = emptyList(),
                nextAction = nextAction,
                taskStatus = "需要用户确认",
                goal = goal,
                additionalInfo = "仅基于 UI 树分析，建议：\n$summary"
            )
        }

    /**
     * 简单的下一步动作推断（规则匹配）
     */
    private fun determineNextAction(uiTree: UiTree, goal: String): ActionSuggestion? {
        // 规则1: 如果有输入框且目标包含搜索内容
        val inputNodes = uiTree.nodes.filter { it.isEditable || it.isFocusable }
        if (inputNodes.isNotEmpty()) {
            val searchKeywords = extractSearchKeywords(goal)
            if (searchKeywords.isNotEmpty()) {
                return ActionSuggestion(
                    type = "input",
                    target = "搜索框",
                    value = searchKeywords,
                    reason = "检测到搜索意图"
                )
            }
        }

        // 规则2: 如果有包含目标文本的可点击元素
        val keyword = goal.split(" ").firstOrNull() ?: ""
        val matchingButton = uiTree.nodes.firstOrNull {
            it.isClickable && it.text.contains(keyword, ignoreCase = true)
        }
        if (matchingButton != null) {
            return ActionSuggestion(
                type = "click",
                target = matchingButton.text,
                value = "",
                reason = "找到匹配的按钮"
            )
        }

        return null
    }

    /**
     * 提取搜索关键词
     */
    private fun extractSearchKeywords(goal: String): String {
        // 简单的关键词提取
        val patterns = listOf(
            "搜索\\s*(.+)",
            "查找\\s*(.+)",
            "找\\s*(.+)",
            "在.*找\\s*(.+)",
            "(.+)\\s*最便"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(goal)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }

        return ""
    }
}

/**
 * 分析场景
 */
enum class AnalysisScenario {
    GENERAL,
    SHOPPING,
    FORM_FILLING,
    CUSTOM
}

/**
 * 分析结果
 */
sealed class AnalysisResult {
    data class Success(
        val appName: String,
        val pageName: String,
        val elements: List<DetectedElement>,
        val nextAction: ActionSuggestion?,
        val taskStatus: String,
        val goal: String,
        val additionalInfo: String? = null
    ) : AnalysisResult()

    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : AnalysisResult()
}

/**
 * 检测到的元素
 */
data class DetectedElement(
    val type: String,
    val description: String,
    val action: String = ""
)

/**
 * 动作建议
 */
data class ActionSuggestion(
    val type: String,
    val target: String,
    val value: String = "",
    val reason: String = ""
)
