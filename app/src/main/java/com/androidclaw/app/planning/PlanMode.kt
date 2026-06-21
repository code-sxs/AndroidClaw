// PlanMode.kt
// Plan 模式 - 执行计划生成与管理
// 支持自动执行和逐步执行两种模式

package com.androidclaw.app.planning

import android.util.Log
import com.androidclaw.app.agent.AgentManager
import com.androidclaw.app.agent.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * 执行计划
 */
data class ExecutionPlan(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,                      // 用户目标
    val steps: List<PlanStep>,            // 执行步骤
    val estimatedTimeSeconds: Int = 0,   // 预计耗时（秒）
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 计划步骤
 */
data class PlanStep(
    val index: Int,                       // 步骤索引
    val description: String,               // 步骤描述
    val toolName: String? = null,         // 工具名称（如果需要调用工具）
    val parameters: Map<String, Any> = emptyMap(), // 工具参数
    val status: StepStatus = StepStatus.PENDING,   // 步骤状态
    val result: String? = null,           // 执行结果
    val error: String? = null             // 错误信息
)

/**
 * 步骤状态
 */
enum class StepStatus {
    PENDING,              // 等待执行
    RUNNING,              // 执行中
    COMPLETED,            // 已完成
    FAILED,               // 执行失败
    CANCELLED,            // 已取消
    WAITING_CONFIRMATION  // 等待用户确认
}

/**
 * 计划执行事件
 */
sealed class PlanExecutionEvent {
    data class StepStarted(val stepIndex: Int, val step: PlanStep) : PlanExecutionEvent()
    data class StepCompleted(val stepIndex: Int, val result: String?) : PlanExecutionEvent()
    data class StepFailed(val stepIndex: Int, val error: String) : PlanExecutionEvent()
    data class WaitingConfirmation(val stepIndex: Int, val step: PlanStep) : PlanExecutionEvent()
    data class PlanCompleted(val success: Boolean, val message: String) : PlanExecutionEvent()
    data class ProgressUpdate(val completedSteps: Int, val totalSteps: Int) : PlanExecutionEvent()
}

/**
 * Plan 模式管理器
 * 负责：生成执行计划、执行计划、暂停/继续/取消
 */
class PlanManager private constructor(private val context: android.content.Context) {

    companion object {
        private const val TAG = "PlanManager"

        private var INSTANCE: PlanManager? = null

        fun getInstance(context: android.content.Context): PlanManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlanManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val agentManager: AgentManager by lazy {
        AgentManager.getInstance(context)
    }

    private var currentPlan: ExecutionPlan? = null
    private var executionJob: kotlinx.coroutines.Job? = null

    /**
     * 生成执行计划
     * @param userRequest 用户请求
     * @return 执行计划
     */
    suspend fun generatePlan(userRequest: String): ExecutionPlan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Log.i(TAG, "Generating plan for: $userRequest")

        try {
            // 使用 AI 生成执行计划
            val prompt = buildPlanGenerationPrompt(userRequest)
            val response = agentManager.sendMessage(prompt)

            // 解析 AI 响应，提取执行步骤
            val steps = parsePlanResponse(response)

            val plan = ExecutionPlan(
                goal = userRequest,
                steps = steps,
                estimatedTimeSeconds = steps.size * 30 // 假设每步 30 秒
            )

            Log.i(TAG, "Plan generated: ${steps.size} steps")
            plan

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate plan", e)
            throw e
        }
    }

    /**
     * 执行计划
     * @param plan 执行计划
     * @param confirmEachStep 是否每步都等待用户确认
     * @return 执行事件 Flow
     */
    suspend fun executePlan(
        plan: ExecutionPlan,
        confirmEachStep: Boolean = false
    ): Flow<PlanExecutionEvent> = flow {
        Log.i(TAG, "Executing plan: ${plan.id}, steps: ${plan.steps.size}, confirmEachStep: $confirmEachStep")

        currentPlan = plan
        var success = true

        try {
            for ((index, step) in plan.steps.withIndex()) {
                // 更新步骤状态
                val currentStep = step.copy(status = StepStatus.RUNNING)
                emit(PlanExecutionEvent.StepStarted(index, currentStep))

                // 如果需要确认，等待用户确认
                if (confirmEachStep || step.toolName != null) {
                    emit(PlanExecutionEvent.WaitingConfirmation(index, currentStep))
                    // TODO: 需要实现等待用户确认的逻辑
                    // 目前先继续执行
                }

                // 执行步骤
                try {
                    val result = executeStep(currentStep)
                    
                    // 更新步骤状态为完成
                    val completedStep = currentStep.copy(
                        status = StepStatus.COMPLETED,
                        result = result
                    )
                    emit(PlanExecutionEvent.StepCompleted(index, result))
                    
                    Log.i(TAG, "Step $index completed: $result")

                } catch (e: Exception) {
                    Log.e(TAG, "Step $index failed", e)
                    
                    // 更新步骤状态为失败
                    val failedStep = currentStep.copy(
                        status = StepStatus.FAILED,
                        error = e.message
                    )
                    emit(PlanExecutionEvent.StepFailed(index, e.message ?: "Unknown error"))
                    
                    success = false
                    
                    // 询问用户是否继续
                    // TODO: 实现错误处理策略（停止/跳过/重试）
                    break
                }

                // 发送进度更新
                emit(PlanExecutionEvent.ProgressUpdate(index + 1, plan.steps.size))
            }

            // 计划执行完成
            val message = if (success) "Plan executed successfully" else "Plan execution failed"
            emit(PlanExecutionEvent.PlanCompleted(success, message))
            Log.i(TAG, "Plan execution completed: $success")

        } catch (e: Exception) {
            Log.e(TAG, "Plan execution error", e)
            emit(PlanExecutionEvent.PlanCompleted(false, "Error: ${e.message}"))
        }
    }

    /**
     * 取消计划执行
     */
    fun cancelPlan(planId: String) {
        Log.i(TAG, "Cancelling plan: $planId")

        // TODO: 取消正在执行的步骤
        executionJob?.cancel()

        currentPlan = null
    }

    /**
     * 执行单个步骤
     */
    private suspend fun executeStep(step: PlanStep): String {
        Log.i(TAG, "Executing step ${step.index}: ${step.description}")

        return if (step.toolName != null) {
            // 调用工具
            val result = agentManager.callTool(step.toolName, step.parameters)
            
            when (result) {
                is ToolResult.Success -> {
                    result.data?.toString() ?: "Step completed successfully"
                }
                is ToolResult.Error -> {
                    throw Exception("Tool execution failed: ${result.message}")
                }
                is ToolResult.Cancelled -> {
                    throw Exception("Tool execution cancelled")
                }
            }
        } else {
            // 不调用工具，直接返回描述
            "Step completed: ${step.description}"
        }
    }

    /**
     * 构建计划生成提示词
     */
    private fun buildPlanGenerationPrompt(userRequest: String): String {
        return """
            You are a task planning assistant for AndroidClaw.
            User request: $userRequest
            
            Generate a step-by-step execution plan.
            Each step should be clear and actionable.
            
            Output format (JSON):
            {
                "steps": [
                    {
                        "description": "Step description",
                        "toolName": "tool_name_if_needed",
                        "parameters": {"param1": "value1"}
                    }
                ]
            }
            
            Only output the JSON, no extra text.
        """.trimIndent()
    }

    /**
     * 解析计划响应
     */
    private fun parsePlanResponse(response: String): List<PlanStep> {
        return try {
            // TODO: 使用 JSON 解析响应
            // 目前返回模拟数据
            listOf(
                PlanStep(
                    index = 0,
                    description = "Step 1: Analyze request",
                    toolName = null,
                    parameters = emptyMap()
                ),
                PlanStep(
                    index = 1,
                    description = "Step 2: Execute action",
                    toolName = "sample_tool",
                    parameters = mapOf("param" to "value")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plan response", e)
            emptyList()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing PlanManager")
        cancelPlan(currentPlan?.id ?: "")
        currentPlan = null
    }
}
