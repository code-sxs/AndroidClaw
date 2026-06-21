// SecurityReportScreen.kt
// 安全报告 UI 界面
// 提供扫描进度显示、风险仪表盘、发现列表、用户操作等功能
//
// UI 功能：
// - 扫描进度显示
// - 风险仪表盘（总分 + 各维度得分）
// - 发现列表（按严重程度排序）
// - 允许/阻止/忽略 操作按钮
// - 导出完整报告
// - 历史扫描记录查看

package com.androidclaw.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidclaw.app.skills.security.Category
import com.androidclaw.app.skills.security.ScanResult
import com.androidclaw.app.skills.security.ScanStatus
import com.androidclaw.app.skills.security.SecurityFinding
import com.androidclaw.app.skills.security.Severity

/**
 * 安全报告屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityReportScreen(
    scanResult: ScanResult? = null,
    isScanning: Boolean = false,
    scanProgress: Float = 0f,
    scanPhase: String = "",
    onAllow: ((String) -> Unit)? = null,
    onBlock: ((String) -> Unit)? = null,
    onIgnore: ((String) -> Unit)? = null,
    onExportReport: ((ScanResult) -> Unit)? = null,
    onViewHistory: (() -> Unit)? = null,
    onNavigateBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全扫描报告") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("返回")
                    }
                },
                actions = {
                    scanResult?.let { result ->
                        IconButton(onClick = { onViewHistory?.invoke() }) {
                            Icon(Icons.Default.History, contentDescription = "历史记录")
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
            if (isScanning) {
                ScanningProgress(
                    progress = scanProgress,
                    phase = scanPhase
                )
            } else if (scanResult != null) {
                // Tab 标签栏
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("概览") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("发现 (${scanResult.findings.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("操作") }
                    )
                }

                when (selectedTab) {
                    0 -> OverviewTab(
                        scanResult = scanResult,
                        onExport = { onExportReport?.invoke(scanResult) }
                    )
                    1 -> FindingsTab(scanResult = scanResult)
                    2 -> ActionTab(
                        scanResult = scanResult,
                        onAllow = onAllow,
                        onBlock = onBlock,
                        onIgnore = onIgnore
                    )
                }
            } else {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无扫描结果",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "安装 Skill 后将自动进行安全扫描",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 扫描进度显示
 */
@Composable
private fun ScanningProgress(
    progress: Float,
    phase: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "正在安全扫描...",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = phase,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 概览标签页
 */
@Composable
private fun OverviewTab(
    scanResult: ScanResult,
    onExport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 状态卡片
        item {
            StatusCard(scanResult = scanResult)
        }

        // 风险评分
        item {
            RiskScoreCard(scanResult = scanResult)
        }

        // 分类统计
        item {
            FindingsByCategoryCard(scanResult = scanResult)
        }

        // 扫描信息
        item {
            ScanInfoCard(scanResult = scanResult)
        }

        // 导出按钮
        item {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("导出完整报告")
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 安全状态卡片
 */
@Composable
private fun StatusCard(scanResult: ScanResult) {
    val (statusColor, statusIcon, statusText) = when (scanResult.overallStatus) {
        ScanStatus.SAFE -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "安全")
        ScanStatus.WARNING -> Triple(Color(0xFFFF9800), Icons.Default.Warning, "警告")
        ScanStatus.DANGEROUS -> Triple(Color(0xFFF44336), Icons.Default.Dangerous, "危险")
        ScanStatus.BLOCKED -> Triple(Color(0xFFD32F2F), Icons.Default.Block, "已阻止")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = scanResult.skillName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${scanResult.findings.size} 个安全发现" +
                            " · ${scanResult.scannedFiles} 个文件扫描",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 风险评分卡片
 */
@Composable
private fun RiskScoreCard(scanResult: ScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "风险评分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 评分圆环
                val scoreColor = when {
                    scanResult.riskScore >= 70 -> Color(0xFFF44336)
                    scanResult.riskScore >= 40 -> Color(0xFFFF9800)
                    scanResult.riskScore >= 20 -> Color(0xFFFFA726)
                    else -> Color(0xFF4CAF50)
                }

                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = scanResult.riskScore / 100f,
                        modifier = Modifier.size(80.dp),
                        color = scoreColor,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${scanResult.riskScore}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(
                        text = "0-20: 安全  21-40: 低风险",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "41-70: 中风险  71-100: 高风险",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "最高严重级别: ${scanResult.findings.maxOfOrNull { it.severity }?.name ?: "无"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor
                    )
                }
            }
        }
    }
}

