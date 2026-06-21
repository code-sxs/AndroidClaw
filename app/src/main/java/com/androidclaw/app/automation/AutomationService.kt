// AutomationService.kt
// 跨应用自动化服务 - 基于 AccessibilityService
// 让 AI Agent 能操作任何 App

package com.androidclaw.app.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.androidclaw.app.MainActivity
import com.androidclaw.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * 跨应用自动化服务
 * 
 * 功能：
 * - 监听窗口状态变化
 * - 读取界面元素（AccessibilityNodeInfo 树）
 * - 执行操作（点击、输入、滑动等）
 * - 截图能力（MediaProjection）
 * - 通知栏常驻，随时可停止
 */
class AutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "AutomationService"
        private const val NOTIFICATION_CHANNEL_ID = "automation_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_STEPS_PER_SESSION = 50  // 防止失控

        // 单例引用
        private var instanceRef: WeakReference<AutomationService>? = null

        fun getInstance(): AutomationService? = instanceRef?.get()

        // 状态流
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _currentActivity = MutableStateFlow("")
        val currentActivity: StateFlow<String> = _currentActivity

        private val _stepCount = MutableStateFlow(0)
        val stepCount: StateFlow<Int> = _stepCount
    }

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // 截图相关
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var screenCapture: ScreenCapture? = null

    // 当前根节点
    private var rootNodeRef: WeakReference<AccessibilityNodeInfo>? = null

    // 状态
    private var isAutomationActive = false
    private var shouldStop = false

    // 回调
    private var onWindowChanged: ((String) -> Unit)? = null
    private var onScreenContentChanged: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AutomationService created")
        instanceRef = WeakReference(this)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AutomationService connected")
        _isRunning.value = true
        createNotificationChannel()
        showNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口状态变化（切换 Activity/应用）
                val className = event.className?.toString() ?: ""
                val packageName = event.packageName?.toString() ?: ""
                val activityName = if (className.isNotEmpty()) {
                    "$packageName/$className"
                } else {
                    packageName
                }

                _currentActivity.value = activityName
                Log.d(TAG, "Window changed: $activityName")

                // 通知监听器
                onWindowChanged?.invoke(activityName)

                // 如果自动化正在运行，检查是否需要停止
                if (isAutomationActive) {
                    serviceScope.launch {
                        onScreenContentChanged?.invoke()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 窗口内容变化
                if (isAutomationActive) {
                    serviceScope.launch {
                        onScreenContentChanged?.invoke()
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(TAG, "View clicked: ${event.source?.let { getNodeDescription(it) }}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutomationService interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "AutomationService destroyed")
        _isRunning.value = false
        instanceRef?.clear()
        instanceRef = null
        stopForeground(true)
        mediaProjection?.stop()
        mediaProjection = null
        screenCapture?.release()
        screenCapture = null
        super.onDestroy()
    }

    /**
     * 获取当前界面的根节点
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (root != null) {
            rootNodeRef = WeakReference(root)
        }
        return root
    }

    /**
     * 执行点击操作
     * @param node 要点击的节点
     * @return 是否成功
     */
    suspend fun clickNode(node: AccessibilityNodeInfo): Boolean = withContext(Dispatchers.IO) {
        checkStepLimit()
        
        if (!isNodeClickable(node)) {
            // 如果节点本身不可点击，尝试找父节点
            var parent = node.parent
            while (parent != null) {
                if (isNodeClickable(parent)) {
                    val result = performClick(parent)
                    parent.recycle()
                    return@withContext result
                }
                val temp = parent
                parent = parent.parent
                temp.recycle()
            }
            Log.w(TAG, "Node not clickable and no clickable parent found")
            return@withContext false
        }

        performClick(node)
    }

    /**
     * 执行点击（内部方法）
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (result) {
                _stepCount.value += 1
                Log.d(TAG, "Click succeeded: ${getNodeDescription(node)}")
            } else {
                Log.w(TAG, "Click failed: ${getNodeDescription(node)}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Click exception", e)
            false
        }
    }

    /**
     * 通过坐标点击
     */
    suspend fun clickAt(x: Int, y: Int): Boolean = withContext(Dispatchers.Main) {
        checkStepLimit()

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val result = dispatchGesture(gesture, null, null)

        if (result) {
            _stepCount.value += 1
            Log.d(TAG, "Click at ($x, $y) succeeded")
        } else {
            Log.w(TAG, "Click at ($x, $y) failed")
        }

        result
    }

    /**
     * 输入文本
     */
    suspend fun inputText(node: AccessibilityNodeInfo, text: String): Boolean = withContext(Dispatchers.IO) {
        checkStepLimit()

        // 先清空现有文本
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundleOf(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE to ""
        ))

        delay(100)

        // 输入新文本
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundleOf(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE to text
        ))

        if (result) {
            _stepCount.value += 1
            Log.d(TAG, "Input text succeeded: $text")
        } else {
            Log.w(TAG, "Input text failed")
        }

        result
    }

    /**
     * 滑动屏幕
     */
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long = 300): Boolean =
        withContext(Dispatchers.Main) {
            checkStepLimit()

            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            val result = dispatchGesture(gesture, null, null)

            if (result) {
                _stepCount.value += 1
                Log.d(TAG, "Swipe from ($startX, $startY) to ($endX, $endY) succeeded")
            } else {
                Log.w(TAG, "Swipe failed")
            }

            result
        }

    /**
     * 滚动节点
     */
    suspend fun scrollNode(node: AccessibilityNodeInfo, direction: ScrollDirection): Boolean =
        withContext(Dispatchers.IO) {
            checkStepLimit()

            val action = when (direction) {
                ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }

            val result = node.performAction(action)

            if (result) {
                _stepCount.value += 1
                Log.d(TAG, "Scroll $direction succeeded")
            } else {
                Log.w(TAG, "Scroll $direction failed")
            }

            result
        }

    /**
     * 返回
     */
    fun goBack(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        if (result) {
            _stepCount.value += 1
            Log.d(TAG, "Back succeeded")
        }
        return result
    }

    /**
     * 回到桌面
     */
    fun goHome(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        if (result) {
            _stepCount.value += 1
            Log.d(TAG, "Home succeeded")
        }
        return result
    }

    /**
     * 打开最近任务
     */
    fun openRecents(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        Log.d(TAG, "Recents: $result")
        return result
    }

    /**
     * 查找节点
     */
    fun findNodeByText(text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { node ->
            if (exact) {
                node.text?.toString() == text
            } else {
                node.text?.toString()?.contains(text, ignoreCase = true) == true
            }
        }
    }

    /**
     * 查找节点（通过 viewId）
     */
    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
    }

    /**
     * 查找节点（通过描述）
     */
    fun findNodeByDescription(description: String): AccessibilityNodeInfo? {
        val root = getRootNode() ?: return null
        return root.findAccessibilityNodeInfosByText(description).firstOrNull { node ->
            node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true
        }
    }

    /**
     * 初始化 MediaProjection（需要 Activity 传入结果）
     */
    fun initMediaProjection(resultCode: Int, data: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
            Log.i(TAG, "MediaProjection initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init MediaProjection", e)
        }
    }

    /**
     * 截图
     */
    suspend fun takeScreenshot(): Bitmap? = withContext(Dispatchers.IO) {
        val projection = mediaProjection
        if (projection == null) {
            Log.w(TAG, "MediaProjection not initialized")
            return@withContext null
        }

        if (screenCapture == null) {
            screenCapture = ScreenCapture(projection, this@AutomationService)
        }

        screenCapture?.capture()
    }

    /**
     * 开始自动化
     */
    fun startAutomation() {
        Log.i(TAG, "Automation started")
        isAutomationActive = true
        shouldStop = false
        _stepCount.value = 0
        updateNotification("运行中")
    }

    /**
     * 停止自动化
     */
    fun stopAutomation() {
        Log.i(TAG, "Automation stopped")
        isAutomationActive = false
        shouldStop = true
        updateNotification("已停止")
    }

    /**
     * 设置窗口变化监听器
     */
    fun setWindowChangedListener(listener: (String) -> Unit) {
        onWindowChanged = listener
    }

    /**
     * 设置内容变化监听器
     */
    fun setContentChangedListener(listener: () -> Unit) {
        onScreenContentChanged = listener
    }

    /**
     * 检查步骤限制
     */
    private fun checkStepLimit() {
        if (_stepCount.value >= MAX_STEPS_PER_SESSION) {
            Log.w(TAG, "Step limit reached: $MAX_STEPS_PER_SESSION")
            stopAutomation()
            throw AutomationException("步骤上限已达，已自动停止")
        }

        if (shouldStop) {
            throw AutomationException("用户已停止自动化")
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "自动化服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AndroidClaw 跨应用自动化服务"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示通知
     */
    private fun showNotification() {
        val stopIntent = Intent(this, AutomationService::class.java).apply {
            action = "STOP_AUTOMATION"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AndroidClaw 自动化")
            .setContentText("就绪")
            .setSmallIcon(R.drawable.ic_automation)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_stop, "停止", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AndroidClaw 自动化")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_automation)
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 判断节点是否可点击
     */
    private fun isNodeClickable(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isEnabled && (
            node.className?.toString()?.contains("Button", ignoreCase = true) == true ||
            node.className?.toString()?.contains("ImageButton", ignoreCase = true) == true
        )
    }

    /**
     * 获取节点描述（用于日志）
     */
    private fun getNodeDescription(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.take(50) ?: ""
        val desc = node.contentDescription?.toString()?.take(30) ?: ""
        val className = node.className?.toString()?.substringAfterLast('.') ?: ""
        return "$className: text='$text' desc='$desc'"
    }

    /**
     * 创建 Bundle（辅助方法）
     */
    private fun bundleOf(vararg pairs: Pair<String, Any?>): android.os.Bundle {
        return android.os.Bundle().apply {
            for ((key, value) in pairs) {
                when (value) {
                    is String -> putCharSequence(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
    }
}

/**
 * 滚动方向
 */
enum class ScrollDirection {
    FORWARD, BACKWARD
}

/**
 * 自动化异常
 */
class AutomationException(message: String) : Exception(message)
