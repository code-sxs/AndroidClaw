# AndroidClaw 编译指南

## 依赖项

要编译 AndroidClaw 项目，需要以下依赖：

### 1. Java JDK (推荐 JDK 17)
- **下载地址**：https://adoptium.net/temurin17/
- **版本**：Eclipse Temurin JDK 17 (LTS)
- **Windows 安装包**：`.msi` 文件，直接安装

### 2. Android SDK
- **方式 A**：安装 Android Studio (推荐，自带 SDK)
  - 下载地址：https://developer.android.com/studio
- **方式 B**：仅安装 Android SDK Command-line Tools
  - 下载地址：https://developer.android.com/studio#command-tools

### 3. 环境变量配置

#### 安装 JDK 后：
- 设置 `JAVA_HOME` 环境变量，指向 JDK 安装目录
  - 例如：`C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot`
- 将 `%JAVA_HOME%\bin` 添加到 `PATH`

#### 安装 Android SDK 后：
- 设置 `ANDROID_HOME` 环境变量，指向 SDK 安装目录
  - 例如：`C:\Users\你的用户名\AppData\Local\Android\Sdk`
- 或者，在项目根目录创建 `local.properties` 文件，内容：
  ```
  sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
  ```

## 快速安装步骤 (Windows)

### 步骤 1：安装 JDK 17
1. 访问 https://adoptium.net/temurin17/
2. 下载 Windows MSI 安装包
3. 运行安装包，按照向导安装
4. 安装完成后，打开新的 PowerShell 窗口，运行 `java -version` 验证

### 步骤 2：安装 Android Studio (推荐)
1. 访问 https://developer.android.com/studio
2. 下载 Android Studio
3. 运行安装包，按照向导安装
4. 首次启动时，会自动下载 Android SDK

### 步骤 3：配置项目
1. 打开 Android Studio
2. 选择 "Open an existing Android Studio project"
3. 选择 `C:\Users\AYU20\.qclaw\workspace\AndroidClaw` 目录
4. 等待 Gradle 同步完成（会自动下载依赖）

### 步骤 4：编译项目
在 Android Studio 中：
- 点击菜单 `Build` → `Make Project` (Ctrl+F9)
- 或者，点击工具栏的 `Build` 按钮 ▶

或者使用命令行：
```powershell
cd C:\Users\AYU20\.qclaw\workspace\AndroidClaw
.\gradlew.bat assembleDebug
```

## 常见问题

### 问题 1：Gradle 下载慢
**解决方案**：配置 Gradle 国内镜像
1. 编辑 `gradle.properties` 文件
2. 添加以下内容：
```properties
systemProp.http.proxyHost=your-proxy-host
systemProp.http.proxyPort=your-proxy-port
systemProp.https.proxyHost=your-proxy-host
systemProp.https.proxyPort=your-proxy-port
```
或者使用国内镜像仓库（修改 `build.gradle.kts`）：
```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    google()
    mavenCentral()
}
```

### 问题 2：Android SDK 版本不匹配
**解决方案**：修改 `app/build.gradle.kts` 中的 `compileSdk` 和 `targetSdk` 版本

### 问题 3：Java 版本不匹配
**解决方案**：确保使用 JDK 17（Android Gradle Plugin 8.0+ 需要 JDK 17）

## 下一步

编译成功后：
1. APK 文件位于：`app/build/outputs/apk/debug/app-debug.apk`
2. 安装到手机：`adb install app/build/outputs/apk/debug/app-debug.apk`
3. 测试应用功能

## 参考资料

- [Android Studio 下载](https://developer.android.com/studio)
- [JDK 17 下载](https://adoptium.net/temurin17/)
- [Android Gradle Plugin 发行说明](https://developer.android.com/studio/releases/gradle-plugin)
- [配置 Gradle 代理](https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy)
