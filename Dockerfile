# AndroidClaw Docker 编译环境
# 使用 OpenJDK 17 作为基础镜像，手动安装 Android SDK

FROM openjdk:17-jdk-bullseye

# 设置环境变量
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0

# 安装依赖
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# 下载 Android SDK Command-line Tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest && \
    rm commandlinetools-linux-11076708_latest.zip

# 接受 Android SDK 许可
RUN yes | sdkmanager --licenses || true

# 安装 Android SDK 组件
RUN sdkmanager \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "extras;android;m2repository" \
    "extras;google;m2repository"

# 设置工作目录
WORKDIR /workspace

# 复制项目文件
COPY . /workspace/

# 赋予 Gradle wrapper 执行权限
RUN chmod +x ./gradlew

# 预下载 Gradle 和依赖（加速首次编译）
RUN ./gradlew --version

# 设置默认命令（编译 Debug APK）
CMD ["./gradlew", "assembleDebug", "--stacktrace", "--no-daemon"]
