// SkillManagementScreen.kt
// Skill 管理界面

package com.androidclaw.app.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidclaw.app.agent.ToolRegistry
import com.androidclaw.app.skills.ToolDefinition
import kotlinx.coroutines.launch

/**
 * Skill 信息
 */
data class SkillInfo(
    val skillName: String,
    val displayName: String,
    val description: String,
    val requiredPermissions: List<String>,
    val tools: List<ToolDefinition>,
    val isEnabled: Boolean = false
)

/**
 * Skill 管理器
 * 维护可用 Skill 列表及启用状态
 */
object SkillManager {
    private val _skills = mutableStateListOf(
        SkillInfo(
            skillName = "camera",
            displayName = "相机",
            description = "访问设备相机，支持拍照和二维码识别",
            requiredPermissions = listOf(Manifest.permission.CAMERA),
            tools = listOf(
                ToolDefinition("take_photo", "拍照", "使用相机拍摄照片", emptyList(), "image"),
                ToolDefinition("scan_qr", "扫描二维码", "识别二维码内容", emptyList(), "string")
            )
        ),
        SkillInfo(
            skillName = "contacts",
            displayName = "联系人",
            description = "读取和管理联系人信息",
            requiredPermissions = listOf(Manifest.permission.READ_CONTACTS),
            tools = listOf(
                ToolDefinition("read_contacts", "读取联系人", "获取联系人列表", emptyList(), "list"),
                ToolDefinition("find_contact", "查找联系人", "按名称搜索联系人", emptyList(), "contact")
            )
        ),
        SkillInfo(
            skillName = "calendar",
            displayName = "日历",
            description = "读取和创建日历事件",
            requiredPermissions = listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ),
            tools = listOf(
                ToolDefinition("read_events", "读取事件", "获取日历事件", emptyList(), "list"),
                ToolDefinition("create_event", "创建事件", "创建新日历事件", emptyList(), "boolean")
            )
        ),
        SkillInfo(
            skillName = "location",
            displayName = "位置",
            description = "获取设备位置信息",
            requiredPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            tools = listOf(
                ToolDefinition("get_location", "获取位置", "获取当前 GPS 位置", emptyList(), "location")
            )
        )
    )

    val skills: SnapshotStateList<SkillInfo> get() = _skills

    fun setEnabled(skillName: String, enabled: Boolean) {
        val index = _skills.indexOfFirst { it.skillName == skillName }
        if (index >= 0) {
            _skills[index] = _skills[index].copy(isEnabled = enabled)
        }
    }

    fun getEnabledSkills(): List<SkillInfo> = _skills.filter { it.isEnabled }
}

/**
 * Skill 管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillManagementScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolRegistry = remember { ToolRegistry.getInstance(context) }
    val skills = SkillManager.skills
    var selectedSkill by remember { mutableStateOf<SkillInfo?>(null) }

    LaunchedEffect(Unit) {
        // 可在此同步 ToolRegistry 中已注册 Skill 的启用状态
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill 管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "已安装 Skill",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "启用 Skill 后，Agent 将能够调用相应的工具。某些 Skill 需要授予 Android 权限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(skills, key = { it.skillName }) { skill ->
                SkillCard(
                    skill = skill,
                    onToggle = { enabled ->
                        SkillManager.setEnabled(skill.skillName, enabled)
                        // 同步到 ToolRegistry：启用则注册，禁用则注销
                        coroutineScope.launch {
                            // TODO: 注册/注销具体 Skill 实现类
                        }
                    },
                    onShowDetails = { selectedSkill = skill }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    selectedSkill?.let { skill ->
        SkillDetailDialog(
            skill = skill,
            onDismiss = { selectedSkill = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillCard(
    skill: SkillInfo,
    onToggle: (Boolean) -> Unit,
    onShowDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onShowDetails
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getSkillIcon(skill.skillName),
                    contentDescription = null,
                    tint = if (skill.isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = skill.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (skill.requiredPermissions.isNotEmpty()) {
                        Text(
                            text = "需要 ${skill.requiredPermissions.size} 项权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Switch(
                checked = skill.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun getSkillIcon(skillName: String) = when (skillName) {
    "camera" -> Icons.Default.CameraAlt
    "contacts" -> Icons.Default.Contacts
    "calendar" -> Icons.Default.CalendarToday
    "location" -> Icons.Default.LocationOn
    else -> Icons.Default.Build
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailDialog(
    skill: SkillInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        icon = {
            Icon(
                imageVector = getSkillIcon(skill.skillName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(skill.displayName)
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text(
                        text = "权限要求",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (skill.requiredPermissions.isEmpty()) {
                    item {
                        Text(
                            text = "无需特殊权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(skill.requiredPermissions) { permission ->
                        PermissionItem(permission = permission)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "提供工具",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(skill.tools) { tool ->
                    ToolItem(tool = tool)
                }
            }
        }
    )
}

@Composable
private fun PermissionItem(permission: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = permission,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ToolItem(tool: ToolDefinition) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = tool.displayName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tool.parameters.isNotEmpty()) {
                Text(
                    text = "参数: ${tool.parameters.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
