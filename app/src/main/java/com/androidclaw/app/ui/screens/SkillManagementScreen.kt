// SkillManagementScreen.kt
// Skill 管理界面 - 现代化 UI 设计

package com.androidclaw.app.ui.screens

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidclaw.app.agent.ToolRegistry
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.SkillManager
import com.androidclaw.app.skills.SkillInfo
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Skill 管理界面 - 现代化设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillManagementScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToMarket: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolRegistry = remember { ToolRegistry.getInstance(context) }
    var selectedSkill by remember { mutableStateOf<SkillInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    
    // 过滤后的技能列表
    val skillsList by SkillManager.skills.collectAsState()
    val filteredSkills = remember(searchQuery, skillsList) {
        if (searchQuery.isBlank()) {
            skillsList
        } else {
            skillsList.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "Skill 管理",
                subtitle = "管理您的 AI 技能扩展",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    // 市场按钮
                    IconButton(
                        onClick = onNavigateToMarket,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Skill 市场",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            GradientFAB(
                onClick = { showAddMenu = true },
                gradientColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加 Skill",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 搜索栏
            item {
                AnimatedSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索 Skill..."
                )
            }
            
            // 技能列表标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已安装 Skill (${filteredSkills.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // 启用计数
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${skillsList.count { it.isEnabled }} 已启用",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            // 技能列表
            items(filteredSkills, key = { it.skillName }) { skill ->
                ModernSkillCard(
                    skill = skill,
                    onToggle = { enabled ->
                        if (enabled) SkillManager.enableSkill(skill.skillName) else SkillManager.disableSkill(skill.skillName)
                    },
                    onClick = { selectedSkill = skill }
                )
            }
            
            // 提示信息
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "启用 Skill 后，Agent 将能够调用相应的工具",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // 添加 Skill 菜单
    if (showAddMenu) {
        GlassDialog(
            onDismissRequest = { showAddMenu = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "添加 Skill",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                AddSkillOption(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI 生成",
                    description = "描述您的需求，AI 自动生成 Skill",
                    gradientColors = listOf(Color(0xFF6C63FF), Color(0xFF8B85FF)),
                    onClick = {
                        showAddMenu = false
                        // TODO: 导航到 AI 生成页面
                    }
                )
                
                AddSkillOption(
                    icon = Icons.Default.Store,
                    title = "从市场安装",
                    description = "浏览和安装社区分享的 Skill",
                    gradientColors = listOf(Color(0xFF34C759), Color(0xFF30D158)),
                    onClick = {
                        showAddMenu = false
                        onNavigateToMarket()
                    }
                )
                
                AddSkillOption(
                    icon = Icons.Default.Code,
                    title = "手动创建",
                    description = "编写代码创建自定义 Skill",
                    gradientColors = listOf(Color(0xFFFF9500), Color(0xFFFFAB76)),
                    onClick = {
                        showAddMenu = false
                        // TODO: 导航到代码编辑器
                    }
                )
            }
        }
    }
    
    // Skill 详情对话框
    selectedSkill?.let { skill ->
        ModernSkillDetailDialog(
            skill = skill,
            onDismiss = { selectedSkill = null }
        )
    }
}

/**
 * 现代 Skill 卡片
 */
@Composable
private fun ModernSkillCard(
    skill: SkillInfo,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val animatedElevation by animateDpAsState(
        targetValue = if (expanded) 8.dp else 0.dp,
        animationSpec = tween(200),
        label = "card_elevation"
    )
    
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        elevation = animatedElevation,
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skill 图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (skill.isEnabled) {
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                } else {
                                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getSkillIcon(skill.skillName),
                        contentDescription = null,
                        tint = if (skill.isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (skill.requiredPermissions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${skill.requiredPermissions.size} 项权限",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // 开关
                AnimatedSwitch(
                    checked = skill.isEnabled,
                    onCheckedChange = onToggle
                )
            }
            
            // 展开/收起详情
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    // 工具列表
                    if (skill.tools.isNotEmpty()) {
                        Text(
                            text = "提供工具",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (tool in skill.tools.take(3)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = tool.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (skill.tools.size > 3) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "+${skill.tools.size - 3}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { /* 编辑 */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("编辑", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        OutlinedButton(
                            onClick = { /* 分享 */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        OutlinedButton(
                            onClick = { /* 删除 */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            
            // 展开按钮
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (expanded) "收起" else "查看详情",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddSkillOption(
    icon: ImageVector,
    title: String,
    description: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush = Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 现代 Skill 详情对话框
 */
@Composable
private fun ModernSkillDetailDialog(
    skill: SkillInfo,
    onDismiss: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getSkillIcon(skill.skillName),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = skill.displayName,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 描述
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 权限要求
                Text(
                    text = "权限要求",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (skill.requiredPermissions.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "无需特殊权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF34C759)
                        )
                    }
                } else {
                    for (permission in skill.requiredPermissions) {
                        PermissionItem(permission = permission)
                    }
                }
                
                // 工具列表
                if (skill.tools.isNotEmpty()) {
                    Text(
                        text = "提供工具",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    for (tool in skill.tools) {
                        ToolItem(tool = tool)
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                onClick = onDismiss,
                cornerRadius = 20.dp
            ) {
                Text(
                    text = "关闭",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun PermissionItem(permission: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = permission,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToolItem(tool: ToolDefinition) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 8.dp,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column {
            Text(
                text = tool.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tool.parameters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "参数: ${tool.parameters.joinToString { it.name }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getSkillIcon(skillName: String): ImageVector = when (skillName) {
    "camera" -> Icons.Default.CameraAlt
    "contacts" -> Icons.Default.Contacts
    "calendar" -> Icons.Default.CalendarToday
    "location" -> Icons.Default.LocationOn
    "file" -> Icons.Default.Folder
    "clipboard" -> Icons.Default.ContentPaste
    "network" -> Icons.Default.Wifi
    "phone" -> Icons.Default.Phone
    "sms" -> Icons.Default.Message
    "music" -> Icons.Default.MusicNote
    "weather" -> Icons.Default.Cloud
    "translation" -> Icons.Default.Translate
    "calculator" -> Icons.Default.Calculate
    "note" -> Icons.Default.Note
    "reminder" -> Icons.Default.Alarm
    "share" -> Icons.Default.Share
    else -> Icons.Default.Extension
}
