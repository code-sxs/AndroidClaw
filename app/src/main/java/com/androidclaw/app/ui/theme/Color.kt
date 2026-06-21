// Color.kt
// AndroidClaw 多主题颜色定义 - 液态玻璃 / MIUI / iOS 风格

package com.androidclaw.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// 主题 1：液态玻璃 (Liquid Glass) - 默认主题
// =============================================================================
object LiquidGlassColors {
    val Primary = Color(0xFF6C63FF)       // 靛蓝紫
    val Secondary = Color(0xFFFF6B6B)     // 珊瑚红
    val Tertiary = Color(0xFF4ECDC4)       // 青绿
    val Background = Color(0xFFF8F9FA)     // 浅灰
    val Surface = Color(0xFFFFFFFF)       // 白色
    val SurfaceVariant = Color(0xFFE8E8F0) // 浅紫灰
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnTertiary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF212529)
    val OnSurface = Color(0xFF212529)
    val OnSurfaceVariant = Color(0xFF6C757D)
    val Outline = Color(0xFFDEE2E6)
    val OutlineVariant = Color(0xFFE9ECEF)
    
    // 深色模式
    val PrimaryDark = Color(0xFF8B85FF)
    val SecondaryDark = Color(0xFFFF8585)
    val TertiaryDark = Color(0xFF6EDDD6)
    val BackgroundDark = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val SurfaceVariantDark = Color(0xFF2D2D3A)
    val OnPrimaryDark = Color(0xFFFFFFFF)
    val OnBackgroundDark = Color(0xFFE9ECEF)
    val OnSurfaceDark = Color(0xFFE9ECEF)
    val OnSurfaceVariantDark = Color(0xFFADB5BD)
    val OutlineDark = Color(0xFF495057)
    
    // 渐变色
    val GradientPrimary = listOf(Color(0xFF6C63FF), Color(0xFFFF6B6B))
    val GradientSecondary = listOf(Color(0xFFFF6B6B), Color(0xFFFFAB76))
    val GradientTertiary = listOf(Color(0xFF4ECDC4), Color(0xFF44A08D))
    val GradientGlass = listOf(Color(0x406C63FF), Color(0x40FF6B6B))
}

// =============================================================================
// 主题 2：MIUI 风格 (Xiaomi)
// =============================================================================
object MiuiColors {
    val Primary = Color(0xFFFF6700)        // 小米橙
    val Secondary = Color(0xFF333333)     // 深灰
    val Tertiary = Color(0xFF00B42A)      // 绿色
    val Background = Color(0xFFF5F5F5)     // 浅灰
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFEEEEEE)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF1A1A1A)
    val OnSurface = Color(0xFF1A1A1A)
    val OnSurfaceVariant = Color(0xFF757575)
    val Outline = Color(0xFFE0E0E0)
    
    // 深色模式
    val BackgroundDark = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val OnBackgroundDark = Color(0xFFE0E0E0)
    val OnSurfaceDark = Color(0xFFE0E0E0)
    
    // 渐变色
    val GradientPrimary = listOf(Color(0xFFFF6700), Color(0xFFFF9500))
    val GradientSecondary = listOf(Color(0xFF333333), Color(0xFF4A4A4A))
}

// =============================================================================
// 主题 3：iOS 风格 (Apple)
// =============================================================================
object IosColors {
    val Primary = Color(0xFF007AFF)        // 苹果蓝
    val Secondary = Color(0xFF34C759)     // 苹果绿
    val Tertiary = Color(0xFFFF9500)       // 橙色
    val Quaternary = Color(0xFFAF52DE)     // 紫色
    val Background = Color(0xFFF2F2F7)     // iOS 灰
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFE5E5EA)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFF000000)
    val OnSurface = Color(0xFF000000)
    val OnSurfaceVariant = Color(0xFF8E8E93)
    val Outline = Color(0xFFC7C7CC)
    val Fill = Color(0x33787880)
    
    // 深色模式
    val BackgroundDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF1C1C1E)
    val SurfaceVariantDark = Color(0xFF2C2C2E)
    val OnBackgroundDark = Color(0xFFFFFFFF)
    val OnSurfaceDark = Color(0xFFFFFFFF)
    val FillDark = Color(0x33F2F2F7)
    
    // 渐变色
    val GradientPrimary = listOf(Color(0xFF007AFF), Color(0xFF5856D6))
    val GradientSecondary = listOf(Color(0xFF34C759), Color(0xFF00C7BE))
    val GradientTertiary = listOf(Color(0xFFFF9500), Color(0xFFFF3B30))
}

// =============================================================================
// 通用颜色
// =============================================================================
object AppColors {
    // 状态色
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9500)
    val Error = Color(0xFFFF3B30)
    val Info = Color(0xFF007AFF)
    
    // 毛玻璃
    val GlassWhite = Color(0xF0FFFFFF)
    val GlassDark = Color(0xF01E1E1E)
    val GlassBorder = Color(0x80FFFFFF)
    val GlassBorderDark = Color(0x802D2D3A)
    
    // 阴影
    val ShadowLight = Color(0x1A000000)
    val ShadowMedium = Color(0x33000000)
    
    // 消息气泡
    val UserBubbleStart = Color(0xFF6C63FF)
    val UserBubbleEnd = Color(0xFFFF6B6B)
    val AiBubbleBackground = Color(0xFFF0F0F5)
    
    // 渐变色
    val BluePurple = listOf(Color(0xFF007AFF), Color(0xFF5856D6))
    val Sunset = listOf(Color(0xFFFF6B6B), Color(0xFFFFAB76))
    val Ocean = listOf(Color(0xFF4ECDC4), Color(0xFF44A08D))
    val Aurora = listOf(Color(0xFF6C63FF), Color(0xFF4ECDC4), Color(0xFFFF6B6B))
}

// =============================================================================
// 渐变画笔扩展
// =============================================================================
package com.androidclaw.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Brush.primaryGradient(): Brush = Brush.linearGradient(
    colors = LiquidGlassColors.GradientPrimary,
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

fun Brush.secondaryGradient(): Brush = Brush.linearGradient(
    colors = LiquidGlassColors.GradientSecondary,
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

fun Brush.tertiaryGradient(): Brush = Brush.linearGradient(
    colors = LiquidGlassColors.GradientTertiary,
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

fun Brush.glassGradient(isDark: Boolean = false): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(Color(0x401E1E1E), Color(0x600D0D0D))
    } else {
        listOf(Color(0x40FFFFFF), Color(0x60E8E8F0))
    }
)

fun Brush.radialGlass(): Brush = Brush.radialGradient(
    colors = listOf(
        Color(0x30FFFFFF),
        Color(0x10FFFFFF),
        Color(0x00FFFFFF)
    )
)
