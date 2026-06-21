# Voice Pipeline Implementation - Task Summary

**Date**: 2026-06-21  
**Project**: AndroidClaw  
**Task**: 实现完整的语音输入/输出管道

## 完成内容

### 1. 语音输入管理器 (VoiceInputManager.kt)
- 使用 Android 内置 SpeechRecognizer API
- 支持离线识别（通过 `PREFER_OFFLINE` extra）
- 支持在线识别（更高精度）
- 实时返回识别中的部分结果（流式）
- 超时自动停止（可配置超时时间）
- 完整的错误处理（6 种错误类型）
- 状态流式 API（StateFlow）

### 2. 语音输出管理器 (VoiceOutputManager.kt)
- 使用 Android 内置 TextToSpeech API
- 支持离线语音合成
- 支持中文语音（优先选择高质量中文语音引擎）
- 语速、音调可配置（范围 0.5-2.0）
- 队列管理（QUEUE_ADD / QUEUE_FLUSH）
- 打断当前播报（speakNow 方法）
- 初始化状态回调和错误处理

### 3. 唤醒词检测器 (WakeWordDetector.kt)
- 占位实现（输出 TODO 日志）
- 接口设计完整
- 配置项：唤醒词列表、灵敏度、后台监听开关
- TODO: 集成 Picovoice Porcupine 引擎

### 4. 统一语音管理器 (VoiceManager.kt)
- 单例模式
- 统一管理语音输入和输出
- 音效播放（开始/结束提示音，带 fallback）
- 配置类 VoiceConfig
- 状态流 VoiceStatus

### 5. 权限处理
- AndroidManifest.xml 已有 RECORD_AUDIO 权限
- 新增语音识别服务查询声明

### 6. 聊天界面更新 (ChatScreen.kt)
- 输入框左侧麦克风按钮
- 语音波形动画（Compose 实现）
- 识别中文字实时显示
- 状态提示（"正在听..."、"正在处理..."）
- 消息气泡播放按钮（仅助手消息）
- 顶部栏语音开关
- 语音设置对话框

### 7. ChatViewModel (ChatViewModel.kt)
- 管理聊天状态和消息列表
- 集成 VoiceManager
- 语音配置管理
- 自动发送语音识别结果（可配置）

### 8. AgentManager 更新
- 集成 VoiceManager
- 新增 sendVoiceMessage 方法
- 新增语音配置 API
- 新增语音输入/输出便捷方法

### 9. 音频资源
- res/raw/ 目录创建
- 占位说明文件（placeholder_audio.xml）
- 代码中有 fallback 机制，音频文件不存在时不影响功能

## 文件列表

新增文件：
- app/src/main/java/com/androidclaw/app/voice/VoiceInputManager.kt (261 行)
- app/src/main/java/com/androidclaw/app/voice/VoiceOutputManager.kt (275 行)
- app/src/main/java/com/androidclaw/app/voice/WakeWordDetector.kt (130 行)
- app/src/main/java/com/androidclaw/app/voice/VoiceManager.kt (293 行)
- app/src/main/java/com/androidclaw/app/ui/screens/ChatViewModel.kt (170 行)
- app/src/main/res/raw/placeholder_audio.xml

修改文件：
- app/src/main/AndroidManifest.xml (添加 queries 声明)
- app/src/main/java/com/androidclaw/app/agent/AgentManager.kt (集成语音)
- app/src/main/java/com/androidclaw/app/ui/screens/ChatScreen.kt (语音 UI)

## Git 状态
- 已 commit: `feat(voice): add STT/TTS voice pipeline`
- commit hash: 6ec87f4
- push 失败（网络问题），代码已提交到本地仓库

## 待完成（可选）
1. 唤醒词检测器实际实现（需要集成 Porcupine）
2. 添加实际音频资源文件（voice_start.mp3, voice_end.mp3, tts_start.mp3）
3. ChatViewModel 中实际调用 AgentManager 进行 LLM 推理

## 代码规范
- Kotlin 协程处理异步
- 完善的错误处理和日志
- 文件头部注释说明功能
- Compose UI 使用 Material Design 3
- StateFlow 响应式状态管理
