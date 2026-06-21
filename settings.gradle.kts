// settings.gradle.kts
// AndroidClaw 项目配置

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MediaPipe 依赖
        maven { url = uri("https://mediapipe-releases.storage.googleapis.com/maven") }
        // MLC-LLM 依赖
        maven { url = uri("https://mlc.ai/mlc-llm/wheels/release") }
    }
}

rootProject.name = "AndroidClaw"
include(":app")
