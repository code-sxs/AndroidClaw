// ChatScreen.kt
// 聊天界面 - 主界面（含语音交互）

package com.androidclaw.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidclaw.app.voice.*
import kotlinx.coroutines.launch

/**
 * 聊天界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToSkillManagement: () -> Unit = {},
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

    // 语音状态提示文本
    val voiceHintText = when (voiceStatus) {
        VoiceStatus.LISTENING -> "正在听..."
        VoiceStatus.PROCESSING -> "正在处理..."
        VoiceStatus.SPEAKING -> "正在播报..."
        VoiceStatus.ERROR -> "出错"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroidClaw") },
                actions = {
                    // 语音开关按钮
                    IconButton(onClick = { 
                        viewModel.updateVoiceConfig(voiceConfig.copy(voiceInputEnabled = !voiceConfig.voiceInputEnabled))
                    }) {
                        Icon(
                            imageVector = if (voiceConfig.voiceInputEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (voiceConfig.voiceInputEnabled) "关闭语音输入" else "开启语音输入",
                            tint = if (voiceConfig.voiceInputEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "菜单"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("模型管理") },
                            onClick = {
                                showMenu = false
                                onNavigateToModelManagement()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Skill 管理") },
                            onClick = {
                                showMenu = false
                                onNavigateToSkillManagement()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("语音设置") },
                            onClick = {
                                showMenu = false
                                showVoiceSettings = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 输入栏
            ChatInputBar(
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
                voiceInputEnabled = voiceConfig.voiceInputEnabled
            )
        }
    ) { paddingValues ->
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(messages) { message ->
                ChatMessageItem(
                    message = message,
                    onPlayClick = {
                        viewModel.speakMessage(message)
                    },
                    voiceOutputEnabled = voiceConfig.voiceOutputEnabled
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 加载指示器
            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            }
        }
    }

    // 语音设置对话框
    if (showVoiceSettings) {
        VoiceSettingsDialog(
            config = voiceConfig,
            onDismiss = { showVoiceSettings = false },
            onConfirm = { newConfig ->
                viewModel.updateVoiceConfig(newConfig)
                showVoiceSettings = false
            }
        )
    }
}

/**
 * 聊天消息 Item
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onPlayClick: () -> Unit = {},
    voiceOutputEnabled: Boolean = true
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 播放按钮（仅助手消息且有语音输出启用时显示）
                if (!isUser && voiceOutputEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "播放语音",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 输入栏（含语音按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    voiceHintText: String,
    partialText: String,
    voiceInputEnabled: Boolean
) {
    Surface(
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 麦克风按钮
            if (voiceInputEnabled) {
                VoiceButton(
                    isListening = isListening,
                    onClick = onVoiceClick
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 输入框
            OutlinedTextField(
                value = if (isListening && partialText.isNotEmpty()) partialText else inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (isListening && voiceHintText.isNotEmpty()) voiceHintText else "输入消息...")
                },
                enabled = !isLoading && !isListening,
                leadingIcon = if (isListening) {
                    {
                        // 语音波形动画
                        VoiceWaveAnimation()
                    }
                } else null
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading && !isListening
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送"
                )
            }
        }
    }
}

/**
 * 语音按钮（含动画）
 */
@Composable
fun VoiceButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .scale(if (isListening) scale else 1f)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (isListening) "停止录音" else "开始录音",
            tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 语音波形动画
 */
@Composable
fun VoiceWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    
    Row(
        modifier = Modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 24f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300, delayMillis = index * 100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "height_$index"
            )
            
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * 加载指示器
 */
@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 语音设置对话框
 */
@Composable
fun VoiceSettingsDialog(
    config: VoiceConfig,
    onDismiss: () -> Unit,
    onConfirm: (VoiceConfig) -> Unit
) {
    var voiceInputEnabled by remember { mutableStateOf(config.voiceInputEnabled) }
    var voiceOutputEnabled by remember { mutableStateOf(config.voiceOutputEnabled) }
    var autoSendVoiceInput by remember { mutableStateOf(config.autoSendVoiceInput) }
    var speechRate by remember { mutableStateOf(config.speechRate) }
    var offlineMode by remember { mutableStateOf(config.offlineMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 语音输入开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("语音输入")
                    Switch(
                        checked = voiceInputEnabled,
                        onCheckedChange = { voiceInputEnabled = it }
                    )
                }
                
                // 自动发送语音识别结果
                if (voiceInputEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动发送识别结果")
                        Switch(
                            checked = autoSendVoiceInput,
                            onCheckedChange = { autoSendVoiceInput = it }
                        )
                    }
                }
                
                // 离线模式
                if (voiceInputEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("离线模式")
                        Switch(
                            checked = offlineMode,
                            onCheckedChange = { offlineMode = it }
                        )
                    }
                }
                
                HorizontalDivider()
                
                // 语音输出开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("语音播报（TTS）")
                    Switch(
                        checked = voiceOutputEnabled,
                        onCheckedChange = { voiceOutputEnabled = it }
                    )
                }
                
                // 语速调节
                if (voiceOutputEnabled) {
                    Column {
                        Text("语速: ${String.format("%.1f", speechRate)}x")
                        Slider(
                            value = speechRate,
                            onValueChange = { speechRate = it },
                            valueRange = 0.5f..2.0f,
                            steps = 5
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
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
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
