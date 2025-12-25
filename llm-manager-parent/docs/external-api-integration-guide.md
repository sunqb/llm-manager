# 外部 API 对接指南

本文档介绍如何对接 LLM Manager 的外部 API，包括直接调用 LLM 模型和调用 ReactAgent 智能体。

## 目录

- [概述](#概述)
- [认证方式](#认证方式)
- [API 端点总览](#api-端点总览)
- [直接调用 LLM 模型](#直接调用-llm-模型)
  - [Chat Completions](#chat-completions)
  - [获取模型列表](#获取模型列表)
- [调用 ReactAgent 智能体](#调用-reactagent-智能体)
  - [Agent Completions](#agent-completions)
  - [获取 Agent 列表](#获取-agent-列表)
- [REST API（原生接口）](#rest-api原生接口)
- [流式响应处理](#流式响应处理)
- [错误处理](#错误处理)
- [完整代码示例](#完整代码示例)

---

## 概述

LLM Manager 提供两套外部 API：

| API 类型 | 用途 | 特点 |
|---------|------|------|
| **OpenAI 兼容 API** | 直接调用 LLM 模型 / ReactAgent | 兼容 OpenAI SDK，易于集成 |
| **REST API** | ReactAgent 专用 | 更丰富的返回信息，进度流 |

### 架构说明

```
┌─────────────────────────────────────────────────────────────────┐
│                        外部 API 层                               │
├─────────────────────────┬───────────────────────────────────────┤
│   OpenAI 兼容 API       │         REST API                      │
│                         │                                       │
│  /v1/chat/completions   │  /api/external/react-agent/{slug}    │
│  /v1/models             │  /api/external/react-agent/list      │
│  /v1/agents/{slug}/*    │                                       │
├─────────────────────────┴───────────────────────────────────────┤
│                      API Key 认证                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 认证方式

所有外部 API 都需要通过 **API Key** 进行认证。

### 获取 API Key

1. 登录 LLM Manager 管理后台
2. 进入「API 密钥」页面
3. 创建新的 API Key

### 使用方式

在 HTTP 请求头中添加 `Authorization` 字段：

```http
Authorization: Bearer sk-xxxx
```

或直接使用 API Key（不带 Bearer 前缀）：

```http
Authorization: sk-xxxx
```

### 示例

```bash
curl -H "Authorization: Bearer sk-xxxx" https://your-domain/v1/models
```

---

## API 端点总览

### OpenAI 兼容 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/chat/completions` | POST | 直接调用 LLM 模型（同步/流式） |
| `/v1/chat/completions/stream` | POST | 直接调用 LLM 模型（显式流式） |
| `/v1/models` | GET | 获取可用 LLM 模型列表 |
| `/v1/models/{model}` | GET | 获取指定模型详情 |
| `/v1/agents` | GET | 获取可用 Agent 列表 |
| `/v1/agents/{slug}` | GET | 获取指定 Agent 详情 |
| `/v1/agents/{slug}/completions` | POST | 调用 Agent（同步/流式） |
| `/v1/agents/{slug}/completions/stream` | POST | 调用 Agent（显式流式） |

### REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/external/react-agent/{slug}` | POST | 同步执行 ReactAgent |
| `/api/external/react-agent/{slug}/stream` | POST | 流式执行 ReactAgent（SSE 进度流） |
| `/api/external/react-agent/list` | GET | 获取可用 Agent 列表 |
| `/api/external/react-agent/{slug}` | GET | 获取 Agent 详情 |

---

## 直接调用 LLM 模型

### Chat Completions

**端点**: `POST /v1/chat/completions`

直接调用配置的 LLM 模型，完全兼容 OpenAI Chat Completions API 格式。

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `model` | string | 是 | 模型标识，支持模型 ID（如 "1"）或 modelIdentifier（如 "gpt-4"） |
| `messages` | array | 是 | 消息数组 |
| `stream` | boolean | 否 | 是否启用流式输出，默认 false |
| `temperature` | number | 否 | 温度参数（0-2），默认使用模型配置 |
| `max_tokens` | number | 否 | 最大输出 token 数 |

#### 消息格式

```json
{
  "messages": [
    {"role": "system", "content": "你是一个有帮助的助手"},
    {"role": "user", "content": "你好"}
  ]
}
```

#### 同步请求示例

```bash
curl -X POST https://your-domain/v1/chat/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [
      {"role": "system", "content": "你是一个翻译助手"},
      {"role": "user", "content": "翻译：Hello World"}
    ]
  }'
```

#### 同步响应格式

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1703123456,
  "model": "gpt-4",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "你好，世界"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 10,
    "total_tokens": 30
  }
}
```

#### 流式请求示例

```bash
curl -N -X POST https://your-domain/v1/chat/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
  }'
```

#### 流式响应格式（SSE）

```
data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","model":"gpt-4","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","model":"gpt-4","choices":[{"index":0,"delta":{"content":"春"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","model":"gpt-4","choices":[{"index":0,"delta":{"content":"风"},"finish_reason":null}]}

data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","model":"gpt-4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

### 获取模型列表

**端点**: `GET /v1/models`

获取系统中配置的所有 LLM 模型。

```bash
curl https://your-domain/v1/models \
  -H "Authorization: Bearer sk-xxxx"
```

**响应**:

```json
{
  "object": "list",
  "data": [
    {
      "id": "1",
      "object": "model",
      "created": 1703123456,
      "owned_by": "llm-manager",
      "description": "GPT-4 (gpt-4)"
    },
    {
      "id": "2",
      "object": "model",
      "created": 1703123456,
      "owned_by": "llm-manager",
      "description": "Qwen Plus (qwen-plus)"
    }
  ]
}
```

---

## 调用 ReactAgent 智能体

ReactAgent 是具有自主推理能力的智能体，支持三种类型：

| 类型 | 说明 | 适用场景 |
|------|------|---------|
| `SINGLE` | 单个 ReAct Agent | 通用任务、工具调用 |
| `SEQUENTIAL` | 顺序执行多个 Agent | 流水线处理 |
| `SUPERVISOR` | Supervisor + Workers | 复杂任务协作 |

### Agent Completions

**端点**: `POST /v1/agents/{slug}/completions`

#### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messages` | array | 是 | 消息数组（取最后一条 user 消息） |
| `stream` | boolean | 否 | 是否启用流式输出，默认 false |

#### 同步请求示例

```bash
curl -X POST https://your-domain/v1/agents/universal-assistant/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "帮我查询北京天气"}]
  }'
```

#### 同步响应格式

```json
{
  "id": "agent-xxx",
  "object": "chat.completion",
  "created": 1703123456,
  "model": "agent/universal-assistant",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "北京今天天气晴朗，温度 25°C，湿度 60%..."
      },
      "finish_reason": "stop"
    }
  ]
}
```

#### 流式请求示例

```bash
curl -N -X POST https://your-domain/v1/agents/universal-assistant/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "写一篇关于 AI 的文章"}],
    "stream": true
  }'
```

### 获取 Agent 列表

**端点**: `GET /v1/agents`

```bash
curl https://your-domain/v1/agents \
  -H "Authorization: Bearer sk-xxxx"
```

**响应**:

```json
{
  "object": "list",
  "data": [
    {
      "id": "universal-assistant",
      "object": "model",
      "created": 1703123456,
      "owned_by": "llm-manager",
      "description": "全能助手",
      "agent_type": "SINGLE"
    },
    {
      "id": "research-pipeline",
      "object": "model",
      "created": 1703123456,
      "owned_by": "llm-manager",
      "description": "研究流水线",
      "agent_type": "SEQUENTIAL"
    }
  ]
}
```

---

## REST API（原生接口）

REST API 提供更丰富的返回信息和进度流支持。

### 同步执行

**端点**: `POST /api/external/react-agent/{slug}`

```bash
curl -X POST https://your-domain/api/external/react-agent/universal-assistant \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查询北京天气"}'
```

**响应**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "result": "北京今天天气晴朗，温度 25°C...",
    "agentName": "全能助手",
    "agentType": "SINGLE",
    "slug": "universal-assistant",
    "agentConfigName": "全能助手"
  }
}
```

### 流式执行（SSE 进度流）

**端点**: `POST /api/external/react-agent/{slug}/stream`

```bash
curl -N -X POST https://your-domain/api/external/react-agent/universal-assistant/stream \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查询北京天气"}'
```

**SSE 事件流**:

```
event: start
data: {"slug":"universal-assistant","agentName":"全能助手","agentType":"SINGLE","status":"processing"}

event: progress
data: {"step":"thinking","message":"Agent 正在分析问题..."}

event: progress
data: {"step":"tool_call","message":"正在调用天气查询工具..."}

event: complete
data: {"success":true,"result":"北京今天天气晴朗，温度 25°C...","status":"completed"}

data: [DONE]
```

---

## 流式响应处理

### Python 处理流式响应

```python
import requests

# OpenAI 兼容 API 流式
response = requests.post(
    "https://your-domain/v1/chat/completions",
    headers={
        "Authorization": "Bearer sk-xxxx",
        "Content-Type": "application/json"
    },
    json={
        "model": "gpt-4",
        "messages": [{"role": "user", "content": "你好"}],
        "stream": True
    },
    stream=True
)

for line in response.iter_lines():
    if line:
        line_text = line.decode('utf-8')
        if line_text.startswith('data: '):
            data = line_text[6:]
            if data == '[DONE]':
                break
            # 解析 JSON 并处理
            import json
            chunk = json.loads(data)
            if chunk['choices'][0]['delta'].get('content'):
                print(chunk['choices'][0]['delta']['content'], end='')
```

### Java 处理流式响应

```java
import java.net.http.*;
import java.net.URI;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://your-domain/v1/chat/completions"))
    .header("Authorization", "Bearer sk-xxxx")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("""
        {
            "model": "gpt-4",
            "messages": [{"role": "user", "content": "你好"}],
            "stream": true
        }
        """))
    .build();

// 使用 BodyHandlers.ofLines() 处理 SSE
client.send(request, HttpResponse.BodyHandlers.ofLines())
    .body()
    .forEach(line -> {
        if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
            String json = line.substring(6);
            // 解析 JSON 并处理
            System.out.print(json);
        }
    });
```

### JavaScript/Node.js 处理流式响应

```javascript
const response = await fetch('https://your-domain/v1/chat/completions', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer sk-xxxx',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    model: 'gpt-4',
    messages: [{ role: 'user', content: '你好' }],
    stream: true
  })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  const chunk = decoder.decode(value);
  const lines = chunk.split('\n');

  for (const line of lines) {
    if (line.startsWith('data: ') && line !== 'data: [DONE]') {
      const json = JSON.parse(line.slice(6));
      const content = json.choices[0]?.delta?.content;
      if (content) {
        process.stdout.write(content);
      }
    }
  }
}
```

---

## 错误处理

### 错误响应格式

```json
{
  "error": {
    "message": "错误描述",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

### 常见错误码

| HTTP 状态码 | 错误类型 | 说明 |
|------------|---------|------|
| 401 | `unauthorized` | API Key 缺失或无效 |
| 403 | `forbidden` | API Key 已禁用 |
| 404 | `not_found` | 模型或 Agent 不存在 |
| 400 | `invalid_request_error` | 请求参数错误 |
| 500 | `internal_error` | 服务器内部错误 |

### 错误处理示例

```python
import requests

response = requests.post(
    "https://your-domain/v1/chat/completions",
    headers={"Authorization": "Bearer sk-xxxx"},
    json={"model": "gpt-4", "messages": [{"role": "user", "content": "你好"}]}
)

if response.status_code == 200:
    result = response.json()
    print(result['choices'][0]['message']['content'])
elif response.status_code == 401:
    print("认证失败，请检查 API Key")
elif response.status_code == 404:
    print("模型不存在")
else:
    error = response.json().get('error', {})
    print(f"请求失败: {error.get('message', '未知错误')}")
```

---

## 完整代码示例

### Python 完整示例

```python
import requests
import json

class LlmManagerClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip('/')
        self.headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }

    # ========== LLM 模型 API ==========

    def chat(self, model: str, messages: list, stream: bool = False):
        """调用 LLM 模型"""
        url = f"{self.base_url}/v1/chat/completions"
        payload = {
            "model": model,
            "messages": messages,
            "stream": stream
        }

        if stream:
            return self._stream_request(url, payload)
        else:
            response = requests.post(url, headers=self.headers, json=payload)
            response.raise_for_status()
            return response.json()['choices'][0]['message']['content']

    def list_models(self):
        """获取模型列表"""
        url = f"{self.base_url}/v1/models"
        response = requests.get(url, headers=self.headers)
        response.raise_for_status()
        return response.json()['data']

    # ========== Agent API ==========

    def agent_chat(self, slug: str, message: str, stream: bool = False):
        """调用 Agent"""
        url = f"{self.base_url}/v1/agents/{slug}/completions"
        payload = {
            "messages": [{"role": "user", "content": message}],
            "stream": stream
        }

        if stream:
            return self._stream_request(url, payload)
        else:
            response = requests.post(url, headers=self.headers, json=payload)
            response.raise_for_status()
            return response.json()['choices'][0]['message']['content']

    def list_agents(self):
        """获取 Agent 列表"""
        url = f"{self.base_url}/v1/agents"
        response = requests.get(url, headers=self.headers)
        response.raise_for_status()
        return response.json()['data']

    # ========== 内部方法 ==========

    def _stream_request(self, url: str, payload: dict):
        """处理流式请求"""
        response = requests.post(
            url,
            headers=self.headers,
            json=payload,
            stream=True
        )
        response.raise_for_status()

        for line in response.iter_lines():
            if line:
                line_text = line.decode('utf-8')
                if line_text.startswith('data: '):
                    data = line_text[6:]
                    if data == '[DONE]':
                        break
                    chunk = json.loads(data)
                    content = chunk.get('choices', [{}])[0].get('delta', {}).get('content')
                    if content:
                        yield content


# 使用示例
if __name__ == "__main__":
    client = LlmManagerClient(
        base_url="https://your-domain",
        api_key="sk-xxxx"
    )

    # 获取模型列表
    models = client.list_models()
    print("可用模型:", [m['id'] for m in models])

    # 同步调用模型
    response = client.chat(
        model="gpt-4",
        messages=[
            {"role": "system", "content": "你是一个有帮助的助手"},
            {"role": "user", "content": "你好"}
        ]
    )
    print("模型响应:", response)

    # 获取 Agent 列表
    agents = client.list_agents()
    print("可用 Agent:", [a['id'] for a in agents])

    # 同步调用 Agent
    response = client.agent_chat("universal-assistant", "帮我查询北京天气")
    print("Agent 响应:", response)

    # 流式调用模型
    print("流式响应: ", end="")
    for chunk in client.chat(
        model="gpt-4",
        messages=[{"role": "user", "content": "写一首诗"}],
        stream=True
    ):
        print(chunk, end="", flush=True)
    print()
```

### Java 完整示例

```java
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmManagerClient {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmManagerClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 LLM 模型
     */
    public String chat(String model, List<Map<String, String>> messages) throws Exception {
        String url = baseUrl + "/v1/chat/completions";
        Map<String, Object> payload = Map.of(
            "model", model,
            "messages", messages
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("请求失败: " + response.body());
        }

        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    /**
     * 调用 Agent
     */
    public String agentChat(String slug, String message) throws Exception {
        String url = baseUrl + "/v1/agents/" + slug + "/completions";
        Map<String, Object> payload = Map.of(
            "messages", List.of(Map.of("role", "user", "content", message))
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("请求失败: " + response.body());
        }

        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    public static void main(String[] args) throws Exception {
        LlmManagerClient client = new LlmManagerClient(
            "https://your-domain",
            "sk-xxxx"
        );

        // 调用模型
        String response = client.chat("gpt-4", List.of(
            Map.of("role", "user", "content", "你好")
        ));
        System.out.println("模型响应: " + response);

        // 调用 Agent
        String agentResponse = client.agentChat("universal-assistant", "帮我查询北京天气");
        System.out.println("Agent 响应: " + agentResponse);
    }
}
```

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v1.0 | 2025-12-25 | 初始版本，API 架构分离 |

---

## 联系支持

如有问题，请联系管理员或查看 [CLAUDE.md](../CLAUDE.md) 获取更多技术细节。
