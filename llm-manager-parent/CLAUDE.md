# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LLM Manager 是一个基于 Spring AI 的大语言模型管理平台，采用多模块 Maven 架构，支持多 LLM 提供商（OpenAI、Ollama、Azure OpenAI 等）的统一管理。

**技术栈**: Spring Boot 3.2.5, Spring AI OpenAI 1.1.0-M4, Java 17+, MyBatis-Plus 3.5.7, MySQL/TiDB, Sa-Token 1.37.0

## Build & Run Commands

### 环境要求
- **JDK 21** (最低 JDK 17)
- **Maven 3.8+**
- **MySQL/TiDB** 数据库

### JDK 配置
容器环境默认使用 JDK 8，需临时切换到 JDK 21：

```bash
export JAVA_HOME=/Volumes/samsungssd/soft/jdk-21.0.8.jdk/Contents/Home
java -version  # 验证版本
```

### Maven 构建命令

```bash
# 编译所有模块
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 仅编译特定模块
mvn clean compile -pl llm-service -am

# 重新编译失败的模块
mvn compile -rf :llm-openapi
```

### 运行应用

**启动管理后台** (llm-ops):
```bash
cd llm-ops
mvn spring-boot:run
# 访问: http://localhost:8080
# 默认账号: admin / 123456
```

**启动外部API服务** (llm-openapi):
```bash
cd llm-openapi
mvn spring-boot:run
# 提供 /api/external/* 接口
```

## Module Architecture

### 模块依赖关系
```
llm-common (公共模块)
    ↑
llm-agent (AI模型交互层)
    ↑
llm-service (业务逻辑层)
    ↑
llm-ops (管理后台)  llm-openapi (外部API)
```

### 模块职责

#### llm-common
- 公共异常、工具类
- 无业务逻辑

#### llm-agent
- **包路径**: `com.llmmanager.agent`
- **核心类**: `LlmChatAgent` - Spring AI 封装层
- **职责**:
  - 与 AI 模型直接交互 (OpenAI API)
  - 管理 ChatModel 缓存
  - 提供同步/流式对话接口
- **关键概念**: 使用 `ChatRequest` DTO 封装请求参数，避免 Service 层直接依赖 Spring AI

#### llm-service
- **包路径**: `com.llmmanager.service`
- **核心层**:
  - `service.core`: 实体、Mapper、基础 Service (Channel, Model, Agent, ApiKey, User, Prompt)
  - `service.orchestration`: `LlmExecutionService` - 业务逻辑编排
- **职责**:
  - 获取 Model/Channel 配置
  - 构建 `ChatRequest` 对象
  - 调用 `llm-agent` 执行对话
  - 管理业务逻辑（如 Agent 温度覆盖、模板渲染）

#### llm-ops
- **包路径**: `com.llmmanager.ops`
- **入口类**: `LlmOpsApplication`
- **配置**: `@SpringBootApplication(scanBasePackages = {"com.llmmanager.ops", "com.llmmanager.service", "com.llmmanager.agent"})`
- **Controllers**: Agent, ApiKey, Auth, Channel, Chat, Model, Prompt
- **端口**: 8080

#### llm-openapi
- **包路径**: `com.llmmanager.openapi`
- **入口类**: `LlmOpenApiApplication`
- **Controller**: `ExternalChatController` - 提供外部 API 访问
- **认证**: `ApiKeyAuthFilter` - Bearer Token 验证

## Core Domain Concepts

### Channel (渠道)
- 代表一个 LLM 提供商的连接配置
- 字段: `name`, `type`, `baseUrl`, `apiKey`
- 作用: 动态配置不同的 API 端点（OpenAI、Ollama、Azure 等）

### LlmModel (模型)
- 关联到 Channel，代表具体的 LLM 模型
- 字段: `name`, `modelIdentifier` (如 gpt-4), `channelId`, `temperature`
- 作用: 定义模型参数和所属渠道

### Agent (智能代理)
- 基于 Model 的定制化 AI 助手
- 字段: `name`, `slug`, `llmModelId`, `systemPrompt`, `temperatureOverride`
- 作用: 通过系统提示词定制 AI 行为

### ApiKey
- 外部应用访问凭证
- 字段: `token`, `name`, `isActive`
- 作用: 保护 `/api/external/*` 接口

## Key Architectural Patterns

