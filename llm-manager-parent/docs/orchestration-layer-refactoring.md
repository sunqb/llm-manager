# 编排层架构重构技术文档

> 更新时间：2025-12-18

## 概述

本文档记录了 `llm-service/orchestration` 层的架构重构，目标是消除代码重复，统一 ChatModel 管理和执行逻辑。

## 重构背景

### 问题分析

在重构前，编排层存在以下问题：

1. **ChatModel 创建重复**
   - `ReactAgentExecutionService`、`DynamicReactAgentExecutionService`、`GraphExecutionService`、`DynamicWorkflowExecutionService` 各自实现 ChatModel/ChatClient 的创建和缓存
   - 代码重复，维护成本高

2. **执行逻辑重复**
   - `ReactAgentExecutionService` 使用 `AgentWrapper.builder()` 构建 Agent
   - `DynamicReactAgentExecutionService` 使用 `ReactAgentFactory` 构建 Agent
   - 两者的执行逻辑（`agent.call()`、`workflow.execute()`、`team.execute()`）完全相同

3. **Graph 工作流执行分散**
   - `GraphExecutionService`（DeepResearch）和 `DynamicWorkflowExecutionService` 都使用 `StateGraph` + `CompiledGraph`
   - 执行逻辑（`compiledGraph.invoke()`）重复实现

4. **API 参数冗余**
   - `executeFromDatabase` 方法的 `modelId` 参数不必要，因为 `ReactAgent` 实体已包含 `modelId`

## 重构方案

### 1. ChatModelProvider - 统一 ChatModel 管理

创建 `ChatModelProvider` 服务，集中管理 ChatModel/ChatClient 的获取和缓存。

**核心职责**：
- 根据 `modelId` 获取 `OpenAiChatModel`
- 根据 `modelId` 获取 `ChatClient`
- 根据 `modelId` 构建 `ChatRequest`
- 管理 ChatModel 缓存

**关键代码**：

```java
@Service
public class ChatModelProvider {
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    public OpenAiChatModel getChatModelByModelId(Long modelId) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);
        String cacheKey = buildCacheKey(channel.getId(), apiKey, baseUrl, model.getModelIdentifier());

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model.getModelIdentifier())
                            .temperature(model.getTemperature())
                            .build())
                    .build();
        });
    }

    public ChatClient getChatClientByModelId(Long modelId) {
        return ChatClient.builder(getChatModelByModelId(modelId)).build();
    }
}
```

### 2. ReactAgentExecutionService - 公共执行方法

添加公共执行方法，供 `DynamicReactAgentExecutionService` 复用。

**公共方法**：

```java
// 执行单个 Agent
public Map<String, Object> executeAgent(AgentWrapper agent, String message);

// 执行顺序工作流
public Map<String, Object> executeWorkflow(ConfigurableAgentWorkflow workflow, String message);

// 执行 Supervisor 团队
public Map<String, Object> executeTeam(SupervisorAgentTeam team, String message);
```

**DynamicReactAgentExecutionService 复用**：

```java
@Service
public class DynamicReactAgentExecutionService {
    @Resource
    private ReactAgentExecutionService reactAgentExecutionService;

    public Map<String, Object> execute(String slug, String message) {
        ReactAgent agentConfig = reactAgentService.getBySlug(slug);
        AgentWrapper agent = reactAgentFactory.createAgent(agentConfig, chatModel);

        // 复用公共执行方法
        return reactAgentExecutionService.executeAgent(agent, message);
    }
}
```

### 3. GraphWorkflowExecutor - 通用执行层

添加通用执行方法，供所有 Graph 工作流复用。

**通用方法**：

```java
// 同步执行
public Map<String, Object> execute(CompiledGraph compiledGraph, Map<String, Object> initialState);

// 流式执行
public Flux<NodeOutput> executeStream(CompiledGraph compiledGraph, Map<String, Object> initialState);

// 带缓存执行
public Map<String, Object> executeWithCache(CompiledGraph compiledGraph, String cacheKey,
                                             Map<String, Object> initialState);
```

**DynamicWorkflowExecutionService 使用**：

