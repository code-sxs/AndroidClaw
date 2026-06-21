# RemoteClaw 部署指南

> **版本**: 1.0.0  
> **日期**: 2026-06-21  
> **描述**: 在服务器上部署 RemoteClaw AI 推理服务，并在 AndroidClaw 中配置连接的完整指南。

---

## 目录

1. [概述](#1-概述)
2. [服务器环境准备](#2-服务器环境准备)
3. [安装 RemoteClaw 服务器](#3-安装-remoteclaw-服务器)
4. [配置](#4-配置)
5. [启动服务](#5-启动服务)
6. [验证服务](#6-验证服务)
7. [在 AndroidClaw 中配置连接](#7-在-androidclaw-中配置连接)
8. [常见问题](#8-常见问题)

---

## 1. 概述

### 什么是 RemoteClaw？

RemoteClaw 是一个轻量级的 AI 推理服务器，允许 AndroidClaw 客户端通过局域网或互联网连接使用更强力的 AI 模型。它支持：

- **多模型**: Llama 3.1, Qwen 2.5, Gemma 2 等开源模型
- **工具调用**: 支持 Function Calling / Tool Use
- **流式响应**: 实时打字机效果
- **多客户端**: 同时服务多个 Android 设备
- **本地网络**: 无需互联网，局域网即可使用

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    RemoteClaw 服务器                        │
│                    (Linux / Mac)                            │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────┐  │
│  │  HTTP    │  │ WebSocket│  │  AI Model Inference       │  │
│  │  API     │  │ Server   │  │  (llama.cpp / vLLM)       │  │
│  │  :8080   │  │  :8080   │  │                          │  │
│  └────┬─────┘  └────┬─────┘  └──────────┬───────────────┘  │
│       │            │                    │                   │
└───────┼────────────┼────────────────────┼─────────────────┘
        │            │                    │
        │   WiFi / LAN                  GPU
        │                                │
┌───────┴────────────────────────────────┴──────────────────┐
│                   AndroidClaw 客户端                       │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  RemoteInferenceManager                              │   │
│  │  • 自动降级到本地模型（如果远程不可用）                  │   │
│  │  • HTTP REST / WebSocket 双协议支持                   │   │
│  │  • 认证 Token 管理                                    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 与本地模型对比

| 对比项 | 本地模型 (LLMManager) | RemoteClaw |
|--------|---------------------|------------|
| 延迟 | 取决于设备性能 | 网络延迟（局域网 < 50ms）|
| 模型大小 | 受手机内存限制 | 服务器 GPU 显存决定 |
| 功耗 | 手机发热大 | 手机零功耗 |
| 离线 | ✅ 完全离线 | 需要服务器在线 |
| 工具调用 | ✅ 支持 | ✅ 支持 |
| 适用场景 | 离线/轻量任务 | 复杂推理/长对话 |

---

## 2. 服务器环境准备

### 硬件要求

| 级别 | CPU | RAM | GPU | 适用场景 |
|------|-----|-----|-----|---------|
| 入门 | 4 核 | 8GB | 无（CPU 推理） | 测试/轻量模型 |
| 推荐 | 8 核 | 16GB | NVIDIA 4GB+ | 7B 模型 |
| 高性能 | 16 核 | 32GB | NVIDIA 8GB+ | 14B 模型 |
| 专业 | 32 核 | 64GB | NVIDIA 16GB+ | 70B+ 模型 |

### 软件要求

```bash
# Python 3.10+
python3 --version  # >= 3.10

# CUDA (GPU 用户)
nvcc --version  # >= 11.8
nvidia-smi        # 查看 GPU

# Git
git --version

# pip
pip3 --version
```

### 安装依赖（Ubuntu/Debian）

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装基础依赖
sudo apt install -y python3 python3-pip python3-venv git curl wget build-essential

# 安装 CUDA (如果使用 GPU)
# 参考: https://developer.nvidia.com/cuda-downloads
# 选择: Linux > x86_64 > Ubuntu > 22.04 > deb (network)
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2204/x86_64/cuda-keyring_1.0-1_all.deb
sudo dpkg -i cuda-keyring_1.0-1_all.deb
sudo apt update
sudo apt install -y cuda-toolkit-12
```

### macOS 准备

```bash
# 使用 Homebrew
brew install python@3.11 git

# macOS 不支持 CUDA，使用 CPU 推理（推荐 llama.cpp）
```

---

## 3. 安装 RemoteClaw 服务器

### 方式一：pip 安装（推荐）

```bash
# 创建虚拟环境
python3 -m venv remote-claw-env
source remote-claw-env/bin/activate

# 安装 RemoteClaw
pip install remote-claw[all]

# 查看安装结果
remote-claw --version
```

### 方式二：从源码安装

```bash
# 克隆仓库
git clone https://github.com/your-org/remote-claw.git
cd remote-claw

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate    # Windows

# 安装依赖
pip install -e ".[all]"

# 查看安装结果
python -m remote_claw --version
```

### 方式三：Docker 部署（推荐生产环境）

```bash
# 拉取镜像
docker pull remoteclaw/server:latest

# 运行（CPU 推理）
docker run -d \
  --name remote-claw \
  -p 8080:8080 \
  -e AUTH_TOKEN=your-secret-token \
  -e DEFAULT_MODEL=llama-3.1-8b \
  -v ~/remote-claw-models:/models \
  remoteclaw/server:latest

# 运行（GPU 推理，需要 nvidia-container-toolkit）
docker run -d \
  --name remote-claw \
  --gpus all \
  -p 8080:8080 \
  -e AUTH_TOKEN=your-secret-token \
  -e DEFAULT_MODEL=llama-3.1-8b \
  -v ~/remote-claw-models:/models \
  remoteclaw/server:latest
```

---

## 4. 配置

### 配置文件

RemoteClaw 使用 YAML 配置文件。创建 `~/.remote-claw/config.yaml`:

```yaml
# 服务配置
server:
  host: "0.0.0.0"      # 监听地址（0.0.0.0 = 所有接口）
  port: 8080            # HTTP/WebSocket 端口
  workers: 4             # 工作进程数

# 认证配置
auth:
  enabled: true
  token: "sk-your-secret-token-here"  # 替换为安全的随机字符串

# 模型配置
models:
  default: "llama-3.1-8b-instruct"
  storage_path: "~/remote-claw-models"

  # 模型列表
  available:
    - name: "llama-3.1-8b-instruct"
      path: "~/remote-claw-models/llama-3.1-8b-instruct-q4_k_m.gguf"
      type: "llama.cpp"
      context_window: 32768
      supports_vision: false
      supports_tools: true

    - name: "qwen2.5-7b-instruct"
      path: "~/remote-claw-models/qwen2.5-7b-instruct-q4_k_m.gguf"
      type: "llama.cpp"
      context_window: 8192
      supports_vision: false
      supports_tools: true

    - name: "gemma-2-2b-it"
      path: "~/remote-claw-models/gemma-2-2b-it-q4_k_m.gguf"
      type: "llama.cpp"
      context_window: 8192
      supports_vision: false
      supports_tools: true

# 工具配置
tools:
  enabled: true
  definitions_path: "~/remote-claw-tools/"
  timeout_seconds: 30

# 日志配置
logging:
  level: "INFO"
  file: "~/remote-claw/server.log"
```

### 生成安全 Token

```bash
# 使用 Python 生成随机 Token
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
# 输出: dGhpcyBpcyBhIHNhbXBsZSB0b2tlbi4uLgp0b2tlbiBmb3IgYXV0aGVudGljYXRpb24=
```

### 下载模型文件

RemoteClaw 推荐使用 GGUF 格式的量化模型（llama.cpp）:

```bash
# 创建模型目录
mkdir -p ~/remote-claw-models

# 使用 Ollama 下载（推荐方式）
# 安装 Ollama: https://ollama.com/download
ollama pull llama-3.1:8b

# 或直接从 HuggingFace 下载
# 前往 https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct-GGUF
# 下载 Q4_K_M 量化版本（约 4.9GB）

# Windows 用户使用 huggingface-cli
pip install huggingface_hub
huggingface-cli download meta-llama/Llama-3.1-8B-Instruct-GGUF \
  Llama-3.1-8B-Instruct-Q4_K_M.gguf \
  --local-dir ~/remote-claw-models
```

---

## 5. 启动服务

### 开发模式（前台运行）

```bash
# 激活虚拟环境
source remote-claw-env/bin/activate

# 启动服务器
remote-claw serve --config ~/.remote-claw/config.yaml

# 或使用默认配置（首次运行会提示配置）
remote-claw serve
```

### 生产模式（后台运行）

```bash
# 使用 systemd（推荐 Linux）
sudo tee /etc/systemd/system/remote-claw.service > /dev/null <<EOF
[Unit]
Description=RemoteClaw AI Inference Server
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME
ExecStart=$HOME/remote-claw-env/bin/remote-claw serve --config $HOME/.remote-claw/config.yaml
Restart=always
RestartSec=10
Environment="PYTHONUNBUFFERED=1"

[Install]
WantedBy=multi-user.target
EOF

# 启用并启动服务
sudo systemctl daemon-reload
sudo systemctl enable remote-claw
sudo systemctl start remote-claw

# 检查状态
sudo systemctl status remote-claw
```

### Docker 运行

```bash
# 查看日志
docker logs -f remote-claw

# 重启
docker restart remote-claw
```

### 验证启动成功

```bash
# 查看端口是否监听
ss -tlnp | grep 8080
# 或
netstat -tlnp | grep 8080

# 查看日志
tail -f ~/remote-claw/server.log
```

---

## 6. 验证服务

### 健康检查

```bash
curl http://localhost:8080/health
```

预期输出：
```json
{
  "status": "ok",
  "version": "1.0.0",
  "models": ["llama-3.1-8b-instruct"],
  "server_time": "2026-06-21T07:30:00Z"
}
```

### 测试文本生成

```bash
curl -X POST http://localhost:8080/v1/generate \
  -H "Authorization: Bearer sk-your-secret-token-here" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "你好，请介绍一下自己", "stream": false}'
```

### 测试流式生成

```bash
curl -X POST http://localhost:8080/v1/generate \
  -H "Authorization: Bearer sk-your-secret-token-here" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "从1数到5", "stream": true}' \
  -N
```

### 获取工具列表

```bash
curl http://localhost:8080/v1/tools \
  -H "Authorization: Bearer sk-your-secret-token-here"
```

---

## 7. 在 AndroidClaw 中配置连接

### 7.1 获取服务器 IP 地址

**在服务器上执行:**

```bash
# Linux
ip addr show | grep "inet "      # 找 192.168.x.x 或 10.x.x.x
hostname -I

# macOS
ifconfig | grep "inet "          # 找 192.168.x.x 或 10.x.x.x

# Windows (PowerShell)
ipconfig | findstr /i "IPv4"
```

### 7.2 AndroidClaw 配置步骤

1. 打开 AndroidClaw 应用
2. 进入 **设置** → **远程推理**
3. 填写配置：

| 配置项 | 值 |
|--------|-----|
| **服务器地址** | `http://192.168.1.100:8080`（替换为你的服务器 IP）|
| **认证 Token** | `sk-your-secret-token-here` |
| **连接超时** | 30 秒 |
| **使用 WebSocket** | ✅ 开启（推荐）|
| **启用远程推理** | ✅ 开启 |

4. 点击 **测试连接**
5. 等待连接成功后，**保存** 配置

### 7.3 局域网防火墙配置

**如果 Android 无法连接，检查服务器防火墙:**

```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 8080/tcp
sudo ufw status

# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# macOS（通常不需要）
# 检查系统偏好设置 > 安全性与隐私 > 防火墙
```

**Android 端网络权限:**

确保 AndroidClaw 已获取局域网权限。在 Android 10+ 设备上，需要在应用的 `AndroidManifest.xml` 中声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

AndroidClaw 默认已包含这些权限（见 `AndroidManifest.xml`）。

---

## 8. 常见问题

### Q1: 连接被拒绝

```
Connection refused: /192.168.1.100:8080
```

**解决:**
1. 确认服务器进程正在运行：`ps aux | grep remote-claw`
2. 确认端口正确：检查配置中的 `server.port`
3. 检查防火墙：`sudo ufw status`

### Q2: 401 Unauthorized

```
{"error": {"code": "UNAUTHORIZED", "message": "Invalid token"}}
```

**解决:**
1. 检查 Token 是否正确（注意空格）
2. 确认服务器配置中 `auth.enabled: true`
3. 检查 AndroidClaw 配置中的 Token 与服务器一致

### Q3: 模型加载失败

```
Error: Model not found or failed to load
```

**解决:**
1. 确认模型文件路径正确
2. 确认文件格式正确（`.gguf`）
3. 检查磁盘空间
4. 查看日志获取详细错误

### Q4: 流式响应卡顿

**解决:**
1. 使用 WebSocket 代替 HTTP（AndroidClaw 中开启 WebSocket）
2. 降低 `context_window` 大小
3. 使用有线网络代替 Wi-Fi
4. 检查服务器 CPU/GPU 负载

### Q5: 手机和服务器不在同一网络

**解决 - 方案 A: 内网穿透（frp）**

```ini
# frp 服务器配置 (frps.ini)
[common]
bind_port = 7000
token = your-token

# 手机端配置 (frpc.ini)
[common]
server_addr = your-vps-ip
server_port = 7000
token = your-token

[remote-claw]
type = tcp
local_ip = 192.168.1.100
local_port = 8080
remote_port = 8080
```

**解决 - 方案 B: Cloudflare Tunnel**

```bash
cloudflared tunnel --url http://localhost:8080
# 获取 public URL，填入 AndroidClaw
```

### Q6: 服务器无 GPU，推理太慢

**解决:**
1. 使用更小的量化模型（Q2_K 代替 Q4_K_M）
2. 使用更小的模型（2B 模型代替 8B）
3. 启用 GPU 加速（安装 CUDA + llama-cpp-python with CUDA）

```bash
# 安装 CUDA 版本的 llama-cpp-python
pip install llama-cpp-python --force-reinstall --no-cache-dir \
  --config-settings="--extra-cflags=-I/usr/local/cuda/include" \
  --config-settings="--extra-ldflags=-L/usr/local/cuda/lib64"
```

---

## 快速命令参考

```bash
# 安装
pip install remote-claw

# 启动
remote-claw serve

# 检查健康
curl http://localhost:8080/health

# 查看帮助
remote-claw --help

# 后台运行（screen）
screen -S remote-claw
remote-claw serve
# Ctrl+A D 分离
# screen -r remote-claw 恢复

# Docker
docker run -d --name remote-claw -p 8080:8080 \
  -e AUTH_TOKEN=sk-test remoteclaw/server:latest
```

---

*本指南由 AndroidClaw 团队编写 | 最后更新: 2026-06-21*