### 1. 分层对话流程
```
Controller (HTTP请求)
    ↓
LlmExecutionService (编排层)
    - 获取 Model 和 Channel 配置
    - 构建 ChatRequest
    ↓
LlmChatAgent (执行层)
    - 创建/复用 ChatClient
    - 执行对话
    ↓
Spring AI OpenAI (底层)
```

### 2. 动态 Channel 配置
- **优先级**: Channel 数据库配置 > 环境变量 > 默认值
- **缓存**: 基于 `channelId_apiKey_baseUrl` 的 ConcurrentHashMap 缓存
- **更新**: 调用 `clearCacheForChannel(channelId)` 清除缓存

### 3. 流式对话实现
- 使用 `Flux<String>` 返回流式数据
- Controller 层使用 `SseEmitter` 发送 SSE 事件
- 格式: `{"choices":[{"delta":{"content":"文本"}}]}` + `[DONE]` 结束标记

## Database Schema

核心表结构:
- `channel`: LLM 提供商配置
- `llm_model`: 模型配置
- `agent`: 智能代理
- `api_key`: API 访问密钥
- `user`: 用户账号
- `prompt`: 提示词模板

ORM: MyBatis-Plus 3.5.7
Mapper 路径: `com.llmmanager.service.core.mapper`

## Configuration

### 数据库配置
位于 `llm-ops/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://your-host:4000/llm-manager
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

### Spring AI 默认配置
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-placeholder}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
```
**注意**: Channel 配置会覆盖此默认值

### Sa-Token 认证
- 用于内部管理后台认证
- 外部 API 使用独立的 ApiKey 认证

## Development Workflow

### 包结构重构记录
- **当前包名**: `com.llmmanager`
- **历史包名**: `com.llmops` (已迁移)
- 重构时需修改: `package`、`import`、`@SpringBootApplication(scanBasePackages)`、`@MapperScan`

### 添加新的 LLM 提供商
1. 在 Channel 表中添加配置（设置正确的 `baseUrl` 和 `apiKey`）
2. 系统自动支持所有 OpenAI 兼容接口
3. 无需修改代码

### 扩展 Agent 功能
1. 在 `Agent` 实体添加新字段
2. 在 `LlmExecutionService.buildChatRequest()` 中构建参数
3. 在 `LlmChatAgent` 中实现执行逻辑

### 调试技巧
- **查看 SQL**: 启用 MyBatis-Plus 日志
- **流式对话调试**: 查看 Console 输出的 `[LLM Agent]` 日志
- **缓存问题**: 调用 `clearCacheForChannel()` 或重启应用

## Common Issues

### 编译错误: 找不到符号
- **原因**: 模块间依赖未正确构建
- **解决**: `mvn clean install` 先安装依赖模块到本地仓库

### 包路径错误
- **检查项**:
  1. `package` 声明是否为 `com.llmmanager`
  2. `import` 语句是否正确
  3. `@SpringBootApplication(scanBasePackages)` 是否包含所有需要的包
  4. `@MapperScan` 路径是否正确

### ChatModel 缓存未更新
- Channel 配置修改后需调用 `LlmExecutionService.clearCacheForChannel()`
- 或通过 ChannelService 更新时自动清除

## API Examples

### 内部管理 API (需登录)
```bash
# 普通流式对话（SseEmitter 实现）
curl -X POST http://localhost:8080/api/chat/{modelId}/stream \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "你好"

# WebFlux 流式对话（推荐，性能更好）
curl -X POST http://localhost:8080/api/chat/{modelId}/stream-flux \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "你好"

# 支持 reasoning 的流式对话（用于 OpenAI o1 等思考模型）
curl -X POST http://localhost:8080/api/chat/{modelId}/stream-with-reasoning \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "你好"
```

### 外部 Agent API (需 API Key)
```bash
# 非流式
curl -X POST http://localhost:8080/api/external/agents/{slug}/chat \
  -H "Authorization: Bearer {api-key}" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'

# 流式 (SSE)
curl -N http://localhost:8080/api/external/agents/{slug}/chat/stream \
  -H "Authorization: Bearer {api-key}" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

### 流式响应格式说明

**普通流式响应**（/stream, /stream-flux）:
```json
{"choices":[{"delta":{"content":"文本内容"}}]}
{"choices":[{"delta":{"content":"更多内容"}}]}
[DONE]
```

**支持 reasoning 的流式响应**（/stream-with-reasoning）:

对于支持思考的模型（如 OpenAI o1），会返回包含 `reasoning_content` 的响应：

```json
// 包含思考内容
{"choices":[{"delta":{"reasoning_content":"正在思考解决方案..."}}]}
{"choices":[{"delta":{"reasoning_content":"分析中...","content":"答案"}}]}

