// SettingsScreen.kt
// 设置界面
// 包含：AI 提供商、MCP Server、远程推理、Plan 模式等设置

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.ai.AiProviderManager
import com.androidclaw.app.mcp.McpSkillManager
import com.androidclaw.app.remote.RemoteInferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设置界面
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
    var remoteTimeout by remember { mutableStateOf("30") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            // ==================== AI 提供商设置 ====================
            item {
                SettingsSection(title = "AI Provider") {
                    Button(
                        onClick = {
                            navController.navigate("ai_provider_settings")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configure AI Provider")
                    }
                }
            }

            // ==================== MCP Server 设置 ====================
            item {
                SettingsSection(title = "MCP Servers") {
                    Button(
                        onClick = {
                            navController.navigate("mcp_server_management")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage MCP Servers")
                    }
                }
            }

            // ==================== 远程推理设置 ====================
            item {
                SettingsSection(title = "Remote Inference") {
                    // 启用/禁用开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Remote Inference")
                        Switch(
                            checked = remoteInferenceEnabled,
                            onCheckedChange = { enabled ->
                                remoteInferenceEnabled = enabled
                                // TODO: Save to DataStore
                            }
                        )
                    }

                    // 服务器地址
                    OutlinedTextField(
                        value = remoteServerAddress,
                        onValueChange = { remoteServerAddress = it },
                        label = { Text("Server Address") },
                        placeholder = { Text("http://192.168.1.100:8000") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 连接超时
                    OutlinedTextField(
                        value = remoteTimeout,
                        onValueChange = { remoteTimeout = it },
                        label = { Text("Timeout (seconds)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 测试连接按钮
                    Button(
                        onClick = {
                            // TODO: Test remote connection
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test Connection")
                    }
                }
            }

            // ==================== Plan 模式设置 ====================
            item {
                SettingsSection(title = "Plan Mode") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Plan Mode")
                        Switch(
                            checked = planModeEnabled,
                            onCheckedChange = { enabled ->
                                planModeEnabled = enabled
                                // TODO: Save to DataStore
                            }
                        )
                    }

                    Text(
                        text = "Plan mode allows the AI to generate and execute multi-step plans",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================== 关于 ====================
            item {
                SettingsSection(title = "About") {
                    Text(
                        text = "AndroidClaw v0.1.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Local AI assistant for Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 设置分组
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}
