# Spring AI 原生 Function Calling 集成

## 概述

已经重构为使用 **Spring AI 原生 Function Calling 机制**，让 LLM 自己决定是否需要调用工具，而不是通过代码正则表达式判断。

## 核心改进

### ❌ 旧方式（手动判断）
```java
// 使用正则表达式检测天气查询意图
String cityName = extractCityFromMessage(message);
if (cityName != null) {
    // 手动调用工具
    ToolResult result = toolExecutor.executeTool(...);
    // 将结果作为上下文传递给 LLM
    String enhancedMessage = buildEnhancedMessage(...);
}
```

**问题**：
- 需要手动编写正则表达式
- 无法处理复杂的意图识别
- 难以扩展到多个工具
- LLM 无法自主决策

### ✅ 新方式（AI 自动判断）
```java
// 注册工具函数为 Spring Bean
@Bean
@Description("Get the weather in location")
public Function<WeatherRequest, WeatherResponse> weatherFunction() {
    return request -> {
        // 调用工具执行器
        ToolResult result = toolExecutor.executeTool(...);
        return new WeatherResponse(...);
    };
}

// 在对话时启用工具
ChatClient.prompt()
    .user("北京今天天气怎么样？")
    .toolNames("weatherFunction")  // 注册工具
    .call()
    .content();
```

**优势**：
- ✅ LLM 自动判断是否需要调用工具
- ✅ 无需手动正则匹配
- ✅ 支持复杂的意图识别
- ✅ 易于扩展到多个工具
- ✅ 符合 Spring AI 最佳实践

---

## 架构设计

### 1. 工具函数配置层

**文件**：`llm-agent/src/main/java/com/llmmanager/agent/config/ToolFunctionConfiguration.java`

```java
@Configuration
public class ToolFunctionConfiguration {

    @Autowired(required = false)
    private ToolExecutor toolExecutor;

    /**
     * 将自定义 Tool 适配为 Spring AI Function
     */
    @Bean
    @Description("Get the weather in location. Return temperature in Celsius or Fahrenheit format.")
    public Function<WeatherRequest, WeatherResponse> weatherFunction() {
        return request -> {
            // 1. 构建工具调用请求
            ToolCall toolCall = ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .name("get_weather")
                    .arguments(Map.of("city", request.city(), "unit", request.unit()))
                    .build();

            // 2. 执行工具
            var result = toolExecutor.executeTool(toolCall);

            // 3. 返回结构化结果
            return new WeatherResponse(...);
        };
    }

    public record WeatherRequest(String city, String unit) {}
    public record WeatherResponse(String city, String condition, double temperature, String unit, String forecast) {}
}
```

**关键点**：
- 使用 `@Bean` 注册为 Spring Bean
- 使用 `@Description` 提供工具描述（LLM 会读取）
- 实现 `Function<Request, Response>` 接口
- 内部调用我们的 ToolExecutor

### 2. ChatRequest 参数扩展

**文件**：`llm-agent/src/main/java/com/llmmanager/agent/dto/ChatRequest.java`

```java
@Data
@Builder
public class ChatRequest {
    // ... 其他参数

    /**
     * 是否启用工具调用（Function Calling）
     */
    @Builder.Default
    private Boolean enableTools = false;
}
```

### 3. LlmChatAgent 集成工具

**文件**：`llm-agent/src/main/java/com/llmmanager/agent/agent/LlmChatAgent.java`

```java
public String chat(ChatRequest request, String conversationId) {
    ChatClient chatClient = createChatClient(request, conversationId);

    var promptBuilder = chatClient.prompt();

    // 如果启用工具，注册工具函数
    if (Boolean.TRUE.equals(request.getEnableTools())) {
        promptBuilder.toolNames("weatherFunction");  // 使用 Spring AI API
    }

    ChatResponse response = promptBuilder
        .user(request.getUserMessage())
        .call()
        .chatResponse();

    return response.getResult().getOutput().getText();
}
```

**关键 API**：
- `.toolNames("weatherFunction")` - 注册工具函数（Bean 名称）
- LLM 会自动判断是否调用工具
- 如果需要，LLM 会提取参数并调用工具
- LLM 会基于工具结果生成最终回复

### 4. Service 层封装

**文件**：`llm-service/.../LlmExecutionService.java`

```java
@Service
public class LlmExecutionService {

    /**
     * 带工具调用的对话
     */
    public String chatWithTools(Long modelId, String userMessage, String conversationId) {
        LlmModel model = llmModelService.getById(modelId);
        Channel channel = getChannel(model);

        ChatRequest request = ChatRequest.builder()
                .channelId(channel.getId())
                .apiKey(channel.getApiKey())
                .baseUrl(channel.getBaseUrl())
                .modelIdentifier(model.getModelIdentifier())
                .temperature(model.getTemperature())
                .userMessage(userMessage)
                .enableTools(true)  // 启用工具调用
                .build();

        return llmChatAgent.chat(request, conversationId);
    }
}
```

### 5. Controller 层接口

**文件**：`llm-ops/.../ChatController.java`

```java
@PostMapping("/{modelId}/with-tools")
public Map<String, Object> chatWithTools(
        @PathVariable Long modelId,
        @RequestBody String message,
        @RequestParam(required = false) String conversationId) {

    Map<String, Object> response = new HashMap<>();

    // LLM 会自动判断是否需要调用工具
    String llmResponse = executionService.chatWithTools(modelId, message, conversationId);

    response.put("success", true);
    response.put("message", llmResponse);

    return response;
}
```