// 仅包含回答内容（普通模型）
{"choices":[{"delta":{"content":"回答内容"}}]}

[DONE]
```

**说明**：
- `reasoning_content`: 模型的思考过程（仅支持思考的模型会返回）
- `content`: 最终的回答内容
- 前端可以分别显示思考过程和回答，提供更好的用户体验

## Spring AI Integration Notes

- **版本**: 1.1.0-M4 (需要 Spring Milestones 仓库)
- **核心类**: `OpenAiChatModel`, `ChatClient`, `OpenAiApi`
- **封装位置**: `llm-agent` 模块
- **配置方式**: 代码动态创建 (非自动配置)
- **支持特性**: 同步对话、流式对话、温度控制、系统提示词

---

## 聊天历史记忆（基于 Spring AI）

### 概述

LLM-Agent 模块统一使用 **Spring AI** 实现历史对话管理：
- 底层使用 Spring AI 的 `ChatClient` 和 `MessageChatMemoryAdvisor`
- 提供封装的简单 API 和原生 API 两种使用方式
- 历史记录持久化到 MySQL 数据库（`chat_history` 表）

### 包结构

```
llm-agent/src/main/java/com/llmmanager/agent/
├── storage/                           # 存储相关
│   ├── core/                          # 核心数据层（直接映射数据库）
│   │   ├── entity/
│   │   │   └── ChatHistory.java      # 数据库实体
│   │   └── mapper/
│   │       └── ChatHistoryMapper.java # MyBatis Mapper
│   └── memory/                        # LLM 聊天记忆业务层
│       ├── MybatisChatMemoryRepository.java  # Spring AI 适配器
│       └── ChatMemoryManager.java            # 聊天记忆管理器
├── advisor/                           # Advisor 实现（预留，未来扩展）
├── agent/
│   └── LlmChatAgent.java             # LLM 对话代理
├── config/
│   ├── ChatMemoryConfig.java         # 聊天记忆配置
│   └── ChatMemoryProperties.java     # 配置属性
└── dto/
    └── ChatRequest.java              # 请求 DTO
```

**职责划分**：
- `storage/core/`: 核心数据层，直接映射数据库（Entity + Mapper）
- `storage/memory/`: LLM 业务层，实现聊天记忆功能（Spring AI 适配 + 管理器）
- `advisor/`: 预留给未来的自定义 Advisor 实现（如 OpsChatMemoryAdvisor）

---

### 配置方式

#### application.yml 配置

```yaml
llm:
  memory:
    enabled: true          # 是否启用历史记忆（默认 true）
    max-messages: 10        # 最大保留消息数
    enable-cleanup: false   # 是否启用历史清理
    retention-days: 7       # 保留天数
```

---

### 使用示例

#### 示例 1：基础对话（封装 API）

```java
@Resource
private LlmChatAgent llmChatAgent;

// 无历史对话
String response = llmChatAgent.chat(request);

// 带历史对话
String response = llmChatAgent.chat(request, "conversation-123");
```

#### 示例 2：使用 ChatRequest 参数透传

```java
ChatRequest request = ChatRequest.builder()
    .apiKey(channelConfig.getApiKey())
    .baseUrl(channelConfig.getBaseUrl())
    .modelIdentifier("gpt-4")
    .temperature(0.7)
    .systemPrompt("你是一个有帮助的助手")
    .userMessage("你好")
    // 历史记忆相关参数
    .conversationId("conversation-123")
    .enableMemory(true)
    // 高级参数
    .topP(0.9)
    .maxTokens(2000)
    .frequencyPenalty(0.5)
    .presencePenalty(0.3)
    .build();

String response = llmChatAgent.chat(request);
```

#### 示例 3：流式对话

```java
Flux<String> stream = llmChatAgent.streamChat(request, "conversation-123");

