// UiParser.kt
// 界面理解器 - 将 AccessibilityNodeInfo 树转换为结构化描述
// 供 LLM 理解当前屏幕内容

package com.androidclaw.app.automation

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * UI 解析器
 * 
 * 功能：
 * - 将 AccessibilityNodeInfo 树转换为结构化描述
 * - 提取关键信息（可点击、可输入、文本、列表等）
 * - 生成 LLM 友好的描述
 * - 序列化为 JSON 格式
 */
object UiParser {

    private const val TAG = "UiParser"
    private const val MAX_DEPTH = 15
    private const val MAX_NODES = 500

    /**
     * 解析节点树
     */
    fun parseNodeTree(root: AccessibilityNodeInfo?): UiTree {
        if (root == null) {
            Log.w(TAG, "Root node is null")
            return UiTree(packageName = "", activityName = "", nodes = emptyList())
        }

        val nodes = mutableListOf<UiNode>()
        parseNodeRecursive(root, nodes, 0, "")

        val packageName = root.packageName?.toString() ?: ""
        val activityName = AutomationService.currentActivity.value

        Log.d(TAG, "Parsed ${nodes.size} nodes from package: $packageName")

        return UiTree(
            packageName = packageName,
            activityName = activityName,
            nodes = nodes
        )
    }

