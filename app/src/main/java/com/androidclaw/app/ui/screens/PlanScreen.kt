// PlanScreen.kt
// Plan 模式界面
// 展示执行计划、控制执行、显示结果

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.planning.PlanManager
import com.androidclaw.app.planning.ExecutionPlan
import com.androidclaw.app.planning.PlanStep
import com.androidclaw.app.planning.StepStatus
import com.androidclaw.app.planning.PlanExecutionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Plan 模式界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    navController: NavController,
    planManager: PlanManager,
    userRequest: String
) {
    var plan by remember { mutableStateOf<ExecutionPlan?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var isExecuting by remember { mutableStateOf(false) }
    var executionProgress by remember { mutableStateOf(0 to 0) } // completed, total
    var executionResult by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Generate plan on launch
    LaunchedEffect(userRequest) {
        try {
            val generatedPlan = planManager.generatePlan(userRequest)
            plan = generatedPlan
        } catch (e: Exception) {
            // TODO: Show error
            e.printStackTrace()
        } finally {
            isGenerating = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Execution Plan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (plan != null && !isGenerating) {
                PlanExecutionControls(
                    isExecuting = isExecuting,
                    onStart = {
                        startPlanExecution(
                            coroutineScope = coroutineScope,
                            planManager = planManager,
                            plan = plan!!,
                            setIsExecuting = { isExecuting = it },
                            setProgress = { executionProgress = it },
                            setResult = { executionResult = it }
                        )
                    },
                    onCancel = {
                        cancelPlanExecution(
                            planManager = planManager,
                            plan = plan!!
                        )
                        isExecuting = false
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isGenerating) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Generating execution plan...")
                    }
                }
            } else if (plan == null) {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to generate plan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Plan display
                PlanDisplay(
                    plan = plan!!,
                    executionProgress = executionProgress,
                    executionResult = executionResult
                )
            }
        }
    }
}

/**
 * Plan 展示
 */
@Composable
fun PlanDisplay(
    plan: ExecutionPlan,
    executionProgress: Pair<Int, Int>,
    executionResult: String?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Goal
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Goal",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = plan.goal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Steps
        Text(
            text = "Steps (${executionProgress.first}/${executionProgress.second})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plan.steps) { step ->
                PlanStepItem(step = step)
            }
        }

        // Execution Result
        executionResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Plan 步骤项
 */
@Composable
fun PlanStepItem(step: PlanStep) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status icon
            Icon(
                imageVector = when (step.status) {
                    StepStatus.PENDING -> Icons.Default.Schedule
                    StepStatus.RUNNING -> Icons.Default.PlayArrow
                    StepStatus.COMPLETED -> Icons.Default.CheckCircle
                    StepStatus.FAILED -> Icons.Default.Error
                    StepStatus.CANCELLED -> Icons.Default.Cancel
                    StepStatus.WAITING_CONFIRMATION -> Icons.Default.HourglassEmpty
                },
                contentDescription = step.status.name,
                tint = when (step.status) {
                    StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    StepStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    StepStatus.FAILED -> MaterialTheme.colorScheme.error
                    StepStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                    StepStatus.WAITING_CONFIRMATION -> MaterialTheme.colorScheme.secondary
                }
            )

            // Step info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${step.index + 1}. ${step.description}",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (step.toolName != null) {
                    Text(
                        text = "Tool: ${step.toolName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (step.result != null) {
                    Text(
                        text = "Result: ${step.result}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (step.error != null) {
                    Text(
                        text = "Error: ${step.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Plan 执行控制
 */
@Composable
fun PlanExecutionControls(
    isExecuting: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isExecuting) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Execution")
                }
            } else {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * 开始执行计划
 */
private fun startPlanExecution(
    coroutineScope: CoroutineScope,
    planManager: PlanManager,
    plan: ExecutionPlan,
    setIsExecuting: (Boolean) -> Unit,
    setProgress: (Pair<Int, Int>) -> Unit,
    setResult: (String?) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        setIsExecuting(true)
        setResult(null)

        try {
            planManager.executePlan(plan, confirmEachStep = false)
                .collect { event ->
                    when (event) {
                        is PlanExecutionEvent.StepStarted -> {
                            // Update UI
                        }
                        is PlanExecutionEvent.StepCompleted -> {
                            setProgress(event.completedSteps to plan.steps.size)
                        }
                        is PlanExecutionEvent.StepFailed -> {
                            setProgress(event.completedSteps to plan.steps.size)
                        }
                        is PlanExecutionEvent.ProgressUpdate -> {
                            setProgress(event.completedSteps to event.totalSteps)
                        }
                        is PlanExecutionEvent.PlanCompleted -> {
                            setResult(event.message)
                            setIsExecuting(false)
                        }
                        else -> {}
                    }
                }
        } catch (e: Exception) {
            setResult("Error: ${e.message}")
            setIsExecuting(false)
        }
    }
}

/**
 * 取消执行计划
 */
private fun cancelPlanExecution(
    planManager: PlanManager,
    plan: ExecutionPlan
) {
    planManager.cancelPlan(plan.id)
}
