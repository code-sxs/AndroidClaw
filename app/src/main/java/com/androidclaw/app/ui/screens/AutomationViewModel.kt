// AutomationViewModel.kt
// 自动化界面 ViewModel

package com.androidclaw.app.ui.screens

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.app.automation.AutomationService
import com.androidclaw.app.automation.UiParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 自动化 ViewModel
 */
class AutomationViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AutomationViewModel"
    }

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        // 监听服务状态
        viewModelScope.launch {
            AutomationService.isRunning.collect { isRunning ->
                _uiState.value = _uiState.value.copy(
                    isServiceRunning = isRunning
                )
            }
        }

        // 监听当前 Activity
        viewModelScope.launch {
            AutomationService.currentActivity.collect { activity ->
                log("页面: $activity")
            }
        }

        // 加载已安装应用
        loadInstalledApps()
    }

    /**
     * 加载已安装应用
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val pm = getApplication<Application>().packageManager
                val packages = pm.getInstalledApplications(0)
                    .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map {
                        AppInfo(
                            name = pm.getApplicationLabel(it).toString(),
                            packageName = it.packageName
                        )
                    }
                    .sortedBy { it.name }

                _uiState.value = _uiState.value.copy(
                    installedApps = packages
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load installed apps", e)
            }
        }
    }

    /**
     * 选择目标应用
     */
    fun selectTargetApp(app: AppInfo) {
        _uiState.value = _uiState.value.copy(
            targetApp = app.name
        )
        log("选择应用: ${app.name}")

        // 启动应用
        viewModelScope.launch {
            try {
                val intent = getApplication<Application>().packageManager
                    .getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    getApplication<Application>().startActivity(intent)
                    log("启动 ${app.name} 成功")
                }
            } catch (e: Exception) {
                log("启动失败: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    /**
     * 开始执行
     */
    fun startExecution() {
        _uiState.value = _uiState.value.copy(
            isExecuting = true,
            currentStep = 0,
            totalSteps = 0
        )
        log("开始执行")

        // TODO: 实现实际执行逻辑
    }

    /**
     * 停止执行
     */
    fun stopExecution() {
        val service = AutomationService.getInstance()
        service?.stopAutomation()

        _uiState.value = _uiState.value.copy(
            isExecuting = false
        )
        log("已停止")
    }

    /**
     * 读取屏幕内容
     */
    fun readScreen() {
        viewModelScope.launch {
            val service = AutomationService.getInstance()
            if (service == null) {
                log("服务未启动", LogLevel.ERROR)
                return@launch
            }

            val root = service.getRootNode()
            if (root == null) {
                log("无法读取屏幕", LogLevel.ERROR)
                return@launch
            }

            try {
                val tree = UiParser.parseNodeTree(root)
                val description = UiParser.generateTextDescription(tree)
                val summary = UiParser.getActionableSummary(tree)

                _uiState.value = _uiState.value.copy(
                    screenContent = summary
                )

                log("读取屏幕成功，${tree.nodes.size} 个节点")
            } catch (e: Exception) {
                log("读取失败: ${e.message}", LogLevel.ERROR)
            } finally {
                root.recycle()
            }
        }
    }

    /**
     * 截图
     */
    fun takeScreenshot() {
        viewModelScope.launch {
            val service = AutomationService.getInstance()
            if (service == null) {
                log("服务未启动", LogLevel.ERROR)
                return@launch
            }

            val bitmap = service.takeScreenshot()
            if (bitmap != null) {
                log("截图成功: ${bitmap.width}x${bitmap.height}")
            } else {
                log("截图失败", LogLevel.ERROR)
            }
        }
    }

    /**
     * 返回
     */
    fun goBack() {
        val service = AutomationService.getInstance()
        if (service != null) {
            val result = service.goBack()
            log(if (result) "返回成功" else "返回失败", if (result) LogLevel.INFO else LogLevel.WARN)
        } else {
            log("服务未启动", LogLevel.ERROR)
        }
    }

    /**
     * 回到桌面
     */
    fun goHome() {
        val service = AutomationService.getInstance()
        if (service != null) {
            val result = service.goHome()
            log(if (result) "回到桌面" else "操作失败", if (result) LogLevel.INFO else LogLevel.WARN)
        } else {
            log("服务未启动", LogLevel.ERROR)
        }
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    /**
     * 显示帮助
     */
    fun showHelp() {
        log("帮助：点击"开启无障碍服务"按钮，在设置中找到 AndroidClaw 并开启")
    }

    /**
     * 关闭引导
     */
    fun dismissGuide() {
        _uiState.value = _uiState.value.copy(showGuideDialog = false)
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 添加日志
     */
    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            time = timeFormat.format(Date()),
            message = message,
            level = level
        )

        val currentLogs = _uiState.value.logs.toMutableList()
        currentLogs.add(entry)

        // 限制日志数量
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }

        _uiState.value = _uiState.value.copy(logs = currentLogs)

        // 同时输出到 Logcat
        when (level) {
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }
}

/**
 * UI 状态
 */
data class AutomationUiState(
    val isServiceRunning: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isExecuting: Boolean = false,
    val showGuideDialog: Boolean = false,
    val targetApp: String? = null,
    val installedApps: List<AppInfo> = emptyList(),
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentAction: String? = null,
    val stepCount: Int = 0,
    val logs: List<LogEntry> = emptyList(),
    val screenContent: String? = null,
    val error: String? = null
)
