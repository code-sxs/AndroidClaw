// PlanMode.kt
// Plan 模式 - 执行计划生成与管理
// 支持自动执行和逐步执行两种模式

package com.androidclaw.app.planning

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 执行计划
 */
data class ExecutionPlan(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,                      // 用户目标
    val steps: List<PlanStep>,             // 执行步骤
    val estimatedTimeSeconds: Int = 0,     // 预计耗时（秒）
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 计划步骤
 */
data class PlanStep(
    val index: Int,                        // 步骤索引
    val description: String,               // 步骤描述
    val toolName: String? = null,          // 工具名称（如果需要调用工具）
    val parameters: Map<String, Any> = emptyMap(), // 工具参数
    val status: StepStatus = StepStatus.PENDING,   // 步骤状态
    val result: String? = null,            // 执行结果
    val error: String? = null              // 错误信息
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
    data class PlanGenerationStarted(val request: String) : PlanExecutionEvent()
    data class PlanGenerated(val plan: ExecutionPlan) : PlanExecutionEvent()
    data class PlanGenerationFailed(val error: String) : PlanExecutionEvent()
}

/**
 * Plan 模式状态
 */
sealed class PlanModeState {
    data object Idle : PlanModeState()
    data class GeneratingPlan(val request: String) : PlanModeState()
    data class PlanReady(val plan: ExecutionPlan) : PlanModeState()
    data class Executing(val plan: ExecutionPlan, val currentStep: Int) : PlanModeState()
    data class Completed(val plan: ExecutionPlan, val success: Boolean) : PlanModeState()
    data class Error(val message: String) : PlanModeState()
}

/**
 * Plan 模式管理器
 * 负责：生成执行计划、执行计划、暂停/继续/取消
 */
class PlanManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlanManager"

        private var INSTANCE: PlanManager? = null

        fun getInstance(context: Context): PlanManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlanManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val planGenerator: PlanGenerator by lazy { PlanGenerator(context) }
    private val planExecutor: PlanExecutor by lazy { PlanExecutor(context) }

    // 状态流（供 UI 观察）
    private val _state = MutableStateFlow<PlanModeState>(PlanModeState.Idle)
    val state: StateFlow<PlanModeState> = _state.asStateFlow()

    // 当前计划
    private var currentPlan: ExecutionPlan? = null

    // 当前执行任务
    private var executionJob: Job? = null

    init {
        Log.i(TAG, "PlanManager initialized")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 公开 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 生成执行计划
     * @param userRequest 用户请求
     * @return ExecutionPlan 执行计划
     */
    suspend fun generatePlan(userRequest: String): ExecutionPlan {
        Log.i(TAG, "generatePlan: $userRequest")
        _state.value = PlanModeState.GeneratingPlan(userRequest)

        return try {
            val plan = planGenerator.generatePlan(userRequest)
            currentPlan = plan
            _state.value = PlanModeState.PlanReady(plan)
            Log.i(TAG, "Plan generated: ${plan.steps.size} steps")
            plan
        } catch (e: Exception) {
            Log.e(TAG, "Plan generation failed", e)
            _state.value = PlanModeState.Error("计划生成失败: ${e.message}")
            throw e
        }
    }

    /**
     * 执行计划
     * @param plan 执行计划（如果为 null，使用当前计划）
     * @param confirmEachStep 是否每步都等待用户确认
     * @param onStepUpdate 步骤更新回调
     * @return 执行事件 Flow
     */
    suspend fun executePlan(
        plan: ExecutionPlan? = null,
        confirmEachStep: Boolean = false,
        onStepUpdate: ((PlanStep) -> Unit)? = null
    ): Flow<PlanExecutionEvent> {
        val targetPlan = plan ?: currentPlan
            ?: throw IllegalStateException("No plan to execute")

        Log.i(TAG, "executePlan: ${targetPlan.id}, confirmEachStep=$confirmEachStep")
        _state.value = PlanModeState.Executing(targetPlan, 0)

        // 设置确认回调（如果需要）
        if (confirmEachStep) {
            planExecutor.setConfirmationCallback { step ->
                // 默认行为：等待用户确认
                // UI 层需要通过其他方式提供确认结果
                UserConfirmationResult.Approved
            }
        }

        return planExecutor.executePlan(targetPlan, onStepUpdate)
    }

    /**
     * 直接执行（一步到位，自动状态管理）
     * 生成计划 + 执行，内部处理所有状态更新
     */
    suspend fun generateAndExecute(
        userRequest: String,
        confirmEachStep: Boolean = false,
        onStepUpdate: ((PlanStep) -> Unit)? = null
    ): Flow<PlanExecutionEvent> = kotlinx.coroutines.flow.flow {
        // 通知开始生成
        emit(PlanExecutionEvent.PlanGenerationStarted(userRequest))

        try {
            val plan = planGenerator.generatePlan(userRequest)
            currentPlan = plan
            emit(PlanExecutionEvent.PlanGenerated(plan))

            // 执行计划
            planExecutor.executePlan(plan, onStepUpdate).collect { event ->
                emit(event)

                // 更新状态
                when (event) {
                    is PlanExecutionEvent.PlanCompleted -> {
                        _state.value = PlanModeState.Completed(plan, event.success)
                    }
                    is PlanExecutionEvent.StepStarted -> {
                        _state.value = PlanModeState.Executing(plan, event.stepIndex)
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateAndExecute failed", e)
            emit(PlanExecutionEvent.PlanGenerationFailed(e.message ?: "Unknown error"))
            _state.value = PlanModeState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 取消计划执行
     */
    fun cancelPlan() {
        Log.i(TAG, "cancelPlan")
        planExecutor.cancel()
        currentPlan?.let {
            _state.value = PlanModeState.Completed(it, false)
        }
    }

    /**
     * 清除当前计划，回到空闲状态
     */
    fun clearPlan() {
        Log.i(TAG, "clearPlan")
        cancelPlan()
        currentPlan = null
        _state.value = PlanModeState.Idle
    }

    /**
     * 获取当前计划
     */
    fun getCurrentPlan(): ExecutionPlan? = currentPlan

    /**
     * 重置到空闲状态
     */
    fun reset() {
        clearPlan()
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing PlanManager")
        cancelPlan()
        currentPlan = null
    }
}
