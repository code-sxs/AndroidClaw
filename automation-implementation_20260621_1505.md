# AndroidClaw 跨应用自动化实现总结

## 实现时间
2026-06-21

## 项目目标
实现基于 AccessibilityService 的跨应用自动化能力，让 AI Agent 能操作任何 App（淘宝、拼多多、1688 等）。

## 已完成的模块

### 1. AutomationService (AccessibilityService)
**文件**: `app/src/main/java/com/androidclaw/app/automation/AutomationService.kt`

**功能**:
- 继承 Android AccessibilityService
- 监听窗口状态变化（WINDOW_STATE_CHANGED、WINDOW_CONTENT_CHANGED）
- 读取界面元素树（AccessibilityNodeInfo）
- 执行操作：
  - 点击（节点点击、坐标点击）
  - 输入文本
  - 滑动（手势识别支持）
  - 滚动（SCROLL_FORWARD/BACKWARD）
  - 返回、回到桌面、打开最近任务
- MediaProjection 截图能力
- 通知栏常驻通知，随时可停止
- 步骤上限保护（50步），防止失控

**关键配置**:
- AndroidManifest.xml 已注册服务
- res/xml/automation_service_config.xml 配置文件
- 支持 canPerformGestures、canRetrieveWindowContent

### 2. UiParser（界面理解器）
**文件**: `app/src/main/java/com/androidclaw/app/automation/UiParser.kt`

**功能**:
- 将 AccessibilityNodeInfo 树转换为结构化数据（UiTree、UiNode）
- 提取关键信息：
  - 可点击元素
  - 可输入元素
  - 文本内容
  - 列表项
  - 当前 Activity 名称
- 生成 LLM 友好的文本描述
- 序列化为 JSON（完整格式 + 紧凑格式）
- 生成可操作元素摘要（最精简格式）

**输出格式示例**:
```
当前页面: com.taobao.taobao/.homepage.MainActivity (淘宝首页)
可见内容:
- 搜索框 [可点击, 可输入] (id: com.taobao:id/search_bar)
- 按钮"我的淘宝" [可点击]
- 文本"双11大促"
- 商品列表 (10项)
```

### 3. ActionExecutor（动作执行器）
**文件**: `app/src/main/java/com/androidclaw/app/automation/ActionExecutor.kt`

**功能**:
- 执行各种自动化动作
- 智能元素查找：
  - 按 text 查找
  - 按 viewId 查找
  - 按 description 查找
  - 按坐标定位
  - 按 hint 查找输入框
- 重试机制（默认 3 次）
- 超时保护（默认 10 秒）
- 结果回调

**支持的动作**:
- Click（节点/坐标）
- Input（文本输入）
- Swipe（滑动）
- Scroll（滚动）
- Back、Home
- LaunchApp（启动应用）
- Wait、WaitFor（等待元素）
- Screenshot（截图）
- ReadScreen（读取屏幕）

### 4. AutomationSkill（自动化 Skill）
**文件**: `app/src/main/java/com/androidclaw/app/skills/AutomationSkill.kt`

**功能**:
- 将自动化能力封装为 Agent 可调用的工具
- 自动注册到 ToolRegistry

**提供的工具**:
- `automation_start(target_app)` — 启动自动化并打开目标 App
- `automation_stop()` — 停止自动化
- `automation_screenshot(question)` — 截图并用 AI 分析
- `automation_click(description)` — 点击匹配描述的元素
- `automation_input(text, into)` — 输入文本
- `automation_swipe(direction)` — 滑动屏幕
- `automation_read_screen()` — 读取当前屏幕内容
- `automation_wait_for(text, timeout)` — 等待元素出现
- `automation_execute_plan(goal)` — 执行多步骤自动化计划
- `automation_check_permission()` — 检查无障碍权限
- `automation_open_settings()` — 打开无障碍设置

### 5. MultimodalAnalyzer（多模态分析器）
**文件**: `app/src/main/java/com/androidclaw/app/automation/MultimodalAnalyzer.kt`

**功能**:
- 调用多模态 LLM（MiniCPM-V 等）分析截图
- 输入：截图 Bitmap + 用户问题/目标
- 输出：
  - 当前页面描述
  - 可操作元素识别
  - 下一步操作建议
  - 任务完成状态判断
- 多种提示词模板（通用、购物、表单填写）
- 降级策略：当多模态不可用时，降级为 UiParser（仅文本分析）

### 6. AutomationPlanner（自动化规划器）
**文件**: `app/src/main/java/com/androidclaw/app/automation/AutomationPlanner.kt`

**功能**:
- 接收用户目标（如"在拼多多搜索 iPhone 15 并找最便宜的"）
- 调用 LLM 生成操作序列（JSON 格式的多步计划）
- 逐步执行计划
- 每步后验证结果
- 失败时重新规划（最多 3 次）
- 支持条件分支

