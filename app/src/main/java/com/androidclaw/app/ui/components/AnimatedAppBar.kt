// AnimatedAppBar.kt
// 动画 AppBar 组件

package com.androidclaw.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 毛玻璃 AppBar
 */
@Composable
fun GlassAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    isOnline: Boolean? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    elevation: Dp = 0.dp
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        shadowElevation = elevation,
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // 标题区域
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // 在线状态指示
                    isOnline?.let { online ->
                        Spacer(modifier = Modifier.width(8.dp))
                        OnlineIndicator(isOnline = online)
                    }
                }
                
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }
    }
}

/**
 * 在线状态指示器
 */
@Composable
fun OnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "online")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "online_alpha"
    )
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isOnline) {
                        Color(0xFF34C759).copy(alpha = alpha)
                    } else {
                        Color(0xFFFF3B30).copy(alpha = 0.7f)
                    }
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isOnline) "在线" else "离线",
            style = MaterialTheme.typography.labelSmall,
            color = if (isOnline) {
                Color(0xFF34C759)
            } else {
                Color(0xFFFF3B30)
            }
        )
    }
}

/**
 * 渐变 AppBar
 */
@Composable
fun GradientAppBar(
    title: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    ),
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(gradientColors)
            )
    ) {
        GlassAppBar(
            title = title,
            showBackButton = showBackButton,
            onBackClick = onBackClick,
            actions = actions,
            backgroundColor = Color.Transparent,
            elevation = 0.dp
        )
    }
}

/**
 * 收起/展开 AppBar（滚动时隐藏/显示）
 */
@Composable
fun CollapsingAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: Any? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    
    // 根据滚动状态动画化展开/收起
    val titleAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(200),
        label = "title_alpha"
    )
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (!isExpanded) 1f else 0f,
        animationSpec = tween(200),
        label = "content_alpha"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        // 主 AppBar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = backgroundColor,
            shadowElevation = if (isExpanded) 4.dp else 0.dp,
            shape = RoundedCornerShape(
                bottomStart = if (isExpanded) 20.dp else 0.dp,
                bottomEnd = if (isExpanded) 20.dp else 0.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 展开内容
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                content = content
            )
        }
    }
}

/**
 * 汉堡菜单按钮（带动画）
 */
@Composable
fun AnimatedHamburgerButton(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val topLineOffset by animateDpAsState(
        targetValue = if (isOpen) 8.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "top_line"
    )
    
    val middleLineAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0f else 1f,
        animationSpec = tween(150),
        label = "middle_alpha"
    )
    
    val bottomLineOffset by animateDpAsState(
        targetValue = if (isOpen) (-8).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottom_line"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            // 上线
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = topLineOffset)
                    .width(24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
            
            // 中线
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = middleLineAlpha))
            )
            
            // 下线
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = bottomLineOffset)
                    .width(24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/**
 * 脉冲动画图标按钮
 */
@Composable
fun PulsingIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_icon")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier.scale(if (isPulsing) scale else 1f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (isPulsing) alpha else 1f)
        )
    }
}

/**
 * 动画 Tab Row
 */
@Composable
fun AnimatedTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit
) {
    val tabWidths = remember { mutableStateListOf<Dp>() }
    
    val indicatorOffset by animateDpAsState(
        targetValue = if (tabs.isNotEmpty() && selectedTabIndex < tabs.size) {
            tabWidths.take(selectedTabIndex).fold(0.dp) { acc, dp -> acc + dp } + tabWidths.getOrElse(selectedTabIndex) { 0.dp } / 2
        } else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "tab_indicator"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 指示器
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidths.getOrElse(selectedTabIndex) { 0.dp })
                .height(3.dp)
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        
        // Tabs
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTabIndex == index
                
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(200),
                    label = "tab_text_color"
                )
                
                TextButton(
                    onClick = { onTabSelected(index) },
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { tabWidths.add(it.width.dp) }
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}


