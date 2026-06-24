// MainScreen.kt
// 主界面 - 底部4Tab导航容器
// Tab: 首页 | AI | 自动化 | 我的

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidclaw.app.ai.AiProviderManager
import com.androidclaw.app.ui.theme.*

enum class MainTab(
    val icon: ImageVector,
    val label: String
) {
    HOME(Icons.Default.Home, "\u9996\u9875"),
    AI(Icons.Default.SmartToy, "AI"),
    AUTOMATION(Icons.Default.AutoMode, "\u81ea\u52a8\u5316"),
    PROFILE(Icons.Default.Person, "\u6211\u7684")
}

@Composable
fun MainScreen(
    onNavigateToChat: () -> Unit = {},
    onNavigateToModelManagement: () -> Unit = {},
    onNavigateToSkillManagement: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToSkillCreator: () -> Unit = {},
    onNavigateToMcpServer: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), spotColor = Color(0x1A000000)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val animScale by animateFloatAsState(if (isSelected) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "s")
                        val animColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(200), label = "c")
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            modifier = Modifier.width(72.dp).graphicsLayer { scaleX = animScale; scaleY = animScale }
                        ) {
                            Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(tab.icon, tab.label, tint = animColor, modifier = Modifier.size(24.dp))
                                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = animColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(targetState = selectedTab, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "tab") { tab ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToModelManagement = onNavigateToModelManagement,
                        onNavigateToSkillManagement = onNavigateToSkillManagement,
                        onNavigateToSkillMarket = onNavigateToSkillMarket,
                        onNavigateToSettings = onNavigateToSettings
                    )
                    MainTab.AI -> AiProviderSettingsTab(aiProviderManager = AiProviderManager.getInstance(context))
                    MainTab.AUTOMATION -> AutomationScreen(onNavigateBack = {})
                    MainTab.PROFILE -> ProfileScreen(
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToMcpServer = onNavigateToMcpServer,
                        onNavigateToModelManagement = onNavigateToModelManagement
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProviderSettingsTab(aiProviderManager: AiProviderManager) {
    AiProviderSettingsScreen(navController = null, aiProviderManager = aiProviderManager)
}