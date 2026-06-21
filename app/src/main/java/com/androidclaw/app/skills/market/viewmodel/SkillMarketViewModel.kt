// SkillMarketViewModel.kt
// Skill 市场 - ViewModel
// 管理 UI 状态，连接 Repository 和 UI 层

package com.androidclaw.app.skills.market.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.app.skills.market.InstallResult
import com.androidclaw.app.skills.market.InstallState
import com.androidclaw.app.skills.market.MarketSearchResult
import com.androidclaw.app.skills.market.MarketSkill
import com.androidclaw.app.skills.market.MarketSource
import com.androidclaw.app.skills.market.db.InstalledSkillEntity
import com.androidclaw.app.skills.market.repository.MarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Skill 市场 ViewModel
 * 管理 UI 状态和业务逻辑
 */
class SkillMarketViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MarketRepository.getInstance(application)

    // ===== 搜索状态 =====
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResult = MutableStateFlow<MarketSearchResult?>(null)
    val searchResult: StateFlow<MarketSearchResult?> = _searchResult.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _currentPage = MutableStateFlow(0)

    // ===== 市场源状态 =====
    private val _selectedMarketId = MutableStateFlow<String?>(null)
    val selectedMarketId: StateFlow<String?> = _selectedMarketId.asStateFlow()

    val marketSources: StateFlow<List<MarketSource>> = MutableStateFlow(repository.getMarketSources()).asStateFlow()

    // ===== Skill 详情状态 =====
    private val _selectedSkill = MutableStateFlow<MarketSkill?>(null)
    val selectedSkill: StateFlow<MarketSkill?> = _selectedSkill.asStateFlow()

    private val _skillDetail = MutableStateFlow<MarketSkill?>(null)
    val skillDetail: StateFlow<MarketSkill?> = _skillDetail.asStateFlow()

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail.asStateFlow()

    // ===== 安装状态 =====
    private val _installingSkillId = MutableStateFlow<String?>(null)
    val installingSkillId: StateFlow<String?> = _installingSkillId.asStateFlow()

    private val _installProgress = MutableStateFlow(0)
    val installProgress: StateFlow<Int> = _installProgress.asStateFlow()

    private val _installState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _installMessage = MutableStateFlow<String?>(null)
    val installMessage: StateFlow<String?> = _installMessage.asStateFlow()

    // ===== 已安装 Skill =====
    val installedSkills: StateFlow<List<InstalledSkillEntity>> = repository
        .getInstalledSkillsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedSkillNames = MutableStateFlow<Set<String>>(emptySet())
    val installedSkillNames: StateFlow<Set<String>> = _installedSkillNames.asStateFlow()

    // ===== 错误状态 =====
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 加载分类
        loadCategories()
        // 加载已安装列表
        refreshInstalledSkills()
        // 默认搜索
        searchSkills()
    }

    /**
     * 搜索 Skill
     */
    fun searchSkills(query: String? = null, refresh: Boolean = false) {
        val searchQuery = query ?: _searchQuery.value
        _searchQuery.value = searchQuery

        if (refresh) {
            _currentPage.value = 0
        }

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val result = repository.searchSkills(
                    query = searchQuery,
                    category = _selectedCategory.value,
                    marketId = _selectedMarketId.value,
                    page = _currentPage.value
                )
                _searchResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 加载更多（下一页）
     */
    fun loadMore() {
        val current = _searchResult.value ?: return
        if (!current.hasMore) return

        _currentPage.value += 1
        searchSkills()
    }

    /**
     * 选择分类
     */
    fun selectCategory(category: String?) {
        _selectedCategory.value = category
        _currentPage.value = 0
        searchSkills()
    }

    /**
     * 选择市场源
     */
    fun selectMarket(marketId: String?) {
        _selectedMarketId.value = marketId
        _currentPage.value = 0
        searchSkills()
    }

    /**
     * 查看 Skill 详情
     */
    fun showSkillDetail(skill: MarketSkill) {
        _selectedSkill.value = skill
        _skillDetail.value = skill  // 先用列表中的数据

        viewModelScope.launch {
            _isLoadingDetail.value = true
            try {
                val detail = repository.getSkillDetail(skill.marketId, skill.skillId)
                if (detail != null) {
                    _skillDetail.value = detail
                }
            } catch (e: Exception) {
                // 使用列表中的基础信息即可
            } finally {
                _isLoadingDetail.value = false
            }
        }
    }

    /**
     * 关闭 Skill 详情
     */
    fun closeSkillDetail() {
        _selectedSkill.value = null
        _skillDetail.value = null
    }

    /**
     * 安装 Skill
     */
    fun installSkill(skill: MarketSkill) {
        _installingSkillId.value = skill.skillId
        _installProgress.value = 0
        _installState.value = InstallState.DOWNLOADING
        _installMessage.value = null

        viewModelScope.launch {
            when (val result = repository.installSkill(skill)) {
                is InstallResult.Success -> {
                    _installState.value = InstallState.INSTALLED
                    _installMessage.value = "${result.skillName} 安装成功"
                    refreshInstalledSkills()
                }
                is InstallResult.AlreadyInstalled -> {
                    _installState.value = InstallState.INSTALLED
                    _installMessage.value = "${result.skillName} 已安装"
                }
                is InstallResult.Error -> {
                    _installState.value = InstallState.ERROR
                    _installMessage.value = result.message
                }
            }
            _installingSkillId.value = null
        }
    }

    /**
     * 卸载 Skill
     */
    fun uninstallSkill(skillName: String) {
        viewModelScope.launch {
            val success = repository.uninstallSkill(skillName)
            if (success) {
                refreshInstalledSkills()
            } else {
                _errorMessage.value = "卸载失败: $skillName"
            }
        }
    }

    /**
     * 刷新已安装 Skill 列表
     */
    private fun refreshInstalledSkills() {
        viewModelScope.launch {
            try {
                val installed = repository.getInstalledSkills()
                _installedSkillNames.value = installed.map { it.skillName }.toSet()
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 清除安装消息
     */
    fun clearInstallMessage() {
        _installMessage.value = null
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val cats = repository.getCategories(_selectedMarketId.value)
                _categories.value = cats
            } catch (e: Exception) {
                // 使用默认分类
                _categories.value = listOf("全部", "生产力", "开发", "工具", "娱乐", "教育", "社交")
            }
        }
    }
}
