// AndroidClawNavHost.kt
// 瀵艰埅瀹夸富 - 鏀寔椤甸潰杞満鍔ㄧ敾

package com.androidclaw.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
 * 瀵艰埅璺敱
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
    
    // 鏂拌矾鐢?
    object Plan : Screen("plan/{userRequest}") {
        fun createRoute(userRequest: String) = "plan/$userRequest"
    }
    object McpServerManagement : Screen("mcp_server_management")
    object AiProviderSettings : Screen("ai_provider_settings")
}

/**
 * 瀵艰埅瀹夸富
 */
@Composable
fun AndroidClawNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Chat.route
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
        // ==================== 鑱婂ぉ鐣岄潰 ====================
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
        
        // ==================== 妯″瀷绠＄悊鐣岄潰 ====================
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
        
        // ==================== Skill 绠＄悊鐣岄潰 ====================
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
        
        // ==================== Skill 甯傚満鐣岄潰 ====================
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
        
        // ==================== Skill 鍒涘缓鍣ㄧ晫闈?====================
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
        
        // ==================== 瀹夊叏鎶ュ憡鐣岄潰 ====================
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
            SecurityReportScreen()
        }
        
        // ==================== 璁剧疆鐣岄潰 ====================
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
                aiProviderManager = com.androidclaw.app.ai.AiProviderManager.getInstance(context),
                mcpSkillManager = McpSkillManager,
                remoteInferenceManager = com.androidclaw.app.remote.RemoteInferenceManager.getInstance(context)
            )
        }
        
        // ==================== 鑷姩鍖栫晫闈?====================
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
        
        // ==================== Plan 妯″紡鐣岄潰 ====================
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
                planManager = PlanManager.getInstance(context),
                userRequest = userRequest
            )
        }
        
        // ==================== MCP Server 绠＄悊鐣岄潰 ====================
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
        
        // ==================== AI 鎻愪緵鍟嗚缃晫闈?====================
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
                aiProviderManager = com.androidclaw.app.ai.AiProviderManager.getInstance(context)
            )
        }
    }
}

/**
 * 3D 缈婚〉杞満鏁堟灉
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
 * 鍏变韩鍏冪礌杞満锛堥渶瑕侀澶栭厤缃級
 */
@Composable
private fun sharedElementTransition(
    matchedRoute: String,
    content: @Composable () -> Unit
) {
    // 绠€鍖栧疄鐜帮細鐩存帴浣跨敤榛樿杞満
    content()
}
