// SkillMarketScreen.kt
// Skill 市场 - 浏览/搜索 UI
// 支持搜索、分类筛选、市场源切换、Skill 卡片列表、详情页、一键安装

package com.androidclaw.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidclaw.app.skills.market.InstallState
import com.androidclaw.app.skills.market.MarketSkill
import com.androidclaw.app.skills.market.MarketSource
import com.androidclaw.app.skills.market.viewmodel.SkillMarketViewModel

/**
 * Skill 市场浏览/搜索界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillMarketScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SkillMarketViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val marketSources by viewModel.marketSources.collectAsState()
    val selectedMarketId by viewModel.selectedMarketId.collectAsState()
    val selectedSkill by viewModel.selectedSkill.collectAsState()
    val skillDetail by viewModel.skillDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()
    val installingSkillId by viewModel.installingSkillId.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installState by viewModel.installState.collectAsState()
    val installMessage by viewModel.installMessage.collectAsState()
    val installedSkillNames by viewModel.installedSkillNames.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 显示安装消息
    LaunchedEffect(installMessage) {
        installMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInstallMessage()
        }
    }

    // 显示错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill 市场") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.searchSkills(it, refresh = true) },
                isSearching = isSearching
            )

            // 市场源 Tab
            if (marketSources.isNotEmpty()) {
                MarketSourceTabs(
                    sources = marketSources,
                    selectedId = selectedMarketId,
                    onSelect = { viewModel.selectMarket(it) }
                )
            }

            // 分类筛选
            if (categories.isNotEmpty()) {
                CategoryFilterRow(
                    categories = categories,
                    selected = selectedCategory,
                    onSelect = { viewModel.selectCategory(it) }
                )
            }

            // Skill 列表
            val skills = searchResult?.skills ?: emptyList()
            if (skills.isEmpty() && !isSearching) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
                ) {
                    items(skills, key = { "${it.marketId}:${it.skillId}" }) { skill ->
                        MarketSkillCard(
                            skill = skill,
                            isInstalled = installedSkillNames.contains(skill.name),
                            isInstalling = installingSkillId == skill.skillId,
                            installProgress = if (installingSkillId == skill.skillId) installProgress else null,
                            installState = if (installingSkillId == skill.skillId) installState else null,
                            onClick = { viewModel.showSkillDetail(skill) },
                            onInstall = { viewModel.installSkill(skill) }
                        )
                    }

                    // 加载更多
                    if (searchResult?.hasMore == true) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = { viewModel.loadMore() }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Skill 详情底部弹窗
    selectedSkill?.let { skill ->
        SkillDetailSheet(
            skill = skillDetail ?: skill,
            isLoading = isLoadingDetail,
            isInstalled = installedSkillNames.contains(skill.name),
            isInstalling = installingSkillId == skill.skillId,
            installProgress = if (installingSkillId == skill.skillId) installProgress else null,
            installState = if (installingSkillId == skill.skillId) installState else null,
            onInstall = { viewModel.installSkill(skill) },
            onDismiss = { viewModel.closeSkillDetail() }
        )
    }
}

/**
 * 搜索栏
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean
) {
    var text by remember(query) { mutableStateOf(query) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("搜索 Skill...") },
        leadingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onQueryChange("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large
    )
}

/**
 * 市场源 Tab 栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketSourceTabs(
    sources: List<MarketSource>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    val tabs = listOf(null to "全部") + sources.map { it.id to it.name }

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedId }.coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEach { (id, name) ->
            Tab(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                text = { Text(name) }
            )
        }
    }
}

/**
 * 分类筛选行
 */
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // "全部"选项
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") }
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) }
            )
        }
    }
}

/**
 * Skill 卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketSkillCard(
    skill: MarketSkill,
    isInstalled: Boolean,
    isInstalling: Boolean,
    installProgress: Int?,
    installState: InstallState?,
    onClick: () -> Unit,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标 + 名称 + 描述
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = if (isInstalled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = skill.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isInstalled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "已安装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 右侧：安装按钮
                if (isInstalling) {
                    CircularProgressIndicator(
                        progress = { (installProgress ?: 0) / 100f },
                        modifier = Modifier.size(36.dp)
                    )
                } else if (!isInstalled) {
                    IconButton(onClick = onInstall) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "安装",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 安装进度条
            if (isInstalling && installProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { installProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 底部信息行
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${skill.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = skill.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    skill.rating?.let { rating ->
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = formatDownloadCount(skill.downloadCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Skill 详情底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    skill: MarketSkill,
    isLoading: Boolean,
    isInstalled: Boolean,
    isInstalling: Boolean,
    installProgress: Int?,
    installState: InstallState?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "v${skill.version} · ${skill.author}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 描述
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 统计信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "评分", value = skill.rating?.let { String.format("%.1f", it) } ?: "-")
                    StatItem(label = "下载", value = formatDownloadCount(skill.downloadCount))
                    StatItem(label = "大小", value = formatFileSize(skill.fileSize))
                    StatItem(label = "分类", value = skill.category)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 权限列表
                if (skill.permissions.isNotEmpty()) {
                    Text(
                        text = "权限要求",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    skill.permissions.forEach { perm ->
                        Text(
                            text = "• $perm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 工具列表
                if (skill.tools.isNotEmpty()) {
                    Text(
                        text = "提供工具",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    skill.tools.forEach { tool ->
                        Text(
                            text = "• ${tool.name}: ${tool.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 标签
                if (skill.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        skill.tags.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 安装按钮
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isInstalling && !isInstalled,
                    colors = if (isInstalled) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("安装中 ${installProgress ?: 0}%")
                    } else if (isInstalled) {
                        Text("已安装")
                    } else {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("安装")
                    }
                }

                // 安装进度条
                if (isInstalling && installProgress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { installProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "搜索 Skill",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "输入关键词搜索或浏览分类",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 格式化下载量
 */
private fun formatDownloadCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_024 * 1_024 -> String.format("%.1f MB", bytes / (1_024.0 * 1_024.0))
        bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
