# install_deps.ps1
# 自动安装 AndroidClaw 编译依赖（JDK 17 + Android SDK）

Write-Host "=== AndroidClaw 编译依赖自动安装 ===" -ForegroundColor Cyan
Write-Host ""

# 1. 下载并安装 JDK 17 (Eclipse Temurin)
Write-Host "[1/3] 下载 JDK 17..." -ForegroundColor Yellow
$jdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9.1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.1.msi"
$jdkInstaller = "$env:TEMP\jdk17.msi"

try {
    Invoke-WebRequest -Uri $jdkUrl -OutFile $jdkInstaller -ErrorAction Stop
    Write-Host "  ✓ JDK 17 下载成功" -ForegroundColor Green
} catch {
    Write-Host "  ✗ JDK 17 下载失败，请手动从 https://adoptium.net/temurin17/ 下载" -ForegroundColor Red
    exit 1
}

Write-Host "[2/3] 安装 JDK 17（静默安装）..." -ForegroundColor Yellow
Start-Process -FilePath "msiexec.exe" -ArgumentList "/i `"$jdkInstaller`" /quiet /norestart" -Wait
Write-Host "  ✓ JDK 17 安装完成" -ForegroundColor Green

# 2. 配置 JAVA_HOME
Write-Host "[3/3] 配置环境变量..." -ForegroundColor Yellow
$jdkPath = "C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, [System.EnvironmentVariableTarget]::User)
$env:JAVA_HOME = $jdkPath

$path = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
if (-not $path.Contains("%JAVA_HOME%\bin")) {
    [System.Environment]::SetEnvironmentVariable("Path", "$path;%JAVA_HOME%\bin", [System.EnvironmentVariableTarget]::User)
}
Write-Host "  ✓ JAVA_HOME 已配置: $jdkPath" -ForegroundColor Green

# 3. 验证 Java 安装
Write-Host ""
Write-Host "验证 Java 安装..." -ForegroundColor Cyan
Start-Sleep -Seconds 2
& "$jdkPath\bin\java.exe" -version

Write-Host ""
Write-Host "=== JDK 安装完成 ===" -ForegroundColor Cyan
Write-Host "下一步：手动安装 Android Studio（包含 Android SDK）" -ForegroundColor Yellow
Write-Host "下载地址: https://developer.android.com/studio" -ForegroundColor Blue
Write-Host ""
Write-Host "安装完成后，打开 Android Studio，它会自动下载 Android SDK。" -ForegroundColor Yellow
Write-Host "然后创建 local.properties 文件，内容：" -ForegroundColor Yellow
Write-Host "  sdk.dir=C:\\Users\\$env:USERNAME\\AppData\\Local\\Android\\Sdk" -ForegroundColor Gray
Write-Host ""
Write-Host "完成后，运行: .\gradlew.bat assembleDebug" -ForegroundColor Green
