// ModelManagementScreen.kt
// 模型管理界面 - 现代化 UI 设计

package com.androidclaw.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidclaw.app.llm.ModelDownloader
import com.androidclaw.app.llm.model.DownloadState
import com.androidclaw.app.llm.model.HardwareCapability
import com.androidclaw.app.llm.model.HardwareDetector
import com.androidclaw.app.llm.model.InferenceEngine
import com.androidclaw.app.llm.model.ModelCatalog
import com.androidclaw.app.llm.model.ModelConfig
import com.androidclaw.app.llm.model.ModelSize
import com.androidclaw.app.llm.model.ModelType
import com.androidclaw.app.ui.components.*
import com.androidclaw.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * 模型管理界面 - 现代化设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val modelDownloader = remember { ModelDownloader.getInstance(context) }
    
    var hardware by remember { mutableStateOf<HardwareCapability?>(null) }
    var isDetectingHardware by remember { mutableStateOf(false) }
    var downloadStates by remember { mutableStateOf(mapOf<String, DownloadState>()) }
    var downloadedModels by remember { mutableStateOf(setOf<String>()) }
    var selectedFilter by remember { mutableStateOf("全部") }
    
    val filters = listOf("全部", "已下载", "未下载", "按大小")
    
    fun refreshDownloadedModels() {
        downloadedModels = ModelCatalog.models
            .filter { modelDownloader.getDownloadedModel(it) != null }
            .map { it.modelId }
            .toSet()
    }
    
    fun detectHardware() {
        if (isDetectingHardware) return
        isDetectingHardware = true
        coroutineScope.launch {
            hardware = HardwareDetector.detect(context)
            isDetectingHardware = false
        }
    }
    
    LaunchedEffect(Unit) {
        refreshDownloadedModels()
    }
    
    // 过滤后的模型列表
    val filteredModels = remember(selectedFilter, downloadedModels) {
        when (selectedFilter) {
            "已下载" -> ModelCatalog.models.filter { downloadedModels.contains(it.modelId) }
            "未下载" -> ModelCatalog.models.filter { !downloadedModels.contains(it.modelId) }
            "按大小" -> ModelCatalog.models.sortedBy { it.fileSizeInBytes }
            else -> ModelCatalog.models
        }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassAppBar(
                title = "模型管理",
                subtitle = "选择适合您设备的 AI 模型",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { detectHardware() }) {
                        if (isDetectingHardware) {
                            RotatingLoader(size = 24.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 硬件信息卡片
            item {
                ModernHardwareCard(
                    hardware = hardware,
                    isDetecting = isDetectingHardware,
                    onDetect = { detectHardware() }
                )
            }
            
            // 筛选栏
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        GlassChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) }
                        )
                    }
                }
            }
            
            // 模型列表标题
            item {
                Text(
                    text = "可用模型 (${filteredModels.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 模型列表
            items(filteredModels, key = { it.modelId }) { model ->
                val downloadState = downloadStates[model.modelId]
                val isDownloaded = downloadedModels.contains(model.modelId)
                
                ModernModelCard(
                    model = model,
                    hardware = hardware,
                    isDownloaded = isDownloaded,
                    downloadState = downloadState,
                    onDownload = {
                        coroutineScope.launch {
                            modelDownloader.downloadModel(model).collect { state ->
                                downloadStates = downloadStates.toMutableMap().apply {
                                    put(model.modelId, state)
                                }
                                if (state is DownloadState.Completed) {
                                    refreshDownloadedModels()
                                }
                            }
                        }
                    },
                    onDelete = {
                        modelDownloader.deleteModel(model)
                        refreshDownloadedModels()
                    }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 现代硬件信息卡片
 */
@Composable
private fun ModernHardwareCard(
    hardware: HardwareCapability?,
    isDetecting: Boolean,
    onDetect: () -> Unit
) {
    GradientGlassCard(
        modifier = Modifier.fillMaxWidth(),
        gradientColors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        ),
        cornerRadius = 20.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "硬件检测",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isDetecting && hardware == null) {
                    GradientButton(
                        onClick = onDetect,
                        gradientColors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.2f)),
                        cornerRadius = 20.dp
                    ) {
                        Text(
                            text = "检测",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            if (hardware != null) {
                // 硬件信息网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HardwareInfoItem(
                        icon = Icons.Default.Speed,
                        label = "GPU",
                        value = if (hardware.hasGPU) "可用" else "不可用",
                        isHighlight = hardware.hasGPU
                    )
                    HardwareInfoItem(
                        icon = Icons.Default.Memory,
                        label = "NPU",
                        value = if (hardware.hasNPU) "可用" else "不可用",
                        isHighlight = hardware.hasNPU
                    )
                    HardwareInfoItem(
                        icon = Icons.Default.Storage,
                        label = "内存",
                        value = formatBytes(hardware.totalRAM),
                        isHighlight = false
                    )
                }
                
                // 推荐模型
                val recommendedSize = hardware.getRecommendedModelSize()
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "推荐模型大小: ${recommendedSize.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (isDetecting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientLoader(size = 32.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "正在检测硬件...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    isHighlight: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlight) Color(0xFF34C759) else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 现代模型卡片
 */
@Composable
private fun ModernModelCard(
    model: ModelConfig,
    hardware: HardwareCapability?,
    isDownloaded: Boolean,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val isRecommended = hardware?.getRecommendedModelSize() == model.modelSize
    val canRun = hardware?.canRunModel(model.fileSizeInBytes) ?: true
    
    val infiniteTransition = rememberInfiniteTransition(label = "download_pulse")
    val progressScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress_pulse"
    )
    
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        onClick = { /* 展开详情 */ }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 模型图标
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = getModelGradientColors(model.modelType)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getModelIcon(model.modelType),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = model.modelName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isRecommended) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "推荐",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${model.parameterCount} · ${formatBytes(model.fileSizeInBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 状态标签
                if (isDownloaded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF34C759).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "已下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // 标签
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 引擎标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = model.preferredEngine.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // 类型标签
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = model.modelType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // GPU/NPU 支持
                if (model.supportsGPU) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "GPU",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // 警告信息
            AnimatedVisibility(
                visible = !canRun,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "设备内存可能不足以运行此模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 下载进度
            AnimatedVisibility(
                visible = downloadState != null && downloadState is DownloadState.Downloading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "下载中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${((downloadState as? DownloadState.Downloading)?.progress ?: 0f * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    AnimatedProgressBar(
                        progress = (downloadState as? DownloadState.Downloading)?.progress ?: 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(Modifier.scale(progressScale)),
                        height = 6.dp,
                        gradientColors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                    
                    Text(
                        text = "${formatBytes((downloadState as? DownloadState.Downloading)?.downloadedBytes ?: 0)} / ${formatBytes((downloadState as? DownloadState.Downloading)?.totalBytes ?: model.fileSizeInBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 失败提示
            AnimatedVisibility(
                visible = downloadState is DownloadState.Failed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = "下载失败: ${(downloadState as? DownloadState.Failed)?.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDownloaded) {
                    GradientButton(
                        onClick = onDelete,
                        gradientColors = listOf(
                            MaterialTheme.colorScheme.error,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.weight(1f),
                        cornerRadius = 12.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "删除",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    GradientButton(
                        onClick = onDownload,
                        enabled = downloadState !is DownloadState.Downloading,
                        modifier = Modifier.weight(1f),
                        cornerRadius = 12.dp
                    ) {
                        if (downloadState is DownloadState.Downloading) {
                            RotatingLoader(size = 18.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "下载",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun getModelIcon(modelType: ModelType): ImageVector = when (modelType) {
    ModelType.LLM -> Icons.Default.Psychology
    ModelType.VLM -> Icons.Default.Visibility
    ModelType.EMBEDDING -> Icons.Default.Fingerprint
    ModelType.VISION -> Icons.Default.Visibility
    ModelType.SPEECH -> Icons.Default.RecordVoiceOver
    ModelType.OTHER -> Icons.Default.Extension
}

private fun getModelGradientColors(modelType: ModelType): List<Color> = when (modelType) {
    ModelType.LLM -> listOf(Color(0xFF6C63FF), Color(0xFF8B85FF))
    ModelType.VLM -> listOf(Color(0xFFFF6B6B), Color(0xFFFF8585))
    ModelType.EMBEDDING -> listOf(Color(0xFF4ECDC4), Color(0xFF44A08D))
    ModelType.VISION -> listOf(Color(0xFFFF6B6B), Color(0xFFFF8585))
    ModelType.SPEECH -> listOf(Color(0xFFFF9500), Color(0xFFFFAB76))
    ModelType.OTHER -> listOf(Color(0xFF8E8E93), Color(0xFF636366))
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.0")
    return df.format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
