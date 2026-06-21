// AutomationSkill.kt
// 自动化 Skill - 将自动化能力封装为 Agent 可调用的工具

package com.androidclaw.app.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.androidclaw.app.automation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动化 Skill
 * 
 * 提供跨应用自动化能力：
 * - 启动/停止自动化
 * - 截图分析
 * - 点击、输入、滑动等操作
 * - 读取屏幕内容
 * - 执行自动化计划
 */
class AutomationSkill : SkillDefinition {

    companion object {
        private const val TAG = "AutomationSkill"
        private const val MAX_EXECUTION_TIME_MS = 60000L  // 最大执行时间 60s
    }

    override val skillName: String = "automation"
    override val displayName: String = "跨应用自动化"
    override val description: String = """
        跨应用自动化操作能力，可以操作其他 App（如淘宝、拼多多、1688 等）。
        
        使用场景：
        - 在电商 App 搜索商品
        - 自动填写表单
        - 批量操作（如选品、上货）
        - 客服自动回复
        
        注意：需要先开启无障碍服务权限。
    """.trimIndent()

    override val requiredPermissions: List<String> = listOf()

    override suspend fun initialize(context: Context) {
        Log.i(TAG, "AutomationSkill initialized")

        // 检查无障碍服务是否已启用
        if (!isAccessibilityServiceEnabled(context)) {
            Log.w(TAG, "Accessibility service not enabled")
        }
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "automation_start",
            displayName = "启动自动化",
            description = "启动自动化服务并打开目标 App",
            parameters = listOf(
                ToolParameter("target_app", "string", true, "目标 App 包名或名称（如：淘宝、拼多多、com.taobao.taobao）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_stop",
            displayName = "停止自动化",
            description = "停止当前的自动化执行",
            parameters = emptyList(),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_screenshot",
            displayName = "截图分析",
            description = "截取当前屏幕并用 AI 分析内容",
            parameters = listOf(
                ToolParameter("question", "string", false, "要分析的问题（如：找到最便宜的商品）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_click",
            displayName = "点击元素",
            description = "点击屏幕上的指定元素",
            parameters = listOf(
                ToolParameter("description", "string", true, "元素描述（如：搜索按钮、商品图片、立即购买）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_input",
            displayName = "输入文本",
            description = "在输入框中输入文字",
            parameters = listOf(
                ToolParameter("text", "string", true, "要输入的文本"),
                ToolParameter("into", "string", false, "输入框描述（如：搜索框、备注输入框）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_swipe",
            displayName = "滑动屏幕",
            description = "滑动屏幕（上/下/左/右）",
            parameters = listOf(
                ToolParameter("direction", "string", true, "滑动方向：up/down/left/right")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_read_screen",
            displayName = "读取屏幕",
            description = "读取当前屏幕的结构化内容",
            parameters = emptyList(),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_wait_for",
            displayName = "等待元素",
            description = "等待指定元素出现",
            parameters = listOf(
                ToolParameter("text", "string", true, "要等待的文本"),
                ToolParameter("timeout", "int", false, "超时时间（毫秒），默认 10000")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_execute_plan",
            displayName = "执行计划",
            description = "执行一个多步骤自动化计划",
            parameters = listOf(
                ToolParameter("goal", "string", true, "用户目标（如：在拼多多搜索 iPhone 15 并找最便宜的）")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_check_permission",
            displayName = "检查权限",
            description = "检查无障碍服务权限是否已开启",
            parameters = emptyList(),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "automation_open_settings",
            displayName = "打开设置",
            description = "打开无障碍服务设置页面",
            parameters = emptyList(),
            returnType = "string"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        Log.i(TAG, "Executing tool: $toolName, params: $parameters")

        return when (toolName) {
            "automation_start" -> executeStart(parameters)
            "automation_stop" -> executeStop()
            "automation_screenshot" -> executeScreenshot(parameters)
            "automation_click" -> executeClick(parameters)
            "automation_input" -> executeInput(parameters)
            "automation_swipe" -> executeSwipe(parameters)
            "automation_read_screen" -> executeReadScreen()
            "automation_wait_for" -> executeWaitFor(parameters)
            "automation_execute_plan" -> executePlan(parameters)
            "automation_check_permission" -> executeCheckPermission()
            "automation_open_settings" -> executeOpenSettings()
            else -> ToolResult.Error("Unknown tool: $toolName")
        }
    }

    override fun release() {
        Log.i(TAG, "AutomationSkill released")
    }

    /**
     * 启动自动化
     */
    private suspend fun executeStart(parameters: Map<String, Any>): ToolResult {
        val targetApp = parameters["target_app"] as? String
            ?: return ToolResult.Error("Missing parameter: target_app")

        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动，请先开启无障碍服务")

        // 解析包名
        val packageName = resolvePackageName(targetApp)

        service.startAutomation()

        return ToolResult.Success("自动化已启动，目标应用：$packageName")
    }

    /**
     * 停止自动化
     */
    private fun executeStop(): ToolResult {
        val service = AutomationService.getInstance()
        service?.stopAutomation()

        return ToolResult.Success("自动化已停止")
    }

    /**
     * 截图分析
     */
    private suspend fun executeScreenshot(parameters: Map<String, Any>): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val bitmap = service.takeScreenshot()
            ?: return ToolResult.Error("截图失败")

        val question = parameters["question"] as? String ?: "描述当前屏幕内容"

        // TODO: 调用多模态分析
        // val analyzer = MultimodalAnalyzer(...)
        // val result = analyzer.analyze(bitmap, question)

        return ToolResult.Success("截图成功，尺寸：${bitmap.width}x${bitmap.height}\n问题：$question\n（多模态分析待实现）")
    }

    /**
     * 点击元素
     */
    private suspend fun executeClick(parameters: Map<String, Any>): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val description = parameters["description"] as? String
            ?: return ToolResult.Error("Missing parameter: description")

        // 尝试查找并点击元素
        val node = service.findNodeByText(description)
            ?: service.findNodeByDescription(description)

        return if (node != null) {
            val result = service.clickNode(node)
            node.recycle()
            if (result) {
                ToolResult.Success("点击成功：$description")
            } else {
                ToolResult.Error("点击失败：$description")
            }
        } else {
            ToolResult.Error("未找到元素：$description")
        }
    }

    /**
     * 输入文本
     */
    private suspend fun executeInput(parameters: Map<String, Any>): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val text = parameters["text"] as? String
            ?: return ToolResult.Error("Missing parameter: text")

        val into = parameters["into"] as? String ?: "输入框"

        // TODO: 实现输入逻辑
        return ToolResult.Success("输入：'$text' 到 '$into'（待实现）")
    }

    /**
     * 滑动屏幕
     */
    private suspend fun executeSwipe(parameters: Map<String, Any>): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val direction = parameters["direction"] as? String
            ?: return ToolResult.Error("Missing parameter: direction")

        // TODO: 实现滑动逻辑
        return ToolResult.Success("向 $direction 滑动（待实现）")
    }

    /**
     * 读取屏幕内容
     */
    private suspend fun executeReadScreen(): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val root = service.getRootNode()
            ?: return ToolResult.Error("无法获取屏幕内容")

        val tree = UiParser.parseNodeTree(root)
        val description = UiParser.generateTextDescription(tree)
        val summary = UiParser.getActionableSummary(tree)

        root.recycle()

        return ToolResult.Success("""
            $summary
            
            --- 详细内容 ---
            $description
        """.trimIndent())
    }

    /**
     * 等待元素
     */
    private suspend fun executeWaitFor(parameters: Map<String, Any>): ToolResult {
        val service = AutomationService.getInstance()
            ?: return ToolResult.Error("自动化服务未启动")

        val text = parameters["text"] as? String
            ?: return ToolResult.Error("Missing parameter: text")

        val timeout = (parameters["timeout"] as? Int)?.toLong() ?: 10000L

        // TODO: 实现等待逻辑
        return ToolResult.Success("等待元素：'$text'，超时：${timeout}ms（待实现）")
    }

    /**
     * 执行计划
     */
    private suspend fun executePlan(parameters: Map<String, Any>): ToolResult {
        val goal = parameters["goal"] as? String
            ?: return ToolResult.Error("Missing parameter: goal")

        // TODO: 实现计划执行
        return ToolResult.Success("执行计划：$goal（待实现）")
    }

    /**
     * 检查权限
     */
    private fun executeCheckPermission(): ToolResult {
        val service = AutomationService.getInstance()
        val isRunning = AutomationService.isRunning.value

        return ToolResult.Success(if (service != null && isRunning) {
            "无障碍服务已开启"
        } else {
            "无障碍服务未开启，请先开启权限"
        })
    }

    /**
     * 打开设置
     */
    private fun executeOpenSettings(): ToolResult {
        // 返回需要打开设置的指令
        return ToolResult.Success("请在设置中开启 AndroidClaw 的无障碍服务权限")
    }

    /**
     * 检查无障碍服务是否已启用
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error finding accessibility setting", e)
        }

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return services?.contains(context.packageName) == true
        }

        return false
    }

    /**
     * 解析包名
     */
    private fun resolvePackageName(appName: String): String {
        // 常见应用包名映射
        return when (appName.lowercase()) {
            "淘宝", "taobao" -> "com.taobao.taobao"
            "拼多多", "pinduoduo", "pdd" -> "com.xunmeng.pinduoduo"
            "1688" -> "com.alibaba.wireless"
            "京东", "jd", "jingdong" -> "com.jingdong.app.mall"
            "微信", "wechat", "weixin" -> "com.tencent.mm"
            "支付宝", "alipay" -> "com.eg.android.AlipayGphone"
            "抖音", "douyin", "tiktok" -> "com.ss.android.ugc.aweme"
            "小红书", "xiaohongshu" -> "com.xingin.xhs"
            else -> appName // 假设已经是包名
        }
    }
}