**计划格式**:
```json
{
  "goal": "在拼多多搜索 iPhone 15",
  "steps": [
    {"action": "launch_app", "params": {"package": "com.xunmeng.pinduoduo"}},
    {"action": "wait", "params": {"ms": 2000}},
    {"action": "click", "params": {"text": "搜索框"}},
    {"action": "input", "params": {"text": "iPhone 15", "into": "搜索框"}},
    {"action": "click", "params": {"text": "搜索按钮"}}
  ],
  "success_criteria": "搜索结果页面显示 iPhone 15 相关商品"
}
```

### 7. ScreenCapture（屏幕截图工具）
**文件**: `app/src/main/java/com/androidclaw/app/automation/ScreenCapture.kt`

**功能**:
- 基于 MediaProjection API 实现截图
- 支持 Android 5.0+ 系统
- 处理屏幕旋转和分辨率变化
- 自动裁剪多余像素
- 协程友好的异步接口

### 8. AutomationScreen（自动化管理 UI）
**文件**: `app/src/main/java/com/androidclaw/app/ui/screens/AutomationScreen.kt`

**功能**:
- 服务状态指示（未启动/运行中）
- 目标应用选择（常用 App 快捷按钮）
- 执行状态显示：
  - 当前步骤
  - 总步骤数
  - 当前动作
  - 已执行步数
- 紧急停止按钮
- 快捷操作：
  - 读取屏幕
  - 截图
  - 返回
  - 回到桌面
- 操作日志（可展开查看详细）
- 引导对话框（首次使用引导开启无障碍服务）

### 9. AutomationViewModel
**文件**: `app/src/main/java/com/androidclaw/app/ui/screens/AutomationViewModel.kt`

**功能**:
- 管理 UI 状态
- 监听服务状态
- 加载已安装应用列表
- 处理用户操作
- 维护操作日志

### 10. 资源文件
- `res/xml/automation_service_config.xml` — 无障碍服务配置
- `res/values/strings.xml` — 字符串资源
- `res/drawable/ic_automation.xml` — 自动化图标
- `res/drawable/ic_stop.xml` — 停止图标

### 11. 导航更新
**文件**: `app/src/main/java/com/androidclaw/app/ui/navigation/AndroidClawNavHost.kt`

- 添加 `Screen.Automation` 路由
- 添加 Automation composable

### 12. ToolRegistry 更新
**文件**: `app/src/main/java/com/androidclaw/app/agent/ToolRegistry.kt`

- 自动注册内置 Skills（包括 AutomationSkill）

## 安全与用户体验考虑

1. **权限引导**: 首次使用必须引导用户开启无障碍服务
2. **通知栏常驻**: 随时可停止自动化
3. **敏感操作**: 需要用户确认（预留接口）
4. **日志记录**: 全程日志，可回放审查
5. **步骤上限**: 限制 50 步，防止失控
6. **隐私保护**: 屏幕内容仅在本地处理

## 代码规范遵循

✅ 使用 Kotlin 协程处理异步  
✅ 完善的错误处理  
✅ 日志使用 Log.d/i/e  
✅ 文件头部注释说明功能  
✅ 使用 WeakReference 防止内存泄漏  

## 后续工作建议

1. **多模态模型集成**: 与 LLMManager 中的 MiniCPM-V 集成
2. **录制功能**: 支持录制操作并保存为脚本
3. **脚本编辑器**: 可视化编辑自动化脚本
4. **更多 App 适配**: 针对特定 App 优化识别规则
5. **性能优化**: 减少 AccessibilityNodeInfo 遍历开销

## Git 提交信息
```
feat(automation): add cross-app automation via AccessibilityService
```

## 文件清单
- app/src/main/java/com/androidclaw/app/automation/
  - AutomationService.kt
  - UiParser.kt
  - ActionExecutor.kt
  - MultimodalAnalyzer.kt
  - AutomationPlanner.kt
  - ScreenCapture.kt
- app/src/main/java/com/androidclaw/app/skills/
  - AutomationSkill.kt
- app/src/main/java/com/androidclaw/app/ui/screens/
  - AutomationScreen.kt
  - AutomationViewModel.kt
- app/src/main/java/com/androidclaw/app/ui/navigation/
  - AndroidClawNavHost.kt (已修改)
- app/src/main/java/com/androidclaw/app/agent/
  - ToolRegistry.kt (已修改)
- app/src/main/res/xml/
  - automation_service_config.xml
- app/src/main/res/values/
  - strings.xml
- app/src/main/res/drawable/
  - ic_automation.xml
  - ic_stop.xml
- app/src/main/AndroidManifest.xml (已修改)
