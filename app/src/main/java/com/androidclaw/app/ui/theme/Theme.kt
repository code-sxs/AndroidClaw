// Theme.kt
// AndroidClaw Material Design 3 主题配置 - 支持液态玻璃 / MIUI / iOS 三种主题

package com.androidclaw.app.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// =============================================================================
// 主题枚举
// =============================================================================
enum class AppTheme {
    LIQUID_GLASS,   // 液态玻璃（默认）
    MIUI,           // MIUI 风格
    IOS             // iOS 风格
}

// 持久化主题选择
val LocalAppTheme = compositionLocalOf { AppTheme.LIQUID_GLASS }

// =============================================================================
// 主题管理器
// =============================================================================
object ThemeManager {
    private var currentTheme by mutableStateOf(AppTheme.LIQUID_GLASS)
    
    fun setTheme(theme: AppTheme) {
        currentTheme = theme
    }
    
    fun getTheme(): AppTheme = currentTheme
}

// =============================================================================
// 主题配置生成器
// =============================================================================
private fun createLiquidGlassColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    context: android.content.Context
): ColorScheme {
    // 动态颜色支持（Android 12+）
    val hasDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    return if (darkTheme) {
        val dynamicDark = if (hasDynamicColor) {
            dynamicDarkColorScheme(context)
        } else null
        
        darkColorScheme(
            primary = dynamicDark?.primary ?: LiquidGlassColors.PrimaryDark,
            onPrimary = dynamicDark?.onPrimary ?: LiquidGlassColors.OnPrimaryDark,
            primaryContainer = dynamicDark?.primaryContainer ?: LiquidGlassColors.Primary.copy(alpha = 0.3f),
            onPrimaryContainer = LiquidGlassColors.OnPrimaryDark,
            secondary = dynamicDark?.secondary ?: LiquidGlassColors.SecondaryDark,
            onSecondary = LiquidGlassColors.OnPrimaryDark,
            secondaryContainer = dynamicDark?.secondaryContainer ?: LiquidGlassColors.Secondary.copy(alpha = 0.3f),
            onSecondaryContainer = LiquidGlassColors.OnPrimaryDark,
            tertiary = dynamicDark?.tertiary ?: LiquidGlassColors.TertiaryDark,
            onTertiary = dynamicDark?.onTertiary ?: LiquidGlassColors.OnPrimaryDark,
            tertiaryContainer = dynamicDark?.tertiaryContainer ?: LiquidGlassColors.Tertiary.copy(alpha = 0.3f),
            onTertiaryContainer = LiquidGlassColors.OnPrimaryDark,
            background = dynamicDark?.background ?: LiquidGlassColors.BackgroundDark,
            onBackground = dynamicDark?.onBackground ?: LiquidGlassColors.OnBackgroundDark,
            surface = dynamicDark?.surface ?: LiquidGlassColors.SurfaceDark,
            onSurface = dynamicDark?.onSurface ?: LiquidGlassColors.OnSurfaceDark,
            surfaceVariant = dynamicDark?.surfaceVariant ?: LiquidGlassColors.SurfaceVariantDark,
            onSurfaceVariant = dynamicDark?.onSurfaceVariant ?: LiquidGlassColors.OnSurfaceVariantDark,
            outline = dynamicDark?.outline ?: LiquidGlassColors.OutlineDark,
            outlineVariant = dynamicDark?.outlineVariant ?: LiquidGlassColors.OutlineDark.copy(alpha = 0.5f),
            error = AppColors.Error,
            onError = Color.White,
            errorContainer = AppColors.Error.copy(alpha = 0.3f),
            onErrorContainer = Color.White,
            inverseSurface = LiquidGlassColors.Background,
            inverseOnSurface = LiquidGlassColors.OnBackground,
            inversePrimary = LiquidGlassColors.Primary,
            surfaceTint = LiquidGlassColors.PrimaryDark
        )
    } else {
        val dynamicLight = if (hasDynamicColor) {
            dynamicLightColorScheme(context)
        } else null
        
        lightColorScheme(
            primary = dynamicLight?.primary ?: LiquidGlassColors.Primary,
            onPrimary = dynamicLight?.onPrimary ?: LiquidGlassColors.OnPrimary,
            primaryContainer = dynamicLight?.primaryContainer ?: LiquidGlassColors.Primary.copy(alpha = 0.15f),
            onPrimaryContainer = LiquidGlassColors.OnPrimary,
            secondary = dynamicLight?.secondary ?: LiquidGlassColors.Secondary,
            onSecondary = dynamicLight?.onSecondary ?: LiquidGlassColors.OnSecondary,
            secondaryContainer = dynamicLight?.secondaryContainer ?: LiquidGlassColors.Secondary.copy(alpha = 0.15f),
            onSecondaryContainer = LiquidGlassColors.OnSecondary,
            tertiary = dynamicLight?.tertiary ?: LiquidGlassColors.Tertiary,
            onTertiary = dynamicLight?.onTertiary ?: LiquidGlassColors.OnTertiary,
            tertiaryContainer = dynamicLight?.tertiaryContainer ?: LiquidGlassColors.Tertiary.copy(alpha = 0.15f),
            onTertiaryContainer = LiquidGlassColors.OnTertiary,
            background = dynamicLight?.background ?: LiquidGlassColors.Background,
            onBackground = dynamicLight?.onBackground ?: LiquidGlassColors.OnBackground,
            surface = dynamicLight?.surface ?: LiquidGlassColors.Surface,
            onSurface = dynamicLight?.onSurface ?: LiquidGlassColors.OnSurface,
            surfaceVariant = dynamicLight?.surfaceVariant ?: LiquidGlassColors.SurfaceVariant,
            onSurfaceVariant = dynamicLight?.onSurfaceVariant ?: LiquidGlassColors.OnSurfaceVariant,
            outline = dynamicLight?.outline ?: LiquidGlassColors.Outline,
            outlineVariant = dynamicLight?.outlineVariant ?: LiquidGlassColors.OutlineVariant,
            error = AppColors.Error,
            onError = Color.White,
            errorContainer = AppColors.Error.copy(alpha = 0.15f),
            onErrorContainer = AppColors.Error,
            inverseSurface = LiquidGlassColors.BackgroundDark,
            inverseOnSurface = LiquidGlassColors.OnBackgroundDark,
            inversePrimary = LiquidGlassColors.PrimaryDark,
            surfaceTint = LiquidGlassColors.Primary
        )
    }
}

