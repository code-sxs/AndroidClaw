// PlanGenerator.kt
// AI 生成执行计划的核心逻辑
// 使用 LLMManager 调用本地 LLM 生成结构化执行计划

package com.androidclaw.app.planning

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 计划生成的 JSON 响应结构
 */
data class PlanJsonResponse(
    val goal: String?,
    val steps: List<PlanStepJson>?,
    val estimated_time_seconds: Int?
)

data class PlanStepJson(
    val description: String,
    @SerializedName("tool_name") val toolName: String?,
    val parameters: Map<String, Any>?
)

/**
 * 计划生成器
 * 负责使用 LLM 生成结构化的执行计划
 */
class PlanGenerator(private val context: Context) {

    companion object {
        private const val TAG = "PlanGenerator"
        private const val MAX_ITERATIONS = 2 // 最多重新生成次数
        private val gson = Gson()
    }

    private val llmManager: LLMManager by lazy {
        LLMManager.getInstance(context)
    }

    // 工具注册表引用（用于验证工具是否存在）
    private val toolRegistry: com.androidclaw.app.agent.ToolRegistry by lazy {
        com.androidclaw.app.agent.ToolRegistry.getInstance(context)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 公开 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 生成执行计划
     * @param userRequest 用户请求
     * @return ExecutionPlan 执行计划
     * @throws Exception 生成失败时抛出
     */
    suspend fun generatePlan(userRequest: String): ExecutionPlan = withContext(Dispatchers.IO) {
        Log.i(TAG, "Generating plan for: $userRequest")

        var lastError: Exception? = null

        for (iteration in 0 until MAX_ITERATIONS) {
            try {
                val response = callLlMForPlan(userRequest, iteration > 0)
                val plan = parseAndValidatePlan(userRequest, response)
                Log.i(TAG, "Plan generated successfully with ${plan.steps.size} steps")
                return@withContext plan
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Plan generation attempt ${iteration + 1} failed: ${e.message}")
            }
        }

        throw lastError ?: Exception("Failed to generate plan after $MAX_ITERATIONS attempts")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 调用 LLM 生成计划
     */
    private suspend fun callLlMForPlan(userRequest: String, isRetry: Boolean): String {
        val retryHint = if (isRetry) {
            "\n\n[注意] 上一轮计划生成有问题，请重新生成更合理的计划。"
        } else ""

        val prompt = buildPlanPrompt(userRequest) + retryHint

        Log.d(TAG, "Calling LLM for plan (attempt=${if (isRetry) 2 else 1})")
        return llmManager.generateText(prompt)
    }

    /**
     * 构建计划生成的提示词
     */
    private fun buildPlanPrompt(userRequest: String): String {
        val availableTools = toolRegistry.getAllSkills()
            .flatMap { it.getTools() }
            .joinToString("\n") { tool ->
                "  - ${tool.toolName}: ${tool.description} (参数: ${tool.parameters.joinToString { it.name }})"
            }

        return """
            |You are a task planning expert for AndroidClaw, a local AI assistant on Android.
            |
            |USER REQUEST: "$userRequest"
            |
            |AVAILABLE TOOLS:
            |$availableTools
            |
            |Please generate a detailed execution plan strictly in the following JSON format (output ONLY the JSON, no other text):
            |
            |```json
            |{
            |  "goal": "A concise description of the user's goal",
            |  "steps": [
            |    {
            |      "description": "Clear description of what this step does",
            |      "tool_name": "exact_tool_name or null if no tool needed",
            |      "parameters": {"paramName": "paramValue"}  // empty {} if no tool
            |    }
            |  ],
            |  "estimated_time_seconds": 30
            |}
            |```
            |
            |RULES:
            |1. Steps must be concrete and actionable
            |2. Use exact tool_name from the AVAILABLE TOOLS list above (case-sensitive)
            |3. If no tool is needed for a step, use null for tool_name
            |4. Estimate total time realistically (in seconds)
            |5. Plan should have 2-10 steps (reasonable for one task)
            |6. Output ONLY the JSON object, no markdown, no explanation, no preamble
        """.trimMargin()
    }

    /**
     * 解析 LLM 输出并验证
     */
    private suspend fun parseAndValidatePlan(userRequest: String, llmOutput: String): ExecutionPlan {
        Log.d(TAG, "Parsing LLM output (${llmOutput.length} chars)")

        // 提取 JSON（可能有 markdown 代码块包裹）
        val jsonString = extractJson(llmOutput)
            ?: throw Exception("Could not extract JSON from LLM output")

        // 解析 JSON
        val jsonResponse = try {
            gson.fromJson(jsonString, PlanJsonResponse::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse error: ${e.message}")
            throw Exception("Invalid JSON format in LLM response")
        }

        // 验证必需字段
        if (jsonResponse.steps.isNullOrEmpty()) {
            throw Exception("LLM returned empty steps")
        }

        val goal = jsonResponse.goal ?: userRequest
        val estimatedTime = jsonResponse.estimated_time_seconds ?: (jsonResponse.steps.size * 30)

        // 转换并验证步骤
        val planSteps = jsonResponse.steps.mapIndexed { index, stepJson ->
            // 验证工具是否存在
            val validatedToolName = stepJson.toolName?.let { toolName ->
                if (isValidTool(toolName)) {
                    toolName
                } else {
                    Log.w(TAG, "Tool '$toolName' not found in registry, setting to null")
                    null
                }
            }

            PlanStep(
                index = index,
                description = stepJson.description,
                toolName = validatedToolName,
                parameters = stepJson.parameters ?: emptyMap(),
                status = StepStatus.PENDING,
                result = null,
                error = null
            )
        }

        return ExecutionPlan(
            goal = goal,
            steps = planSteps,
            estimatedTimeSeconds = estimatedTime
        )
    }

    /**
     * 从 LLM 输出中提取 JSON（处理 markdown 代码块）
     */
    private fun extractJson(text: String): String? {
        var candidate = text.trim()

        // 去掉 markdown 代码块包裹
        if (candidate.startsWith("```json")) {
            candidate = candidate.removePrefix("```json").trim()
        } else if (candidate.startsWith("```")) {
            candidate = candidate.removePrefix("```").trim()
        }
        if (candidate.endsWith("```")) {
            candidate = candidate.removeSuffix("```").trim()
        }

        // 尝试作为 JSON 解析
        return try {
            gson.fromJson(candidate, PlanJsonResponse::class.java)
            candidate
        } catch (_: Exception) {
            // 尝试找 JSON 对象边界
            val firstBrace = candidate.indexOf('{')
            val lastBrace = candidate.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                val extracted = candidate.substring(firstBrace, lastBrace + 1)
                try {
                    gson.fromJson(extracted, PlanJsonResponse::class.java)
                    extracted
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }

    /**
     * 验证工具是否在注册表中
     */
    private fun isValidTool(toolName: String): Boolean {
        return toolRegistry.getAllSkills()
            .flatMap { it.getTools() }
            .any { it.toolName.equals(toolName, ignoreCase = true) }
    }
}
