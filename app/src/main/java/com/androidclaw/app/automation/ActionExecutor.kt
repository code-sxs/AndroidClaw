// ActionExecutor.kt
// 动作执行器 - 执行各种自动化动作
// 所有动作都有回调通知结果

package com.androidclaw.app.automation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 动作执行器
 * 
 * 功能：
 * - 执行各种自动化动作
 * - 智能元素查找
 * - 重试机制
 * - 超时保护
 * - 结果回调
 */
class ActionExecutor(
    private val context: Context,
    private val service: AutomationService
) {
    companion object {
        private const val TAG = "ActionExecutor"
        private const val DEFAULT_TIMEOUT_MS = 10000L
        private const val DEFAULT_RETRY_COUNT = 3
        private const val DEFAULT_RETRY_DELAY_MS = 500L
    }

    /**
     * 执行动作
     */
    suspend fun execute(action: AutomationAction): ActionResult {
        Log.i(TAG, "Executing action: ${action.type}")

        return try {
            withTimeout(action.timeout ?: DEFAULT_TIMEOUT_MS) {
                when (action) {
                    is AutomationAction.Click -> executeClick(action)
                    is AutomationAction.Input -> executeInput(action)
                    is AutomationAction.Swipe -> executeSwipe(action)
                    is AutomationAction.Scroll -> executeScroll(action)
                    is AutomationAction.Back -> executeBack()
                    is AutomationAction.Home -> executeHome()
                    is AutomationAction.LaunchApp -> executeLaunchApp(action)
                    is AutomationAction.Wait -> executeWait(action)
                    is AutomationAction.Screenshot -> executeScreenshot()
                    is AutomationAction.Gesture -> executeGesture(action)
                    is AutomationAction.WaitFor -> executeWaitFor(action)
                    is AutomationAction.ReadScreen -> executeReadScreen()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action failed: ${action.type}", e)
            ActionResult.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * 执行点击
     */
    private suspend fun executeClick(action: AutomationAction.Click): ActionResult {
        // 方式1: 通过节点点击
        if (action.nodeId != null) {
            val node = findNodeById(action.nodeId)
            if (node != null) {
                val result = service.clickNode(node)
                node.recycle()
                return if (result) ActionResult.Success() else ActionResult.Error("Click failed")
            }
        }

        // 方式2: 通过文本查找
        if (action.text != null) {
            return executeWithRetry {
                val node = service.findNodeByText(action.text, action.exactMatch ?: false)
                if (node != null) {
                    val result = service.clickNode(node)
                    node.recycle()
                    if (result) ActionResult.Success() else ActionResult.Error("Click failed")
                } else {
                    ActionResult.Error("Node not found: ${action.text}")
                }
            }
        }

        // 方式3: 通过描述查找
        if (action.description != null) {
            return executeWithRetry {
                val node = service.findNodeByDescription(action.description)
                if (node != null) {
                    val result = service.clickNode(node)
                    node.recycle()
                    if (result) ActionResult.Success() else ActionResult.Error("Click failed")
                } else {
                    ActionResult.Error("Node not found: ${action.description}")
                }
            }
        }

        // 方式4: 通过坐标点击
        if (action.x != null && action.y != null) {
            val result = service.clickAt(action.x, action.y)
            return if (result) ActionResult.Success() else ActionResult.Error("Click at (${action.x}, ${action.y}) failed")
        }

        return ActionResult.Error("Click action requires nodeId, text, description, or coordinates")
    }

    /**
     * 执行输入
     */
    private suspend fun executeInput(action: AutomationAction.Input): ActionResult {
        val node = when {
            action.nodeId != null -> findNodeById(action.nodeId)
            action.into != null -> findInputNode(action.into)
            else -> null
        }

        if (node == null) {
            return ActionResult.Error("Input node not found")
        }

        val result = service.inputText(node, action.text)
        node.recycle()

        return if (result) ActionResult.Success() else ActionResult.Error("Input failed")
    }

    /**
     * 执行滑动
     */
    private suspend fun executeSwipe(action: AutomationAction.Swipe): ActionResult {
        val result = when (action.direction?.lowercase()) {
            "up", "down", "left", "right" -> {
                val (startX, startY, endX, endY) = calculateSwipeCoordinates(action.direction, action.startX, action.startY)
                service.swipe(startX, startY, endX, endY, action.duration ?: 300L)
            }
            else -> {
                if (action.startX != null && action.startY != null && 
                    action.endX != null && action.endY != null) {
                    service.swipe(action.startX, action.startY, action.endX, action.endY, action.duration ?: 300L)
                } else {
                    return ActionResult.Error("Swipe requires direction or coordinates")
                }
            }
        }

        return if (result) ActionResult.Success() else ActionResult.Error("Swipe failed")
    }

    /**
     * 执行滚动
     */
    private suspend fun executeScroll(action: AutomationAction.Scroll): ActionResult {
        val node = when {
            action.nodeId != null -> findNodeById(action.nodeId)
            action.inContainer != null -> findScrollableNode(action.inContainer)
            else -> {
                // 默认找第一个可滚动的节点
                val root = service.getRootNode()
                val scrollable = findFirstScrollableNode(root)
                root?.recycle()
                scrollable
            }
        }

        if (node == null) {
            return ActionResult.Error("Scrollable node not found")
        }

        val direction = when (action.direction?.lowercase()) {
            "up", "forward" -> ScrollDirection.FORWARD
            "down", "backward" -> ScrollDirection.BACKWARD
            else -> ScrollDirection.FORWARD
        }

        val result = service.scrollNode(node, direction)
        node.recycle()

        return if (result) ActionResult.Success() else ActionResult.Error("Scroll failed")
    }

    /**
     * 执行返回
     */
    private fun executeBack(): ActionResult {
        val result = service.goBack()
        return if (result) ActionResult.Success() else ActionResult.Error("Back failed")
    }

    /**
     * 执行回到桌面
     */
    private fun executeHome(): ActionResult {
        val result = service.goHome()
        return if (result) ActionResult.Success() else ActionResult.Error("Home failed")
    }

    /**
     * 启动应用
     */
    private fun executeLaunchApp(action: AutomationAction.LaunchApp): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult.Success()
            } else {
                // 尝试打开应用市场
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${action.packageName}"))
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(marketIntent)
                ActionResult.Error("App not installed, opened Play Store")
            }
        } catch (e: Exception) {
            ActionResult.Error("Failed to launch app: ${e.message}")
        }
    }

    /**
     * 等待
     */
    private suspend fun executeWait(action: AutomationAction.Wait): ActionResult {
        delay(action.ms)
        return ActionResult.Success()
    }

    /**
     * 截图
     */
    private suspend fun executeScreenshot(): ActionResult {
        val bitmap = service.takeScreenshot()
        return if (bitmap != null) {
            ActionResult.Success(bitmap)
        } else {
            ActionResult.Error("Screenshot failed - MediaProjection not initialized")
        }
    }

    /**
     * 执行手势
     */
    private suspend fun executeGesture(action: AutomationAction.Gesture): ActionResult {
        // TODO: 实现复杂手势
        return ActionResult.Error("Gesture not implemented yet")
    }

    /**
     * 等待元素出现
     */
    private suspend fun executeWaitFor(action: AutomationAction.WaitFor): ActionResult {
        val timeout = action.timeout ?: DEFAULT_TIMEOUT_MS
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeout) {
            val node = service.findNodeByText(action.text)
            if (node != null) {
                node.recycle()
                return ActionResult.Success()
            }
            delay(200)
        }

        return ActionResult.Error("Timeout waiting for: ${action.text}")
    }

    /**
     * 读取屏幕内容
     */
    private suspend fun executeReadScreen(): ActionResult {
        val root = service.getRootNode()
        if (root == null) {
            return ActionResult.Error("Cannot get root node")
        }

        val tree = UiParser.parseNodeTree(root)
        val description = UiParser.generateTextDescription(tree)
        val json = UiParser.toJson(tree)

        root.recycle()

        return ActionResult.Success(ScreenContent(
            description = description,
            json = json.toString(),
            tree = tree
        ))
    }

    /**
     * 带重试的执行
     */
    private suspend fun <T> executeWithRetry(
        retryCount: Int = DEFAULT_RETRY_COUNT,
        retryDelay: Long = DEFAULT_RETRY_DELAY_MS,
        block: suspend () -> ActionResult
    ): ActionResult {
        var lastError: ActionResult.Error? = null

        repeat(retryCount) { attempt ->
            val result = block()

            when (result) {
                is ActionResult.Success -> return result
                is ActionResult.Error -> {
                    lastError = result
                    if (attempt < retryCount - 1) {
                        Log.w(TAG, "Attempt ${attempt + 1} failed, retrying...")
                        delay(retryDelay)
                    }
                }
            }
        }

        return lastError ?: ActionResult.Error("Unknown error")
    }

    /**
     * 查找节点（通过 ID）
     */
    private fun findNodeById(nodeId: String): AccessibilityNodeInfo? {
        return service.findNodeById(nodeId)
    }

    /**
     * 查找输入节点
     */
    private fun findInputNode(description: String): AccessibilityNodeInfo? {
        val root = service.getRootNode() ?: return null

        // 尝试通过描述查找
        var node = root.findAccessibilityNodeInfosByText(description).firstOrNull {
            it.isEditable || it.className?.contains("Edit", ignoreCase = true) == true
        }

        // 尝试通过 hint 查找
        if (node == null) {
            node = findNodeByHint(root, description)
        }

        root.recycle()
        return node
    }

    /**
     * 通过 hint 查找节点
     */
    private fun findNodeByHint(root: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        // 遍历所有节点查找 hint
        return findNodeRecursive(root) { node ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                node.hintText?.toString()?.contains(hint, ignoreCase = true) == true
            } else {
                false
            }
        }
    }

    /**
     * 查找可滚动节点
     */
    private fun findScrollableNode(description: String): AccessibilityNodeInfo? {
        val root = service.getRootNode() ?: return null

        val node = root.findAccessibilityNodeInfosByText(description).firstOrNull {
            it.isScrollable
        } ?: findFirstScrollableNode(root)

        root.recycle()
        return node
    }

    /**
     * 查找第一个可滚动节点
     */
    private fun findFirstScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        return findNodeRecursive(root) { node ->
            node.isScrollable
        }
    }

    /**
     * 递归查找节点
     */
    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, predicate)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    /**
     * 计算滑动坐标
     */
    private fun calculateSwipeCoordinates(
        direction: String,
        startX: Int?,
        startY: Int?
    ): IntArray {
        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val centerX = startX ?: screenWidth / 2
        val centerY = startY ?: screenHeight / 2
        val distance = (screenHeight * 0.3).toInt()

        return when (direction.lowercase()) {
            "up" -> intArrayOf(centerX, centerY + distance, centerX, centerY - distance)
            "down" -> intArrayOf(centerX, centerY - distance, centerX, centerY + distance)
            "left" -> intArrayOf(centerX + distance, centerY, centerX - distance, centerY)
            "right" -> intArrayOf(centerX - distance, centerY, centerX + distance, centerY)
            else -> intArrayOf(centerX, centerY, centerX, centerY)
        }
    }
}

