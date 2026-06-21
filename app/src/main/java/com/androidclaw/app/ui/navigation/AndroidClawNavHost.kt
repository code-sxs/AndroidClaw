// AndroidClawNavHost.kt
// 导航宿主 - 支持页面转场动画

package com.androidclaw.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.androidclaw.app.planning.PlanManager
import com.androidclaw.app.ui.screens.*
import com.androidclaw.app.mcp.McpSkillManager

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
    
    // 新路由
    object Plan : Screen("plan/{userRequest}") {
        fun createRoute(userRequest: String) = "plan/$userRequest"
    }
    object McpServerManagement : Screen("mcp_server_management")
    object AiProviderSettings : Screen("ai_provider_settings")
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
        // ==================== 聊天界面 ====================
        composable(
            route = Screen.Chat.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            }
        ) {
            ChatScreen(
                onNavigateToModelManagement = {
                    navController.navigate(Screen.ModelManagement.route)
                },
                onNavigateToSkillManagement = {
                    navController.navigate(Screen.SkillManagement.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        // ==================== 模型管理界面 ====================
        composable(
            route = Screen.ModelManagement.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(300, delayMillis = 50)
                ) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) +
                scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(300, delayMillis = 50)
                ) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            }
        ) {
            ModelManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // ==================== Skill 管理界面 ====================
        composable(
            route = Screen.SkillManagement.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            SkillManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMarket = {
                    navController.navigate(Screen.SkillMarket.route)
                }
            )
        }
        
        // ==================== Skill 市场界面 ====================
        composable(
            route = Screen.SkillMarket.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            SkillMarketScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // ==================== Skill 创建器界面 ====================
        composable(
            route = Screen.SkillCreator.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                )
            }
        ) {
            SkillCreatorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSecurityReport = { skillName ->
                    navController.navigate(Screen.SecurityReport.createRoute(skillName))
                }
            )
        }
        
        // ==================== 安全报告界面 ====================
        composable(
            route = Screen.SecurityReport.route,
            arguments = listOf(
                navArgument("skillName") { type = androidx.navigation.NavType.StringType }
            ),
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val skillName = backStackEntry.arguments?.getString("skillName") ?: ""
            SecurityReportScreen(skillName = skillName)
        }
        
        // ==================== 设置界面 ====================
        composable(
            route = Screen.Settings.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            SettingsScreen(
                navController = navController,
                aiProviderManager = com.androidclaw.app.ai.AiProviderManager.getInstance(/* TODO: Get context */),
                mcpSkillManager = McpSkillManager,
                remoteInferenceManager = com.androidclaw.app.remote.RemoteInferenceManager.getInstance(/* TODO: Get context */)
            )
        }
        
        // ==================== 自动化界面 ====================
        composable(
            route = Screen.Automation.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            AutomationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // ==================== Plan 模式界面 ====================
        composable(
            route = Screen.Plan.route,
            arguments = listOf(
                navArgument("userRequest") { type = androidx.navigation.NavType.StringType }
            ),
            enterTransition = {
                fadeIn(animationSpec = tween(400)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val userRequest = backStackEntry.arguments?.getString("userRequest") ?: ""
            PlanScreen(
                navController = navController,
                planManager = PlanManager.getInstance(/* TODO: Get context */),
                userRequest = userRequest
            )
        }
        
        // ==================== MCP Server 管理界面 ====================
        composable(
            route = Screen.McpServerManagement.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            McpServerManagementScreen(
                navController = navController,
                mcpSkillManager = McpSkillManager
            )
        }
        
        // ==================== AI 提供商设置界面 ====================
        composable(
            route = Screen.AiProviderSettings.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300, delayMillis = 50)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                )
            }
        ) {
            AiProviderSettingsScreen(
                navController = navController,
                aiProviderManager = com.androidclaw.app.ai.AiProviderManager.getInstance(/* TODO: Get context */)
            )
        }
    }
}

/**
 * 3D 翻页转场效果
 */
@Composable
private fun pageFlipTransition(
    initialProgress: Float,
    targetProgress: Float,
    content: @Composable (progress: Float) -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "page_flip"
    )
    
    content(animatedProgress)
}

/**
 * 共享元素转场（需要额外配置）
 */
@Composable
private fun sharedElementTransition(
    matchedRoute: String,
    content: @Composable () -> Unit
) {
    // 简化实现：直接使用默认转场
    content()
}
