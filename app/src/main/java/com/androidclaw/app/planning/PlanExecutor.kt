// PlanExecutor.kt
// 计划执行器
// 负责逐步执行 ExecutionPlan，支持逐步确认和错误处理

package com.androidclaw.app.planning

import android.content.Context
import android.util.Log
import com.androidclaw.app.agent.ToolRegistry
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 用户确认结果
 * 用于 PlanExecutor 等待 UI 层确认
 */
sealed class UserConfirmationResult {
    data object Approved : UserConfirmationResult()  // 用户确认执行
    data object Skip : UserConfirmationResult()        // 用户跳过此步
    data object Cancel : UserConfirmationResult()     // 用户取消整个计划
}

/**
 * 计划执行器
 * 逐步执行 ExecutionPlan，发送事件到 UI 层
 */
class PlanExecutor(private val context: Context) {

    companion object {
        private const val TAG = "PlanExecutor"
    }

    private val toolRegistry: ToolRegistry by lazy {
        ToolRegistry.getInstance(context)
    }

    // 外部设置的确认回调（由 UI 层提供）
    private var confirmationCallback: (suspend (PlanStep) -> UserConfirmationResult)? = null

    // 当前取消标志
    @Volatile
    private var isCancelled = false

    // 当前执行任务
    private var currentJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // 公开 API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 设置用户确认回调
     * @param callback 回调函数，接收 PlanStep，返回用户确认结果
     */
    fun setConfirmationCallback(callback: suspend (PlanStep) -> UserConfirmationResult) {
        this.confirmationCallback = callback
    }

    /**
     * 执行计划
     * @param plan 执行计划
     * @param onStepUpdate 步骤状态更新回调（用于非 Flow 模式）
     * @return PlanExecutionEvent Flow
     */
    suspend fun executePlan(
        plan: ExecutionPlan,
        onStepUpdate: ((PlanStep) -> Unit)? = null
    ): Flow<PlanExecutionEvent> = flow {
        Log.i(TAG, "Executing plan: ${plan.id}, steps=${plan.steps.size}")
        isCancelled = false

        var success = true
        val mutablePlan = plan.copy(
            steps = plan.steps.toMutableList()
        )

        for ((index, step) in mutablePlan.steps.withIndex()) {
            // 检查是否已取消
            if (isCancelled) {
                Log.i(TAG, "Plan cancelled at step $index")
                emit(PlanExecutionEvent.PlanCompleted(
                    success = false,
                    message = "Plan cancelled by user"
                ))
                return@flow
            }

            // 更新步骤状态为运行中
            val runningStep = step.copy(status = StepStatus.RUNNING)
            mutablePlan.steps[index] = runningStep
            onStepUpdate?.invoke(runningStep)
            emit(PlanExecutionEvent.StepStarted(index, runningStep))

            // 需要用户确认
            if (step.toolName != null && confirmationCallback != null) {
                val waitingStep = runningStep.copy(status = StepStatus.WAITING_CONFIRMATION)
                mutablePlan.steps[index] = waitingStep
                emit(PlanExecutionEvent.WaitingConfirmation(index, waitingStep))

                val confirmation = try {
                    confirmationCallback!!(waitingStep)
                } catch (e: Exception) {
                    Log.e(TAG, "Confirmation callback failed", e)
                    UserConfirmationResult.Cancel
                }

                when (confirmation) {
                    is UserConfirmationResult.Cancel -> {
                        Log.i(TAG, "User cancelled plan at step $index")
                        val cancelledStep = runningStep.copy(status = StepStatus.CANCELLED)
                        mutablePlan.steps[index] = cancelledStep
                        emit(PlanExecutionEvent.StepFailed(index, "Cancelled by user"))
                        emit(PlanExecutionEvent.PlanCompleted(false, "Cancelled at step ${index + 1}"))
                        return@flow
                    }
                    is UserConfirmationResult.Skip -> {
                        Log.i(TAG, "User skipped step $index")
                        val skippedStep = runningStep.copy(
                            status = StepStatus.COMPLETED,
                            result = "Skipped by user"
                        )
                        mutablePlan.steps[index] = skippedStep
                        emit(PlanExecutionEvent.StepCompleted(index, "Skipped"))
                        emit(PlanExecutionEvent.ProgressUpdate(index + 1, plan.steps.size))
                        continue
                    }
                    is UserConfirmationResult.Approved -> {
                        // 继续执行
                        val approvedStep = runningStep.copy(status = StepStatus.RUNNING)
                        mutablePlan.steps[index] = approvedStep
                    }
                }
            }

            // 执行步骤
            try {
                val result = executeStep(runningStep)
                val completedStep = runningStep.copy(
                    status = StepStatus.COMPLETED,
                    result = result
                )
                mutablePlan.steps[index] = completedStep
                onStepUpdate?.invoke(completedStep)
                emit(PlanExecutionEvent.StepCompleted(index, result))

                Log.i(TAG, "Step $index completed: ${result.take(100)}")

            } catch (e: Exception) {
                Log.e(TAG, "Step $index failed", e)
                success = false

                val failedStep = runningStep.copy(
                    status = StepStatus.FAILED,
                    error = e.message ?: "Unknown error"
                )
                mutablePlan.steps[index] = failedStep
                onStepUpdate?.invoke(failedStep)
                emit(PlanExecutionEvent.StepFailed(index, e.message ?: "Unknown error"))

                // 根据错误决定是否继续
                val shouldContinue = handleStepError(index, e, plan)
                if (!shouldContinue) {
                    emit(PlanExecutionEvent.PlanCompleted(false, "Failed at step ${index + 1}"))
                    return@flow
                }
            }

            // 发送进度更新
            emit(PlanExecutionEvent.ProgressUpdate(index + 1, plan.steps.size))
        }

        // 计划执行完成
        val message = if (success) {
            "Plan executed successfully (${plan.steps.size} steps)"
        } else {
            "Plan completed with errors"
        }
        emit(PlanExecutionEvent.PlanCompleted(success, message))
        Log.i(TAG, "Plan execution completed: success=$success")
    }.flowOn(Dispatchers.IO)

    /**
     * 取消计划执行
     */
    fun cancel() {
        Log.i(TAG, "Cancelling plan execution")
        isCancelled = true
        currentJob?.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 执行单个步骤
     */
    private suspend fun executeStep(step: PlanStep): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "executeStep ${step.index}: ${step.description}")

        return@withContext if (step.toolName != null) {
            // 通过工具注册表执行工具
            val result = toolRegistry.executeTool(step.toolName, step.parameters)
            parseToolResult(result)
        } else {
            // 无工具步骤，直接返回描述
            "Completed: ${step.description}"
        }
    }

    /**
     * 解析工具执行结果
     */
    private fun parseToolResult(result: ToolResult): String {
        return when (result) {
            is ToolResult.Success -> {
                result.data?.toString() ?: "Tool executed successfully"
            }
            is ToolResult.Error -> {
                throw Exception("Tool execution failed: ${result.message}")
            }
            is ToolResult.Cancelled -> {
                throw Exception("Tool execution was cancelled")
            }
        }
    }

    /**
     * 处理步骤执行错误
     * 当前策略：失败后停止执行
     * TODO: 可扩展为重试、跳过等策略
     */
    private suspend fun handleStepError(
        stepIndex: Int,
        error: Exception,
        plan: ExecutionPlan
    ): Boolean {
        Log.w(TAG, "Step $stepIndex failed: ${error.message}")
        // 当前：遇到任何错误就停止
        return false
    }
}
