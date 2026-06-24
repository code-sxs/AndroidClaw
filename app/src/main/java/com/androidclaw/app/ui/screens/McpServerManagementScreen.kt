// McpServerManagementScreen.kt
// MCP Server 管理界面 - 现代化 UI 设计

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.mcp.McpSkill
import com.androidclaw.app.mcp.McpSkillManager
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MCP Server 管理界面 - 现代化设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerManagementScreen(
    navController: NavController,
    mcpSkillManager: McpSkillManager
) {
    var servers by remember { mutableStateOf(listOf<McpSkill>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var expandedServer by remember { mutableStateOf<String?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // 加载服务器列表
    LaunchedEffect(Unit) {
        servers = mcpSkillManager.getAllSkills()
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "MCP 服务器",
                subtitle = "管理 MCP 扩展服务",
                showBackButton = true,
                onBackClick = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = {
                            servers = mcpSkillManager.getAllSkills()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            GradientFAB(
                onClick = { showAddDialog = true },
                gradientColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加服务器",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        if (servers.isEmpty()) {
            // 空状态
            EmptyState(
                modifier = Modifier.padding(paddingValues),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                },
                title = "暂无 MCP 服务器",
                description = "添加 MCP 服务器以扩展 AI 功能",
                action = {
                    GradientButton(
                        onClick = { showAddDialog = true },
                        cornerRadius = 20.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "添加服务器",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 说明卡片
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "MCP 服务器为 AI 提供额外的工具和能力",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // 服务器列表
                items(servers, key = { it.skillName }) { server ->
                    ModernMcpServerCard(
                        server = server,
                        isExpanded = expandedServer == server.skillName,
                        onExpandToggle = {
                            expandedServer = if (expandedServer == server.skillName) {
                                null
                            } else {
                                server.skillName
                            }
                        },
                        onConnect = {
                            // TODO: 实现连接/断开
                        },
                        onRemove = {
                            removeServer(
                                coroutineScope,
                                mcpSkillManager,
                                server,
                                onComplete = { servers = mcpSkillManager.getAllSkills() }
                            )
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // 添加服务器对话框
    if (showAddDialog) {
        ModernAddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { serverUrl, serverName ->
                addServer(
                    coroutineScope,
                    mcpSkillManager,
                    serverUrl,
                    serverName,
                    setIsLoading = { isLoading = it },
                    onComplete = {
                        servers = mcpSkillManager.getAllSkills()
                        showAddDialog = false
                    }
                )
            },
            isLoading = isLoading
        )
    }
}

/**
 * 现代 MCP 服务器卡片
 */
@Composable
private fun ModernMcpServerCard(
    server: McpSkill,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onConnect: () -> Unit,
    onRemove: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    
    // 模拟连接状态（实际应从服务器状态获取）
    val isConnected = remember { mutableStateOf(false) }
    val isError = remember { mutableStateOf(false) }
    
    val statusColor = when {
        isError.value -> MaterialTheme.colorScheme.error
        isConnected.value -> Color(0xFF34C759)
        else -> MaterialTheme.colorScheme.outline
    }
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse_alpha"
    )
    
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = onExpandToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected.value) {
                                statusColor.copy(alpha = pulseAlpha)
                            } else {
                                statusColor
                            }
                        )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 服务器信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = server.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                // 展开/收起按钮
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 展开详情
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    
                    // URL 信息
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 8.dp,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = server.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 工具列表
                    if (server.mcpTools.isNotEmpty()) {
                        Text(
                            text = "可用工具",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            server.mcpTools.take(4).forEach { tool ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (server.mcpTools.size > 4) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "+${server.mcpTools.size - 4}",
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
                        // 连接/断开按钮
                        GradientButton(
                            onClick = onConnect,
                            gradientColors = if (isConnected.value) {
                                listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            } else {
                                listOf(Color(0xFF34C759), Color(0xFF30D158))
                            },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp
                        ) {
                            Icon(
                                imageVector = if (isConnected.value) Icons.Default.LinkOff else Icons.Default.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isConnected.value) "断开" else "连接",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // 删除按钮
                        OutlinedButton(
                            onClick = onRemove,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
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
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 现代添加 MCP 服务器对话框
 */
@Composable
private fun ModernAddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (serverUrl: String, serverName: String) -> Unit,
    isLoading: Boolean
) {
    var serverUrl by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    
    // URL 验证
    fun validateUrl(url: String): Boolean {
        return try {
            val regex = Regex("^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$")
            regex.matches(url)
        } catch (e: Exception) {
            false
        }
    }
    
    GlassDialog(
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp) ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "添加 MCP 服务器",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 服务器名称
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "服务器名称",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                AnimatedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    placeholder = "My MCP Server",
                    leadingIcon = Icons.Default.Label
                )
            }
            
            // 服务器 URL
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "服务器 URL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                AnimatedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        urlError = if (it.isNotBlank() && !validateUrl(it)) {
                            "请输入有效的 URL"
                        } else null
                    },
                    placeholder = "https://example.com/mcp",
                    leadingIcon = Icons.Default.Link,
                    isError = urlError != null,
                    errorMessage = urlError
                )
            }
            
            // 提示
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 8.dp,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "输入 MCP 服务器的 WebSocket 或 HTTP URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
                
                GradientButton(
                    onClick = { onAdd(serverUrl, serverName) },
                    enabled = !isLoading && serverUrl.isNotBlank() && serverName.isNotBlank() && urlError == null,
                    modifier = Modifier.weight(1f),
                    cornerRadius = 12.dp
                ) {
                    if (isLoading) {
                        GradientLoader(size = 20.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "添加",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 添加 MCP 服务器
 */
private fun addServer(
    coroutineScope: CoroutineScope,
    mcpSkillManager: McpSkillManager,
    serverUrl: String,
    serverName: String,
    setIsLoading: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        setIsLoading(true)
        
        try {
            // TODO: 实现添加逻辑
            // mcpSkillManager.addServer(context, serverUrl, serverName)
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            setIsLoading(false)
        }
    }
}

/**
 * 移除 MCP 服务器
 */
private fun removeServer(
    coroutineScope: CoroutineScope,
    mcpSkillManager: McpSkillManager,
    server: McpSkill,
    onComplete: () -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            val serverName = server.skillName.removePrefix("mcp_")
            // TODO: 实现移除逻辑
            // mcpSkillManager.removeServer(serverName)
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
