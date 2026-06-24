// AutomationPlanner.kt
// 自动化规划器 - 使用 LLM 生成操作序列
// 根据用户目标自动规划和执行

package com.androidclaw.app.automation

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自动化规划器
 * 
 * 功能：
 * - 接收用户目标
 * - 调用 LLM 生成操作序列
 * - 逐步执行并验证
 * - 失败时重新规划
 */
class AutomationPlanner(
    private val context: Context,
    private val llmManager: LLMManager,
    private val executor: ActionExecutor,
    private val analyzer: MultimodalAnalyzer
) {
    companion object {
        private const val TAG = "AutomationPlanner"
        private const val MAX_REPLAN_COUNT = 3
    }

    // 当前计划
    private var currentPlan: AutomationPlan? = null

    // 当前步骤索引
    private var currentStepIndex = 0

    // 规划回调
    var onPlanGenerated: ((AutomationPlan) -> Unit)? = null
    var onStepExecuted: ((Int, AutomationAction, ActionResult) -> Unit)? = null
    var onPlanCompleted: ((Boolean, String) -> Unit)? = null

    /**
     * 规划并执行
     */
    suspend fun planAndExecute(goal: String): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Planning for goal: $goal")

        var attemptCount = 0
        var success = false

        while (attemptCount < MAX_REPLAN_COUNT && !success) {
            attemptCount++

            try {
                // 1. 获取当前屏幕状态
                val service = AutomationService.getInstance()
                if (service == null) {
                    Log.e(TAG, "AutomationService not available")
                    return@withContext false
                }

                val root = service.getRootNode()
                if (root == null) {
                    Log.e(TAG, "Cannot get root node")
                    return@withContext false
                }

                val uiTree = UiParser.parseNodeTree(root)
                val screenDescription = UiParser.generateTextDescription(uiTree)

                // 2. 生成计划
                val plan = generatePlan(goal, screenDescription)
                if (plan == null) {
                    Log.e(TAG, "Failed to generate plan")
                    continue
                }

                currentPlan = plan
                onPlanGenerated?.invoke(plan)

                // 3. 执行计划
                success = executePlan(plan)

            } catch (e: Exception) {
                Log.e(TAG, "Plan execution failed (attempt $attemptCount)", e)
            }
        }

        val message = if (success) "任务完成" else "任务失败，已重试 $attemptCount 次"
        onPlanCompleted?.invoke(success, message)

        success
    }

    /**
     * 生成计划
     */
    private suspend fun generatePlan(goal: String, screenDescription: String): AutomationPlan? =
        withContext(Dispatchers.IO) {
            try {
                val prompt = """
你是一个手机自动化规划助手。根据用户目标生成操作序列。

用户目标：$goal

当前屏幕状态：
$screenDescription

请生成一个操作计划，格式如下：
```json
{
  "goal": "用户目标",
  "steps": [
    {"action": "launch_app", "params": {"package": "包名"}},
    {"action": "wait", "params": {"ms": 2000}},
    {"action": "click", "params": {"text": "按钮文本"}},
    {"action": "input", "params": {"text": "输入内容", "into": "输入框描述"}},
    {"action": "swipe", "params": {"direction": "up"}}
  ],
  "success_criteria": "成功标准描述"
}
```

注意：
1. 每个步骤都是独立的操作
2. 操作之间要留有等待时间
3. 使用 text 而不是 viewId 来定位元素（更稳定）
4. 如果不确定某个步骤，可以添加验证步骤

只输出 JSON，不要其他内容。
"""

                // 调用 LLM
                val response = llmManager.generateText(prompt)

                // 解析计划
                parsePlan(response, goal)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate plan", e)
                null
            }
        }

    /**
     * 解析计划
     */
    private fun parsePlan(response: String, goal: String): AutomationPlan? {
        return try {
            // 提取 JSON
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)

            val steps = mutableListOf<AutomationAction>()
            val stepsArray = json.getJSONArray("steps")

            for (i in 0 until stepsArray.length()) {
                val stepJson = stepsArray.getJSONObject(i)
                val action = parseAction(stepJson)
                if (action != null) {
                    steps.add(action)
                }
            }

            val successCriteria = json.optString("success_criteria", "任务完成")

            AutomationPlan(
                goal = goal,
                steps = steps,
                successCriteria = successCriteria
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plan", e)
            null
        }
    }

    /**
     * 解析单个动作
     */
    private fun parseAction(json: JSONObject): AutomationAction? {
        val actionType = json.getString("action")
        val params = json.getJSONObject("params")

        return when (actionType) {
            "launch_app" -> AutomationAction.LaunchApp(
                packageName = params.getString("package")
            )

            "wait" -> AutomationAction.Wait(
                ms = params.getLong("ms")
            )

            "click" -> {
                val text = params.optString("text", null)
                val desc = params.optString("description", null)
                val x = params.optInt("x", -1)
                val y = params.optInt("y", -1)

                AutomationAction.Click(
                    text = text,
                    description = desc,
                    x = if (x >= 0) x else null,
                    y = if (y >= 0) y else null
                )
            }

            "input" -> AutomationAction.Input(
                text = params.getString("text"),
                into = params.optString("into", null)
            )

            "swipe" -> AutomationAction.Swipe(
                direction = params.optString("direction", null),
                startX = params.optInt("startX", -1).let { if (it >= 0) it else null },
                startY = params.optInt("startY", -1).let { if (it >= 0) it else null },
                endX = params.optInt("endX", -1).let { if (it >= 0) it else null },
                endY = params.optInt("endY", -1).let { if (it >= 0) it else null }
            )

            "scroll" -> AutomationAction.Scroll(
                direction = params.optString("direction", "up")
            )

            "back" -> AutomationAction.Back()

            "home" -> AutomationAction.Home()

            "wait_for" -> AutomationAction.WaitFor(
                text = params.getString("text"),
                timeout = params.optLong("timeout", 10000)
            )

            "screenshot" -> AutomationAction.Screenshot()

            "read_screen" -> AutomationAction.ReadScreen()

            else -> {
                Log.w(TAG, "Unknown action type: $actionType")
                null
            }
        }
    }

    /**
     * 执行计划
     */
    private suspend fun executePlan(plan: AutomationPlan): Boolean {
        Log.i(TAG, "Executing plan with ${plan.steps.size} steps")

        currentStepIndex = 0

        for ((index, action) in plan.steps.withIndex()) {
            currentStepIndex = index

            Log.i(TAG, "Step ${index + 1}/${plan.steps.size}: ${action.javaClass.simpleName}")

            // 执行动作
            val result = executor.execute(action)

            // 回调通知
            onStepExecuted?.invoke(index, action, result)

            // 检查结果
            when (result) {
                is ActionResult.Success -> {
                    Log.d(TAG, "Step ${index + 1} succeeded")

                    // 如果需要验证，截图并分析
                    if (action is AutomationAction.Screenshot) {
                        @Suppress("UNCHECKED_CAST")
                        val bitmap = result.data as? android.graphics.Bitmap
                        if (bitmap != null) {
                            // 可以在这里进行验证
                            Log.d(TAG, "Screenshot captured, size: ${bitmap.width}x${bitmap.height}")
                        }
                    }
                }

                is ActionResult.Error -> {
                    Log.w(TAG, "Step ${index + 1} failed: ${result.message}")

                    // 某些失败可以容忍（如等待超时）
                    if (!isTolerableFailure(action, result)) {
                        return false
                    }
                }
            }

            // 步骤之间短暂等待
            Thread.sleep(300)
        }

        // 验证是否完成任务
        return verifyCompletion(plan.successCriteria)
    }

    /**
     * 判断失败是否可容忍
     */
    private fun isTolerableFailure(action: AutomationAction, result: ActionResult.Error): Boolean {
        // 等待超时可容忍
        if (action is AutomationAction.WaitFor && result.message.contains("Timeout")) {
            return true
        }

        // 滚动失败可容忍（可能已经到底）
        if (action is AutomationAction.Scroll && result.message.contains("Scroll failed")) {
            return true
        }

        return false
    }

    /**
     * 验证任务完成
     */
    private suspend fun verifyCompletion(criteria: String): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Verifying completion: $criteria")

        val service = AutomationService.getInstance() ?: return@withContext false
        val root = service.getRootNode() ?: return@withContext false

        val uiTree = UiParser.parseNodeTree(root)
        val description = UiParser.generateTextDescription(uiTree)

        // 简单的关键词匹配
        val keywords = criteria.split(" ").filter { it.length > 2 }
        val hasMatch = keywords.any { keyword ->
            description.contains(keyword, ignoreCase = true)
        }

        if (hasMatch) {
            Log.i(TAG, "Verification passed")
        } else {
            Log.w(TAG, "Verification failed - criteria keywords not found in screen")
        }

        hasMatch
    }

    /**
     * 从响应中提取 JSON
     */
    private fun extractJson(response: String): String {
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
     * 停止当前执行
     */
    fun stop() {
        // 停止标志会由 AutomationService 处理
    }

    /**
     * 获取当前步骤索引
     */
    fun getCurrentStepIndex(): Int = currentStepIndex

    /**
     * 获取当前计划
     */
    fun getCurrentPlan(): AutomationPlan? = currentPlan
}

/**
 * 自动化计划
 */
data class AutomationPlan(
    val goal: String,
    val steps: List<AutomationAction>,
    val successCriteria: String
)