stream.subscribe(
    chunk -> System.out.print(chunk),
    error -> System.err.println("错误: " + error.getMessage()),
    () -> System.out.println("\n[对话完成]")
);
```

#### 示例 4：清除会话历史

```java
llmChatAgent.clearConversationHistory("conversation-123");
```

#### 示例 5：使用 Spring AI 原生 API（高级用法）

**场景：需要组合 RAG、Tool Calling 等高级功能**

```java
@Resource
private LlmChatAgent llmChatAgent;

// 方式1：获取预配置的 ChatClient（带 Memory Advisor）
@Resource
private ChatModel springAiChatModel;  // 注入 Spring AI ChatModel

ChatClient chatClient = llmChatAgent.createChatClient(springAiChatModel);

String response = chatClient.prompt()
    .user("你好，我是张三")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv-123"))
    .call()
    .content();

// 方式2：纯净的 ChatClient（无预配置）
ChatClient pureClient = llmChatAgent.createPureChatClient(springAiChatModel);

String response = pureClient.prompt()
    .system("你是翻译助手")
    .user("Translate: Hello World")
    .call()
    .content();

// 方式3：组合多个 Advisor（Memory + RAG + Tool）
@Resource
private VectorStore vectorStore;

QuestionAnswerAdvisor ragAdvisor = new QuestionAnswerAdvisor(vectorStore);
MessageChatMemoryAdvisor memoryAdvisor = llmChatAgent.getMemoryAdvisor();

ChatClient advancedClient = llmChatAgent.createChatClientWithAdvisors(
    springAiChatModel,
    memoryAdvisor,
    ragAdvisor
);

String response = advancedClient.prompt()
    .user("根据知识库解释分布式锁")
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "conv-123"))
    .call()
    .content();
```

---

### 参数优先级

当同时传入多个 `conversationId` 时，优先级如下：

```
方法参数 > ChatRequest.conversationId
```

示例：
```java
ChatRequest request = ChatRequest.builder()
    .conversationId("request-id")
    .build();

// 实际使用 "param-id"
llmChatAgent.chat(request, "param-id");
```

---

### 核心实现类

**1. MybatisChatMemoryRepository**

MyBatis 实现的 ChatMemoryRepository，将 Spring AI 接口适配到 MySQL 存储：

```java
@Repository  // 位于 storage/memory/
public class MybatisChatMemoryRepository implements ChatMemoryRepository {
    private final ChatHistoryMapper chatHistoryMapper;

    // 实现 Spring AI 接口
    void saveAll(String conversationId, List<Message> messages);
    List<Message> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
    List<String> findConversationIds();
}
```

**数据流向**：Spring AI ChatMemory → MybatisChatMemoryRepository → ChatHistoryMapper → MySQL

**2. ChatMemoryManager**

聊天记忆管理器，封装 Spring AI 组件：

```java
@Component  // 位于 storage/memory/
public class ChatMemoryManager {
    private final ChatMemory chatMemory;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public MessageChatMemoryAdvisor getMemoryAdvisor() { ... }
    public ChatMemory getChatMemory() { ... }
    public void clearHistory(String conversationId) { ... }
}
```

**3. ChatHistory**

数据库实体，映射 `chat_history` 表：

```java
@TableName("chat_history")  // 位于 storage/core/entity/
public class ChatHistory {
    private Long id;
    private String conversationId;
    private String messageType;  // SYSTEM/USER/ASSISTANT/TOOL
    private String content;
    private Map<String, Object> metadata;
}
```

**4. ChatHistoryMapper**

MyBatis-Plus Mapper：

```java
@Mapper  // 位于 storage/core/mapper/
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
    List<ChatHistory> selectRecentMessages(@Param("conversationId") String conversationId,
                                            @Param("limit") int limit);
}
```

**5. LlmChatAgent**

统一使用 Spring AI 的执行代理：

```java
@Component  // 位于 agent/
public class LlmChatAgent {
    // 封装的简单 API
    public String chat(ChatRequest request, String conversationId);
    public Flux<String> streamChat(ChatRequest request, String conversationId);

    // Spring AI 原生 API
    public ChatClient createChatClient(ChatModel chatModel);
    public ChatClient createPureChatClient(ChatModel chatModel);
    public ChatClient createChatClientWithAdvisors(ChatModel chatModel, Advisor... advisors);
    public MessageChatMemoryAdvisor getMemoryAdvisor();
    public ChatMemory getChatMemory();
}
```

**6. ChatRequest**

请求参数封装，支持历史记忆和高级参数：

- `conversationId`: 会话ID
- `enableMemory`: 是否启用历史记忆
- `topP`, `maxTokens`, `frequencyPenalty`, `presencePenalty`: 模型参数

---

### 数据库表结构

```sql
CREATE TABLE chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(20) NOT NULL,  -- SYSTEM/USER/ASSISTANT/TOOL
    content TEXT NOT NULL,
    metadata JSON,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 注意事项

