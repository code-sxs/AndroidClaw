// AndroidClawNavHost.kt
// 导航宿主

package com.androidclaw.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.androidclaw.app.ui.screens.ChatScreen
import com.androidclaw.app.ui.screens.ModelManagementScreen
import com.androidclaw.app.ui.screens.SecurityReportScreen
import com.androidclaw.app.ui.screens.SkillCreatorScreen
import com.androidclaw.app.ui.screens.SkillManagementScreen
import com.androidclaw.app.ui.screens.SkillMarketScreen

/**
 * 导航路由
 */
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object ModelManagement : Screen("model_management")
    object SkillManagement : Screen("skill_management")
    object SecurityReport : Screen("security_report/{skillName}") {
        fun createRoute(skillName: String) = "security_report/$skillName"
    }
    object SkillMarket : Screen("skill_market")
    object SkillCreator : Screen("skill_creator")
    object Settings : Screen("settings")
    object Automation : Screen("automation")
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
            ChatScreen(
                onNavigateToModelManagement = {
                    navController.navigate(Screen.ModelManagement.route)
                },
                onNavigateToSkillManagement = {
                    navController.navigate(Screen.SkillManagement.route)
                }
            )
        }

        // 模型管理界面
        composable(
            route = Screen.ModelManagement.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            ModelManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Skill 管理界面
        composable(
            route = Screen.SkillManagement.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            SkillManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMarket = {
                    navController.navigate(Screen.SkillMarket.route)
                }
            )
        }

        // Skill 市场界面
        composable(
            route = Screen.SkillMarket.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            SkillMarketScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Skill 创建器界面
        composable(
            route = Screen.SkillCreator.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            SkillCreatorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSecurityReport = { skillName ->
                    navController.navigate(Screen.SecurityReport.createRoute(skillName))
                }
            )
        }

        // TODO: 设置界面
        composable(Screen.Settings.route) {
            // SettingsScreen()
        }

        // 自动化界面
        composable(Screen.Automation.route) {
            AutomationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