---

## 使用示例

### 1. 天气查询（AI 自动识别）

```bash
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "北京今天天气怎么样？"
```

**LLM 自动判断**：
1. LLM 分析用户意图："查询北京的天气"
2. LLM 决定调用 `weatherFunction`
3. LLM 提取参数：`city="北京", unit="celsius"`
4. 系统调用工具获取天气数据
5. LLM 基于天气数据生成自然语言回复

**响应示例**：
```json
{
  "success": true,
  "message": "根据最新的天气信息，北京今天是晴天，温度为22摄氏度，湿度65%。今日天气良好，适合外出活动。"
}
```

### 2. 普通对话（AI 不调用工具）

```bash
curl -X POST http://localhost:8080/api/chat/1/with-tools \
  -H "Content-Type: text/plain" \
  -d "你好，请介绍一下自己"
```

**LLM 自动判断**：
- LLM 分析发现不需要调用工具
- 直接生成回复

**响应**：
```json
{
  "success": true,
  "message": "你好！我是一个 AI 助手，基于大语言模型构建..."
}
```

### 3. 流式对话

```bash
curl -N -X POST http://localhost:8080/api/chat/1/with-tools/stream \
  -H "Content-Type: text/plain" \
  -d "上海的天气如何？"
```

---

## 工作流程

```
┌─────────────┐
│  用户输入   │
│"北京天气?"  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────┐
│   LLM 分析用户意图      │
│ (Spring AI 自动处理)    │
└──────┬──────────────────┘
       │
       ▼
    需要工具？
       │
   ┌───┴───┐
   │  是   │  否
   ▼       ▼
┌─────┐ ┌──────────┐
│调用 │ │直接生成  │
│工具 │ │  回复    │
└──┬──┘ └────┬─────┘
   │         │
   ▼         │
┌─────────┐  │
│获取结果 │  │
└────┬────┘  │
     │       │
     ▼       │
┌──────────┐ │
│LLM基于   │ │
│结果生成  ├─┤
│自然回复  │ │
└────┬─────┘ │
     │       │
     ▼       ▼
  ┌─────────────┐
  │  返回用户   │
  └─────────────┘
```

---

## 扩展：添加更多工具

### 示例：添加计算器工具

```java
@Bean
@Description("Perform mathematical calculations. Supports add, subtract, multiply, divide.")
public Function<CalculatorRequest, CalculatorResponse> calculatorFunction() {
    return request -> {
        ToolCall toolCall = ToolCall.builder()
                .id(UUID.randomUUID().toString())
                .name("calculator")
                .arguments(Map.of(
                    "operation", request.operation(),
                    "a", request.a(),
                    "b", request.b()
                ))
                .build();

        var result = toolExecutor.executeTool(toolCall);
        return new CalculatorResponse(result.getContent());
    };
}

public record CalculatorRequest(String operation, double a, double b) {}
public record CalculatorResponse(String result) {}
```

然后在 LlmChatAgent 中注册：

```java
if (Boolean.TRUE.equals(request.getEnableTools())) {
    promptBuilder.toolNames("weatherFunction", "calculatorFunction");
}
```

---

## 对比总结

| 特性 | 旧方式（正则匹配） | 新方式（AI 自动） |
|------|-------------------|------------------|
| 意图识别 | 手动正则表达式 | LLM 自动分析 |
| 参数提取 | 手动解析 | LLM 自动提取 |
| 扩展性 | 每个工具需写正则 | 只需注册 Bean |
| 准确性 | 依赖正则质量 | LLM 语义理解 |
| 维护成本 | 高（需维护正则） | 低（AI 自动） |
| 多工具支持 | 复杂 | 简单 |
| 复杂查询 | 难以处理 | 自然支持 |

---

## 注意事项

1. **模型要求**：需要支持 Function Calling 的模型（如 GPT-3.5-turbo、GPT-4 等）
2. **工具描述**：`@Description` 的内容很重要，LLM 会根据描述决定是否调用工具
3. **参数结构**：使用 Java Record 定义清晰的请求/响应结构
4. **错误处理**：工具执行失败时，LLM 会收到错误信息并告知用户
5. **性能考虑**：Function Calling 会增加一次 LLM 调用（判断 → 调用工具 → 生成回复）

---

## 测试建议

### 测试用例

1. **明确的工具调用请求**：
   - "北京今天天气怎么样？"
   - "查询上海的天气"
   - "告诉我深圳的气温"

2. **模糊的请求（测试 AI 判断）**：
   - "北京好冷啊" → AI 可能调用工具查询天气
   - "我要去北京" → AI 不应调用工具

3. **普通对话**：
   - "你好"
   - "介绍一下自己"
   - "什么是 Spring AI？"

4. **复杂查询**：
   - "北京和上海哪个更热？" → AI 需要调用两次工具
   - "如果北京超过30度就告诉我" → AI 需要理解条件逻辑

---

**相关文件**：
- `llm-agent/src/main/java/com/llmmanager/agent/config/ToolFunctionConfiguration.java` - 工具函数配置
- `llm-agent/src/main/java/com/llmmanager/agent/agent/LlmChatAgent.java` - Agent 集成
- `llm-service/src/main/java/com/llmmanager/service/orchestration/LlmExecutionService.java` - Service 层
- `llm-ops/src/main/java/com/llmmanager/ops/controller/ChatController.java` - Controller 接口
