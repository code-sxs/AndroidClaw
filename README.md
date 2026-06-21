# AndroidClaw

<p align="center">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Status-In%20Development-yellow.svg" alt="Status" />
</p>

**AndroidClaw** 是 [PhoneClaw](https://github.com/kellyvv/PhoneClaw) (iOS) 的 Android 实现，一个完全离线运行的本地私人 AI Agent。

## 🌟 核心特性

- **🔒 完全离线**: 所有数据处理都在设备上完成，无需联网
- **🤖 三引擎推理**: MediaPipe LLM + MLC-LLM + LiteRT (TFLite)，自动选择最优引擎
- **📦 模块化模型**: 支持应用内下载安装 Gemma 4 E2B / Gemma 4 E4B / MiniCPM-V 4.6
- **🛠️ Skill 系统**: 类似 PhoneClaw 的基于文件的 Skill 扩展系统
- **🎨 现代 UI**: Jetpack Compose + Material Design 3 (动态配色)
- **🔊 多模态**: 支持图片理解 (MiniCPM-V)、语音输入输出
- **🔌 跨 App 自动化**: App Intents / Shortcuts / Deep Link 集成

## 🏗️ 架构设计

```
┌─────────────────────────────────────────┐
│           Agent 框架层                  │
│  (Skill 系统 / Tool Registry / 对话管理) │
└──────────────┬──────────────────────────┘
               │
┌──────────────┴──────────────────────────┐
│        模型管理抽象层 (LLMManager)       │
│  - 硬件检测 (NPU/GPU/CPU)            │
│  - 引擎自动选择                      │
│  - 模型下载管理                       │
└──────┬──────────┬──────────┬─────────┘
       │          │          │
┌──────▼───┐  ┌▼──────┐  ┌▼────────┐
│MediaPipe  │  │ MLC-   │  │ LiteRT  │
│LLM API    │  │ LLM    │  │ (TFLite)│
│(首选 GPU) │  │(备用)  │  │(第三选项)│
└───────────┘  └────────┘  └─────────┘
```

### 推理引擎优先级

1. **MediaPipe LLM Inference API** - 首选，支持 GPU 加速，Google 官方维护
2. **MLC-LLM** - 备用，支持更多模型格式，GPU 加速
3. **Google LiteRT (TFLite)** - 第三选项，最广泛兼容

## 📱 支持的模型

| 模型 | 参数规模 | 推荐设备 | 用途 |
|------|----------|----------|------|
| Gemma 4 E2B | 2B | 中端设备 (6GB+ RAM) | 快速推理、日常对话 |
| Gemma 4 E4B | 4B | 高端设备 (8GB+ RAM) | 高质量推理 |
| MiniCPM-V 4.6 | 4B | 高端设备 (8GB+ RAM) | 多模态 (图片理解) |

应用会自动检测硬件并推荐可用模型。

## 🚀 快速开始

### 系统要求

- Android 10 (API 29) 或更高版本
- 6GB+ RAM (推荐 8GB+)
- 支持 GPU / NPU 加速 (可选，但推荐)
- 存储空间: 2-4GB (取决于下载的模型)

### 构建步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/code-sxs/AndroidClaw.git
   cd AndroidClaw
   ```

2. **打开项目**
   - 使用 Android Studio Hedgehog (2023.1.1) 或更高版本
   - 等待 Gradle 同步完成

3. **下载模型**
   - 首次启动应用会引导下载模型
   - 或通过设置 > 模型管理手动下载

4. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 Run 按钮

### 从源码构建

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK (需要签名配置)
./gradlew assembleRelease

# 构建 Bundle (Google Play)
./gradlew bundleRelease
```

## 📦 项目结构

```
AndroidClaw/
├── app/
│   ├── src/main/
│   │   ├── java/com/androidclaw/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/                    # UI 层
│   │   │   │   ├── theme/            # Material 3 主题
│   │   │   │   ├── screens/          # 各屏幕 Composable
│   │   │   │   └── components/       # 复用 UI 组件
│   │   │   ├── llm/                  # 推理引擎层
│   │   │   │   ├── LLMManager.kt
│   │   │   │   ├── MediaPipeEngine.kt
│   │   │   │   ├── MLCEngine.kt
│   │   │   │   ├── LiteRTEngine.kt
│   │   │   │   └── ModelDownloader.kt
│   │   │   ├── agent/                # Agent 框架
│   │   │   │   ├── AgentManager.kt
│   │   │   │   ├── ToolRegistry.kt
│   │   │   │   └── ConversationManager.kt
│   │   │   ├── skills/               # Skill 系统
│   │   │   │   ├── SkillDefinition.kt
│   │   │   │   ├── calendar/
│   │   │   │   ├── contacts/
│   │   │   │   ├── clipboard/
│   │   │   │   ├── health/
│   │   │   │   ├── fileops/
│   │   │   │   └── websearch/
│   │   │   ├── multimodal/           # 多模态功能
│   │   │   │   ├── VisionManager.kt
│   │   │   │   └── CameraManager.kt
│   │   │   ├── voice/                # 语音功能
│   │   │   │   ├── ASRManager.kt
│   │   │   │   └── TTSManager.kt
│   │   │   └── data/                 # 数据层
│   │   │       ├── repository/
│   │   │       └── model/
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── LICENSE
└── README.md
```

## 🛠️ 内置 Skill

AndroidClaw 支持类似 PhoneClaw 的 Skill 系统，内置以下 Skill:

- **📅 日历**: 读取/创建/修改日历事件
- **📇 通讯录**: 搜索/读取联系人信息
- **📋 剪贴板**: 读取/写入剪贴板内容
- **🌐 翻译**: 文本翻译 (本地模型)
- **📁 文件操作**: 读取/写入/删除文件
- **❤️ 健康数据**: 读取健康数据 (Google Fit)
- **🔍 联网搜索**: 用户主动触发的联网搜索
- **📷 图片理解**: 分析图片内容 (MiniCPM-V)

## 🎨 UI/UX

AndroidClaw 使用 **Jetpack Compose** + **Material Design 3**:

- **动态配色**: 跟随系统壁纸自动适配
- **深色模式**: 完整支持
- **大屏适配**: 支持平板、折叠屏
- **无障碍**: 完整 TalkBack 支持

## 🔒 隐私与安全

- **100% 离线**: 无数据上传，无云端依赖
- **本地推理**: 所有 AI 计算都在设备上完成
- **权限最小化**: 仅在需要时请求权限
- **开源透明**: Apache 2.0 协议，代码完全透明

## 🗺️ 开发路线图

### Phase 1: 基础设施 + 核心聊天 (当前)
- [x] 项目初始化
- [ ] 三引擎推理层实现
- [ ] 模型下载管理
- [ ] 基础聊天 UI
- [ ] 内置 Skill (日历、通讯录、剪贴板、翻译)

### Phase 2: 扩展 Skill + 多模型协作
- [ ] 文件操作、健康数据、照片库 Skill
- [ ] 多模型协作链路 (小模型参数提取 + 大模型推理)
- [ ] 联网搜索 Skill

### Phase 3: 多模态 + 语音
- [ ] MiniCPM-V 4.6 图片理解
- [ ] LIVE 摄像头实时模式
- [ ] ASR (语音识别) + TTS (语音合成)

### Phase 4: 跨 App 自动化
- [ ] App Intents / Shortcuts 集成
- [ ] URL Scheme / Deep Link 调度
- [ ] 通知监听与唤起

### Phase 5: 外部硬件扩展
- [ ] 外部视频输入
- [ ] 屏幕画面理解
- [ ] 外部硬件联动

## 🤝 贡献指南

我们欢迎任何形式的贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

### 多 Agent 协作审查

每次提交前会通过多 Agent 协作审查:
1. **代码质量 Agent** - 检查 Kotlin 规范、Compose 最佳实践
2. **UI/UX Agent** - 检查 Material Design 3 合规性、无障碍
3. **安全 Agent** - 检查权限使用、数据隐私、推理引擎安全

不合格则自动打回重做。

## 📄 许可证

本项目采用 **Apache License 2.0** 开源协议。

详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- [PhoneClaw](https://github.com/kellyvv/PhoneClaw) - iOS 版本，本项目的灵感来源
- [MediaPipe](https://developers.google.com/mediapipe) - Google 的 ML 推理框架
- [MLC-LLM](https://github.com/mlc-ai/mlc-llm) - 高效 LLM 推理引擎
- [Google LiteRT](https://ai.google.dev/edge/litert) - TensorFlow Lite 的继任者

## 📧 联系方式

- **GitHub Issues**: [提交问题](https://github.com/code-sxs/AndroidClaw/issues)
- **讨论区**: [参与讨论](https://github.com/code-sxs/AndroidClaw/discussions)

---

<p align="center">
  Made with ❤️ by the AndroidClaw community
</p>
