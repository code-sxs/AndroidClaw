// ChatScreen.kt
// 聊天界面 - 现代化 UI 设计（液态玻璃 / iOS 风格）

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import com.androidclaw.app.voice.*
import kotlinx.coroutines.launch

/**
 * 聊天界面 - 现代化设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToSkillManagement: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    
    // 从 ViewModel 获取状态
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val voiceConfig by viewModel.voiceConfig.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val partialText by viewModel.partialText.collectAsState()
    
    var showMenu by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // 语音状态提示
    val voiceHintText = when (voiceStatus) {
        VoiceStatus.LISTENING -> "正在听..."
        VoiceStatus.PROCESSING -> "正在处理..."
        VoiceStatus.SPEAKING -> "正在播报..."
        VoiceStatus.ERROR -> "语音出错"
        else -> ""
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 毛玻璃 AppBar
            GlassAppBar(
                title = "AndroidClaw",
                subtitle = "智能助手",
                isOnline = true,
                actions = {
                    // 语音输入按钮（脉冲动画）
                    PulsingIconButton(
                        onClick = {
                            viewModel.updateVoiceConfig(
                                voiceConfig.copy(voiceInputEnabled = !voiceConfig.voiceInputEnabled)
                            )
                        },
                        icon = if (voiceConfig.voiceInputEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "语音输入",
                        isPulsing = voiceStatus == VoiceStatus.LISTENING,
                        tint = if (voiceConfig.voiceInputEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    
                    // 设置按钮
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 毛玻璃底部栏
            GlassBottomBar {
                // 附件按钮
                Box(modifier = Modifier.weight(1f)) {
                    // 输入框区域
                    ModernChatInputBar(
                        inputText = inputText,
                        onInputChange = { viewModel.updateInputText(it) },
                        onSend = {
                            if (inputText.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(inputText)
                            }
                        },
                        onVoiceClick = {
                            if (voiceStatus == VoiceStatus.LISTENING) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        },
                        isLoading = isLoading,
                        isListening = voiceStatus == VoiceStatus.LISTENING,
                        voiceHintText = voiceHintText,
                        partialText = partialText,
                        voiceInputEnabled = voiceConfig.voiceInputEnabled,
                        onAttachmentClick = { showAttachmentMenu = true }
                    )
                    
                    // 附件弹出菜单
                    DropdownMenu(
                        expanded = showAttachmentMenu,
                        onDismissRequest = { showAttachmentMenu = false }
                    ) {
                        AttachmentMenuItem(
                            icon = Icons.Default.Image,
                            title = "相册",
                            onClick = {
                                showAttachmentMenu = false
                                // TODO: 打开相册
                            }
                        )
                        AttachmentMenuItem(
                            icon = Icons.Default.CameraAlt,
                            title = "相机",
                            onClick = {
                                showAttachmentMenu = false
                                // TODO: 打开相机
                            }
                        )
                        AttachmentMenuItem(
                            icon = Icons.Default.InsertDriveFile,
                            title = "文件",
                            onClick = {
                                showAttachmentMenu = false
                                // TODO: 选择文件
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(300)
                    )
                ) {
                    ModernChatMessageItem(
                        message = message,
                        onPlayClick = { viewModel.speakMessage(message) },
                        voiceOutputEnabled = voiceConfig.voiceOutputEnabled
                    )
                }
            }
            
            // 打字指示器
            if (isLoading) {
                item {
                    TypingIndicator(
                        modifier = Modifier.padding(start = 8.dp),
                        dotColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    // 菜单弹出
    if (showMenu) {
        GlassDialog(
            onDismissRequest = { showMenu = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "菜单",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                MenuItem(
                    icon = Icons.Default.SmartToy,
                    title = "模型管理",
                    onClick = {
                        showMenu = false
                        onNavigateToModelManagement()
                    }
                )
                MenuItem(
                    icon = Icons.Default.Extension,
                    title = "Skill 管理",
                    onClick = {
                        showMenu = false
                        onNavigateToSkillManagement()
                    }
                )
                MenuItem(
                    icon = Icons.Default.Settings,
                    title = "语音设置",
                    onClick = {
                        showMenu = false
                        showVoiceSettings = true
                    }
                )
                MenuItem(
                    icon = Icons.Default.Tune,
                    title = "应用设置",
                    onClick = {
                        showMenu = false
                        onNavigateToSettings()
                    }
                )
            }
        }
    }
    
    // 语音设置对话框
    if (showVoiceSettings) {
        ModernVoiceSettingsDialog(
            config = voiceConfig,
            onDismiss = { showVoiceSettings = false },
            onConfirm = { newConfig ->
                viewModel.updateVoiceConfig(newConfig)
                showVoiceSettings = false
            }
        )
    }
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * 现代聊天消息项
 */
