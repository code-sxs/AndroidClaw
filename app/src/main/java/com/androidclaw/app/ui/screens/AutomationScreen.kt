// AutomationScreen.kt
// 自动化管理界面

package com.androidclaw.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidclaw.app.automation.AutomationService
import com.androidclaw.app.automation.UiParser
import com.androidclaw.app.ui.components.StatusIndicator
import kotlinx.coroutines.launch

/**
 * 自动化管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: AutomationViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (onNavigateBack != null) {
                val onBack = onNavigateBack
                TopAppBar(
                    title = { Text("跨应用自动化") },
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.showHelp() }) {
                            Icon(Icons.Default.Help, "帮助")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (uiState.isServiceRunning && !uiState.isExecuting) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startExecution() },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text("开始执行") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务状态卡片
            item {
                ServiceStatusCard(
                    isServiceRunning = uiState.isServiceRunning,
                    isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                    onEnableAccessibility = {
                        // 打开无障碍设置
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 如果服务已运行，显示控制面板
            if (uiState.isServiceRunning) {
                // 目标应用选择
                item {
                    TargetAppCard(
                        selectedApp = uiState.targetApp,
                        apps = uiState.installedApps,
                        onSelect = { viewModel.selectTargetApp(it) }
                    )
                }

                // 执行状态
                item {
                    ExecutionStatusCard(
                        isExecuting = uiState.isExecuting,
                        currentStep = uiState.currentStep,
                        totalSteps = uiState.totalSteps,
                        currentAction = uiState.currentAction,
                        stepCount = uiState.stepCount,
                        maxSteps = 50
                    )
                }

                // 紧急停止按钮（执行时显示）
                if (uiState.isExecuting) {
                    item {
                        EmergencyStopButton(
                            onStop = { viewModel.stopExecution() }
                        )
                    }
                }

                // 快捷操作
                if (!uiState.isExecuting) {
                    item {
                        QuickActionsCard(
                            onReadScreen = { viewModel.readScreen() },
                            onScreenshot = { viewModel.takeScreenshot() },
                            onBack = { viewModel.goBack() },
                            onHome = { viewModel.goHome() }
                        )
                    }
                }

                // 操作日志
                item {
                    OperationLogCard(
                        logs = uiState.logs,
                        onClear = { viewModel.clearLogs() }
                    )
                }

                // 当前屏幕内容预览
                if (uiState.screenContent != null) {
                    item {
                        ScreenContentCard(
                            content = uiState.screenContent!!,
                            onRefresh = { viewModel.readScreen() }
                        )
                    }
                }
            }
        }
    }

    // 引导对话框
    if (uiState.showGuideDialog) {
        GuideDialog(
            onDismiss = { viewModel.dismissGuide() },
            onEnable = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )
    }

    // 错误提示
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            // 可以显示 Snackbar
            viewModel.clearError()
        }
    }
}

/**
 * 服务状态卡片
 */
@Composable
fun ServiceStatusCard(
    isServiceRunning: Boolean,
    isAccessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "服务状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusIndicator(
                    isRunning = isServiceRunning,
                    label = if (isServiceRunning) "运行中" else "未启动"
                )
            }

            if (!isAccessibilityEnabled) {
                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "需要开启无障碍服务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "用于读取其他应用的界面并执行操作",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(onClick = onEnableAccessibility) {
                        Text("去开启")
                    }
                }
            } else if (isServiceRunning) {
                HorizontalDivider()
                
                Text(
                    text = "✓ 无障碍服务已启用，可以开始自动化",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 目标应用卡片
 */
@Composable
fun TargetAppCard(
    selectedApp: String?,
    apps: List<AppInfo>,
    onSelect: (AppInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "目标应用",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 常用应用快捷按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val commonApps = listOf(
                    "淘宝" to "com.taobao.taobao",
                    "拼多多" to "com.xunmeng.pinduoduo",
                    "1688" to "com.alibaba.wireless",
                    "京东" to "com.jingdong.app.mall"
                )

                commonApps.forEach { (name, _) ->
                    FilterChip(
                        selected = selectedApp == name,
                        onClick = { /* onSelect */ },
                        label = { Text(name) }
                    )
                }
            }
        }
    }
}

/**
 * 执行状态卡片
 */
@Composable
fun ExecutionStatusCard(
    isExecuting: Boolean,
    currentStep: Int,
    totalSteps: Int,
    currentAction: String?,
    stepCount: Int,
    maxSteps: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "执行状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (isExecuting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "执行中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 进度条
            if (totalSteps > 0) {
                LinearProgressIndicator(
                    progress = { (currentStep.toFloat() / totalSteps) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "步骤 $currentStep / $totalSteps",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 当前动作
            if (currentAction != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = currentAction,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 步骤计数器（防止失控）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本会话已执行步骤",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$stepCount / $maxSteps",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (stepCount > maxSteps * 0.8) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

/**
 * 紧急停止按钮
 */
@Composable
fun EmergencyStopButton(
    onStop: () -> Unit
) {
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("紧急停止")
    }
}

/**
 * 快捷操作卡片
 */
@Composable
fun QuickActionsCard(
    onReadScreen: () -> Unit,
    onScreenshot: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "快捷操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReadScreen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("读取屏幕")
                }

                OutlinedButton(
                    onClick = onScreenshot,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("截图")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("返回")
                }

                OutlinedButton(
                    onClick = onHome,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("桌面")
                }
            }
        }
    }
}

/**
 * 操作日志卡片
 */
@Composable
fun OperationLogCard(
    logs: List<LogEntry>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "操作日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onClear) {
                    Text("清空")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logs.takeLast(10).forEach { log ->
                        Text(
                            text = "[${log.time}] ${log.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = when (log.level) {
                                LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                LogLevel.WARN -> Color(0xFFFFA000)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 屏幕内容卡片
 */
@Composable
fun ScreenContentCard(
    content: String,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "屏幕内容",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 20,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 引导对话框
 */
@Composable
fun GuideDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启无障碍服务") },
        text = {
            Text("""
                AndroidClaw 需要无障碍服务权限才能操作其他应用。
                
                请在设置中找到 AndroidClaw 并开启无障碍服务。
                
                您的隐私安全：
                • 所有操作都在本地执行
                • 不会上传您的屏幕内容
                • 可随时在通知栏停止
            """.trimIndent())
        },
        confirmButton = {
            Button(onClick = onEnable) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 应用信息
 */
data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Any? = null
)

/**
 * 日志条目
 */
data class LogEntry(
    val time: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    INFO, WARN, ERROR
}
