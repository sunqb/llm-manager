# Spring AI 工具注册方式详解：tools() vs toolCallbacks()

## 概述

Spring AI 提供了两种注册工具的方式：

| 方法 | 输入类型 | 执行位置 | 工具发现时机 | 典型场景 |
|------|----------|----------|--------------|----------|
| `tools()` | `@Tool` 注解的 Bean | 本地 JVM | 编译时（静态） | 本地 Function Calling |
| `toolCallbacks()` | `ToolCallback` 接口 | 本地或远程 | 运行时（可动态） | MCP、HTTP API、插件系统 |

---

## 1. `tools()` - 本地静态工具（Function Calling）

### 定义方式

使用 `@Tool` 和 `@ToolParam` 注解定义工具：

```java
@Component
public class WeatherTools {
    
    @Tool(description = "获取指定城市的当前天气信息")
    public WeatherResult getWeather(
            @ToolParam(description = "城市名称，如：北京、上海") String city,
            @ToolParam(description = "温度单位：celsius 或 fahrenheit") String unit) {
        // 本地代码直接执行
        return new WeatherResult(city, "晴朗", 25.0, unit);
    }
    
    public record WeatherResult(String city, String condition, double temperature, String unit) {}
}
```

### 注册方式

```java
@Resource
private WeatherTools weatherTools;

// 使用 tools() 注册
promptBuilder.tools(weatherTools);
```

### 工作原理

```
┌─────────────────────────────────────────────────────────────┐
│                        本地 JVM                              │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │   LLM       │───►│  Spring AI  │───►│  WeatherTools   │  │
│  │  (远程)     │◄───│  tools()    │◄───│  @Tool 方法     │  │
│  └─────────────┘    └─────────────┘    └─────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

1. Spring AI 通过**反射**扫描 `@Tool` 注解
2. 自动生成 **JSON Schema** 发送给 LLM
3. LLM 决定调用工具时，Spring AI **直接执行本地方法**
4. 将结果返回给 LLM

### 特点

- ✅ 简单直观，声明式定义
- ✅ 编译时类型检查
- ✅ 执行速度快（本地调用）
- ❌ 工具在编译时固定，无法动态添加
- ❌ 只能执行本地代码

---

## 2. `toolCallbacks()` - 外部/动态工具

### ToolCallback 接口

`ToolCallback` 是 Spring AI 的工具抽象接口：

```java
public interface ToolCallback {
    // 工具定义（名称、描述、参数 schema）
    ToolDefinition getToolDefinition();
    
    // 执行工具
    String call(String toolInput);
}
```

### 使用场景

#### 场景 1：MCP 工具（远程服务）

MCP (Model Context Protocol) 工具通过 `SyncMcpToolCallback` 实现：

```java
// Spring AI MCP 提供的实现
public class SyncMcpToolCallback implements ToolCallback {
    private final McpSyncClient mcpClient;
    private final McpSchema.Tool tool;
    
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .build();
    }
    
    @Override
    public String call(String toolInput) {
        // 通过 MCP 协议调用远程服务器
        return mcpClient.callTool(tool.name(), toolInput);
    }
}
```

注册方式：

```java
// 获取 MCP 工具回调
ToolCallback[] mcpCallbacks = mcpClientManager.getAllToolCallbacks();

// 使用 toolCallbacks() 注册
promptBuilder.toolCallbacks(mcpCallbacks);
```

工作原理：

```
┌─────────────────┐                      ┌─────────────────┐
│   你的应用      │      MCP 协议        │  MCP 服务器      │
│                 │  ─────────────────►  │                 │
│  ToolCallback   │  ◄─────────────────  │  fetch 工具     │
│  (代理调用)     │                      │  search 工具    │
└─────────────────┘                      └─────────────────┘
                                                  │
                                                  ▼
                                         ┌─────────────────┐
                                         │   外部服务       │
                                         │  (互联网/API)   │
                                         └─────────────────┘
```

#### 场景 2：动态工具（运行时生成）

用户通过配置界面定义工具，运行时动态创建：

```java
// 从数据库读取用户配置的工具
UserToolConfig config = userToolService.getById(toolId);

// 动态创建 ToolCallback
ToolCallback dynamicTool = new ToolCallback() {
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(config.getName())
            .description(config.getDescription())
            .inputSchema(config.getInputSchema())
            .build();
    }
    
    @Override
    public String call(String toolInput) {
        // 根据用户配置动态执行
        return httpClient.post(config.getApiUrl(), toolInput);
    }
};

promptBuilder.toolCallbacks(dynamicTool);
```

#### 场景 3：插件系统

支持第三方插件扩展工具能力：

```java
// 加载插件提供的工具
List<ToolCallback> pluginTools = pluginManager.loadTools();

promptBuilder.toolCallbacks(pluginTools.toArray(new ToolCallback[0]));
```

### 特点

- ✅ 支持远程工具调用（MCP、HTTP API）
- ✅ 支持运行时动态添加工具
- ✅ 灵活的插件系统支持
- ❌ 需要手动实现 ToolCallback 接口
- ❌ 远程调用有网络延迟

---

## 3. 混合使用

在实际项目中，可以同时使用两种方式：

```java
private void addTools(ChatClient.ChatClientRequestSpec promptBuilder, ChatRequest request) {
    int totalTools = 0;

    // 1. 本地工具（@Tool 注解）- 使用 tools()
    if (Boolean.TRUE.equals(request.getEnableTools())) {
        Object[] toolObjects = toolFunctionManager.getToolObjects(request.getToolNames());
        if (toolObjects.length > 0) {
            promptBuilder.tools(toolObjects);
            totalTools += toolObjects.length;
        }
    }

    // 2. MCP 工具（ToolCallback）- 使用 toolCallbacks()
    if (Boolean.TRUE.equals(request.getEnableMcpTools())) {
        ToolCallback[] mcpCallbacks = mcpClientManager.getAllToolCallbacks();
        if (mcpCallbacks.length > 0) {
            promptBuilder.toolCallbacks(mcpCallbacks);
            totalTools += mcpCallbacks.length;
        }
    }

    log.info("启用工具调用，总工具数: {}", totalTools);
}
```

---

## 4. 常见错误

### 错误：将 ToolCallback 传给 tools()

```java
// ❌ 错误用法
ToolCallback[] mcpCallbacks = mcpClientManager.getAllToolCallbacks();
promptBuilder.tools(mcpCallbacks);  // 会报错！

// 错误信息：
// No @Tool annotated methods found in SyncMcpToolCallback.
// Did you mean to pass a ToolCallback? If so, use .toolCallbacks() instead of .tools()
```

### 正确做法

```java
// ✅ 本地工具用 tools()
promptBuilder.tools(weatherTools, calculatorTools);

// ✅ MCP/外部工具用 toolCallbacks()
promptBuilder.toolCallbacks(mcpCallbacks);
```

---

## 5. 总结

| 场景 | 推荐方法 | 原因 |
|------|----------|------|
| 本地业务工具 | `tools()` | 简单、类型安全、编译检查 |
| MCP 工具 | `toolCallbacks()` | MCP SDK 返回的是 ToolCallback |
| HTTP API 封装 | `toolCallbacks()` | 需要自定义调用逻辑 |
| 用户自定义工具 | `toolCallbacks()` | 运行时动态创建 |
| 插件系统 | `toolCallbacks()` | 第三方扩展 |

**简单记忆**：
- `tools()` = 本地写死的工具（`@Tool` 注解）
- `toolCallbacks()` = 任何来源的工具（MCP、远程 API、动态生成等）
