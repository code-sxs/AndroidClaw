// settings.gradle.kts
// AndroidClaw 项目配置

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        // MediaPipe 依赖
        maven { url = uri("https://mediapipe-releases.storage.googleapis.com/maven") }
        // MLC-LLM 依赖（需要时取消注释并使用正确 Maven 地址）
        // maven { url = uri("https://repo.mlc.ai/maven") }
    }
}

rootProject.name = "AndroidClaw"
include(":app")