```java
public Map<String, Object> executeWorkflow(String slug, Map<String, Object> initialState) {
    GraphWorkflow config = graphWorkflowService.getBySlug(slug);
    CompiledGraph compiledGraph = graphBuilder.build(config, chatClient);

    // 使用通用执行方法
    return graphWorkflowExecutor.execute(compiledGraph, initialState);
}
```

### 4. API 简化

移除 `executeFromDatabase` 方法的 `modelId` 参数：

```java
// 修改前
@PostMapping("/db/{slug}/{modelId}")
public Map<String, Object> executeFromDatabase(
        @PathVariable String slug,
        @PathVariable Long modelId,
        @RequestBody String message);

// 修改后
@PostMapping("/db/{slug}")
public Map<String, Object> executeFromDatabase(
        @PathVariable String slug,
        @RequestBody String message);
```

## 服务职责对比

| 服务 | 数据来源 | 执行逻辑 | ChatModel |
|------|---------|---------|-----------|
| `ReactAgentExecutionService` | 硬编码 | 自身实现公共方法 | ChatModelProvider |
| `DynamicReactAgentExecutionService` | 数据库 | 复用 ReactAgentExecutionService | ChatModelProvider |
| `GraphExecutionService` | 硬编码 (DeepResearch) | GraphWorkflowExecutor | ChatModelProvider |
| `DynamicWorkflowExecutionService` | 数据库 | GraphWorkflowExecutor | ChatModelProvider |

## 修改文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `ChatModelProvider.java` | 新增 | 统一 ChatModel 管理 |
| `ReactAgentExecutionService.java` | 修改 | 添加公共执行方法 |
| `DynamicReactAgentExecutionService.java` | 修改 | 复用公共执行方法，移除 modelId 参数 |
| `GraphWorkflowExecutor.java` | 修改 | 添加通用执行方法 |
| `GraphExecutionService.java` | 修改 | 使用 ChatModelProvider |
| `DynamicWorkflowExecutionService.java` | 修改 | 使用 GraphWorkflowExecutor |
| `ReactAgentController.java` | 修改 | API 路径简化 |

## 设计模式

### 1. Provider 模式

`ChatModelProvider` 作为统一的 ChatModel 提供者，封装了创建和缓存逻辑。

**优点**：
- 单一职责：ChatModel 管理集中在一处
- 缓存复用：避免重复创建 ChatModel 实例
- 易于扩展：新增服务只需注入 ChatModelProvider

### 2. Template Method 模式

`ReactAgentExecutionService` 的公共执行方法定义了执行骨架：

```java
public Map<String, Object> executeAgent(AgentWrapper agent, String message) {
    Map<String, Object> response = new HashMap<>();
    try {
        String result = agent.call(message);  // 核心执行
        response.put("success", true);
        response.put("result", result);
    } catch (Exception e) {
        response.put("success", false);
        response.put("error", e.getMessage());
    }
    return response;
}
```

**优点**：
- 执行逻辑统一
- 错误处理一致
- 返回格式标准化

### 3. Facade 模式

`GraphWorkflowExecutor` 封装了复杂的工作流执行逻辑：

```java
public Map<String, Object> execute(CompiledGraph compiledGraph, Map<String, Object> initialState) {
    RunnableConfig config = RunnableConfig.builder()
            .threadId(UUID.randomUUID().toString())
            .build();

    Optional<OverAllState> stateResult = compiledGraph.invoke(initialState, config);
    // ... 结果处理
}
```

**优点**：
- 隐藏 Spring AI Alibaba Graph 的复杂性
- 提供简洁的执行接口
- 统一结果格式

### 4. Cache 模式

ChatModel 和 CompiledGraph 都使用 `ConcurrentHashMap` 缓存：

```java
private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

return chatModelCache.computeIfAbsent(cacheKey, k -> {
    // 创建新实例
});
```

**优点**：
- 避免重复创建开销
- 线程安全
- 支持按需清除

## 总结

本次重构遵循以下原则：

1. **DRY（Don't Repeat Yourself）**：消除重复代码
2. **单一职责**：每个类只负责一件事
3. **依赖注入**：通过 `@Resource` 注入依赖
4. **向后兼容**：保持现有 API 可用

重构后的架构更加清晰，维护成本降低，扩展性增强。

