// ModelManagementScreen.kt
// 模型管理界面

package com.androidclaw.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidclaw.app.llm.ModelDownloader
import com.androidclaw.app.llm.model.DownloadState
import com.androidclaw.app.llm.model.HardwareCapability
import com.androidclaw.app.llm.model.HardwareDetector
import com.androidclaw.app.llm.model.InferenceEngine
import com.androidclaw.app.llm.model.ModelConfig
import com.androidclaw.app.llm.model.ModelSize
import com.androidclaw.app.llm.model.ModelType
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * 模型目录
 * 提供推荐的本地模型列表
 */
object ModelCatalog {
    val models = listOf(
        ModelConfig(
            modelId = "gemma2-2b-it",
            modelName = "Gemma 2 2B Instruct",
            modelType = ModelType.LLM,
            modelSize = ModelSize.SMALL,
            parameterCount = "2B",
            downloadUrl = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it.bin",
            fileName = "gemma-2-2b-it.bin",
            fileSizeInBytes = 1_500_000_000L,
            md5Checksum = "00000000000000000000000000000000",
            preferredEngine = InferenceEngine.MEDIAPIPE,
            minRAMRequired = 3L * 1024 * 1024 * 1024,
            supportsGPU = true,
            supportsNPU = true
        ),
        ModelConfig(
            modelId = "gemma2-4b-it",
            modelName = "Gemma 2 4B Instruct",
            modelType = ModelType.LLM,
            modelSize = ModelSize.MEDIUM,
            parameterCount = "4B",
            downloadUrl = "https://huggingface.co/google/gemma-2-4b-it/resolve/main/gemma-2-4b-it.bin",
            fileName = "gemma-2-4b-it.bin",
            fileSizeInBytes = 3_000_000_000L,
            md5Checksum = "00000000000000000000000000000000",
            preferredEngine = InferenceEngine.MEDIAPIPE,
            minRAMRequired = 6L * 1024 * 1024 * 1024,
            supportsGPU = true,
            supportsNPU = true
        ),
        ModelConfig(
            modelId = "phi3-mini",
            modelName = "Phi-3 Mini",
            modelType = ModelType.LLM,
            modelSize = ModelSize.SMALL,
            parameterCount = "3.8B",
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/resolve/main/phi3-mini.bin",
            fileName = "phi3-mini.bin",
            fileSizeInBytes = 2_200_000_000L,
            md5Checksum = "00000000000000000000000000000000",
            preferredEngine = InferenceEngine.MLC_LLM,
            minRAMRequired = 4L * 1024 * 1024 * 1024,
            supportsGPU = true,
            supportsNPU = false
        ),
        ModelConfig(
            modelId = "qwen2-0.5b",
            modelName = "Qwen2 0.5B Instruct",
            modelType = ModelType.LLM,
            modelSize = ModelSize.SMALL,
            parameterCount = "0.5B",
            downloadUrl = "https://huggingface.co/qwen/Qwen2-0.5B-Instruct/resolve/main/qwen2-0.5b-instruct.bin",
            fileName = "qwen2-0.5b-instruct.bin",
            fileSizeInBytes = 400_000_000L,
            md5Checksum = "00000000000000000000000000000000",
            preferredEngine = InferenceEngine.LITERT,
            minRAMRequired = 2L * 1024 * 1024 * 1024,
            supportsGPU = false,
            supportsNPU = false
        )
    )
}

/**
 * 模型管理界面
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { detectHardware() }) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "检测硬件"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HardwareInfoCard(
                    hardware = hardware,
                    isDetecting = isDetectingHardware,
                    onDetect = { detectHardware() }
                )
            }

            item {
                Text(
                    text = "可用模型",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(ModelCatalog.models, key = { it.modelId }) { model ->
                val downloadState = downloadStates[model.modelId]
                val isDownloaded = downloadedModels.contains(model.modelId)

                ModelCard(
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HardwareInfoCard(
    hardware: HardwareCapability?,
    isDetecting: Boolean,
    onDetect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "硬件信息",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hardware == null) {
                Text(
                    text = "点击右上角按钮检测硬件能力，获取模型推荐",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HardwareDetailRow(
                    label = "GPU",
                    value = if (hardware.hasGPU) "可用 (${hardware.gpuRenderer})" else "不可用"
                )
                HardwareDetailRow(
                    label = "NPU",
                    value = if (hardware.hasNPU) "可用" else "不可用"
                )
                HardwareDetailRow(label = "总内存", value = formatBytes(hardware.totalRAM))
                HardwareDetailRow(label = "可用内存", value = formatBytes(hardware.availableRAM))
                HardwareDetailRow(label = "Android 版本", value = "API ${hardware.androidVersion}")
                HardwareDetailRow(label = "CPU 架构", value = hardware.abi)

                val recommendedSize = hardware.getRecommendedModelSize()
                Spacer(modifier = Modifier.height(12.dp))
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = "推荐模型大小: ${recommendedSize.name}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            AnimatedVisibility(
                visible = isDetecting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HardwareDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelConfig,
    hardware: HardwareCapability?,
    isDownloaded: Boolean,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val isRecommended = hardware?.getRecommendedModelSize() == model.modelSize
    val canRun = hardware?.canRunModel(model.fileSizeInBytes) ?: true

    val cardColor = if (isRecommended) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.parameterCount} • ${model.preferredEngine.name} • ${formatBytes(model.fileSizeInBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isRecommended) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "推荐",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = !canRun,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = "⚠ 设备内存可能不足以运行此模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = downloadState != null,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "下载中...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${(downloadState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${formatBytes(downloadState.downloadedBytes)} / ${formatBytes(downloadState.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is DownloadState.Failed -> {
                        Text(
                            text = "下载失败: ${downloadState.error}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is DownloadState.Completed -> {
                        Text(
                            text = "已下载完成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDownloaded) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = downloadState !is DownloadState.Downloading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (downloadState is DownloadState.Downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (downloadState is DownloadState.Downloading) "下载中" else "下载")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.0")
    return df.format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
