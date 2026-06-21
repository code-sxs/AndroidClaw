// SkillCreatorScreen.kt
// Skill 创建器 - UI 界面
// 提供 Skill 生成与共享的完整用户界面

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidclaw.app.skills.creator.*
import com.androidclaw.app.skills.security.Severity

/**
 * Skill 创建器界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillCreatorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSecurityReport: (String) -> Unit = {},
    viewModel: SkillCreatorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val progressMessage by viewModel.progressMessage.collectAsState()
    val parsedRequirement by viewModel.parsedRequirement.collectAsState()
    val generatedCode by viewModel.generatedCode.collectAsState()
    val scanResult by viewModel.scanResult.collectAsState()
    val desensitizeReport by viewModel.desensitizeReport.collectAsState()
    val shareResults by viewModel.shareResults.collectAsState()
    
    // Tab 状态
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("生成 Skill", "分享管理")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill 创建器") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState is SkillCreatorUiState.Generated) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重新开始")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 栏
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // 进度指示器
            if (uiState is SkillCreatorUiState.Parsing ||
                uiState is SkillCreatorUiState.Generating ||
                uiState is SkillCreatorUiState.Optimizing ||
                uiState is SkillCreatorUiState.Sharing
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = progressMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 内容区域
            when (selectedTab) {
                0 -> GenerateTab(
                    uiState = uiState,
                    parsedRequirement = parsedRequirement,
                    generatedCode = generatedCode,
                    scanResult = scanResult,
                    progressMessage = progressMessage,
                    onParseRequirement = { viewModel.parseRequirement(it) },
                    onGenerate = { viewModel.generateSkill() },
                    onOptimize = { viewModel.optimizeCode(it) },
                    onSave = { viewModel.saveSkill() },
                    onNavigateToSecurityReport = onNavigateToSecurityReport,
                    onPrepareShare = { 
                        // 切换到分享 Tab
                        selectedTab = 1 
                    }
                )
                1 -> ShareTab(
                    uiState = uiState,
                    parsedRequirement = parsedRequirement,
                    desensitizeReport = desensitizeReport,
                    shareResults = shareResults,
                    onScan = { viewModel.scanSensitiveInfo(it) },
                    onShare = { skillDir, author, tags, category, markets, skip ->
                        viewModel.shareSkill(skillDir, author, tags, category, markets, skip)
                    },
                    onWithdraw = { viewModel.withdrawShare(it) }
                )
            }
        }
    }
}

/**
 * 生成 Tab
 */