@Composable
fun ModernChatMessageItem(
    message: ChatMessage,
    onPlayClick: () -> Unit = {},
    voiceOutputEnabled: Boolean = true
) {
    val isUser = message.role == "user"
    
    // 用户消息 - 右侧，渐变背景
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // 时间戳
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                )
                
                // 消息气泡（渐变背景）
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 用户头像
            Box(
                modifier = Modifier
                    .size(36.dp)
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        // AI 消息 - 左侧，毛玻璃背景
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // AI 头像
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                // 消息气泡（毛玻璃效果）
                GlassCard(
                    modifier = Modifier.widthIn(max = 280.dp),
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    cornerRadius = 20.dp,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    elevation = 0.dp
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                    )
                }
                
                // 播放按钮和时间戳
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (voiceOutputEnabled) {
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "播放语音",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * 现代聊天输入栏
 */
@Composable
private fun ModernChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    voiceHintText: String,
    partialText: String,
    voiceInputEnabled: Boolean,
    onAttachmentClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "send_pulse")
    val sendScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "send_scale"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 附件按钮
        Box {
            IconButton(
                onClick = onAttachmentClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加附件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // 输入框
        VoiceAnimatedTextField(
            value = if (isListening && partialText.isNotEmpty()) partialText else inputText,
            onValueChange = onInputChange,
            onVoiceClick = onVoiceClick,
            isListening = isListening,
            modifier = Modifier.weight(1f),
            placeholder = if (isListening && voiceHintText.isNotEmpty()) voiceHintText else "输入消息...",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Send
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = { onSend() }
            )
        )
        
        // 发送按钮
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (inputText.isNotBlank() && !isLoading && !isListening) {
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                )
                .then(
                    if (inputText.isNotBlank() && !isLoading && !isListening) {
                        Modifier.scale(sendScale)
                    } else Modifier
                )
                .clickable(enabled = inputText.isNotBlank() && !isLoading && !isListening) {
                    onSend()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (inputText.isNotBlank() && !isLoading && !isListening) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title) },
        onClick = onClick,
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null)
        }
    )
}

/**
 * 现代语音设置对话框
 */
@Composable
private fun ModernVoiceSettingsDialog(
    config: VoiceConfig,
    onDismiss: () -> Unit,
    onConfirm: (VoiceConfig) -> Unit
) {
    var voiceInputEnabled by remember { mutableStateOf(config.voiceInputEnabled) }
    var voiceOutputEnabled by remember { mutableStateOf(config.voiceOutputEnabled) }
    var autoSendVoiceInput by remember { mutableStateOf(config.autoSendVoiceInput) }
    var speechRate by remember { mutableStateOf(config.speechRate) }
    var offlineMode by remember { mutableStateOf(config.offlineMode) }
    
    GlassDialog(
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                text = "语音设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 语音输入开关
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "语音输入",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "使用麦克风进行语音输入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedSwitch(
                        checked = voiceInputEnabled,
                        onCheckedChange = { voiceInputEnabled = it }
                    )
                }
            }
            
            // 自动发送开关
            AnimatedVisibility(visible = voiceInputEnabled) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "自动发送",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "识别完成后自动发送",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedSwitch(
                            checked = autoSendVoiceInput,
                            onCheckedChange = { autoSendVoiceInput = it }
                        )
                    }
                }
            }
            
            // 语音输出开关
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "语音播报",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "AI 回复自动朗读",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedSwitch(
                        checked = voiceOutputEnabled,
                        onCheckedChange = { voiceOutputEnabled = it }
                    )
                }
            }
            
            // 语速调节
            AnimatedVisibility(visible = voiceOutputEnabled) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Column {
                        Text(
                            text = "语速: ${String.format("%.1f", speechRate)}x",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = speechRate,
                            onValueChange = { speechRate = it },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
            
            // 离线模式
            AnimatedVisibility(visible = voiceInputEnabled) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "离线模式",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "使用本地语音识别",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedSwitch(
                            checked = offlineMode,
                            onCheckedChange = { offlineMode = it }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                    onClick = {
                        onConfirm(
                            config.copy(
                                voiceInputEnabled = voiceInputEnabled,
                                voiceOutputEnabled = voiceOutputEnabled,
                                autoSendVoiceInput = autoSendVoiceInput,
                                speechRate = speechRate,
                                offlineMode = offlineMode
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "确定",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