private fun createMiuiColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = MiuiColors.Primary,
            onPrimary = MiuiColors.OnPrimary,
            primaryContainer = MiuiColors.Primary.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = MiuiColors.Secondary,
            onSecondary = MiuiColors.OnSecondary,
            tertiary = MiuiColors.Tertiary,
            onTertiary = Color.White,
            background = MiuiColors.BackgroundDark,
            onBackground = MiuiColors.OnBackgroundDark,
            surface = MiuiColors.SurfaceDark,
            onSurface = MiuiColors.OnSurfaceDark,
            surfaceVariant = MiuiColors.SurfaceDark,
            onSurfaceVariant = MiuiColors.OnSurfaceVariant,
            outline = MiuiColors.Outline
        )
    } else {
        lightColorScheme(
            primary = MiuiColors.Primary,
            onPrimary = MiuiColors.OnPrimary,
            primaryContainer = MiuiColors.Primary.copy(alpha = 0.15f),
            onPrimaryContainer = MiuiColors.OnPrimary,
            secondary = MiuiColors.Secondary,
            onSecondary = MiuiColors.OnSecondary,
            tertiary = MiuiColors.Tertiary,
            onTertiary = Color.White,
            background = MiuiColors.Background,
            onBackground = MiuiColors.OnBackground,
            surface = MiuiColors.Surface,
            onSurface = MiuiColors.OnSurface,
            surfaceVariant = MiuiColors.SurfaceVariant,
            onSurfaceVariant = MiuiColors.OnSurfaceVariant,
            outline = MiuiColors.Outline
        )
    }
}

private fun createIosColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = IosColors.Primary,
            onPrimary = IosColors.OnPrimary,
            primaryContainer = IosColors.Primary.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = IosColors.Secondary,
            onSecondary = IosColors.OnSecondary,
            tertiary = IosColors.Tertiary,
            onTertiary = Color.White,
            quaternary = IosColors.Quaternary,
            background = IosColors.BackgroundDark,
            onBackground = IosColors.OnBackgroundDark,
            surface = IosColors.SurfaceDark,
            onSurface = IosColors.OnSurfaceDark,
            surfaceVariant = IosColors.SurfaceVariantDark,
            onSurfaceVariant = IosColors.OnSurfaceVariant,
            outline = IosColors.Outline,
            outlineVariant = IosColors.Outline.copy(alpha = 0.3f)
        )
    } else {
        lightColorScheme(
            primary = IosColors.Primary,
            onPrimary = IosColors.OnPrimary,
            primaryContainer = IosColors.Primary.copy(alpha = 0.15f),
            onPrimaryContainer = IosColors.OnPrimary,
            secondary = IosColors.Secondary,
            onSecondary = IosColors.OnSecondary,
            tertiary = IosColors.Tertiary,
            onTertiary = Color.White,
            quaternary = IosColors.Quaternary,
            background = IosColors.Background,
            onBackground = IosColors.OnBackground,
            surface = IosColors.Surface,
            onSurface = IosColors.OnSurface,
            surfaceVariant = IosColors.SurfaceVariant,
            onSurfaceVariant = IosColors.OnSurfaceVariant,
            outline = IosColors.Outline,
            outlineVariant = IosColors.Outline.copy(alpha = 0.5f)
        )
    }
}

// =============================================================================
// 主主题
// =============================================================================
@Composable
fun AndroidClawTheme(
    theme: AppTheme = AppTheme.LIQUID_GLASS,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 启用动态配色 (Android 12+)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentTheme = ThemeManager.getTheme()
    
    val colorScheme = when (currentTheme) {
        AppTheme.LIQUID_GLASS -> createLiquidGlassColorScheme(darkTheme, dynamicColor, context)
        AppTheme.MIUI -> createMiuiColorScheme(darkTheme)
        AppTheme.IOS -> createIosColorScheme(darkTheme)
    }
    
    // 动画颜色过渡
    val animatedColorScheme = colorScheme.copy(
        primary = animateColorAsState(
            targetValue = colorScheme.primary,
            animationSpec = tween(300),
            label = "primary"
        ).value,
        background = animateColorAsState(
            targetValue = colorScheme.background,
            animationSpec = tween(300),
            label = "background"
        ).value,
        surface = animateColorAsState(
            targetValue = colorScheme.surface,
            animationSpec = tween(300),
            label = "surface"
        ).value
    )
    
    // 设置状态栏
    val view = android.app.ActivityLocalStorage.current
    if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val window = (view as? android.app.Activity)?.window
        window?.let {
            val flags = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (!darkTheme) {
                it.decorView.systemUiVisibility = it.decorView.systemUiVisibility or flags
            } else {
                it.decorView.systemUiVisibility = it.decorView.systemUiVisibility and flags.inv()
            }
        }
    }
    
    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = AppTypography,
        content = content
    )
}

// 状态栏配置扩展
private fun configureSystemBars(darkTheme: Boolean) {
    // 状态栏颜色将在布局中通过 WindowInsets 处理
}