1. **灵活性**：
   - 提供封装的简单 API：`chat()`, `streamChat()`
   - 暴露 Spring AI 原生 API：支持高度自定义（RAG、Tool Calling 等）

2. **数据安全**：
   - 所有历史记录使用软删除（`is_delete` 字段）
   - 可配置定期清理过期数据（`llm.memory.enable-cleanup`）

3. **性能**：
   - ChatModel 实例自动缓存（基于 `channelId_apiKey_baseUrl`）
   - 调用 `clearCacheForChannel()` 清除指定渠道缓存

---

## LLM-Agent 模块重构计划

### 重构目标
集成 Spring AI Alibaba 核心功能，构建 Augmented LLM 抽象层，支持：
1. **Augmented LLM**：Model、Tool、MCP、Message、Vector Store 等基础抽象
2. **Agent Framework**：ReactAgent 设计理念的 Agent 开发框架
3. **Graph**：低级别工作流和多代理协调框架

### 新包结构
```
llm-agent/
├── message/              # 消息抽象层（✅ 已完成）
│   ├── MessageType.java
│   ├── Message.java
│   ├── SystemMessage.java
│   ├── UserMessage.java
│   ├── AssistantMessage.java
│   └── MessageConverter.java
├── model/                # 模型抽象层（✅ 已完成）
│   ├── ChatModel.java         # 聊天模型接口
│   ├── ChatOptions.java       # 模型选项配置
│   ├── ChatResponse.java      # 聊天响应
│   └── impl/
│       └── OpenAiChatModelAdapter.java  # OpenAI 适配器
├── advisor/              # 历史对话管理（✅ 已完成）
│   ├── ChatMemoryStore.java       # 历史存储接口
│   └── ChatMemoryManager.java     # 历史管理器
├── storage/              # 数据持久化（✅ 已完成）
│   ├── entity/
│   │   └── ChatHistory.java
│   ├── mapper/
│   │   └── ChatHistoryMapper.java
│   └── impl/
│       └── ChatMemoryStoreImpl.java
├── agent/                # Agent 执行层（✅ 已完成）
│   └── LlmChatAgent.java
├── tool/                 # 工具调用层（🔲 待实现）
│   ├── Tool.java
│   ├── ToolCall.java
│   ├── ToolResult.java
│   └── ToolRegistry.java
├── mcp/                  # MCP 支持（🔲 待实现）
│   └── McpClient.java
├── graph/                # 工作流编排（🔲 待实现）
│   └── WorkflowGraph.java
└── dto/
    └── ChatRequest.java
```

---

### 阶段 1：Augmented LLM 基础抽象（✅ 已完成）

#### 目标
- ✅ 创建 Message 消息抽象层
- ✅ 创建 ChatModel 模型抽象层
- ✅ 集成历史对话管理（MySQL 存储）
- ✅ 重构 LlmChatAgent 使用新抽象

#### 核心实现

**1. Message 消息抽象层**
- `Message`：消息基类（类型、内容、时间戳、元数据）
- `MessageType`：枚举（SYSTEM, USER, ASSISTANT, TOOL）
- `SystemMessage`, `UserMessage`, `AssistantMessage`：具体消息类型
- `MessageConverter`：Spring AI Message 与自定义 Message 互转

**2. ChatModel 模型抽象层**
- `ChatModel`：统一模型接口
  - `ChatResponse chat(List<Message> messages, ChatOptions options)`
  - `Flux<String> streamChat(List<Message> messages, ChatOptions options)`
- `ChatOptions`：模型配置（model, temperature, topP, maxTokens 等）
- `ChatResponse`：响应封装（message, tokensUsed, finishReason 等）
- `OpenAiChatModelAdapter`：OpenAI 实现适配器

**3. 历史对话管理**
- `ChatMemoryStore`：历史存储接口（addMessage, getMessages, clearMessages）
- `ChatMemoryManager`：历史管理器（加载历史、保存消息）
- `ChatMemoryStoreImpl`：MySQL 持久化实现
- `ChatHistory` 实体 + `ChatHistoryMapper`：数据库映射

