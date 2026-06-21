# RemoteClaw API 规范

> **版本**: 1.0.0  
> **日期**: 2026-06-21  
> **描述**: RemoteClaw 服务器的 REST API 和 WebSocket 协议规范，供 AndroidClaw 客户端连接使用。

---

## 目录

1. [概述](#1-概述)
2. [认证](#2-认证)
3. [REST API](#3-rest-api)
4. [WebSocket 协议](#4-websocket-协议)
5. [错误码](#5-错误码)
6. [请求/响应示例](#6-请求响应示例)

---

## 1. 概述

### 基础信息

| 项目 | 值 |
|------|-----|
| 协议 | HTTP REST + WebSocket |
| 基础 URL | `http://localhost:8080/v1` |
| WebSocket URL | `ws://localhost:8080` |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

### 服务器要求

- **硬件**: 推荐 8GB+ RAM, NVIDIA GPU (4GB+ VRAM)
- **操作系统**: Linux (Ubuntu 20.04+), macOS
- **依赖**: Python 3.10+, CUDA 12.x (GPU)

---

## 2. 认证

### Bearer Token

所有请求（除 `/health` 外）需要在 Header 中携带认证 Token：

```
Authorization: Bearer <token>
```

### Token 配置

- 服务器启动时通过环境变量或配置文件设置 Token
- Token 由管理员生成，分发给授权客户端
- Token 支持设置过期时间

### 示例

```http
GET /v1/tools HTTP/1.1
Host: localhost:8080
Authorization: Bearer sk-remote-claw-secret-token
```

---

## 3. REST API

### 3.1 健康检查

```
GET /health
```

检查服务器状态。

**响应 (200 OK):**

```json
{
  "status": "ok",
  "version": "1.0.0",
  "models": [
    "llama-3.1-8b",
    "qwen2.5-7b",
    "gemma-2-2b"
  ],
  "server_time": "2026-06-21T07:30:00Z"
}
```

---

### 3.2 文本生成（非流式）

```
POST /v1/generate
Content-Type: application/json
Authorization: Bearer <token>

{
  "prompt": "用户消息",
  "history": [
    {"role": "user", "content": "之前的问题"},
    {"role": "assistant", "content": "之前的回答"}
  ],
  "tools": [...],        // 可选，工具定义列表
  "stream": false,
  "stream_options": {
    "include_usage": true
  }
}
```

**响应 (200 OK):**

```json
{
  "id": "msg_abc123",
  "text": "这是 AI 的回复文本",
  "tool_calls": [
    {
      "tool_name": "calendar_check",
      "parameters": {"date": "2026-06-22"}
    }
  ],
  "done": true,
  "usage": {
    "prompt_tokens": 120,
    "completion_tokens": 45,
    "total_tokens": 165
  }
}
```

**请求字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `prompt` | string | ✅ | 当前用户消息 |
| `history` | array | ❌ | 对话历史，每项包含 `role` 和 `content` |
| `tools` | array | ❌ | 可用工具定义列表 |
| `stream` | boolean | ❌ | 是否流式响应（false = 非流式） |
| `stream_options` | object | ❌ | 流式选项 |

---

### 3.3 文本生成（流式）

```
POST /v1/generate
Content-Type: application/json
Authorization: Bearer <token>

{
  "prompt": "写一首诗",
  "stream": true,
  "stream_options": {
    "include_usage": true
  }
}
```

**响应: Server-Sent Events (SSE)**

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Transfer-Encoding: chunked

data: {"id":"msg_abc","text":"春","done":false}

data: {"id":"msg_abc","text":"眠","done":false}

data: {"id":"msg_abc","text":"不觉","done":false}

data: {"id":"msg_abc","text":"晓","done":false}

data: {"id":"msg_abc","text":"","done":true,"usage":{"prompt_tokens":5,"completion_tokens":8,"total_tokens":13}}

data: [DONE]
```

---

### 3.4 图片分析

```
POST /v1/vision
Content-Type: application/json
Authorization: Bearer <token>

{
  "image_url": "https://example.com/image.jpg",  // 或
  "image_base64": "data:image/jpeg;base64,/9j/4AAQ...",
  "prompt": "描述这张图片"
}
```

**响应 (200 OK):**

```json
{
  "description": "一只橘色的猫在阳光下睡觉",
  "objects": ["猫", "阳光", "沙发"],
  "tags": ["动物", "室内", "温馨"]
}
```

---

### 3.5 获取工具列表

```
GET /v1/tools
Authorization: Bearer <token>
```

**响应 (200 OK):**

```json
{
  "tools": [
    {
      "tool_name": "calendar_check",
      "description": "检查日历中的日程",
      "parameters": [
        {
          "name": "date",
          "description": "要查询的日期 (YYYY-MM-DD)",
          "type": "string",
          "required": true,
          "enum_values": null,
          "default": null
        }
      ]
    },
    {
      "tool_name": "web_search",
      "description": "搜索互联网获取信息",
      "parameters": [
        {
          "name": "query",
          "description": "搜索关键词",
          "type": "string",
          "required": true
        },
        {
          "name": "max_results",
          "description": "最大结果数",
          "type": "integer",
          "required": false,
          "default": 5
        }
      ]
    }
  ]
}
```

---

### 3.6 调用工具

```
POST /v1/tools/call
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "calendar_check",
  "parameters": {"date": "2026-06-22"}
}
```

**响应 (200 OK):**

```json
{
  "id": "tool_call_xyz",
  "text": "2026-06-22 有以下日程：\n- 10:00 团队会议\n- 15:00 1对1",
  "tool_calls": null,
  "done": true,
  "usage": null
}
```

---

### 3.7 获取可用模型列表

```
GET /v1/models
Authorization: Bearer <token>
```

**响应 (200 OK):**

```json
{
  "models": [
    {
      "name": "llama-3.1-8b-instruct",
      "display_name": "Llama 3.1 8B",
      "supports_vision": false,
      "supports_tools": true,
      "max_tokens": 8192,
      "context_window": 128000
    },
    {
      "name": "qwen2.5-14b-instruct",
      "display_name": "Qwen 2.5 14B",
      "supports_vision": false,
      "supports_tools": true,
      "max_tokens": 4096,
      "context_window": 32768
    }
  ],
  "current_model": "llama-3.1-8b-instruct"
}
```

---

### 3.8 切换模型

```
POST /v1/models/select
Content-Type: application/json
Authorization: Bearer <token>

{
  "model": "qwen2.5-14b-instruct"
}
```

**响应 (200 OK):**

```json
{
  "status": "ok",
  "current_model": "qwen2.5-14b-instruct"
}
```

---

## 4. WebSocket 协议

### 4.1 连接

```
ws://localhost:8080?token=<token>
```

**连接成功:**

```json
{"type": "connected", "server_version": "1.0.0"}
```

### 4.2 客户端请求格式

```json
{
  "type": "generateStream",  // 请求类型
  "id": "req_123",           // 请求 ID（用于匹配响应）
  "payload": {
    "prompt": "...",
    "history": [...],
    "tools": [...]
  }
}
```

**支持的请求类型:**

| type | 说明 |
|------|------|
| `generate` | 非流式文本生成 |
| `generateStream` | 流式文本生成 |
| `analyzeImage` | 图片分析 |
| `listTools` | 获取工具列表 |
| `callTool` | 调用工具 |
| `ping` | 心跳保活 |

### 4.3 服务器响应格式

```json
{
  "id": "req_123",           // 对应请求 ID
  "type": "chunk",           // 响应类型
  "data": "文本片段",         // 数据
  "error": null              // 错误信息（如果有）
}
```

**响应类型:**

| type | 说明 |
|------|------|
| `connected` | 连接成功 |
| `chunk` | 流式文本块 |
| `text` | 完整文本（非流式） |
| `done` | 流式传输完成 |
| `error` | 错误 |
| `tools` | 工具列表响应 |
| `toolResult` | 工具调用结果 |
| `pong` | 心跳响应 |

### 4.4 流式示例

**请求:**
```json
{
  "type": "generateStream",
  "id": "req_abc",
  "payload": {"prompt": "讲个笑话"}
}
```

**响应序列:**
```
{"id":"req_abc","type":"chunk","data":"从前","error":null}
{"id":"req_abc","type":"chunk","data":"有","error":null}
{"id":"req_abc","type":"chunk","data":"一只","error":null}
{"id":"req_abc","type":"chunk","data":"猫...","error":null}
{"id":"req_abc","type":"done","data":null,"error":null}
```

### 4.5 心跳

客户端应每 30 秒发送一次心跳:

```json
{"type": "ping", "id": "ping_1", "payload": null}
```

服务器响应:

```json
{"id": "ping_1", "type": "pong", "data": "pong", "error": null}
```

---

## 5. 错误码

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求格式错误 |
| 401 | 未授权（Token 无效或缺失）|
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 429 | 请求过于频繁（限流）|
| 500 | 服务器内部错误 |
| 502 | 上游服务错误 |
| 503 | 服务暂时不可用 |

### 错误响应格式

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "错误描述",
    "details": {}
  }
}
```

### 错误代码

| code | 说明 |
|------|------|
| `INVALID_REQUEST` | 请求格式或参数错误 |
| `UNAUTHORIZED` | Token 无效或过期 |
| `FORBIDDEN` | 没有权限执行此操作 |
| `NOT_FOUND` | 请求的资源不存在 |
| `RATE_LIMITED` | 请求频率超限 |
| `MODEL_NOT_FOUND` | 指定的模型不可用 |
| `MODEL_NOT_SUPPORTED` | 模型不支持该功能（如不支持视觉）|
| `TOOL_NOT_FOUND` | 工具不存在 |
| `TOOL_EXECUTION_ERROR` | 工具执行失败 |
| `INTERNAL_ERROR` | 服务器内部错误 |
| `SERVICE_UNAVAILABLE` | 服务暂时不可用 |

---

## 6. 请求/响应示例

### 6.1 完整对话流程（REST）

```http
POST /v1/generate HTTP/1.1
Host: localhost:8080
Authorization: Bearer sk-xxx
Content-Type: application/json

{
  "prompt": "明天天气怎么样？",
  "history": [
    {"role": "user", "content": "我在北京"},
    {"role": "assistant", "content": "好的，已记住您在北京。请问有什么可以帮您？"}
  ],
  "tools": [
    {
      "tool_name": "weather_check",
      "description": "查询天气预报",
      "parameters": [
        {"name": "city", "type": "string", "required": true, "description": "城市名称"}
      ]
    }
  ]
}
```

```json
{
  "id": "msg_001",
  "text": "好的，让我帮您查询一下北京明天的天气。",
  "tool_calls": [
    {
      "tool_name": "weather_check",
      "parameters": {"city": "北京"}
    }
  ],
  "done": true
}
```

```http
POST /v1/tools/call HTTP/1.1
Host: localhost:8080
Authorization: Bearer sk-xxx
Content-Type: application/json

{"name": "weather_check", "parameters": {"city": "北京"}}
```

```json
{
  "id": "tool_001",
  "text": "北京明天（2026-06-22）天气：\n- 天气：多云转晴\n- 温度：24-32°C\n- 风力：东南风 2-3级",
  "done": true
}
```

### 6.2 流式生成（cURL 示例）

```bash
curl -X POST http://localhost:8080/v1/generate \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "写一段代码", "stream": true}' \
  -N
```

### 6.3 WebSocket 连接示例（Python）

```python
import websockets
import json

async def main():
    uri = "ws://localhost:8080?token=sk-xxx"
    async with websockets.connect(uri) as ws:
        # 发送请求
        await ws.send(json.dumps({
            "type": "generateStream",
            "id": "req_1",
            "payload": {"prompt": "你好"}
        }))

        # 接收响应
        async for message in ws:
            data = json.loads(message)
            if data["type"] == "chunk":
                print(data["data"], end="", flush=True)
            elif data["type"] == "done":
                print()  # 换行
                break

import asyncio
asyncio.run(main())
```

### 6.4 图片分析示例

```bash
curl -X POST http://localhost:8080/v1/vision \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "image_base64": "'$(base64 -w0 image.jpg)'",
    "prompt": "这张图片里有什么？"
  }'
```

```json
{
  "description": "一只白色的猫正在窗台上晒太阳",
  "objects": ["猫", "窗台", "阳光"],
  "tags": ["动物", "室内", "温馨"]
}
```

---

## 附录 A: 工具定义 JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "tool_name": {
      "type": "string",
      "description": "工具的唯一标识名称（snake_case）"
    },
    "description": {
      "type": "string",
      "description": "工具功能的自然语言描述，供 LLM 理解何时使用"
    },
    "parameters": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "description": {"type": "string"},
          "type": {"type": "string", "enum": ["string", "integer", "number", "boolean", "array", "object"]},
          "required": {"type": "boolean", "default": false},
          "enum_values": {"type": "array", "items": {"type": "string"}},
          "default": {}
        },
        "required": ["name", "type"]
      }
    }
  },
  "required": ["tool_name", "description"]
}
```

---

*本文档由 AndroidClaw 自动生成 | RemoteClaw 服务器版本 1.0.0*
