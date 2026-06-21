// SettingsScreen.kt
// 设置界面 - 现代化 UI 设计

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.ai.AiProviderManager
import com.androidclaw.app.mcp.McpSkillManager
import com.androidclaw.app.remote.RemoteInferenceManager
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设置界面 - 现代化设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    aiProviderManager: AiProviderManager,
    mcpSkillManager: McpSkillManager,
    remoteInferenceManager: RemoteInferenceManager
) {
    var planModeEnabled by remember { mutableStateOf(false) }
    var remoteInferenceEnabled by remember { mutableStateOf(false) }
    var remoteServerAddress by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf(AppTheme.LIQUID_GLASS) }
    var isDarkMode by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "设置",
                subtitle = "自定义您的应用体验",
                showBackButton = true,
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 用户卡片
            item {
                ModernUserCard()
            }
            
            // 外观设置
            item {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            item {
                ModernSettingsCard {
                    // 主题选择
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "主题",
                        subtitle = getThemeName(selectedTheme),
                        onClick = { showThemeDialog = true }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 深色模式
                    SettingsItemWithSwitch(
                        icon = Icons.Default.DarkMode,
                        title = "深色模式",
                        subtitle = "跟随系统或手动开启",
                        checked = isDarkMode,
                        onCheckedChange = { isDarkMode = it }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 动态颜色
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Gradient,
                        title = "动态颜色",
                        subtitle = "跟随壁纸变色 (Android 12+)",
                        checked = true,
                        onCheckedChange = { }
                    )
                }
            }
            
            // AI 设置
            item {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            item {
                ModernSettingsCard {
                    // AI 提供商
                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = "AI 提供商",
                        subtitle = "配置 AI 模型来源",
                        onClick = { navController.navigate("ai_provider_settings") },
                        showArrow = true
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 模型管理
                    SettingsItem(
                        icon = Icons.Default.Memory,
                        title = "本地模型",
                        subtitle = "下载和管理本地 AI 模型",
                        onClick = { navController.navigate("model_management") },
                        showArrow = true
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // MCP Server
                    SettingsItem(
                        icon = Icons.Default.Dns,
                        title = "MCP 服务器",
                        subtitle = "管理 MCP 扩展服务",
                        onClick = { navController.navigate("mcp_server_management") },
                        showArrow = true
                    )
                }
            }
            
            // 功能设置
            item {
                Text(
                    text = "功能",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            item {
                ModernSettingsCard {
                    // Plan 模式
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Assignment,
                        title = "Plan 模式",
                        subtitle = "AI 生成并执行多步骤计划",
                        checked = planModeEnabled,
                        onCheckedChange = { planModeEnabled = it }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 自动化
                    SettingsItem(
                        icon = Icons.Default.AutoMode,
                        title = "自动化",
                        subtitle = "配置自动化任务",
                        onClick = { navController.navigate("automation") },
                        showArrow = true
                    )
                }
            }
            
            // 关于
            item {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            item {
                ModernSettingsCard {
                    // 版本
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "版本",
                        subtitle = "AndroidClaw v0.1.0",
                        onClick = { }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 开源协议
                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "开源协议",
                        subtitle = "查看开源许可",
                        onClick = { /* TODO: 打开 GitHub */ },
                        showArrow = true
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    // 贡献者
                    SettingsItem(
                        icon = Icons.Default.People,
                        title = "贡献者",
                        subtitle = "查看项目贡献者列表",
                        onClick = { /* TODO: 打开贡献者页面 */ },
                        showArrow = true
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    // 主题选择对话框
    if (showThemeDialog) {
        ThemeSelectionDialog(
            selectedTheme = selectedTheme,
            onThemeSelected = {
                selectedTheme = it
                ThemeManager.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

/**
 * 现代用户卡片
 */
@Composable
private fun ModernUserCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "用户",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AndroidClaw 用户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Pro 用户",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            IconButton(
                onClick = { /* 编辑 */ },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 现代设置卡片
 */
@Composable
private fun ModernSettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            content = content
        )
    }
}

/**
 * 设置项
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showArrow: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 带开关的设置项
 */
@Composable
private fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        AnimatedSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 主题选择对话框
 */
@Composable
private fun ThemeSelectionDialog(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "选择主题",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 液态玻璃主题
            ThemeOption(
                name = "液态玻璃",
                description = "通透感、模糊效果、光影层次",
                colors = listOf(Color(0xFF6C63FF), Color(0xFF4ECDC4), Color(0xFFFF6B6B)),
                isSelected = selectedTheme == AppTheme.LIQUID_GLASS,
                onClick = { onThemeSelected(AppTheme.LIQUID_GLASS) }
            )
            
            // MIUI 主题
            ThemeOption(
                name = "MIUI 风格",
                description = "简洁、功能性强、卡片化设计",
                colors = listOf(Color(0xFFFF6700), Color(0xFF333333), Color(0xFF00B42A)),
                isSelected = selectedTheme == AppTheme.MIUI,
                onClick = { onThemeSelected(AppTheme.MIUI) }
            )
            
            // iOS 主题
            ThemeOption(
                name = "iOS 风格",
                description = "优雅、流畅动画、毛玻璃效果",
                colors = listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500)),
                isSelected = selectedTheme == AppTheme.IOS,
                onClick = { onThemeSelected(AppTheme.IOS) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    name: String,
    description: String,
    colors: List<Color>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "theme_border"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 颜色预览
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun getThemeName(theme: AppTheme): String = when (theme) {
    AppTheme.LIQUID_GLASS -> "液态玻璃"
    AppTheme.MIUI -> "MIUI 风格"
    AppTheme.IOS -> "iOS 风格"
}