**4. 数据库表**
```sql
CREATE TABLE chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(20) NOT NULL,  -- SYSTEM/USER/ASSISTANT/TOOL
    content TEXT NOT NULL,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**5. LlmChatAgent 重构**
- 支持 `conversationId` 参数启用历史对话
- 自动加载历史消息
- 自动保存用户消息和助手回复
- 流式对话支持历史聚合

#### 使用示例

```java
// 无历史对话
String response = llmChatAgent.chat(request);

// 带历史对话
String response = llmChatAgent.chat(request, "conversation-123");

// 流式对话带历史
Flux<String> stream = llmChatAgent.streamChat(request, "conversation-123");

// 清除会话历史
llmChatAgent.clearConversationHistory("conversation-123");
```

#### 架构调整说明
**重要**：为避免循环依赖，历史对话存储实现已移至 `llm-agent` 模块内部：
- ❌ **错误**：在 llm-service 实现 → 导致 llm-agent ↔ llm-service 循环依赖
- ✅ **正确**：在 llm-agent 内部实现 → llm-agent 自包含

**依赖配置**：
```xml
<dependencies>
    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>

    <!-- MySQL Driver -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

### 阶段 2：工具调用层（Tool Layer）🔲 待实现

#### 目标
- 实现 Function Calling 支持
- 定义 Tool 抽象接口
- 创建 ToolRegistry 工具注册中心
- 支持工具动态发现和调用

#### 核心实现

**1. Tool 抽象**
```java
public interface Tool {
    String getName();
    String getDescription();
    JsonSchema getParameterSchema();
    ToolResult execute(Map<String, Object> parameters);
}
```

**2. ToolRegistry**
```java
@Component
public class ToolRegistry {
    void registerTool(Tool tool);
    Tool getTool(String name);
    List<Tool> getAllTools();
}
```

**3. ToolCall 流程**
```java
// 1. ChatModel 返回 tool_call 请求
// 2. ToolRegistry 查找工具
// 3. 执行工具并获取结果
// 4. 将结果作为 ToolMessage 回传给模型
```

#### 示例工具实现
```java
@Component
public class WeatherTool implements Tool {
    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "获取指定城市的天气信息";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String city = (String) params.get("city");
        // 调用天气API
        return ToolResult.success(weatherData);
    }
}
```

---

### 阶段 3：消息增强与多模态（Message Enhancement）🔲 待实现

#### 目标
- 支持多模态消息（图片、文件）
- 扩展 Message 体系
- 实现富文本消息

#### 核心实现

**1. 多模态消息**
```java
public class MediaMessage extends Message {
    private MediaType mediaType;  // IMAGE, FILE, AUDIO
    private String mediaUrl;
    private byte[] mediaData;
}
```

**2. 工具消息**
```java
public class ToolMessage extends Message {
    private String toolCallId;
    private String toolName;
    private String toolResult;
}
```

---

### 阶段 4：MCP + Vector Store（RAG 支持）🔲 待实现

#### 目标
- 集成 Model Context Protocol (MCP)
- 添加 Vector Store 支持
- 实现 RAG（检索增强生成）

#### 核心实现

**1. MCP 客户端**
```java
public interface McpClient {
    void connect(String serverUrl);
    List<McpResource> listResources();
    McpResource getResource(String resourceId);
    ToolResult callTool(String toolName, Map<String, Object> params);
}
```

**2. Vector Store 抽象**
```java
public interface VectorStore {
    void addDocuments(List<Document> documents);
    List<Document> similaritySearch(String query, int k);
    void deleteDocuments(List<String> ids);
}
```

**3. RAG 流程**
```java
// 1. 用户提问
// 2. VectorStore 检索相关文档
// 3. 将文档作为上下文传递给模型
// 4. 模型基于上下文生成回答
```

---

### 实施优先级
1. ✅ **阶段 1**：Augmented LLM 基础抽象（已完成）
2. 🔲 **阶段 2**：工具调用层（下一阶段）
3. 🔲 **阶段 3**：多模态支持
4. 🔲 **阶段 4**：MCP + Vector Store

### 后续集成 Agent Framework 和 Graph
完成以上 4 个阶段后，将集成：
- **Agent Framework**：基于 ReactAgent 的智能代理框架
- **Graph**：工作流编排和多代理协调