    /**
     * 递归解析节点
     */
    private fun parseNodeRecursive(
        node: AccessibilityNodeInfo,
        nodes: MutableList<UiNode>,
        depth: Int,
        parentId: String
    ) {
        // 深度限制
        if (depth > MAX_DEPTH) {
            return
        }

        // 节点数量限制
        if (nodes.size >= MAX_NODES) {
            return
        }

        // 节点不可见
        if (!node.isVisibleToUser) {
            return
        }

        val nodeId = generateNodeId(node, depth, nodes.size)

        // 解析节点信息
        val uiNode = parseSingleNode(node, nodeId, parentId)
        nodes.add(uiNode)

        // 递归处理子节点
        val childCount = node.childCount
        for (i in 0 until childCount) {
            try {
                val child = node.getChild(i)
                if (child != null) {
                    parseNodeRecursive(child, nodes, depth + 1, nodeId)
                    child.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting child $i", e)
            }
        }
    }

    /**
     * 解析单个节点
     */
    private fun parseSingleNode(
        node: AccessibilityNodeInfo,
        nodeId: String,
        parentId: String
    ): UiNode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        return UiNode(
            id = nodeId,
            parentId = parentId,
            viewId = node.viewIdResourceName ?: "",
            className = node.className?.toString() ?: "",
            text = node.text?.toString() ?: "",
            contentDescription = node.contentDescription?.toString() ?: "",
            hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                node.hintText?.toString() ?: ""
            } else "",
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            isFocusable = node.isFocusable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            bounds = bounds,
            depth = node.depth(),
            childCount = node.childCount
        )
    }

    /**
     * 生成节点 ID
     */
    private fun generateNodeId(node: AccessibilityNodeInfo, depth: Int, index: Int): String {
        val viewId = node.viewIdResourceName
        return if (!viewId.isNullOrEmpty()) {
            viewId
        } else {
            "node_${depth}_${index}"
        }
    }

    /**
     * 生成文本描述（供 LLM 阅读）
     */
    fun generateTextDescription(tree: UiTree): String {
        val sb = StringBuilder()

        sb.appendLine("当前页面: ${tree.activityName.ifEmpty { tree.packageName }}")
        sb.appendLine()

        // 分类节点
        val clickableNodes = tree.nodes.filter { it.isClickable && it.text.isNotEmpty() }
        val inputNodes = tree.nodes.filter { it.isEditable || it.isFocusable }
        val textNodes = tree.nodes.filter { it.text.isNotEmpty() && !it.isClickable }
        val scrollableNodes = tree.nodes.filter { it.isScrollable }

        // 可点击元素
        if (clickableNodes.isNotEmpty()) {
            sb.appendLine("可点击元素:")
            clickableNodes.forEach { node ->
                sb.appendLine("  - ${node.text} [${node.className.substringAfterLast('.')}] ${formatLocation(node.bounds)}")
            }
            sb.appendLine()
        }

        // 可输入元素
        if (inputNodes.isNotEmpty()) {
            sb.appendLine("可输入元素:")
            inputNodes.forEach { node ->
                val hint = node.hint.ifEmpty { node.contentDescription }
                sb.appendLine("  - 输入框 ${if (hint.isNotEmpty()) "(提示: $hint)" else ""} ${formatLocation(node.bounds)}")
            }
            sb.appendLine()
        }

        // 文本内容（只显示前 20 条）
        if (textNodes.isNotEmpty()) {
            sb.appendLine("文本内容:")
            textNodes.take(20).forEach { node ->
                sb.appendLine("  - ${node.text.take(100)}")
            }
            if (textNodes.size > 20) {
                sb.appendLine("  ... 还有 ${textNodes.size - 20} 条")
            }
            sb.appendLine()
        }

        // 可滚动元素
        if (scrollableNodes.isNotEmpty()) {
            sb.appendLine("可滚动区域: ${scrollableNodes.size} 个")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * 格式化位置信息
     */
    private fun formatLocation(bounds: Rect): String {
        return "(x:${bounds.left}-${bounds.right}, y:${bounds.top}-${bounds.bottom})"
    }

    /**
     * 转换为 JSON（供 LLM 解析）
     */
    fun toJson(tree: UiTree): JSONObject {
        val json = JSONObject()

        json.put("package_name", tree.packageName)
        json.put("activity_name", tree.activityName)

        val nodesArray = JSONArray()
        tree.nodes.forEach { node ->
            val nodeJson = JSONObject()
            nodeJson.put("id", node.id)
            nodeJson.put("view_id", node.viewId)
            nodeJson.put("class", node.className.substringAfterLast('.'))
            nodeJson.put("text", node.text)
            nodeJson.put("description", node.contentDescription)
            nodeJson.put("hint", node.hint)
            nodeJson.put("clickable", node.isClickable)
            nodeJson.put("editable", node.isEditable)
            nodeJson.put("scrollable", node.isScrollable)
            nodeJson.put("checkable", node.isCheckable)
            nodeJson.put("checked", node.isChecked)
            nodeJson.put("bounds", JSONObject().apply {
                put("left", node.bounds.left)
                put("top", node.bounds.top)
                put("right", node.bounds.right)
                put("bottom", node.bounds.bottom)
            })
            nodeJson.put("child_count", node.childCount)
            nodesArray.put(nodeJson)
        }

        json.put("nodes", nodesArray)
        json.put("total_nodes", tree.nodes.size)

        return json
    }

    /**
     * 转换为紧凑 JSON（减少 token 消耗）
     */
    fun toCompactJson(tree: UiTree): String {
        val sb = StringBuilder()

        sb.append("{\"pkg\":\"${tree.packageName}\",\"act\":\"${tree.activityName}\",\"nodes\":[")

        tree.nodes.forEachIndexed { index, node ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"${escapeJson(node.id)}\"")
            if (node.text.isNotEmpty()) sb.append(",\"txt\":\"${escapeJson(node.text.take(100))}\"")
            if (node.contentDescription.isNotEmpty()) sb.append(",\"desc\":\"${escapeJson(node.contentDescription.take(50))}\"")
            if (node.isClickable) sb.append(",\"clk\":1")
            if (node.isEditable) sb.append(",\"edit\":1")
            if (node.isScrollable) sb.append(",\"scr\":1")
            if (node.isCheckable) sb.append(",\"chk\":${if (node.isChecked) "1" else "0"}")
            sb.append(",\"b\":[${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}]")
            sb.append("}")
        }

        sb.append("]}")

        return sb.toString()
    }

    /**
     * 转义 JSON 字符串
     */
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 获取可操作元素摘要（最精简格式）
     */
    fun getActionableSummary(tree: UiTree): String {
        val sb = StringBuilder()

        // 可点击按钮/链接
        val buttons = tree.nodes.filter { 
            it.isClickable && it.text.isNotEmpty() 
        }.take(10)

        // 输入框
        val inputs = tree.nodes.filter { 
            it.isEditable || (it.isFocusable && it.className.contains("Edit", ignoreCase = true))
        }

        sb.append("页面: ${tree.packageName}\n")
        sb.append("按钮: ${buttons.joinToString(", ") { "'${it.text.take(20)}'" }}\n")
        sb.append("输入框: ${inputs.size}个\n")

        // 关键文本（包含价格的、数量的等）
        val keyTexts = tree.nodes.filter { node ->
            node.text.matches(Regex(".*[¥$€£].*")) ||  // 价格
            node.text.matches(Regex(".*\\d+.*"))        // 数字
        }.take(5)

        if (keyTexts.isNotEmpty()) {
            sb.append("关键信息: ${keyTexts.joinToString(", ") { "'${it.text.take(30)}'" }}\n")
        }

        return sb.toString()
    }
}

/**
 * UI 树结构
 */
data class UiTree(
    val packageName: String,
    val activityName: String,
    val nodes: List<UiNode>
)

/**
 * UI 节点
 */
data class UiNode(
    val id: String,
    val parentId: String,
    val viewId: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val hint: String,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val isFocusable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val bounds: Rect,
    val depth: Int,
    val childCount: Int
) {
    /**
     * 中心坐标
     */
    val centerX: Int
        get() = (bounds.left + bounds.right) / 2

    val centerY: Int
        get() = (bounds.top + bounds.bottom) / 2

    /**
     * 宽高
     */
    val width: Int
        get() = bounds.width()

    val height: Int
        get() = bounds.height()
}

/**
 * AccessibilityNodeInfo 扩展方法
 */
fun AccessibilityNodeInfo.depth(): Int {
    var d = 0
    var parent = this.parent
    while (parent != null) {
        d++
        parent = parent.parent
    }
    return d
}
