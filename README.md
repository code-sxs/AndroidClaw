# AndroidClaw 🤖

<p align="center">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License: Apache 2.0" />
  <img src="https://img.shields.io/badge/Platform-Android%2010+-green.svg" alt="Platform: Android 10+" />
  <img src="https://img.shields.io/badge/Kotlin-2.0+-purple.svg" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg" alt="UI: Jetpack Compose" />
</p>

<p align="center">
  <strong>完全离线的本地私人 AI Agent · PhoneClaw 的 Android 实现</strong>
</p>

---

## ✨ 这是什么？

**AndroidClaw** 是 [PhoneClaw](https://github.com/kellyvv/PhoneClaw) (iOS) 的 Android 完整复刻版 —— 一个**完全离线运行**的本地私人 AI 助手。

> 🔒 **你的数据永远留在设备上。** 无需联网、无需云服务、无 API Key。

### 🌟 核心特性

| 特性 | 说明 |
|------|------|
| 🔒 **完全离线** | 所有 AI 推理在本地完成，零网络依赖 |
| 🤖 **三引擎推理** | MediaPipe LLM / MLC-LLM / LiteRT 自动选择最优引擎 |
| 📦 **模块化模型** | 应用内下载 Gemma 4 / MiniCPM-V，自动匹配硬件 |
| 🛠️ **Skill 系统** | 可扩展的工具系统（日历、通讯录、剪贴板…） |
| 🎨 **现代 UI** | Jetpack Compose + Material Design 3 |
| 📱 **最低 Android 10** | 覆盖绝大多数 Android 设备 |

---

## 🏗️ 架构

```
┌──────────────────────────────────────┐
│         UI 层 (Jetpack Compose)       │
│   聊天 · 模型管理 · Skill 管理        │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│          Agent 框架层                  │
│   对话管理 · Tool 调用 · Skill 路由    │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│        LLMManager (统一推理层)         │
│   硬件检测 · 引擎选择 · 模型管理        │
└──┬──────────┬──────────┬────────────┘
   │          │          │
┌─▼────┐ ┌──▼─────┐ ┌─▼──────────┐
│MediaPipe│ │MLC-LLM │ │  LiteRT    │
│ LLM API│ │        │ │ (TFLite)   │
│ ★首选  │ │☆备用   │ │ ☆第三选项  │
└────────┘ └────────┘ └────────────┘
```

---

## 📦 支持的模型

| 模型 | 参数量 | 推荐设备 | 用途 |
|------|--------|----------|------|
| **Gemma 4 E2B** | 2B | 6GB+ RAM | 日常对话、快速响应 |
| **Gemma 4 E4B** | 4B | 8GB+ RAM | 高质量推理 |
| **MiniCPM-V 4.6** | 4B | 8GB+ RAM | 图片理解（多模态）|

应用启动时自动检测硬件能力，推荐最适合的模型。

---

## 🛠️ 内置 Skill

| Skill | 功能 | 状态 |
|-------|------|------|
| 📅 **日历** | 读取/创建/修改日历事件 | ✅ 已实现 |
| 📇 **通讯录** | 搜索/读取/创建联系人 | ✅ 已实现 |
| 📋 **剪贴板** | 读取/写入/清除剪贴板 | ✅ 已实现 |
| 🌐 **翻译** | 本地文本翻译 | 🚧 开发中 |
| 📁 **文件操作** | 文件读写管理 | ⏳ 计划中 |
| ❤️ **健康数据** | Google Fit 数据读取 | ⏳ 计划中 |
| 🔍 **联网搜索** | 用户主动触发的搜索 | ⏳ 计划中 |
| 📷 **图片理解** | MiniCPM-V 视觉分析 | ⏳ 计划中 |

> Skill 系统基于 `SkillDefinition` 接口，开发者可以轻松扩展自定义 Skill。

---

## 🚀 快速开始

### 前置要求

- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **Android SDK** API 29 (Android 10) 或更高
- **JDK** 17+
- 一台 Android 设备或模拟器（推荐 8GB+ RAM）

### 构建运行

```bash
# 1. 克隆仓库
git clone https://github.com/code-sxs/AndroidClaw.git
cd AndroidClaw

# 2. 用 Android Studio 打开项目
#    等待 Gradle 同步完成

# 3. 连接设备或启动模拟器后点击 Run
#    或使用命令行:
./gradlew assembleDebug
```

### 首次启动

1. 应用会自动检测设备硬件能力（GPU/NPU/RAM）
2. 推荐适合你设备的模型
3. 下载模型（约 2-4GB，取决于选择的模型）
4. 开始与 AndroidClaw 对话！ 🎉

---

## 📂 项目结构

```
AndroidClaw/
├── app/src/main/java/com/androidclaw/app/
│   ├── app/
│   │   ├── AndroidClawApplication.kt    # Application 入口
│   │   └── MainActivity.kt              # 主 Activity
│   ├── agent/                           # 🤖 Agent 框架
│   │   ├── AgentManager.kt              # 对话管理 & Tool 调用
│   │   └── ToolRegistry.kt              # 工具注册中心
│   ├── llm/                             # 🧠 推理引擎层
│   │   ├── LLMManager.kt               # 三引擎统一管理
│   │   ├── ModelDownloader.kt           # 模型下载 (断点续传)
│   │   ├── engine/
│   │   │   ├── BaseEngine.kt            # 引擎抽象基类
│   │   │   ├── MediaPipeEngine.kt       # MediaPipe LLM (★首选)
│   │   │   ├── MLCEngine.kt             # MLC-LLM (☆备用)
│   │   │   └── LiteRTEngine.kt          # LiteRT (☆第三)
│   │   └── model/
│   │       ├── Model.kt                 # 模型数据定义
│   │       └── HardwareDetector.kt      # 硬件能力检测
│   ├── skills/                          # 🛠️ Skill 系统
│   │   ├── SkillDefinition.kt           # Skill 接口定义
│   │   ├── CalendarSkill.kt             # 📅 日历
│   │   ├── ContactsSkill.kt             # 📇 通讯录
│   │   └── ClipboardSkill.kt            # 📋 剪贴板
│   └── ui/                              # 🎨 UI 层 (Compose)
│       ├── navigation/
│       │   └── AndroidClawNavHost.kt    # 导航路由
│       ├── screens/
│       │   ├── ChatScreen.kt            # 聊天界面
│       │   ├── ModelManagementScreen.kt # 模型管理
│       │   └── SkillManagementScreen.kt # Skill 管理
│       └── theme/
│           ├── Theme.kt                 # M3 主题 (动态配色)
│           ├── Color.kt                 # 颜色定义
│           └── Typography.kt            # 字体样式
├── app/src/test/                        # 🧪 单元测试
│   └── skills/
│       ├── CalendarSkillTest.kt
│       ├── ContactsSkillTest.kt
│       └── ClipboardSkillTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE                              # Apache 2.0
└── README.md
```

---

## 🔒 隐私与安全

| 安全特性 | 说明 |
|----------|------|
| 🔒 **100% 离线** | 零网络依赖，零数据上传 |
| 🧠 **本地推理** | 所有 AI 计算在设备上完成 |
| 🔑 **权限最小化** | 仅在需要时请求必要权限 |
| 📖 **开源透明** | Apache 2.0，代码完全可审计 |
| 🚫 **无追踪** | 无分析、无遥测、无第三方 SDK |

---

## 🗺️ 开发路线图

### ✅ Phase 1: 基础设施 + 核心聊天（进行中）
- [x] 项目初始化 & GitHub 仓库
- [x] 三引擎推理层框架 (MediaPipe / MLC / LiteRT)
- [x] MediaPipe 引擎完整实现
- [x] 硬件检测 & 模型下载管理
- [x] Skill 系统框架 + 3 个内置 Skill
- [x] Agent 框架核心
- [x] 基础 UI (聊天 / 模型管理 / 导航)
- [ ] MLC-LLM & LiteRT 引擎完整实现
- [ ] 多模型协作链路
- [ ] 单元测试 & 集成测试

### 🚧 Phase 2: 扩展 Skill + 多模型协作
- [ ] 文件操作 / 健康数据 / 照片库 Skill
- [ ] 小模型参数提取 + 大模型推理协作
- [ ] 联网搜索 Skill

### ⏳ Phase 3: 多模态 + 语音
- [ ] MiniCPM-V 4.6 图片理解
- [ ] LIVE 摄像头实时模式
- [ ] ASR 语音识别 + TTS 语音合成

### ⏳ Phase 4: 跨 App 自动化
- [ ] App Intents / Shortcuts 集成
- [ ] URL Scheme / Deep Link 调度
- [ ] 通知监听与唤起

### ⏳ Phase 5: 外部硬件扩展
- [ ] 外部视频输入 & 屏幕画面理解
- [ ] 外部硬件联动

---

## 🤝 贡献

欢迎贡献！详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 开发流程

本项目采用 **多 Agent 协作开发** 模式：
1. Fork 并创建功能分支
2. 提交前通过多 Agent 协作审查（代码质量 / UI/UX / 安全）
3. 提交 Pull Request

---

## 🙏 致谢

- [**PhoneClaw**](https://github.com/kellyvv/PhoneClaw) — iOS 版本，本项目的灵感来源与架构参考
- [**MediaPipe**](https://developers.google.com/mediapipe) — Google ML 推理框架
- [**MLC-LLM**](https://mlc.ai/mlc-llm/) — 通用机器学习编译器
- [**Google LiteRT**](https://ai.google.dev/edge/litert) — 端侧推理运行时

---

## 📄 许可证

[Apache License 2.0](LICENSE)

---

<p align="center">
  <strong>⭐ 如果这个项目对你有帮助，请给一个 Star！</strong>
</p>