@Composable
private fun GenerateTab(
    uiState: SkillCreatorUiState,
    parsedRequirement: ParsedRequirement?,
    generatedCode: String?,
    scanResult: com.androidclaw.app.skills.security.ScanResult?,
    progressMessage: String,
    onParseRequirement: (String) -> Unit,
    onGenerate: () -> Unit,
    onOptimize: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateToSecurityReport: (String) -> Unit,
    onPrepareShare: () -> Unit
) {
    var requirementText by remember { mutableStateOf("") }
    var optimizeFeedback by remember { mutableStateOf("") }
    var showCodePreview by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 需求输入区
        item {
            RequirementInputCard(
                value = requirementText,
                onValueChange = { requirementText = it },
                onParse = { onParseRequirement(requirementText) },
                isLoading = uiState is SkillCreatorUiState.Parsing
            )
        }
        
        // 解析结果
        if (parsedRequirement != null) {
            item {
                ParsedRequirementCard(
                    requirement = parsedRequirement,
                    onGenerate = onGenerate,
                    isLoading = uiState is SkillCreatorUiState.Generating
                )
            }
        }
        
        // 生成结果
        if (uiState is SkillCreatorUiState.Generated && generatedCode != null) {
            item {
                GeneratedResultCard(
                    requirement = parsedRequirement!!,
                    code = generatedCode,
                    scanResult = scanResult,
                    showCodePreview = showCodePreview,
                    onToggleCodePreview = { showCodePreview = !showCodePreview },
                    onNavigateToSecurityReport = onNavigateToSecurityReport,
                    onSave = onSave,
                    onPrepareShare = onPrepareShare
                )
            }
            
            // 优化反馈
            item {
                OptimizeCard(
                    feedback = optimizeFeedback,
                    onFeedbackChange = { optimizeFeedback = it },
                    onOptimize = { 
                        if (optimizeFeedback.isNotBlank()) {
                            onOptimize(optimizeFeedback)
                            optimizeFeedback = ""
                        }
                    },
                    isLoading = uiState is SkillCreatorUiState.Optimizing
                )
            }
        }
        
        // 错误状态
        if (uiState is SkillCreatorUiState.Error) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分享 Tab
 */
@Composable
private fun ShareTab(
    uiState: SkillCreatorUiState,
    parsedRequirement: ParsedRequirement?,
    desensitizeReport: Desensitizer.DesensitizeReport?,
    shareResults: List<SkillSharer.ShareResult>,
    onScan: (java.io.File) -> Unit,
    onShare: (java.io.File, String, List<String>, String, List<String>, Boolean) -> Unit,
    onWithdraw: (SkillSharer.SharedSkill) -> Unit
) {
    var skillDir by remember { mutableStateOf<java.io.File?>(null) }
    var author by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var selectedMarkets by remember { mutableStateOf(setOf("clawhub")) }
    var skipDesensitize by remember { mutableStateOf(false) }
    
    val categories = listOf(
        "general" to "通用",
        "productivity" to "效率工具",
        "entertainment" to "娱乐",
        "education" to "教育",
        "social" to "社交",
        "tools" to "系统工具",
        "network" to "网络"
    )
    
    val availableMarkets = listOf(
        "clawhub" to "Clawhub",
        "skillhub" to "SkillHub",
        "skillsmp" to "Skillsmp"
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 分享表单
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "分享设置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("标签 (逗号分隔)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // 分类选择
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { (id, name) ->
                            FilterChip(
                                selected = category == id,
                                onClick = { category = id },
                                label = { Text(name) }
                            )
                        }
                    }
                    
                    // 市场选择
                    Text(
                        text = "目标市场",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    availableMarkets.forEach { (id, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = id in selectedMarkets,
                                onCheckedChange = { 
                                    selectedMarkets = if (it) {
                                        selectedMarkets + id
                                    } else {
                                        selectedMarkets - id
                                    }
                                }
                            )
                            Text(name)
                        }
                    }
                    
                    // 跳过脱敏
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = skipDesensitize,
                            onCheckedChange = { skipDesensitize = it }
                        )
                        Text("跳过脱敏处理 (不推荐)")
                    }
                }
            }
        }
        
        // 脱敏报告
        if (desensitizeReport != null) {
            item {
                DesensitizeReportCard(report = desensitizeReport)
            }
        }
        
        // 分享结果
        if (shareResults.isNotEmpty()) {
            item {
                ShareResultsCard(results = shareResults)
            }
        }
        
        // 分享按钮
        item {
            Button(
                onClick = {
                    // 这里需要实际的 skillDir
                    // 实际应用中应该从文件选择器获取
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = author.isNotBlank() && selectedMarkets.isNotEmpty() &&
                          uiState !is SkillCreatorUiState.Sharing
            ) {
                if (uiState is SkillCreatorUiState.Sharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("分享到市场")
            }
        }
    }
}

/**
 * 需求输入卡片
 */
@Composable
private fun RequirementInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onParse: () -> Unit,
    isLoading: Boolean
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "描述你的需求",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("例如：我想要一个 Skill，能帮我查快递") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 5
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onParse,
                    enabled = value.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("智能拆分")
                }
            }
        }
    }
}

/**
 * 解析结果卡片
 */
