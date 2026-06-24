// AutomationViewModel.kt
// Automation ViewModel

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

class AutomationViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AutomationViewModel"
    }

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        viewModelScope.launch {
            AutomationService.isRunning.collect { isRunning ->
                _uiState.value = _uiState.value.copy(isServiceRunning = isRunning)
            }
        }
        viewModelScope.launch {
            AutomationService.currentActivity.collect { activity ->
                log("Page: $activity")
            }
        }
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val pm = getApplication<Application>().packageManager
                val packages = pm.getInstalledApplications(0)
                    .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map { AppInfo(name = pm.getApplicationLabel(it).toString(), packageName = it.packageName) }
                    .sortedBy { it.name }
                _uiState.value = _uiState.value.copy(installedApps = packages)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load installed apps", e)
            }
        }
    }

    fun selectTargetApp(app: AppInfo) {
        _uiState.value = _uiState.value.copy(targetApp = app.name)
        log("Selected app: ${app.name}")
        viewModelScope.launch {
            try {
                val intent = getApplication<Application>().packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    getApplication<Application>().startActivity(intent)
                    log("Started ${app.name}")
                }
            } catch (e: Exception) {
                log("Start failed: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun startExecution() {
        _uiState.value = _uiState.value.copy(isExecuting = true, currentStep = 0, totalSteps = 0)
        log("Execution started")
    }

    fun stopExecution() {
        val service = AutomationService.getInstance()
        service?.stopAutomation()
        _uiState.value = _uiState.value.copy(isExecuting = false)
        log("Stopped")
    }

    fun readScreen() {
        viewModelScope.launch {
            val service = AutomationService.getInstance()
            if (service == null) { log("Service not running", LogLevel.ERROR); return@launch }
            val root = service.getRootNode()
            if (root == null) { log("Cannot read screen", LogLevel.ERROR); return@launch }
            try {
                val tree = UiParser.parseNodeTree(root)
                val summary = UiParser.getActionableSummary(tree)
                _uiState.value = _uiState.value.copy(screenContent = summary)
                log("Screen read OK, ${tree.nodes.size} nodes")
            } catch (e: Exception) {
                log("Read failed: ${e.message}", LogLevel.ERROR)
            } finally {
                root.recycle()
            }
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            val service = AutomationService.getInstance()
            if (service == null) { log("Service not running", LogLevel.ERROR); return@launch }
            val bitmap = service.takeScreenshot()
            if (bitmap != null) { log("Screenshot: ${bitmap.width}x${bitmap.height}") }
            else { log("Screenshot failed", LogLevel.ERROR) }
        }
    }

    fun goBack() {
        val service = AutomationService.getInstance()
        if (service != null) {
            val result = service.goBack()
            log(if (result) "Back OK" else "Back failed", if (result) LogLevel.INFO else LogLevel.WARN)
        } else { log("Service not running", LogLevel.ERROR) }
    }

    fun goHome() {
        val service = AutomationService.getInstance()
        if (service != null) {
            val result = service.goHome()
            log(if (result) "Home OK" else "Operation failed", if (result) LogLevel.INFO else LogLevel.WARN)
        } else { log("Service not running", LogLevel.ERROR) }
    }

    fun clearLogs() { _uiState.value = _uiState.value.copy(logs = emptyList()) }

    fun showHelp() { log("Help: Click Enable Accessibility button, find AndroidClaw in Settings") }

    fun dismissGuide() { _uiState.value = _uiState.value.copy(showGuideDialog = false) }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(time = timeFormat.format(Date()), message = message, level = level)
        val currentLogs = _uiState.value.logs.toMutableList()
        currentLogs.add(entry)
        if (currentLogs.size > 100) { currentLogs.removeAt(0) }
        _uiState.value = _uiState.value.copy(logs = currentLogs)
        when (level) {
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }
}

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
