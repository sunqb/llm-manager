# 工具调用集成演示

## 概述

在 `ChatController` 中新增了两个端点，演示 LLM 与天气工具的集成调用：

- `POST /api/chat/{modelId}/with-tools` - 带工具调用的同步对话
- `POST /api/chat/{modelId}/with-tools/stream` - 带工具调用的流式对话

## 工作流程

```
用户输入 → 关键词检测 → 调用天气工具 → 获取天气数据 → 增强提示词 → LLM 生成回复
```

### 详细步骤

1. **意图识别**：通过正则表达式检测用户消息中是否包含天气查询意图
2. **城市提取**：从消息中提取城市名称（如"北京"、"上海"）
3. **工具调用**：自动调用 WeatherTool 获取天气数据
4. **上下文增强**：将天气数据作为上下文信息传递给 LLM
5. **自然回复**：LLM 基于天气数据生成自然语言回复

## API 使用示例

### 1. 同步对话接口

```bash
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "北京今天天气怎么样？"
```

**响应示例**：
```json
{
  "success": true,
  "message": "根据最新的天气信息，北京今天是晴天，温度为22摄氏度，湿度65%，风速12公里/小时。今日天气良好，适合外出活动。建议您外出时做好防晒措施。",
  "toolCalled": true,
  "toolName": "get_weather",
  "toolResult": "{\"city\":\"北京\",\"temperature\":22,\"unit\":\"摄氏度\",\"condition\":\"晴天\",\"humidity\":\"65%\",\"windSpeed\":\"12 km/h\",\"forecast\":\"今日天气良好，适合外出活动\"}",
  "executionTimeMs": 15
}
```

### 2. 流式对话接口

```bash
curl -N -X POST http://localhost:8080/api/chat/1/with-tools/stream \
  -H "Content-Type: text/plain" \
  -d "上海的天气如何？"
```

**响应示例**（SSE 流）：
```
data: {"type":"tool_call","tool":"get_weather","city":"上海"}

data: {"choices":[{"delta":{"content":"根据"}}]}

data: {"choices":[{"delta":{"content":"最新"}}]}

data: {"choices":[{"delta":{"content":"的天气"}}]}

...

data: [DONE]
```

### 3. 支持的查询模式

系统支持以下天气查询模式：

- "北京的天气"
- "上海天气"
- "查询深圳天气"
- "广州今天天气怎么样"
- "告诉我杭州现在天气如何"
- "看看成都天气"

### 4. 非天气查询

如果用户消息中未检测到天气查询意图，系统会直接调用 LLM 进行普通对话：

```bash
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "你好，请介绍一下自己"
```

**响应**：
```json
{
  "success": true,
  "message": "你好！我是一个AI助手...",
  "toolCalled": false
}
```

## 关键实现细节

### 1. 城市名称提取

使用正则表达式匹配：
```java
Pattern pattern = Pattern.compile(
    "(查询|查看|告诉我|帮我查|看看|今天|现在)?([\\u4e00-\\u9fa5]{2,10})(的)?(天气|气温|温度)",
    Pattern.CASE_INSENSITIVE
);
```

### 2. 增强提示词构建

```java
String enhancedMessage = String.format(
    "用户问题：%s\n\n" +
    "【系统提供的%s天气信息】：%s\n\n" +
    "请基于以上天气信息，用自然、友好的语气回答用户的问题。" +
    "不要直接列出JSON数据，而是用人类能理解的语言描述天气情况。",
    originalMessage,
    cityName,
    weatherData
);
```

### 3. 工具调用流程

```java
// 构建工具调用请求
ToolCall toolCall = ToolCall.builder()
    .id(UUID.randomUUID().toString())
    .name("get_weather")
    .arguments(Map.of("city", cityName, "unit", "celsius"))
    .build();

// 执行工具
ToolResult toolResult = toolExecutor.executeTool(toolCall);

// 检查结果
if (toolResult.isSuccess()) {
    // 使用天气数据增强提示词
    String enhancedMessage = buildEnhancedMessage(...);
    // 调用 LLM
    String response = executionService.chat(modelId, enhancedMessage);
}
```

## 扩展建议

### 1. 添加更多工具

可以按照相同的模式添加其他工具：

- **计算器工具**：检测数学计算意图
- **翻译工具**：检测翻译需求
- **搜索工具**：检测信息查询需求

### 2. 改进意图识别

- 使用更复杂的 NLP 模型进行意图识别
- 支持多轮对话中的上下文理解
- 支持多个工具的组合调用

### 3. 真实工具集成

- 将 WeatherTool 连接到真实的天气 API（如 OpenWeatherMap）
- 添加缓存机制，避免重复调用外部 API
- 实现错误重试和降级策略

## 测试建议

### 测试用例

1. **基础天气查询**：
   - "北京天气"
   - "上海今天天气怎么样"
   - "查询深圳的天气"

2. **边界情况**：
   - 不包含城市名称的消息
   - 包含多个城市名称的消息
   - 非天气相关的查询

3. **工具失败处理**：
   - 模拟工具调用超时
   - 模拟工具返回错误

### 性能测试

```bash
# 并发测试
ab -n 100 -c 10 -p query.txt -T "text/plain" \
  http://localhost:8080/api/chat/1/with-tools
```

## 注意事项

1. **当前限制**：
   - 仅支持天气工具的自动调用
   - 使用简单的关键词匹配进行意图识别
   - 天气数据是模拟的，非真实数据

2. **安全考虑**：
   - 限制工具调用频率，防止滥用
   - 对工具执行设置超时时间
   - 验证用户输入，防止注入攻击

3. **性能优化**：
   - 缓存常见城市的天气数据
   - 使用异步调用，提高响应速度
   - 监控工具调用的性能指标

## 完整示例

```bash
# 1. 启动应用
cd llm-ops
mvn spring-boot:run

# 2. 测试天气查询
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "北京今天天气怎么样？"

# 3. 测试流式天气查询
curl -N -X POST http://localhost:8080/api/chat/1/with-tools/stream \
  -H "Content-Type: text/plain" \
  -d "上海的天气如何？"

# 4. 测试普通对话
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "你好，请介绍一下自己"
```

---

**相关文件**：
- `llm-ops/src/main/java/com/llmmanager/ops/controller/ChatController.java` - 控制器实现
- `llm-agent/src/main/java/com/llmmanager/agent/tool/impl/WeatherTool.java` - 天气工具实现
- `llm-agent/src/main/java/com/llmmanager/agent/tool/ToolExecutor.java` - 工具执行器
