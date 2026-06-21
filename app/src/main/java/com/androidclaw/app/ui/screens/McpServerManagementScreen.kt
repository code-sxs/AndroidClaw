// McpServerManagementScreen.kt
// MCP Server 管理界面
// 允许用户添加/删除/启用 MCP Server

package com.androidclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.androidclaw.app.mcp.McpSkill
import com.androidclaw.app.mcp.McpSkillManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MCP Server 管理界面
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

    val coroutineScope = rememberCoroutineScope()

    // Load servers on launch
    LaunchedEffect(Unit) {
        servers = mcpSkillManager.getAllSkills()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP Servers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add MCP Server")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (servers.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No MCP Servers added yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Server list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(servers) { server ->
                        McpServerItem(
                            server = server,
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
                }
            }
        }
    }

    // Add Server Dialog
    if (showAddDialog) {
        AddMcpServerDialog(
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
 * MCP Server 列表项
 */
@Composable
fun McpServerItem(
    server: McpSkill,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 添加 MCP Server 对话框
 */
@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (serverUrl: String, serverName: String) -> Unit,
    isLoading: Boolean
) {
    var serverUrl by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://example.com/mcp") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("My MCP Server") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(serverUrl, serverName) },
                enabled = !isLoading && serverUrl.isNotBlank() && serverName.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 添加 MCP Server
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
            mcpSkillManager.addServer(
                context = /* TODO: Get context */,
                serverUrl = serverUrl,
                serverName = serverName
            )
            onComplete()
        } catch (e: Exception) {
            // TODO: Show error message
            e.printStackTrace()
        } finally {
            setIsLoading(false)
        }
    }
}

/**
 * 移除 MCP Server
 */
private fun removeServer(
    coroutineScope: CoroutineScope,
    mcpSkillManager: McpSkillManager,
    server: McpSkill,
    onComplete: () -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            // Extract server name from skill name (format: mcp_<serverName>)
            val serverName = server.skillName.removePrefix("mcp_")
            mcpSkillManager.removeServer(serverName)
            onComplete()
        } catch (e: Exception) {
            // TODO: Show error message
            e.printStackTrace()
        }
    }
}
