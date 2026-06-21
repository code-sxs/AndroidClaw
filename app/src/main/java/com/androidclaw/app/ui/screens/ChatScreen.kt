// ChatScreen.kt
// 聊天界面 - 主界面

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 聊天界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToSkillManagement: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 对话状态
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroidClaw") },
                actions = {
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
                    }
                }
            )
        },
        bottomBar = {
            // 输入栏
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isLoading) {
                        // 发送消息
                        val userMessage = ChatMessage(
                            role = "user",
                            content = inputText
                        )
                        messages = messages + userMessage
                        inputText = ""
                        isLoading = true

                        // TODO: 调用 AgentManager.sendMessage()
                        // 这里需要实际实现 LLM 推理
                        coroutineScope.launch {
                            // 模拟回复
                            kotlinx.coroutines.delay(1000)
                            val assistantMessage = ChatMessage(
                                role = "assistant",
                                content = "This is a placeholder response. LLM inference not yet implemented."
                            )
                            messages = messages + assistantMessage
                            isLoading = false
                        }
                    }
                },
                isLoading = isLoading
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
                ChatMessageItem(message = message)
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
}

/**
 * 聊天消息 Item
 */
@Composable
fun ChatMessageItem(message: ChatMessage) {
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
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 输入栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
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
            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send"
                )
            }
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
 * 聊天消息数据类
 */
data class ChatMessage(
    val role: String,  // "user" / "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