@Composable
private fun ParsedRequirementCard(
    requirement: ParsedRequirement,
    onGenerate: () -> Unit,
    isLoading: Boolean
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "解析结果",
                style = MaterialTheme.typography.titleMedium
            )
            
            LabeledText("Skill 名称", requirement.skillName)
            LabeledText("显示名称", requirement.displayName)
            LabeledText("描述", requirement.description)
            
            if (requirement.requiredPermissions.isNotEmpty()) {
                Text(
                    text = "所需权限:",
                    style = MaterialTheme.typography.bodyMedium
                )
                requirement.requiredPermissions.forEach { perm ->
                    Text(
                        text = "• $perm",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Text(
                text = "工具 (${requirement.tools.size} 个):",
                style = MaterialTheme.typography.bodyMedium
            )
            requirement.tools.forEach { tool ->
                Text(
                    text = "• ${tool.name}: ${tool.description}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            if (requirement.riskAssessment.isNotEmpty()) {
                Text(
                    text = "风险评估:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (requirement.riskAssessment.contains("⚠️")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = requirement.riskAssessment,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("生成 Skill")
            }
        }
    }
}

/**
 * 生成结果卡片
 */
@Composable
private fun GeneratedResultCard(
    requirement: ParsedRequirement,
    code: String,
    scanResult: com.androidclaw.app.skills.security.ScanResult?,
    showCodePreview: Boolean,
    onToggleCodePreview: () -> Unit,
    onNavigateToSecurityReport: (String) -> Unit,
    onSave: () -> Unit,
    onPrepareShare: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "生成成功 ✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // 安全扫描结果
            if (scanResult != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (scanResult.overallStatus) {
                            com.androidclaw.app.skills.security.ScanStatus.SAFE -> Icons.Default.CheckCircle
                            com.androidclaw.app.skills.security.ScanStatus.WARNING -> Icons.Default.Warning
                            com.androidclaw.app.skills.security.ScanStatus.DANGEROUS -> Icons.Default.Error
                            com.androidclaw.app.skills.security.ScanStatus.BLOCKED -> Icons.Default.Block
                        },
                        contentDescription = null,
                        tint = when (scanResult.overallStatus) {
                            com.androidclaw.app.skills.security.ScanStatus.SAFE -> Color(0xFF4CAF50)
                            com.androidclaw.app.skills.security.ScanStatus.WARNING -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "安全扫描: ${scanResult.overallStatus.name} (风险分: ${scanResult.riskScore})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (scanResult.findings.isNotEmpty()) {
                    TextButton(onClick = { onNavigateToSecurityReport(requirement.skillName) }) {
                        Text("查看详细报告")
                    }
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onToggleCodePreview) {
                    Icon(Icons.Default.Code, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showCodePreview) "隐藏代码" else "查看代码")
                }
                
                Button(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存")
                }
                
                Button(onClick = onPrepareShare) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("分享")
                }
            }
            
            // 代码预览
            if (showCodePreview) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = code,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFD4D4D4)
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 优化卡片
 */
@Composable
private fun OptimizeCard(
    feedback: String,
    onFeedbackChange: (String) -> Unit,
    onOptimize: () -> Unit,
    isLoading: Boolean
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "迭代优化",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedTextField(
                value = feedback,
                onValueChange = onFeedbackChange,
                label = { Text("描述需要的修改") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：添加错误重试机制") }
            )
            
            Button(
                onClick = onOptimize,
                enabled = feedback.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("优化代码")
            }
        }
    }
}

/**
 * 脱敏报告卡片
 */
@Composable
private fun DesensitizeReportCard(report: Desensitizer.DesensitizeReport) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (report.hasHighRisk) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "脱敏报告",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (report.findings.isNotEmpty()) {
                report.findings.take(5).forEach { finding ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (finding.severity) {
                                Desensitizer.Severity.CRITICAL -> Icons.Default.Error
                                Desensitizer.Severity.HIGH -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (finding.severity) {
                                Desensitizer.Severity.CRITICAL -> Color.Red
                                Desensitizer.Severity.HIGH -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${finding.category}: ${finding.description}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (report.findings.size > 5) {
                    Text(
                        text = "... 还有 ${report.findings.size - 5} 处",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 分享结果卡片
 */
@Composable
private fun ShareResultsCard(results: List<SkillSharer.ShareResult>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "分享结果",
                style = MaterialTheme.typography.titleMedium
            )
            
            results.forEach { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.success) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = result.marketId ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.success && result.shareUrl != null) {
                            Text(
                                text = result.shareUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (result.error != null) {
                            Text(
                                text = result.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 标签文本
 */
@Composable
private fun LabeledText(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
