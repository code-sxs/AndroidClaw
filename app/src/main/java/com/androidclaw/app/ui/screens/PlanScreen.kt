// PlanScreen.kt
// Plan 模式界面 - 现代化 UI 设计

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.planning.PlanManager
import com.androidclaw.app.planning.ExecutionPlan
import com.androidclaw.app.planning.PlanStep
import com.androidclaw.app.planning.StepStatus
import com.androidclaw.app.planning.PlanExecutionEvent
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * Plan 模式界面 - 现代化设计
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
    var executionProgress by remember { mutableStateOf(0 to 0) }
    var executionResult by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 生成计划
    LaunchedEffect(userRequest) {
        try {
            val generatedPlan = planManager.generatePlan(userRequest)
            plan = generatedPlan
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isGenerating = false
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "执行计划",
                subtitle = "查看并执行 AI 生成的任务计划",
                showBackButton = true,
                onBackClick = { navController.popBackStack() }
            )
        },
        bottomBar = {
            if (plan != null && !isGenerating) {
                ModernPlanExecutionBar(
                    isExecuting = isExecuting,
                    progress = if (plan != null && executionProgress.second > 0) {
                        executionProgress.first.toFloat() / executionProgress.second.toFloat()
                    } else 0f,
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
                        cancelPlanExecution(planManager, plan!!)
                        isExecuting = false
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isGenerating -> {
                    // 生成中状态
                    PlanGeneratingState()
                }
                plan == null -> {
                    // 错误状态
                    PlanErrorState()
                }
                else -> {
                    // 计划展示
                    ModernPlanDisplay(
                        plan = plan!!,
                        executionProgress = executionProgress,
                        executionResult = executionResult
                    )
                }
            }
        }
    }
}

/**
 * 计划生成中状态
 */
@Composable
private fun PlanGeneratingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // AI 思考动画
        Box(
            contentAlignment = Alignment.Center
        ) {
            // 外圈旋转
            RotatingLoader(
                size = 80.dp,
                strokeWidth = 6.dp,
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary
                )
            )
            
            // 内圈脉冲
            PulseLoader(
                size = 40.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "AI 正在生成执行计划...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请稍候",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 计划错误状态
 */
@Composable
private fun PlanErrorState() {
    EmptyState(
        icon = {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        title = "生成计划失败",
        description = "无法生成执行计划，请稍后重试",
        action = {
            GradientButton(
                onClick = { /* 重试 */ },
                cornerRadius = 20.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "重试",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

/**
 * 现代计划展示
 */
@Composable
private fun ModernPlanDisplay(
    plan: ExecutionPlan,
    executionProgress: Pair<Int, Int>,
    executionResult: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 目标卡片
        item {
            GradientGlassCard(
                modifier = Modifier.fillMaxWidth(),
                gradientColors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                ),
                cornerRadius = 20.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "目标",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Text(
                        text = plan.goal,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 进度信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "步骤进度",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        
                        Text(
                            text = "${executionProgress.first}/${executionProgress.second}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        // 步骤列表
        item {
            Text(
                text = "执行步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        itemsIndexed(plan.steps) { index, step ->
            ModernPlanStepItem(
                step = step,
                index = index,
                totalSteps = plan.steps.size,
                isCurrentlyExecuting = executionProgress.first == index && executionProgress.second == plan.steps.size
            )
        }
        
        // 执行结果
        executionResult?.let { result ->
            item {
                ModernExecutionResult(
                    result = result,
                    isSuccess = result.contains("success", ignoreCase = true) ||
                              result.contains("完成", ignoreCase = true)
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/**
 * 现代计划步骤项
 */
@Composable
private fun ModernPlanStepItem(
    step: PlanStep,
    index: Int,
    totalSteps: Int,
    isCurrentlyExecuting: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step_animation")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "step_pulse"
    )
    
    val stepColor = when (step.status) {
        StepStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
        StepStatus.RUNNING -> MaterialTheme.colorScheme.primary
        StepStatus.COMPLETED -> Color(0xFF34C759)
        StepStatus.FAILED -> MaterialTheme.colorScheme.error
        StepStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        StepStatus.WAITING_CONFIRMATION -> MaterialTheme.colorScheme.secondary
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 连接线和序号
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 序号圆圈
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .then(
                        if (isCurrentlyExecuting) Modifier.scale(pulseScale) else Modifier
                    )
                    .clip(CircleShape)
                    .background(
                        if (step.status == StepStatus.COMPLETED) {
                            Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF30D158)))
                        } else {
                            Brush.linearGradient(listOf(stepColor, stepColor))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (step.status) {
                    StepStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    StepStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            
            // 连接线
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(
                            if (step.status == StepStatus.COMPLETED) {
                                Color(0xFF34C759)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 步骤内容卡片
        GlassCard(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (isCurrentlyExecuting) {
                        Modifier
                    } else Modifier
                ),
            cornerRadius = 16.dp,
            backgroundColor = if (isCurrentlyExecuting) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentlyExecuting) FontWeight.Medium else FontWeight.Normal
                )
                
                // 工具信息
                step.toolName?.let { toolName ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "工具: $toolName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 结果
                step.result?.let { result ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34C759)
                        )
                    }
                }
                
                // 错误信息
                step.error?.let { error ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 现代执行结果展示
 */
@Composable
private fun ModernExecutionResult(
    result: String,
    isSuccess: Boolean
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = if (isSuccess) {
            Color(0xFF34C759).copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.error
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = if (isSuccess) "执行成功" else "执行失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.error
                )
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 现代计划执行底部栏
 */
@Composable
private fun ModernPlanExecutionBar(
    isExecuting: Boolean,
    progress: Float,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    GlassBottomBar {
        if (isExecuting) {
            // 进度显示和取消按钮
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在执行...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedProgressBar(
                    progress = progress,
                    height = 6.dp,
                    gradientColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            GradientButton(
                onClick = onCancel,
                gradientColors = listOf(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                ),
                cornerRadius = 20.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "取消",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // 开始执行按钮
            GradientButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始执行",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
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
                        is PlanExecutionEvent.StepStarted -> { }
                        is PlanExecutionEvent.StepCompleted -> {
                            setProgress((event.stepIndex + 1) to plan.steps.size)
                        }
                        is PlanExecutionEvent.StepFailed -> {
                            setProgress((event.stepIndex + 1) to plan.steps.size)
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
    planManager.cancelPlan()
}
