// HomeScreen.kt
// 首页 - 硬件信息 + 模型状态 + 快捷入口 + 最近对话

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit = {},
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToSkillManagement: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部问候
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Column {
                Text(
                    text = "AndroidClaw",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$greeting，您的私人 AI 助手已就绪",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 硬件信息卡片
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("设备信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HardwareItem("设备", android.os.Build.MODEL, Icons.Default.Devices)
                        HardwareItem("系统", "Android ${android.os.Build.VERSION.SDK_INT}", Icons.Default.Android)
                        HardwareItem("架构", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown", Icons.Default.Memory)
                    }
                }
            }
        }

        // 模型状态卡片
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF9500)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("模型状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("未加载模型", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("下载本地模型即可离线使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GradientButton(
                        onClick = onNavigateToModelManagement,
                        cornerRadius = 12.dp
                    ) {
                        Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("下载模型", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 快捷入口
        item {
            Text("快捷入口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Chat,
                    title = "新建对话",
                    gradient = LiquidGlassColors.GradientPrimary,
                    onClick = onNavigateToChat
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Memory,
                    title = "模型管理",
                    gradient = LiquidGlassColors.GradientSecondary,
                    onClick = onNavigateToModelManagement
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Store,
                    title = "Skill 市场",
                    gradient = LiquidGlassColors.GradientTertiary,
                    onClick = onNavigateToSkillMarket
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SmartToy,
                    title = "AI 设置",
                    gradient = AppColors.BluePurple,
                    onClick = onNavigateToSettings
                )
            }
        }

        // 特性介绍
        item {
            Text("核心特性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { FeatureCard("完全离线", "数据永不离开设备", Icons.Default.Lock, LiquidGlassColors.GradientPrimary) }
                item { FeatureCard("三引擎推理", "MediaPipe/MLC/LiteRT", Icons.Default.Speed, LiquidGlassColors.GradientSecondary) }
                item { FeatureCard("Skill 系统", "可扩展工具能力", Icons.Default.Extension, LiquidGlassColors.GradientTertiary) }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun HardwareItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String, icon: ImageVector, gradient: List<Color>) {
    GlassCard(modifier = Modifier.width(160.dp), cornerRadius = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