/**
 * 自动化动作基类
 */
sealed class AutomationAction {
    abstract val timeout: Long?

    data class Click(
        val nodeId: String? = null,
        val text: String? = null,
        val description: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val exactMatch: Boolean? = null,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Input(
        val text: String,
        val nodeId: String? = null,
        val into: String? = null,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Swipe(
        val direction: String? = null,
        val startX: Int? = null,
        val startY: Int? = null,
        val endX: Int? = null,
        val endY: Int? = null,
        val duration: Long? = null,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Scroll(
        val direction: String? = null,
        val nodeId: String? = null,
        val inContainer: String? = null,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Back(
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Home(
        override val timeout: Long? = null
    ) : AutomationAction()

    data class LaunchApp(
        val packageName: String,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Wait(
        val ms: Long,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Screenshot(
        override val timeout: Long? = null
    ) : AutomationAction()

    data class Gesture(
        val gestureDescription: String,
        override val timeout: Long? = null
    ) : AutomationAction()

    data class WaitFor(
        val text: String,
        val timeout: Long? = null
    ) : AutomationAction()

    data class ReadScreen(
        override val timeout: Long? = null
    ) : AutomationAction()
}

/**
 * 动作执行结果
 */
sealed class ActionResult {
    data class Success(val data: Any? = null) : ActionResult()
    data class Error(val message: String, val exception: Exception? = null) : ActionResult()
}

/**
 * 屏幕内容
 */
data class ScreenContent(
    val description: String,
    val json: String,
    val tree: UiTree
)