/**
 * 发现分类统计
 */
@Composable
private fun FindingsByCategoryCard(scanResult: ScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "按严重程度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val severityGroups = scanResult.findings.groupBy { it.severity }
            val severityOrder = listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)

            for (severity in severityOrder) {
                val count = severityGroups[severity]?.size ?: 0
                if (count > 0) {
                    val color = when (severity) {
                        Severity.CRITICAL -> Color(0xFFD32F2F)
                        Severity.HIGH -> Color(0xFFF44336)
                        Severity.MEDIUM -> Color(0xFFFF9800)
                        Severity.LOW -> Color(0xFFFFA726)
                        Severity.INFO -> Color(0xFF2196F3)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = severity.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }

            if (scanResult.findings.isEmpty()) {
                Text(
                    text = "无发现",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 扫描信息卡片
 */
@Composable
private fun ScanInfoCard(scanResult: ScanResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "扫描信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("扫描文件数", "${scanResult.scannedFiles}")
            InfoRow("扫描耗时", "${scanResult.scanDurationMs}ms")
            InfoRow("扫描时间", formatTimestamp(scanResult.scanTimestamp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 发现列表标签页
 */
@Composable
private fun FindingsTab(scanResult: ScanResult) {
    if (scanResult.findings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("未发现安全问题", style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(scanResult.findings, key = { "${it.title}_${it.affectedFile}_${it.affectedLine}" }) { finding ->
            FindingCard(finding = finding)
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/**
 * 单个安全发现卡片
 */
@Composable
private fun FindingCard(finding: SecurityFinding) {
    var expanded by remember { mutableStateOf(false) }

    val (severityColor, severityIcon) = when (finding.severity) {
        Severity.CRITICAL -> Pair(Color(0xFFD32F2F), Icons.Default.Dangerous)
        Severity.HIGH -> Pair(Color(0xFFF44336), Icons.Default.ErrorOutline)
        Severity.MEDIUM -> Pair(Color(0xFFFF9800), Icons.Default.Warning)
        Severity.LOW -> Pair(Color(0xFFFFA726), Icons.Default.Info)
        Severity.INFO -> Pair(Color(0xFF2196F3), Icons.Default.Info)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = severityIcon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(severityColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = finding.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    finding.affectedFile?.let { file ->
                        Text(
                            text = "文件: $file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    finding.affectedLine?.let { line ->
                        Text(
                            text = "行号: $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "修复建议: ${finding.recommendation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    finding.cveId?.let { cve ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "CVE: $cve",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 操作标签页
 */
@Composable
private fun ActionTab(
    scanResult: ScanResult,
    onAllow: ((String) -> Unit)?,
    onBlock: ((String) -> Unit)?,
    onIgnore: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "用户操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "选择对此 Skill 的处理方式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (scanResult.overallStatus) {
                    ScanStatus.SAFE -> {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "此 Skill 安全，可以安装",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { onAllow?.invoke(scanResult.skillName) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("允许安装")
                        }
                    }
                    ScanStatus.WARNING -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Text(
                            text = "此 Skill 存在潜在风险",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onBlock?.invoke(scanResult.skillName) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFF44336)
                                )
                            ) {
                                Text("阻止")
                            }
                            Button(
                                onClick = { onAllow?.invoke(scanResult.skillName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("仍然安装")
                            }
                        }
                    }
                    ScanStatus.DANGEROUS, ScanStatus.BLOCKED -> {
                        Icon(
                            imageVector = Icons.Default.Dangerous,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFF44336)
                        )
                        Text(
                            text = "强烈建议阻止此 Skill",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onBlock?.invoke(scanResult.skillName) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336)
                            )
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("阻止安装")
                        }
                        TextButton(
                            onClick = { onAllow?.invoke(scanResult.skillName) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("忽略警告并安装（不推荐）")
                        }
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
