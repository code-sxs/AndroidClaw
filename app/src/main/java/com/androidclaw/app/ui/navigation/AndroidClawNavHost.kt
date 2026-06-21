// AndroidClawNavHost.kt
// 导航宿主

package com.androidclaw.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.androidclaw.app.ui.screens.ChatScreen

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object ModelManagement : Screen("model_management")
    object SkillManagement : Screen("skill_management")
    object Settings : Screen("settings")
}

/**
 * 导航宿主
 */
@Composable
fun AndroidClawNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Chat.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        // 聊天界面
        composable(Screen.Chat.route) {
            ChatScreen()
        }

        // TODO: 模型管理界面
        composable(Screen.ModelManagement.route) {
            // ModelManagementScreen()
        }

        // TODO: Skill 管理界面
        composable(Screen.SkillManagement.route) {
            // SkillManagementScreen()
        }

        // TODO: 设置界面
        composable(Screen.Settings.route) {
            // SettingsScreen()
        }
    }
}
