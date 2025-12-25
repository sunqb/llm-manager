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

# 智能体流式对话（使用业务智能体配置）
curl -N -X POST http://localhost:8080/api/chat/agents/{slug}/stream \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "你好"

# 智能体流式对话（带会话历史记忆）
curl -N -X POST "http://localhost:8080/api/chat/agents/{slug}/stream?conversationCode=conv-123" \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "继续上次的话题"

# 图片对话（通过 URL，流式）
curl -N -X POST "http://localhost:8080/api/chat/{modelId}/with-image-url?conversationCode=conv-123" \
  -H "Cookie: satoken={token}" \
  -d "message=这张图片里有什么？" \
  -d "imageUrls=https://example.com/image.jpg"

# 图片对话（通过 URL，同步）
curl -X POST "http://localhost:8080/api/chat/{modelId}/with-image-url/sync?conversationCode=conv-123" \
  -H "Cookie: satoken={token}" \
  -d "message=描述这张图片" \
  -d "imageUrls=https://example.com/image.jpg"

# 图片对话（文件上传）
curl -X POST "http://localhost:8080/api/chat/{modelId}/with-image?conversationCode=conv-123" \
  -H "Cookie: satoken={token}" \
  -F "message=这是什么？" \
  -F "images=@/path/to/image.png"
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

对于支持思考的模型（如 DeepSeek R1、OpenAI o1），会返回包含 `reasoning_content` 的响应：

```json
// 包含思考内容
{"choices":[{"delta":{"reasoning_content":"正在思考解决方案..."}}]}
{"choices":[{"delta":{"reasoning_content":"分析中...","content":"答案"}}]}

// 仅包含回答内容（普通模型）
{"choices":[{"delta":{"content":"回答内容"}}]}

[DONE]
```

**说明**：
- `reasoning_content`: 模型的思考过程（仅支持思考的模型会返回，如 DeepSeek R1）
- `content`: 最终的回答内容
- 前端可以分别显示思考过程和回答，提供更好的用户体验
- Spring AI 1.1+ 会自动解析 `reasoning_content` 并映射到 `AssistantMessage.getMetadata().get("reasoningContent")`

## Spring AI Integration Notes

- **版本**: 1.1.0 (需要 Spring Milestones 仓库)
- **核心类**: `OpenAiChatModel`, `ChatClient`, `OpenAiApi`
- **封装位置**: `llm-agent` 模块
- **配置方式**: 代码动态创建 (非自动配置)
- **支持特性**: 同步对话、流式对话、温度控制、系统提示词、思考模式（ReasoningContent）

---

## 思考模式实现（Thinking/Reasoning）

### 概述

支持豆包/火山引擎、OpenAI o1/o3、DeepSeek R1 等模型的思考模式。

**核心挑战**：Spring AI 的 `ModelOptionsUtils.merge()` 会丢弃 `extraBody` 参数（因为没有 `@JsonProperty` 注解），导致 thinking 参数无法传递到 API。

**解决方案**：使用 `metadata` 传递参数（在 merge 中被保留），然后在 HTTP 层面展开到 `extraBody`。

参考：https://github.com/spring-projects/spring-ai/issues/4879

### 核心组件

| 组件 | 路径 | 职责 |
|------|------|------|
| **ThinkingAdvisor** | `advisor/ThinkingAdvisor.java` | 将 thinkingMode 放入 metadata |
| **ThinkingAwareOpenAiApi** | `model/ThinkingAwareOpenAiApi.java` | 从 metadata 展开到 extraBody |

### 数据流

```
ChatRequest.thinkingMode: "enabled"
        ↓
ThinkingAdvisor.before()
    - 读取 thinkingMode
    - 放入 OpenAiChatOptions.metadata = {"thinking_mode": "enabled"}
        ↓
OpenAiChatModel (Spring AI 正常流程)
    - merge() 时 metadata 被保留
        ↓
ThinkingAwareOpenAiApi.chatCompletionStream()
    - expandMetadataToExtraBody()
    - extraBody = {"thinking": {"type": "enabled"}}
        ↓
HTTP JSON (通过 @JsonAnyGetter 打平)
{
  "model": "doubao-pro",
  "messages": [...],
  "thinking": {"type": "enabled"}  // ← 成功出现在根层级
}
```

### 支持的格式

| 模型 | 格式 | metadata 键 | extraBody 结果 |
|------|------|-------------|----------------|
| 豆包/火山引擎 | DOUBAO | `thinking_mode` | `{"thinking": {"type": "enabled"}}` |
| OpenAI o1/o3 | OPENAI | `reasoning_effort` | `{"reasoning_effort": "medium"}` |
| DeepSeek R1 | DEEPSEEK | 无需参数 | R1 模型自动启用 |

### 使用示例

```java
ChatRequest request = ChatRequest.builder()
    .modelIdentifier("doubao-pro")
    .thinkingMode("enabled")           // enabled | disabled
    .reasoningFormat(ReasoningFormat.DOUBAO)
    .enableTools(true)                 // 工具调用和思考模式可同时启用
    .userMessage("解释量子纠缠")
    .build();

Flux<ChatResponse> stream = llmChatAgent.stream(request, conversationCode);
```

### 已删除的组件

以下组件已在 v2.0 中删除：

| 组件 | 删除原因 |
|------|---------|
| `ThinkingChatModel.java` | metadata 方案无需包装器 |
| `ThinkingContext.java` | 不再使用 ThreadLocal |

详细文档：[`docs/advisor-and-thinking-model-solution.md`](./docs/advisor-and-thinking-model-solution.md)

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
│   │   │   ├── ChatHistory.java      # 消息历史实体
│   │   │   ├── Conversation.java     # 会话实体
│   │   │   └── ConversationTurn.java # 轮次实体（一次问答）
│   │   ├── mapper/
│   │   │   ├── ChatHistoryMapper.java
│   │   │   ├── ConversationMapper.java
│   │   │   └── ConversationTurnMapper.java
│   │   └── service/
│   │       ├── ChatHistoryService.java
│   │       ├── ConversationService.java
│   │       └── ConversationTurnService.java
│   └── memory/                        # LLM 聊天记忆业务层
│       ├── MybatisChatMemoryRepository.java  # Spring AI 适配器
│       └── ChatMemoryManager.java            # 聊天记忆管理器
├── advisor/                           # Advisor 实现
│   └── ThinkingAdvisor.java          # 思考模式 Advisor（metadata 方案）
├── model/                             # 模型扩展
│   └── ThinkingAwareOpenAiApi.java   # 支持 thinking 参数的 OpenAiApi
├── agent/
│   └── LlmChatAgent.java             # LLM 对话代理
├── config/
│   ├── ChatMemoryConfig.java         # 聊天记忆配置
│   └── ChatMemoryProperties.java     # 配置属性
└── dto/
    └── ChatRequest.java              # 请求 DTO
```

**职责划分**：
- `storage/core/`: 核心数据层，直接映射数据库（Entity + Mapper + Service）
- `storage/memory/`: LLM 业务层，实现聊天记忆功能（Spring AI 适配 + 管理器）
- `advisor/`: 自定义 Advisor 实现（ThinkingAdvisor 处理思考模式参数）
- `model/`: 模型扩展（ThinkingAwareOpenAiApi 处理 metadata 展开）

### 数据模型

```
Conversation (会话)
    └── ConversationTurn (轮次) - 一次完整的问答
          ├── USER Message (用户消息)
          └── ASSISTANT Message (助手消息)
```

**命名规范**：
- `conversationCode`：会话业务唯一标识（32位UUID，无连字符）
- `messageCode`：消息业务唯一标识（32位UUID，无连字符）
- `turnCode`：轮次业务唯一标识（32位UUID，无连字符）
- `fileCode`：媒体文件业务唯一标识（32位UUID，无连字符）

### 媒体文件存储

多模态对话中的图片 URL 会自动保存到数据库（`a_media_files` 表），与对应的用户消息关联。

**核心 Service**：

```java
// MediaFileService 接口（位于 storage/core/service/）
public interface MediaFileService {
    // 保存图片URL（便捷方法）
    MediaFile saveImageUrl(String conversationCode, String messageCode,
                           String imageUrl, String mimeType);

    // 批量保存图片URL
    List<MediaFile> saveImageUrls(String conversationCode, String messageCode,
                                   List<String> imageUrls);

    // 为最新的用户消息保存图片URL（自动查找最新 USER 消息）
    List<MediaFile> saveImageUrlsForLatestUserMessage(String conversationCode,
                                                       List<String> imageUrls);

    // 查询相关方法
    List<MediaFile> findByMessageCode(String messageCode);
    List<MediaFile> findByConversationCode(String conversationCode);
}
```

**数据关联**：
```
a_chat_history (用户消息)
    └── message_code ────> a_media_files (媒体文件)
                               └── file_url (图片URL)
```

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
    .conversationCode("conversation-123")
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

当同时传入多个 `conversationCode` 时，优先级如下：

```
方法参数 > ChatRequest.conversationCode
```

示例：
```java
ChatRequest request = ChatRequest.builder()
    .conversationCode("request-id")
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
    void saveAll(String conversationCode, List<Message> messages);
    List<Message> findByConversationCode(String conversationCode);
    void deleteByConversationCode(String conversationCode);
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
    public void clearHistory(String conversationCode) { ... }
}
```

**3. ChatHistory**

数据库实体，映射 `chat_history` 表：

```java
@TableName("chat_history")  // 位于 storage/core/entity/
public class ChatHistory {
    private Long id;
    private String conversationCode;
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
    List<ChatHistory> selectRecentMessages(@Param("conversationCode") String conversationCode,
                                            @Param("limit") int limit);
}
```

**5. LlmChatAgent**

统一使用 Spring AI 的执行代理：

```java
@Component  // 位于 agent/
public class LlmChatAgent {
    // 封装的简单 API
    public String chat(ChatRequest request, String conversationCode);
    public Flux<String> streamChat(ChatRequest request, String conversationCode);

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

- `conversationCode`: 会话标识（32位UUID，无连字符）
- `enableMemory`: 是否启用历史记忆
- `topP`, `maxTokens`, `frequencyPenalty`, `presencePenalty`: 模型参数

---

### 数据库表结构

```sql
-- 会话表
CREATE TABLE a_conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_code VARCHAR(100) NOT NULL UNIQUE COMMENT '会话唯一标识（32位UUID）',
    title VARCHAR(255) COMMENT '会话标题',
    message_count INT DEFAULT 0 COMMENT '消息数量',
    last_message_time DATETIME COMMENT '最后消息时间',
    -- 标准字段
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_conversation_code (conversation_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 对话轮次表（一次问答的关联）
CREATE TABLE a_conversation_turns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    turn_code VARCHAR(32) NOT NULL UNIQUE COMMENT 'Turn唯一标识（32位UUID）',
    conversation_code VARCHAR(100) NOT NULL COMMENT '会话标识',
    turn_index INT NOT NULL DEFAULT 0 COMMENT '轮次序号（从0开始）',
    user_message_code VARCHAR(32) COMMENT '用户消息标识',
    assistant_message_code VARCHAR(32) COMMENT '助手消息标识',
    prompt_tokens INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens INT DEFAULT 0 COMMENT '总token数',
    latency_ms INT DEFAULT 0 COMMENT '响应耗时(毫秒)',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCESS/FAILED/TIMEOUT',
    error_message TEXT COMMENT '错误信息',
    -- 标准字段
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_turn_code (turn_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话轮次表';

-- 聊天历史表
CREATE TABLE a_chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_code VARCHAR(100) NOT NULL COMMENT '会话标识',
    message_code VARCHAR(32) NOT NULL UNIQUE COMMENT '消息唯一标识（32位UUID）',
    message_index INT NOT NULL DEFAULT 0 COMMENT '消息在会话中的序号',
    turn_code VARCHAR(32) COMMENT '轮次标识（关联 a_conversation_turns.turn_code）',
    message_type VARCHAR(20) NOT NULL COMMENT '消息类型：SYSTEM/USER/ASSISTANT/TOOL',
    content TEXT NOT NULL COMMENT '消息内容',
    metadata JSON COMMENT '元数据',
    -- 标准字段
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_conversation_code (conversation_code),
    INDEX idx_message_code (message_code),
    INDEX idx_turn_code (turn_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天历史表';
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
├── tool/                 # 工具调用层（✅ 已完成）
│   ├── Tool.java
│   ├── ToolCall.java
│   ├── ToolResult.java
│   ├── ToolException.java
│   ├── ToolRegistry.java
│   ├── ToolExecutor.java
│   ├── annotation/
│   │   └── ToolComponent.java
│   └── impl/
│       ├── WeatherTool.java
│       └── CalculatorTool.java
├── mcp/                  # MCP 支持（✅ 已实现）
│   └── McpClientManager.java
├── graph/                # 工作流编排（✅ 已实现）
│   └── GraphWorkflowExecutor.java
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
- 支持 `conversationCode` 参数启用历史对话
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

### 阶段 2：工具调用层（Tool Layer）✅ 已完成

#### 目标
- ✅ 实现 Function Calling 支持
- ✅ 使用 Spring AI 原生 @Tool 注解
- ✅ 创建 ToolFunctionManager 工具管理器
- ✅ 支持工具动态发现和调用
- ✅ 提供示例工具实现（天气、计算器）
- ✅ 创建 ToolController 接口
- ✅ 前端工具选择面板

#### 实现方案：Spring AI 原生 @Tool 注解

采用 Spring AI 原生的 `@Tool` 和 `@ToolParam` 注解实现工具调用，而非自定义 Tool 接口。

**优势**：
- ✅ 使用 Spring AI 官方推荐方式
- ✅ 自动解析方法签名生成 JSON Schema
- ✅ LLM 自动决策何时调用工具
- ✅ 与 ChatClient 无缝集成

#### 核心实现

**1. 工具类定义（使用 @Tool 注解）**

```java
@Slf4j
@Component
public class WeatherTools {

    @Tool(description = "获取指定城市的当前天气信息，包括温度、天气状况、湿度等")
    public WeatherResponse getWeather(
            @ToolParam(description = "城市名称，例如：北京、上海、深圳") String city,
            @ToolParam(description = "温度单位，可选值：celsius 或 fahrenheit") String unit) {

        log.info("[WeatherTools] LLM 调用天气工具，城市: {}, 单位: {}", city, unit);
        // 模拟天气数据
        return new WeatherResponse(city, "晴朗", 25.0, "°C", 60, "天气晴好");
    }

    public record WeatherResponse(
        String city, String condition, double temperature,
        String unit, int humidity, String forecast
    ) {}
}
```

**2. ToolFunctionManager - 工具管理器**

```java
@Slf4j
@Component
public class ToolFunctionManager {

    // 存储工具信息：工具名 -> ToolInfo
    private final Map<String, ToolInfo> registeredTools = new ConcurrentHashMap<>();

    public record ToolInfo(
        String name,           // 工具名称（方法名）
        String description,    // 工具描述
        Object beanInstance,   // Bean 实例
        String beanName,       // Bean 名称
        Class<?> beanClass     // Bean 类
    ) {}

    @PostConstruct
    public void discoverTools() {
        // 自动扫描所有带 @Tool 注解的方法
        // 注册到 registeredTools
    }

    // 获取工具对象（供 ChatClient.tools() 使用）
    public Object[] getToolObjects(List<String> toolNames) {
        // 返回 Bean 实例数组
    }

    // 获取所有工具（供前端展示）
    public Map<String, String> getAllTools() {
        // 返回 {工具名 -> 描述}
    }

    // 获取所有工具名称
    public List<String> getAllToolNames() { ... }

    // 检查工具是否存在
    public boolean hasTool(String toolName) { ... }

    // 获取工具详情
    public ToolInfo getToolInfo(String toolName) { ... }
}
```

**3. LlmChatAgent 集成**

```java
// 如果启用工具，注册工具对象（使用 Spring AI 原生 @Tool 注解方式）
if (Boolean.TRUE.equals(request.getEnableTools())) {
    Object[] toolObjects = toolFunctionManager.getToolObjects(request.getToolNames());
    if (toolObjects.length > 0) {
        log.info("[LlmChatAgent] 启用工具调用，注册工具数: {}", toolObjects.length);
        promptBuilder.tools(toolObjects);  // 使用 .tools() 传递工具对象
    }
}
```

#### 已实现的工具

| 工具名 | 类 | 描述 | 参数 |
|--------|-----|------|------|
| `getWeather` | WeatherTools | 获取城市天气信息 | city, unit |
| `calculate` | CalculatorTools | 执行数学计算 | operation, a, b |

#### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /api/tools` | GET | 获取所有工具列表 |
| `GET /api/tools/{toolName}` | GET | 获取工具详情 |
| `GET /api/tools/{toolName}/exists` | GET | 检查工具是否存在 |
| `POST /api/chat/{modelId}/stream-flux-with-tools` | POST | 带工具调用的流式对话 |

#### 文件结构

```
llm-agent/src/main/java/com/llmmanager/agent/
├── tools/                        # Spring AI 原生工具类
│   ├── WeatherTools.java        # @Tool 天气工具
│   └── CalculatorTools.java     # @Tool 计算器工具
├── config/
│   └── ToolFunctionManager.java # 工具管理器（自动发现 @Tool）
└── agent/
    └── LlmChatAgent.java        # 使用 .tools() 传递工具对象

llm-ops/src/main/java/com/llmmanager/ops/controller/
└── ToolController.java          # 工具列表接口
```

#### 前端工具选择

前端 `ChatView.vue` 提供美观的工具选择面板：
- 工具开关：一键启用/禁用工具调用
- 工具选择面板：下拉展示所有可用工具
- 全选/取消全选：快速批量选择
- 工具描述：悬停查看详细描述

#### 使用示例

```java
// 1. 定义工具类（使用 Spring AI @Tool 注解）
@Component
public class MyTools {
    @Tool(description = "我的工具描述")
    public String myTool(@ToolParam(description = "参数描述") String param) {
        return "结果";
    }
}

// 2. 工具自动发现（启动时 @PostConstruct）
// ToolFunctionManager 会扫描所有 @Tool 注解的方法

// 3. 前端选择工具
// GET /api/tools 获取工具列表，用户选择要使用的工具

// 4. 对话时传递工具名称
// POST /api/chat/{modelId}/stream-flux-with-tools?toolNames=getWeather,calculate

// 5. LLM 自动决策是否调用工具
// 用户："北京今天天气怎么样？"
// -> LLM 识别需要调用 getWeather 工具
// -> 自动执行工具并返回结果
// -> LLM 基于结果生成回复
```

#### ChatRequest 工具相关参数

```java
@Data
@Builder
public class ChatRequest {
    // ... 其他参数

    // 工具调用相关
    private Boolean enableTools;       // 是否启用工具调用
    private List<String> toolNames;    // 指定可用工具（null 表示全部）
}
```

---

### 阶段 3：消息增强与多模态（Message Enhancement）✅ 已完成

#### 目标
- ✅ 支持多模态消息（图片、文件）
- ✅ 扩展 Message 体系
- ✅ 实现媒体文件存储

#### 核心实现

**1. MediaMessage 多模态消息**
```java
public class MediaMessage extends Message {
    private List<MediaContent> mediaContents;  // 支持多个媒体

    public static class MediaContent {
        private MediaType mediaType;  // IMAGE, DOCUMENT, AUDIO, VIDEO
        private String url;           // 媒体URL
        private String base64Data;    // Base64编码数据
        private String mimeType;      // MIME类型
    }
}
```

**2. MediaFile 媒体文件存储**
```java
@TableName("a_media_files")
public class MediaFile {
    private String fileCode;          // 文件唯一标识
    private String conversationCode;  // 关联会话
    private String messageCode;       // 关联消息
    private String mediaType;         // IMAGE/DOCUMENT/AUDIO/VIDEO
    private String fileUrl;           // 文件URL
    private String mimeType;          // MIME类型
}
```

**3. 多模态对话 API**
- `POST /api/chat/{modelId}/with-image-url` - 图片URL对话（流式）
- `POST /api/chat/{modelId}/with-media/sync` - 多模态同步对话
- 统一流式接口支持 `mediaUrls` 参数

**4. 包结构**
```
llm-agent/src/main/java/com/llmmanager/agent/
├── message/
│   ├── MediaMessage.java        ✅ 多模态消息
│   └── MessageConverter.java    ✅ 支持 Media 转换
├── storage/core/
│   ├── entity/MediaFile.java    ✅ 媒体文件实体
│   ├── mapper/MediaFileMapper.java
│   └── service/
│       ├── MediaFileService.java
│       └── impl/MediaFileServiceImpl.java
```

---

### 阶段 4：MCP（Model Context Protocol）✅ 已完成

#### 目标
- 集成 Spring AI MCP 支持
- 连接外部 MCP 服务器
- 使用 MCP 工具进行对话

#### 已完成功能

**1. MCP 服务器管理**
- `McpServer` 实体 - 支持 STDIO、SSE、Streamable HTTP 三种传输类型
- `McpServerMapper` - 数据库操作
- `McpServerService` - 服务层
- `McpServerController` - REST API（CRUD + 连接管理）

**2. MCP 客户端管理**
- `McpClientManager` - 客户端连接管理器
- 自动初始化（启动时连接所有已启用的服务器）
- 工具发现和回调获取
- 连接/断开/重连操作

**3. LlmChatAgent 集成**
- `enableMcpTools` 参数支持
- `mcpServerCodes` 指定服务器
- 本地工具 + MCP 工具混合使用

#### 包结构
```
llm-agent/src/main/java/com/llmmanager/agent/mcp/
├── entity/
│   └── McpServer.java           # MCP 服务器实体
├── mapper/
│   └── McpServerMapper.java     # 数据库 Mapper
├── service/
│   └── McpServerService.java    # 服务层
├── config/
│   ├── McpClientConfig.java     # 配置类
│   └── McpClientProperties.java # 配置属性
└── McpClientManager.java        # 客户端管理器
```

#### 配置示例
```yaml
llm:
  mcp:
    enabled: true
    request-timeout: 30
    auto-initialize: true
    client-type: SYNC
```

#### 使用示例
```java
// 在 ChatRequest 中启用 MCP 工具
ChatRequest request = ChatRequest.builder()
    .modelIdentifier("gpt-4")
    .userMessage("今天北京天气怎么样？")
    .enableMcpTools(true)  // 启用 MCP 工具
    .mcpServerCodes(List.of("weather-server"))  // 可选：指定服务器
    .build();

String response = llmChatAgent.chat(request);
```

**注意**：当前版本仅支持 SSE 和 Streamable HTTP 传输类型，STDIO 传输暂不支持。

---

### 阶段 4.5：Vector Store（RAG 支持）✅ 已完成（✅ 已测试）

> **测试状态**（2025-12-16）：
> - ✅ 应用启动正常，RAG 组件初始化成功
> - ✅ 知识库 CRUD 功能正常
> - ✅ 文档添加、处理流程正常
> - ⚠️ Embedding API 需要有效的 API Key（测试时使用占位符，API 调用失败属预期行为）
> - ⚠️ 完整的向量检索测试需要配置真实的 Embedding API

#### 目标
- ✅ 添加 Vector Store 支持（基于 Spring AI SimpleVectorStore）
- ✅ 实现 RAG（检索增强生成）
- ✅ 创建知识库管理功能
- ✅ 集成 RetrievalAugmentationAdvisor
- ✅ Embedding 配置独立化（支持自定义 baseUrl/apiKey）
- ✅ Milvus 向量数据库支持

#### 待完成功能（TODO）

| 功能 | 说明 | 位置 |
|------|------|------|
| **URL 文档解析** | 实现网页内容抓取，支持 URL 类型文档 | `DocumentProcessor.java:147` |
| **文件解析（PDF/DOCX/HTML）** | 集成文档解析库（如 Tika），支持富文本文件 | `DocumentProcessor.java:154` |

**实现建议**：
- URL 解析：可使用 Jsoup 或 Spring AI 的 `WebDocumentReader`
- 文件解析：可重新引入 `spring-ai-tika-document-reader` 依赖，或使用 Apache POI（Office 文档）+ PDFBox（PDF）

#### 技术选型

| 组件 | 选择 | 说明 |
|------|------|------|
| **VectorStore** | SimpleVectorStore / Milvus | 内存+文件 或 向量数据库 |
| **EmbeddingModel** | OpenAiEmbeddingModel | 支持自定义 API（Ollama 等） |
| **DocumentSplitter** | TokenTextSplitter | 基于 Token 的分块策略 |
| **RAG Advisor** | RetrievalAugmentationAdvisor | Spring AI 官方 RAG Advisor |

#### 包结构

```
llm-agent/src/main/java/com/llmmanager/agent/
├── rag/                                  # RAG 模块
│   ├── config/
│   │   ├── RagConfig.java               # RAG Bean 配置
│   │   └── RagProperties.java           # 配置属性类
│   ├── VectorStoreManager.java          # VectorStore 管理器
│   ├── DocumentProcessor.java           # 文档处理器
│   └── RagAdvisorBuilder.java           # RAG Advisor 构建器
├── storage/core/
│   ├── entity/
│   │   ├── KnowledgeBase.java           # 知识库实体
│   │   └── KnowledgeDocument.java       # 文档实体
│   ├── mapper/
│   │   ├── KnowledgeBaseMapper.java
│   │   └── KnowledgeDocumentMapper.java
│   └── service/
│       ├── KnowledgeBaseService.java
│       └── KnowledgeDocumentService.java
```

#### 配置方式

```yaml
llm:
  rag:
    enabled: true
    embedding:
      # Embedding API 配置（可选，默认使用 spring.ai.openai 配置）
      # base-url: https://api.openai.com    # 或 Ollama: http://localhost:11434
      # api-key: sk-xxx
      model: text-embedding-3-small         # Ollama 可用: nomic-embed-text, bge-m3
      dimensions: 1536                       # nomic-embed-text: 768, bge-m3: 1024
    vector-store:
      type: simple                           # simple | milvus
      # === Simple VectorStore 配置 ===
      persist-path: ./data/vectorstore
      top-k: 5
      similarity-threshold: 0.5
      # === Milvus 配置（type=milvus 时启用）===
      # milvus-host: localhost
      # milvus-port: 19530
      # milvus-database: default
      # milvus-collection-prefix: llm_kb_
      # milvus-index-type: IVF_FLAT
      # milvus-metric-type: COSINE
    splitter:
      chunk-size: 1000
      chunk-overlap: 200
      min-chunk-size: 100
```

#### 核心组件

**1. VectorStoreManager - 向量存储管理器**

```java
@Slf4j
@Component
public class VectorStoreManager {
    // 每个知识库一个 VectorStore 实例
    private final Map<String, VectorStore> vectorStores = new ConcurrentHashMap<>();

    // 获取或创建 VectorStore
    public VectorStore getOrCreateVectorStore(String kbCode);

    // 添加文档到知识库
    public void addDocuments(String kbCode, List<Document> documents);

    // 相似性搜索（单个知识库）
    public List<Document> similaritySearch(String kbCode, String query, int topK);

    // 全局搜索（所有启用的知识库）
    public List<Document> similaritySearchGlobal(String query, int topK);

    // 删除文档
    public void deleteDocuments(String kbCode, List<String> documentIds);

    // 清空知识库
    public void clearVectorStore(String kbCode);
}
```

**2. DocumentProcessor - 文档处理器**

```java
@Slf4j
@Component
public class DocumentProcessor {
    // 处理单个文档（分割 + 向量化）
    public int processDocument(KnowledgeDocument doc);

    // 处理文本内容
    public int processContent(String kbCode, String content, Map<String, Object> metadata);

    // 批量处理待处理文档
    public int processPendingDocuments(int limit);
}
```

**3. RagAdvisorBuilder - RAG Advisor 构建器**

```java
@Slf4j
@Component
public class RagAdvisorBuilder {
    // 为单个知识库创建 Advisor
    public Advisor buildAdvisor(String kbCode);
    public Advisor buildAdvisor(String kbCode, Integer topK);

    // 为全局知识库创建 Advisor
    public Advisor buildGlobalAdvisor();

    // 为多个知识库创建 Advisor
    public Advisor buildMultiKbAdvisor(List<String> kbCodes, Integer topK);

    // 带元数据过滤的 Advisor
    public Advisor buildAdvisorWithMetadata(String kbCode, Map<String, Object> metadata);
}
```

#### ChatRequest RAG 参数

```java
@Data
@Builder
public class ChatRequest {
    // ... 其他参数

    // RAG 相关参数
    @Builder.Default
    private Boolean enableRag = false;           // 是否启用 RAG
    private List<String> ragKbCodes;             // 知识库 Code 列表
    private Integer ragTopK;                     // 返回文档数量
    private Double ragSimilarityThreshold;       // 相似度阈值
    private Map<String, Object> ragFilterMetadata; // 元数据过滤
}
```

#### LlmChatAgent 集成

```java
// 在 createChatClient 中动态添加 RAG Advisor
private ChatClient createChatClient(ChatRequest request, String conversationCode) {
    // ...

    // 2. RagAdvisor（需要 enableRag=true）
    if (Boolean.TRUE.equals(request.getEnableRag()) && ragAdvisorBuilder != null) {
        Advisor ragAdvisor = buildRagAdvisor(request);
        if (ragAdvisor != null) {
            advisors.add(ragAdvisor);
            log.info("[LlmChatAgent] 启用 RAG Advisor, kbCodes: {}", request.getRagKbCodes());
        }
    }

    // ...
}
```

#### REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/knowledge-base` | GET | 获取所有知识库 |
| `/api/knowledge-base/enabled` | GET | 获取已启用的知识库 |
| `/api/knowledge-base/{id}` | GET/PUT/DELETE | 知识库 CRUD |
| `/api/knowledge-base/code/{kbCode}` | GET | 按 Code 获取知识库 |
| `/api/knowledge-base/{kbCode}/documents` | GET | 获取知识库文档列表 |
| `/api/knowledge-base/{kbCode}/documents/text` | POST | 添加文本文档 |
| `/api/knowledge-base/{kbCode}/documents/markdown` | POST | 添加 Markdown 文档 |
| `/api/knowledge-base/{kbCode}/documents/url` | POST | 添加 URL 文档 |
| `/api/knowledge-base/documents/{docCode}/process` | POST | 处理文档 |
| `/api/knowledge-base/documents/process-pending` | POST | 批量处理待处理文档 |
| `/api/knowledge-base/{kbCode}/search` | POST | 知识库搜索 |
| `/api/knowledge-base/global/search` | POST | 全局搜索 |
| `/api/knowledge-base/{kbCode}/clear` | POST | 清空知识库 |

#### 使用示例

**1. 创建知识库并添加文档**

```bash
# 创建知识库
curl -X POST http://localhost:8080/api/knowledge-base \
  -H "Content-Type: application/json" \
  -d '{"name": "产品文档", "description": "产品相关文档", "kbType": "PRODUCT"}'

# 添加文本文档
curl -X POST http://localhost:8080/api/knowledge-base/{kbCode}/documents/text \
  -H "Content-Type: application/json" \
  -d '{
    "title": "产品介绍",
    "content": "这是产品介绍内容...",
    "processNow": true
  }'

# 搜索知识库
curl -X POST http://localhost:8080/api/knowledge-base/{kbCode}/search \
  -H "Content-Type: application/json" \
  -d '{"query": "产品功能", "topK": 5}'
```

**2. 在对话中使用 RAG**

```java
// ChatRequest 启用 RAG
ChatRequest request = ChatRequest.builder()
    .modelIdentifier("gpt-4")
    .userMessage("介绍一下产品功能")
    .enableRag(true)
    .ragKbCodes(List.of("kb-product"))  // 指定知识库
    .ragTopK(5)
    .build();

String response = llmChatAgent.chat(request);
```

**3. 全局知识库搜索**

```java
// 不指定知识库，使用全局搜索
ChatRequest request = ChatRequest.builder()
    .modelIdentifier("gpt-4")
    .userMessage("公司有什么产品？")
    .enableRag(true)
    // ragKbCodes 为 null，将搜索所有启用的知识库
    .build();
```

#### 数据库表结构

```sql
-- 知识库表
CREATE TABLE IF NOT EXISTS a_knowledge_bases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_code VARCHAR(32) NOT NULL UNIQUE COMMENT '知识库唯一标识',
    name VARCHAR(255) NOT NULL COMMENT '知识库名称',
    description TEXT COMMENT '描述',
    kb_type VARCHAR(50) DEFAULT 'GENERAL' COMMENT '类型：GENERAL/FAQ/PRODUCT/CUSTOM',
    embedding_model VARCHAR(100) DEFAULT 'text-embedding-3-small',
    embedding_dimensions INT DEFAULT 1536,
    channel_id BIGINT COMMENT '关联的 Channel ID',
    document_count INT DEFAULT 0,
    vector_count INT DEFAULT 0,
    is_public TINYINT(1) DEFAULT 0,
    enabled TINYINT(1) DEFAULT 1,
    metadata JSON,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_kb_code (kb_code),
    INDEX idx_enabled (enabled)
);

-- 知识库文档表
CREATE TABLE IF NOT EXISTS a_knowledge_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_code VARCHAR(32) NOT NULL UNIQUE COMMENT '文档唯一标识',
    kb_code VARCHAR(32) NOT NULL COMMENT '关联的知识库',
    title VARCHAR(500) COMMENT '文档标题',
    doc_type VARCHAR(20) DEFAULT 'TEXT' COMMENT '类型：TEXT/MARKDOWN/PDF/URL',
    content MEDIUMTEXT COMMENT '原始内容',
    content_hash VARCHAR(64) COMMENT '内容哈希',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    error_message TEXT,
    chunk_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    source_url VARCHAR(500),
    metadata JSON,
    enabled TINYINT(1) DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_doc_code (doc_code),
    INDEX idx_kb_code (kb_code),
    INDEX idx_status (status)
);
```

#### 注意事项

1. **EmbeddingModel 配置**：需要在 Channel 中配置支持 Embedding 的 API（如 OpenAI）
2. **向量持久化**：SimpleVectorStore 会将向量存储到文件，重启后自动加载
3. **性能考虑**：生产环境建议使用 PgVector 或 Milvus
4. **分块策略**：默认使用 TokenTextSplitter，chunk_size=1000, overlap=200

---

### 阶段 5：Super Agent with Spring AI Alibaba 🎯 推荐方案

#### 概述

**Spring AI Alibaba 提供了完整的 Agent 框架**，无需手动实现 ReAct 循环、工作流编排等功能。建议直接使用官方框架，开发效率提升 3 倍！

官方文档：https://github.com/alibaba/spring-ai-alibaba

#### Spring AI Alibaba 核心组件

**1. Agent Framework**（智能体框架）

提供多种开箱即用的 Agent 类型：
- **ReactAgent**：基于 ReAct 模式（Reasoning + Acting）
- **SequentialAgent**：顺序执行多个步骤
- **ParallelAgent**：并行执行多个任务
- **LoopAgent**：循环执行直到满足条件
- **RoutingAgent**：动态路由到不同的处理分支

**2. Graph Runtime**（工作流引擎）

低级别的工作流编排框架，支持：
- 节点（Node）定义和注册
- 边（Edge）和条件路由
- 状态管理（State）
- 异步执行
- 导出 PlantUML/Mermaid 图

**3. MCP 集成**（Model Context Protocol）

原生支持 MCP 工具调用：
- `McpRouterService`：MCP 路由服务
- 自动搜索和调用 MCP 工具
- 支持多个 MCP 服务器

**4. A2A（Agent-to-Agent）**

多 Agent 协作框架：
- Agent 间通信协议
- Agent 注册和发现
- 请求路由和转发

---

#### 依赖配置

在 `llm-agent/pom.xml` 中添加：

```xml
<dependencies>
    <!-- Spring AI Alibaba Agent Framework -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter-agent</artifactId>
        <version>1.0.0-M4</version>
    </dependency>

    <!-- Spring AI Alibaba Graph -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-graph-core</artifactId>
        <version>1.0.0-M4</version>
    </dependency>

    <!-- Spring AI Alibaba MCP -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-mcp-router</artifactId>
        <version>1.0.0-M4</version>
    </dependency>

    <!-- A2A Agent 通信 -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-a2a-client</artifactId>
        <version>1.0.0-M4</version>
    </dependency>
</dependencies>
```

---

#### DeepResearch 风格工作流实现

```java
/**
 * 基于 Spring AI Alibaba Graph 的 DeepResearch 智能体
 */
@Service
public class DeepResearchAgent {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private Function<WebSearchRequest, WebSearchResponse> webSearchFunction;

    /**
     * 构建 DeepResearch 工作流
     */
    @Bean
    public CompiledGraph deepResearchGraph() {
        // 定义全局状态
        OverAllStateFactory stateFactory = () -> {
            OverAllState state = new OverAllState();
            state.registerKeyAndStrategy("question", new ReplaceStrategy());
            state.registerKeyAndStrategy("search_results", new AppendStrategy());
            state.registerKeyAndStrategy("analysis", new ReplaceStrategy());
            state.registerKeyAndStrategy("final_answer", new ReplaceStrategy());
            return state;
        };

        // 节点1：初始搜索
        NodeAction initialSearchNode = (state, config) -> {
            String question = (String) state.value("question").orElse("");

            // 调用搜索工具
            WebSearchResponse result = webSearchFunction.apply(
                new WebSearchRequest(question, 5)
            );

            state.update("search_results", result);
            return state;
        };

        // 节点2：分析并决定下一步
        NodeAction analysisNode = (state, config) -> {
            String question = (String) state.value("question").orElse("");
            List<Object> searchResults = (List<Object>) state.value("search_results").orElse(List.of());

            // 让 LLM 分析结果，决定是否需要更多信息
            String prompt = String.format("""
                问题：%s

                当前搜索结果：
                %s

                分析这些信息是否足够回答问题。
                如果足够，请返回 "SUFFICIENT"。
                如果不够，请返回 "NEED_MORE: [具体需要什么信息]"
                """, question, searchResults);

            String analysis = chatModel.call(prompt);
            state.update("analysis", analysis);
            return state;
        };

        // 节点3：深度搜索
        NodeAction deepSearchNode = (state, config) -> {
            String analysis = (String) state.value("analysis").orElse("");

            // 提取需要补充的信息
            String additionalQuery = extractQuery(analysis);

            // 执行深度搜索
            WebSearchResponse result = webSearchFunction.apply(
                new WebSearchRequest(additionalQuery, 3)
            );

            state.update("search_results", result);
            return state;
        };

        // 节点4：生成最终答案
        NodeAction finalAnswerNode = (state, config) -> {
            String question = (String) state.value("question").orElse("");
            List<Object> allResults = (List<Object>) state.value("search_results").orElse(List.of());

            String prompt = String.format("""
                问题：%s

                收集到的所有信息：
                %s

                基于以上信息，给出全面的答案。
                """, question, allResults);

            String answer = chatModel.call(prompt);
            state.update("final_answer", answer);
            return state;
        };

        // 构建工作流图
        StateGraph graph = new StateGraph("DeepResearch", stateFactory)
            .addNode("initial_search", node_async(initialSearchNode))
            .addNode("analysis", node_async(analysisNode))
            .addNode("deep_search", node_async(deepSearchNode))
            .addNode("final_answer", node_async(finalAnswerNode))

            .addEdge(START, "initial_search")
            .addEdge("initial_search", "analysis")

            // 条件路由：根据分析结果决定是否需要更多搜索
            .addConditionalEdges("analysis",
                edge_async(new AnalysisDispatcher()),
                Map.of(
                    "SUFFICIENT", "final_answer",
                    "NEED_MORE", "deep_search"
                ))

            .addEdge("deep_search", "analysis")  // 循环：深度搜索后再次分析
            .addEdge("final_answer", END);

        return graph.compile();
    }

    /**
     * 分析结果路由器
     */
    static class AnalysisDispatcher implements EdgeAction {
        @Override
        public String apply(OverAllState state) {
            String analysis = (String) state.value("analysis").orElse("");
            return analysis.startsWith("SUFFICIENT") ? "SUFFICIENT" : "NEED_MORE";
        }
    }

    /**
     * 执行 DeepResearch
     */
    public String research(String question) {
        Map<String, Object> initialState = Map.of("question", question);

        RunnableConfig config = RunnableConfig.builder()
            .threadId(UUID.randomUUID().toString())
            .build();

        OverAllState finalState = deepResearchGraph().invoke(initialState, config);

        return (String) finalState.value("final_answer").orElse("无法生成答案");
    }

    /**
     * 流式执行（前端实时展示）
     */
    public Flux<GraphEvent> researchStream(String question) {
        Map<String, Object> initialState = Map.of("question", question);

        RunnableConfig config = RunnableConfig.builder()
            .threadId(UUID.randomUUID().toString())
            .build();

        return deepResearchGraph().toFlux(initialState, config)
            .map(state -> new GraphEvent(
                (String) state.value("current_node").orElse("unknown"),
                state.values()
            ));
    }
}
```

---

#### MCP 工具集成

```java
/**
 * 集成 MCP 工具的智能体
 */
@Service
public class SuperAgentWithMcp {

    @Autowired
    private McpRouterService mcpRouter;

    @Autowired
    private ChatModel chatModel;

    /**
     * 智能选择并调用 MCP 工具
     */
    public String executeWithMcp(String userQuery) {
        // 1. 让 LLM 分析需要什么工具
        String toolAnalysis = chatModel.call(
            "用户问题：" + userQuery + "\n需要什么工具来回答这个问题？"
        );

        // 2. 搜索合适的 MCP 服务器
        String mcpServers = mcpRouter.searchMcpServer(
            toolAnalysis,
            "database,web,search,analysis",
            5
        );

        // 3. 让 LLM 决定使用哪个服务器和工具
        String toolDecision = chatModel.call(
            "可用服务器：" + mcpServers + "\n选择最合适的工具"
        );

        // 4. 执行工具
        ToolCall toolCall = parseToolCall(toolDecision);
        String result = mcpRouter.useTool(
            toolCall.serverName(),
            toolCall.toolName(),
            toolCall.arguments()
        );

        // 5. 基于工具结果生成最终答案
        return chatModel.call(
            "用户问题：" + userQuery + "\n工具结果：" + result + "\n给出答案"
        );
    }

    private ToolCall parseToolCall(String decision) {
        // 解析 LLM 决策，提取工具调用信息
        // ...
    }
}

record ToolCall(String serverName, String toolName, String arguments) {}
```

---

#### A2A 多 Agent 协作

```java
/**
 * 多 Agent 协作示例
 */
@Service
public class MultiAgentOrchestrator {

    @Autowired
    private A2aClient a2aClient;

    /**
     * 协调多个 Agent 完成复杂任务
     */
    public String coordinateAgents(String userQuery) {
        // 步骤1：调用研究 Agent（联网搜索）
        String researchResult = a2aClient.call(
            "research-agent",
            String.format("{\"task\": \"web-search\", \"query\": \"%s\"}", userQuery)
        );

        // 步骤2：调用数据分析 Agent
        String analysisResult = a2aClient.call(
            "data-analysis-agent",
            String.format("{\"task\": \"analyze\", \"data\": %s}", researchResult)
        );

        // 步骤3：调用摘要 Agent
        String summary = a2aClient.call(
            "summary-agent",
            String.format("{\"task\": \"summarize\", \"content\": \"%s\"}", analysisResult)
        );

        return summary;
    }
}
```

---

#### Controller API

```java
/**
 * Super Agent API Controller
 */
@RestController
@RequestMapping("/api/super-agent")
public class SuperAgentController {

    @Autowired
    private DeepResearchAgent deepResearchAgent;

    /**
     * 同步 DeepResearch
     */
    @PostMapping("/research")
    public ResponseEntity<String> research(@RequestBody ResearchRequest request) {
        String answer = deepResearchAgent.research(request.question());
        return ResponseEntity.ok(answer);
    }

    /**
     * 流式 DeepResearch（前端实时展示）
     */
    @GetMapping(value = "/research-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<GraphEvent>> researchStream(@RequestParam String question) {
        return deepResearchAgent.researchStream(question)
            .map(event -> ServerSentEvent.<GraphEvent>builder()
                .event(event.nodeName())
                .data(event)
                .build());
    }
}

record ResearchRequest(String question) {}
record GraphEvent(String nodeName, Map<String, Object> state) {}
```

---

#### 数据库设计

```sql
-- 超级智能体配置表
CREATE TABLE super_agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '智能体名称',
    slug VARCHAR(100) UNIQUE NOT NULL COMMENT 'URL标识',
    description TEXT COMMENT '描述',
    workflow_type VARCHAR(50) COMMENT '工作流类型（DEEP_RESEARCH/SEQUENTIAL/PARALLEL）',
    graph_config JSON COMMENT 'Graph 配置（节点、边、路由）',
    model_id BIGINT COMMENT '默认模型ID',
    max_iterations INT DEFAULT 5 COMMENT '最大推理轮数',
    enabled_tools JSON COMMENT '启用的工具列表',
    mcp_servers JSON COMMENT 'MCP 服务器配置',
    is_active TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,
    INDEX idx_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超级智能体配置';

-- 智能体执行日志表
CREATE TABLE agent_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL COMMENT '任务ID',
    agent_id BIGINT COMMENT '智能体ID',
    conversation_id VARCHAR(255) COMMENT '会话ID',
    question TEXT COMMENT '用户问题',
    node_name VARCHAR(100) COMMENT '节点名称',
    node_output TEXT COMMENT '节点输出',
    execution_time_ms INT COMMENT '执行耗时（毫秒）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体执行日志';
```

---

#### 包结构（使用 Spring AI Alibaba）

```
llm-agent/src/main/java/com/llmmanager/agent/
├── super/                          # 超级智能体（基于 Spring AI Alibaba）
│   ├── DeepResearchAgent.java     # DeepResearch 工作流
│   ├── SuperAgentWithMcp.java     # 集成 MCP 工具
│   └── MultiAgentOrchestrator.java # 多 Agent 协作
├── graph/                          # Spring AI Alibaba Graph 封装
│   ├── nodes/                      # 自定义节点
│   ├── edges/                      # 自定义路由
│   └── states/                     # 状态定义
└── tools/                          # 扩展工具
    └── web/
        └── WebSearchTool.java      # 联网搜索工具
```

---

#### 优势对比

| 特性 | 手动实现 | Spring AI Alibaba |
|------|---------|-------------------|
| **ReAct Agent** | 需要自己实现 | ✅ 开箱即用 |
| **工作流编排** | 需要自己设计状态机 | ✅ StateGraph 原生支持 |
| **MCP 集成** | 需要自己实现客户端 | ✅ McpRouterService 内置 |
| **多 Agent 协作** | 需要自己设计通信协议 | ✅ A2A 框架 |
| **条件路由** | 需要自己实现 | ✅ addConditionalEdges |
| **并行执行** | 需要管理线程池 | ✅ node_async 自动处理 |
| **状态管理** | 需要自己设计 | ✅ OverAllState + Strategy |
| **可观测性** | 需要集成 Micrometer | ✅ 内置 Observation 支持 |
| **开发时间** | 2-3 周 | **3-5 天** |

---

#### 实施计划（使用 Spring AI Alibaba）

1. **第 1 天**：集成 Spring AI Alibaba 依赖和配置
2. **第 2-3 天**：实现 DeepResearch 工作流
3. **第 4 天**：集成 MCP 工具和 A2A 协作
4. **第 5 天**：前端展示和 API 对接

**总计约 5 天**，比手动实现快 **3 倍**！

---

### 实施优先级

1. ✅ **阶段 1**：Augmented LLM 基础抽象（已完成）
2. ✅ **阶段 2**：工具调用层（已完成）
3. ✅ **阶段 3**：消息增强与多模态（已完成）
4. ✅ **阶段 4**：MCP（Model Context Protocol）（已完成）
5. ✅ **阶段 4.5**：Vector Store（RAG 支持）（已完成，已测试）
6. ✅ **阶段 5a**：Graph 工作流（**已完成，支持动态配置**）
7. ✅ **阶段 5b**：ReactAgent 智能体（**已完成**，基于 `spring-ai-alibaba-agent-framework:1.1.0.0-RC1`）

---

### 阶段 5a：Graph 工作流（已完成，支持动态配置）

基于 `spring-ai-alibaba-graph-core:1.0.0.2` 实现的工作流编排，支持两种方式：

1. **硬编码工作流**：适用于固定流程（如原有 DeepResearch）
2. **动态工作流**：通过 JSON 配置创建自定义工作流（新增功能）

> 📖 **详细配置文档**：[`docs/dynamic-workflow-guide.md`](./docs/dynamic-workflow-guide.md)
> 包含：配置值速查表、节点类型详解、DeepResearch 工作流详解

#### 动态工作流架构

**设计理念**：遵循 Spring AI Alibaba 官方推荐
- 使用 `OverAllState.value(key)` 直接从状态获取值
- 使用 `ChatClient.prompt().system().user()` 构建提示词
- 通过 `next_node` 状态键实现条件路由
- 节点返回 `Map<String, Object>` 更新状态

**核心组件**：

| 组件 | 路径 | 说明 |
|------|------|------|
| `DynamicGraphBuilder` | llm-agent/.../graph/dynamic/ | 动态构建 StateGraph |
| `NodeExecutor` | llm-agent/.../graph/dynamic/executor/ | 节点执行器接口 |
| `LlmNodeExecutor` | executor/LlmNodeExecutor.java | LLM 调用节点 |
| `ConditionNodeExecutor` | executor/ConditionNodeExecutor.java | 条件路由节点 |
| `TransformNodeExecutor` | executor/TransformNodeExecutor.java | 数据转换节点 |
| `DynamicWorkflowController` | llm-ops/.../controller/ | REST API |

#### 可用节点类型

| 节点类型 | 说明 | 必需配置 |
|---------|------|---------|
| `LLM_NODE` | 调用 LLM 进行文本生成 | `input_key`, `output_key` |
| `CONDITION_NODE` | 条件路由决策 | `condition_field`, `routes` |
| `TRANSFORM_NODE` | 数据转换处理 | `transform_type`, `input_keys`, `output_key` |

**TransformNodeExecutor 支持的转换类型**：

| 类型 | 说明 |
|------|------|
| `MERGE` | 合并多个字段值（换行分隔） |
| `EXTRACT` | 提取单个字段值 |
| `FORMAT` | 格式化多字段（key: value） |
| `SPLIT_LINES` | 按行分割为列表 |
| `PARSE_NUMBER` | 解析数字 |
| `PARSE_JSON` | 解析 JSON |
| `THRESHOLD_CHECK` | 阈值检查（返回 PASS/NEED_IMPROVEMENT） |
| `INCREMENT` | 递增数值 |

#### 工作流 JSON 配置格式

```json
{
  "name": "工作流名称",
  "description": "描述",
  "stateConfig": {
    "keys": [
      {"key": "question", "append": false},
      {"key": "results", "append": true}
    ]
  },
  "nodes": [
    {
      "id": "node_1",
      "type": "LLM_NODE",
      "name": "节点名称",
      "config": {
        "input_key": "question",
        "output_key": "answer",
        "system_prompt": "你是一个助手..."
      }
    }
  ],
  "edges": [
    {"from": "START", "to": "node_1", "type": "SIMPLE"},
    {"from": "node_1", "to": "END", "type": "SIMPLE"}
  ]
}
```

#### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/workflow/node-types` | GET | 获取可用节点类型 |
| `/api/workflow/validate` | POST | 验证工作流配置 |
| `/api/workflow/execute/{modelId}` | POST | 执行自定义工作流 |
| `/api/workflow/deep-research/{modelId}` | POST | 执行 DeepResearch 工作流 |

#### 使用示例

**1. 获取节点类型**
```bash
curl http://localhost:8080/api/workflow/node-types
```

**2. 执行 DeepResearch**
```bash
curl -X POST http://localhost:8080/api/workflow/deep-research/1 \
  -H "Content-Type: application/json" \
  -d '{"question": "人工智能的发展历史是什么？"}'
```

**3. 执行自定义工作流**
```bash
curl -X POST http://localhost:8080/api/workflow/execute/1 \
  -H "Content-Type: application/json" \
  -d '{
    "workflowConfig": "{...JSON配置...}",
    "initialState": {"question": "你的问题"}
  }'
```

#### 包结构

```
llm-agent/src/main/java/com/llmmanager/agent/graph/
├── dynamic/                           # 动态工作流（新增）
│   ├── DynamicGraphBuilder.java       # 核心构建器
│   ├── DynamicGraphTestService.java   # 测试服务
│   ├── dto/                           # 配置 DTO
│   │   ├── GraphWorkflowConfig.java
│   │   ├── NodeConfig.java
│   │   ├── EdgeConfig.java
│   │   └── StateKeyConfig.java
│   └── executor/                      # 节点执行器
│       ├── NodeExecutor.java          # 接口
│       ├── LlmNodeExecutor.java       # LLM 节点
│       ├── ConditionNodeExecutor.java # 条件节点
│       └── TransformNodeExecutor.java # 转换节点
├── workflow/                          # 硬编码工作流（原有）
│   └── DeepResearchWorkflow.java
├── node/                              # 硬编码节点（原有）
├── state/                             # 状态定义（原有）
└── core/                              # 工作流存储层（v2.5.0 迁移）
    ├── entity/GraphWorkflow.java      # 工作流实体
    ├── mapper/GraphWorkflowMapper.java
    └── service/
        ├── GraphWorkflowService.java       # CRUD 服务
        └── GraphWorkflowExecutor.java      # 执行器

llm-agent/src/main/resources/
└── workflows/
    └── deep-research.json             # DeepResearch 配置示例

llm-ops/src/main/java/com/llmmanager/ops/controller/
└── GraphWorkflowController.java       # 统一工作流 REST API（合并了 DynamicWorkflowController）
```

---

### 阶段 5b：ReactAgent 智能体（已完成）

基于 `spring-ai-alibaba-agent-framework:1.1.0.0-RC1` 实现的智能体框架，支持两种协作模式：

#### 核心组件

| 组件 | 路径 | 说明 |
|------|------|------|
| `AgentWrapper` | reactagent/core/ | ReactAgent 封装，简化使用 |
| `AgentToolAdapter` | reactagent/core/ | Agent-as-Tool 适配器 |
| `ConfigurableAgentWorkflow` | reactagent/configurable/ | 配置驱动的协作框架 |
| `SupervisorAgentTeam` | reactagent/autonomous/ | Agent 自主协作框架 |

#### 方案 A：ConfigurableAgentWorkflow（配置协作模式）

大流程人工配置，节点内 Agent 自主推理。支持三种模式：

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| `SEQUENTIAL` | 顺序执行多个 Agent | 流水线处理 |
| `PARALLEL` | 并行执行多个 Agent | 独立任务并发 |
| `ROUTING` | LLM 动态路由到不同 Agent | 智能分发 |

```java
// 使用示例
ConfigurableAgentWorkflow workflow = ConfigurableAgentWorkflow.builder()
    .name("research-workflow")
    .pattern(WorkflowPattern.SEQUENTIAL)
    .agent(researchAgent)
    .agent(analysisAgent)
    .agent(summaryAgent)
    .build();

WorkflowResult result = workflow.execute("研究人工智能的发展趋势");
```

#### 方案 B：SupervisorAgentTeam（自主协作模式）

Supervisor Agent 完全自主决定调用哪个 Worker Agent。

```java
// 使用示例
SupervisorAgentTeam team = SupervisorAgentTeam.builder()
    .name("research-team")
    .chatModel(chatModel)
    .worker(researchAgent)
    .worker(analysisAgent)
    .worker(writerAgent)
    .build();

String result = team.execute("帮我研究并撰写一篇关于量子计算的报告");
```

#### 包结构

```
llm-agent/src/main/java/com/llmmanager/agent/reactagent/
├── core/                                # 核心基础
│   ├── AgentWrapper.java               # ReactAgent 封装
│   └── AgentToolAdapter.java           # Agent-as-Tool 适配器
├── configurable/                        # 方案A：配置协作模式
│   ├── ConfigurableAgentWorkflow.java  # 核心类
│   ├── WorkflowPattern.java            # 模式枚举
│   ├── config/
│   │   ├── AgentConfig.java            # Agent 配置
│   │   └── AgentWorkflowConfig.java    # 工作流配置
│   └── pattern/
│       ├── PatternExecutor.java        # 模式执行器接口
│       ├── SequentialPatternExecutor.java
│       ├── ParallelPatternExecutor.java
│       ├── RoutingPatternExecutor.java
│       └── WorkflowResult.java         # 执行结果
├── autonomous/                          # 方案B：自主协作
│   └── SupervisorAgentTeam.java        # Supervisor + Workers
└── example/                             # 示例
    ├── SingleAgentExample.java          # 单个 ReactAgent 示例
    ├── ConfigurableWorkflowExample.java # 方案A 示例
    └── AutonomousTeamExample.java       # 方案B 示例
```

---

#### 概念区分

| 概念 | 说明 | 依赖 |
|------|------|------|
| **动态 Graph** | JSON 配置驱动，用户可自定义 | `spring-ai-alibaba-graph-core` ✅ |
| **硬编码 Graph** | 代码定义，固定流程 | `spring-ai-alibaba-graph-core` ✅ |
| **ReactAgent** | LLM 自主推理，动态决策 | `spring-ai-alibaba-agent-framework` ✅ |

---

### 总结

**当前状态**：
- ✅ Graph 工作流（硬编码）已实现
- ✅ 动态工作流（JSON 配置）已实现
- ✅ Vector Store（RAG 支持）已实现
- ✅ ReactAgent 智能体已实现（基于 `spring-ai-alibaba-agent-framework:1.1.0.0-RC1`）
- ✅ 多 Agent 协作已实现（ConfigurableAgentWorkflow + SupervisorAgentTeam）

---

## 编排层架构重构（2025-12-18）

### 重构目标

消除 `llm-service/orchestration` 层的代码重复，统一 ChatModel 管理和执行逻辑。

### 核心问题

1. **ChatModel 创建重复**：多个服务各自实现 ChatModel/ChatClient 的创建和缓存
2. **执行逻辑重复**：ReactAgent 和 Graph 工作流的执行逻辑分散在各服务中
3. **API 参数冗余**：`executeFromDatabase` 方法的 `modelId` 参数不必要

### 重构方案

#### 1. ChatModelProvider - 统一 ChatModel 管理

创建 `ChatModelProvider` 服务，集中管理 ChatModel/ChatClient 的获取和缓存：

```java
@Service
public class ChatModelProvider {
    // ChatModel 缓存
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    // 根据模型 ID 获取 OpenAiChatModel
    public OpenAiChatModel getChatModelByModelId(Long modelId);

    // 根据模型 ID 获取 ChatClient
    public ChatClient getChatClientByModelId(Long modelId);

    // 根据模型 ID 构建 ChatRequest
    public ChatRequest buildChatRequest(Long modelId);

    // 清除缓存
    public void clearCacheForChannel(Long channelId);
    public void clearAllCache();
}
```

#### 2. ReactAgentExecutionService - 公共执行方法

添加公共执行方法，供 `DynamicReactAgentExecutionService` 复用：

```java
@Service
public class ReactAgentExecutionService {
    // 公共执行方法
    public Map<String, Object> executeAgent(AgentWrapper agent, String message);
    public Map<String, Object> executeWorkflow(ConfigurableAgentWorkflow workflow, String message);
    public Map<String, Object> executeTeam(SupervisorAgentTeam team, String message);

    // 预定义场景（硬编码）
    public Map<String, Object> executeUniversalAssistant(Long modelId, String message);
    public Map<String, Object> executeResearchPipeline(Long modelId, String message);
    public Map<String, Object> executeEnterpriseTeam(Long modelId, String message);
}
```

#### 3. GraphWorkflowExecutor - 通用执行层

添加通用执行方法，供所有 Graph 工作流复用：

```java
@Service
public class GraphWorkflowExecutor {
    // 通用执行方法
    public Map<String, Object> execute(CompiledGraph compiledGraph, Map<String, Object> initialState);
    public Flux<NodeOutput> executeStream(CompiledGraph compiledGraph, Map<String, Object> initialState);
    public Map<String, Object> executeWithCache(CompiledGraph compiledGraph, String cacheKey, Map<String, Object> initialState);
    public Map<String, Object> executeFromCache(String cacheKey, Map<String, Object> initialState);

    // DeepResearch 专用方法（保持向后兼容）
    public ResearchResult deepResearch(ChatClient chatClient, String cacheKey, String question);
    public Flux<ResearchProgress> deepResearchStream(ChatClient chatClient, String cacheKey, String question);
}
```

#### 4. API 简化

移除 `executeFromDatabase` 方法的 `modelId` 参数，从 `ReactAgent` 实体获取：

```java
// 修改前
@PostMapping("/db/{slug}/{modelId}")
public Map<String, Object> executeFromDatabase(@PathVariable String slug, @PathVariable Long modelId, @RequestBody String message);

// 修改后
@PostMapping("/db/{slug}")
public Map<String, Object> executeFromDatabase(@PathVariable String slug, @RequestBody String message);
```

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Controller 层                                   │
│  ReactAgentController    GraphController    DynamicWorkflowController       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Orchestration 层                                   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      ChatModelProvider                               │    │
│  │  - getChatModelByModelId(modelId)                                   │    │
│  │  - getChatClientByModelId(modelId)                                  │    │
│  │  - buildChatRequest(modelId)                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│           ┌────────────────────────┼────────────────────────┐               │
│           ▼                        ▼                        ▼               │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │ ReactAgent      │    │ Graph           │    │ Dynamic         │         │
│  │ ExecutionService│    │ ExecutionService│    │ WorkflowService │         │
│  │                 │    │                 │    │                 │         │
│  │ 公共执行方法:    │    │ 使用:           │    │ 使用:           │         │
│  │ - executeAgent  │    │ GraphWorkflow   │    │ GraphWorkflow   │         │
│  │ - executeWorkflow│   │ Executor        │    │ Executor        │         │
│  │ - executeTeam   │    │                 │    │                 │         │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘         │
│           ▲                        │                        │               │
│           │                        ▼                        ▼               │
│  ┌─────────────────┐    ┌─────────────────────────────────────────┐        │
│  │ Dynamic         │    │           GraphWorkflowExecutor          │        │
│  │ ReactAgent      │    │  - execute(CompiledGraph, initialState)  │        │
│  │ ExecutionService│    │  - executeStream(...)                    │        │
│  │                 │    │  - deepResearch(...)                     │        │
│  │ 复用公共执行方法 │    └─────────────────────────────────────────┘        │
│  └─────────────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Agent 层                                        │
│  AgentWrapper    ConfigurableAgentWorkflow    SupervisorAgentTeam           │
│  CompiledGraph   DeepResearchWorkflow         DynamicGraphBuilder           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 服务职责对比

| 服务 | 数据来源 | 执行逻辑 | ChatModel |
|------|---------|---------|-----------|
| `ReactAgentExecutionService` | 硬编码 | 自身实现 | ChatModelProvider |
| `DynamicReactAgentExecutionService` | 数据库 | 复用 ReactAgentExecutionService | ChatModelProvider |
| `GraphExecutionService` | 硬编码 (DeepResearch) | GraphWorkflowExecutor | ChatModelProvider |
| `DynamicWorkflowExecutionService` | 数据库 | GraphWorkflowExecutor | ChatModelProvider |

### 修改文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `ChatModelProvider.java` | 新增 | 统一 ChatModel 管理 |
| `ReactAgentExecutionService.java` | 修改 | 添加公共执行方法 |
| `DynamicReactAgentExecutionService.java` | 修改 | 复用公共执行方法，移除 modelId 参数 |
| `GraphWorkflowExecutor.java` | 修改 | 添加通用执行方法 |
| `GraphExecutionService.java` | 修改 | 使用 ChatModelProvider |
| `DynamicWorkflowExecutionService.java` | 修改 | 使用 GraphWorkflowExecutor |
| `ReactAgentController.java` | 修改 | API 路径简化 |

### 设计模式

1. **Provider 模式**：`ChatModelProvider` 统一提供 ChatModel 实例
2. **Template Method 模式**：公共执行方法定义执行骨架，子类/调用方提供具体实现
3. **Facade 模式**：`GraphWorkflowExecutor` 封装复杂的工作流执行逻辑
4. **Cache 模式**：ChatModel 和 CompiledGraph 缓存

---

## 可观测性（Observability）✅ 已实现

### 概述

基于无侵入方式实现的可观测性支持，包括：
- **基础监控**：Spring Boot Actuator 端点
- **业务指标**：MetricsAdvisor (LLM 对话) + ObservabilityAspect (Agent/Graph)
- **分布式追踪**：Micrometer Tracing + Brave + Zipkin

### 配置开关

```yaml
llm:
  observability:
    enabled: true           # 总开关（false 则所有组件不加载）
    metrics-enabled: true   # 指标收集开关
    tracing-enabled: true   # 分布式追踪开关
```

### 监控端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 指标列表 |
| `/actuator/metrics/{name}` | 指定指标详情 |

### 关键指标

| 指标 | 类型 | 标签 | 说明 |
|-----|------|------|------|
| `llm.chat.duration` | Timer | model, channel, status | LLM 对话耗时 |
| `llm.tokens.prompt` | Counter | model, channel | 输入 Token 数 |
| `llm.tokens.completion` | Counter | model, channel | 输出 Token 数 |
| `llm.chat.total` | Counter | model, channel, status | 对话总次数 |
| `agent.execution.duration` | Timer | type, method, status | Agent 执行耗时 |
| `agent.execution.total` | Counter | type, method, status | Agent 执行次数 |
| `graph.workflow.duration` | Timer | type, method, status | 工作流耗时 |
| `graph.workflow.total` | Counter | type, method, status | 工作流执行次数 |

### 分布式追踪

**Zipkin 配置**：
```yaml
management:
  tracing:
    enabled: ${llm.observability.tracing-enabled:true}
    sampling:
      probability: 1.0  # 采样率（生产环境建议 0.1）
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

**日志格式**（含 TraceId）：
```
2025-12-18 15:00:00.000 [main] [abc123def456] [12345678] INFO c.l.a.o.ObservabilityAspect - 开始执行...
```

### 包结构

```
llm-agent/src/main/java/com/llmmanager/agent/
├── advisor/
│   └── MetricsAdvisor.java          # LLM 对话指标收集
└── observability/
    ├── ObservabilityAspect.java     # AOP 切面（Agent/Graph 指标）
    ├── ObservabilityConfig.java     # 配置类
    └── ObservabilityProperties.java # 配置属性
```

### 验收方式

```bash
# 启动应用后验证
# 1. 健康检查
curl http://localhost:8080/actuator/health

# 2. 查看 LLM 对话指标（需要先执行对话）
curl http://localhost:8080/actuator/metrics/llm.chat.duration

# 3. 查看 Agent 执行指标（需要先执行 Agent）
curl http://localhost:8080/actuator/metrics/agent.execution.duration

# 4. Zipkin UI（需要启动 Zipkin）
# docker run -d -p 9411:9411 openzipkin/zipkin
# 访问 http://localhost:9411
```

---

## 📋 待开发任务

### 1. 可观测性增强 🟢 低优先级

**目标**：在已实现基础上进一步增强

| 任务 | 说明 | 状态 |
|------|------|------|
| 执行时间统计 | 总耗时、各节点耗时 | ✅ 已实现 |
| Token 使用量统计 | 输入/输出/总计 | ✅ 已实现 |
| 成功率/失败率统计 | 执行结果统计 | ✅ 已实现 |
| 结构化日志 | JSON 格式日志 | ⏳ 可选 |
| 请求追踪 ID | TraceId 贯穿全链路 | ✅ 已实现 |
| Prometheus 指标 | 指标暴露 | ⏳ 可选（当前使用 Actuator） |
| 执行历史持久化 | 执行记录存储 | ✅ 已有（a_conversation_turns 表） |
| Grafana 仪表盘 | 可视化面板 | ⏳ 可选 |

### 2. 整体重构（异常处理、返回格式）🟡 中优先级

**目标**：统一异常处理机制和返回格式，提升代码质量

| 任务 | 说明 | 状态 |
|------|------|------|
| 业务异常体系 | BaseException、BusinessException、SystemException | ⏳ |
| 全局异常处理器 | @ControllerAdvice | ⏳ |
| 异常码标准化 | 模块前缀 + 错误码 | ⏳ |
| Result<T> 返回类 | 统一返回格式 | ⏳ |
| PageResult<T> | 分页响应格式 | ⏳ |
| 参数校验统一 | @Valid + 自定义校验器 | ⏳ |

### 3. ReactAgent 服务化 🔴 高优先级

**目标**：将 ReactAgent 能力对外暴露为服务，供其他项目调用

**背景**：有实际项目需要使用 ReactAgent 能力，需要提供标准化的服务接口

#### 核心任务

| 任务 | 说明 | 状态 |
|------|------|------|
| REST API 接口 | `/api/external/react-agent/{slug}` | ✅ 已完成 |
| OpenAI 兼容 API | `/v1/chat/completions` 格式 | ✅ 已完成 |
| 流式输出支持 | SSE 流式响应（进度流） | ✅ 已完成 |
| API Key 认证 | 复用现有认证机制 | ✅ 已完成 |
| Java SDK | 封装 HTTP 调用的客户端库 | ⏳ |
| 接口文档 | Swagger/OpenAPI 文档 | ⏳ |

#### 已实现的 API 端点

**REST API**:

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/external/react-agent/{slug}` | POST | 同步执行 ReactAgent |
| `/api/external/react-agent/{slug}/stream` | POST | 流式执行（SSE 进度流） |
| `/api/external/react-agent/list` | GET | 获取可用 Agent 列表 |
| `/api/external/react-agent/{slug}` | GET | 获取 Agent 详情 |

**OpenAI 兼容 API**:

**LLM 模型 API**（直接调用模型）:

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/chat/completions` | POST | Chat Completions（同步/流式） |
| `/v1/chat/completions/stream` | POST | Chat Completions（显式流式） |
| `/v1/models` | GET | 列出可用 LLM 模型 |
| `/v1/models/{model}` | GET | 获取模型详情 |

**ReactAgent API**（调用智能体）:

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/agents` | GET | 列出所有可用 Agent |
| `/v1/agents/{slug}` | GET | 获取 Agent 详情 |
| `/v1/agents/{slug}/completions` | POST | Agent 对话（同步/流式） |
| `/v1/agents/{slug}/completions/stream` | POST | Agent 对话（显式流式） |

**Model 格式说明**（用于 `/v1/chat/completions`）:
- `{modelId}` - 直接使用模型 ID（如 "1", "2"）
- `{modelIdentifier}` - 使用模型标识（如 "gpt-4", "qwen-plus"）

**Agent 调用说明**（用于 `/v1/agents/{slug}/completions`）:
- `{slug}` - Agent 的唯一标识（如 "universal-assistant"）
- 支持 SINGLE、SEQUENTIAL、SUPERVISOR 三种类型

#### 调用示例

**同步调用**：
```bash
curl -X POST https://your-domain/api/external/react-agent/universal-assistant \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查询北京天气"}'
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "success": true,
    "result": "北京今天天气晴朗，温度25°C...",
    "agentName": "全能助手",
    "agentType": "SINGLE",
    "slug": "universal-assistant",
    "agentConfigName": "全能助手"
  }
}
```

**流式调用**：
```bash
curl -N -X POST https://your-domain/api/external/react-agent/universal-assistant/stream \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我查询北京天气"}'
```

**SSE 事件流**：
```
event: start
data: {"slug":"universal-assistant","agentName":"全能助手","agentType":"SINGLE","status":"processing"}

event: progress
data: {"step":"thinking","message":"Agent 正在分析问题..."}

event: complete
data: {"success":true,"result":"北京今天天气晴朗...","status":"completed"}

data: [DONE]
```

**获取 Agent 列表**：
```bash
curl https://your-domain/api/external/react-agent/list \
  -H "Authorization: Bearer sk-xxxx"
```

#### OpenAI 兼容 API 调用示例

**Python 调用**：
```python
import openai
import requests

# ========== 直接调用 LLM 模型 ==========
client = openai.OpenAI(
    api_key="your-api-key",
    base_url="https://your-domain/v1"
)

# 使用模型 ID 调用
response = client.chat.completions.create(
    model="1",  # 模型 ID
    messages=[{"role": "user", "content": "你好"}]
)
print(response.choices[0].message.content)

# 使用模型标识调用
response = client.chat.completions.create(
    model="gpt-4",  # 模型标识（如 modelIdentifier）
    messages=[
        {"role": "system", "content": "你是一个翻译助手"},
        {"role": "user", "content": "翻译：Hello World"}
    ]
)

# 流式调用
for chunk in client.chat.completions.create(
    model="1",
    messages=[{"role": "user", "content": "写一首诗"}],
    stream=True
):
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")

# ========== 调用 ReactAgent ==========
# 注意：ReactAgent 使用独立的 /v1/agents 端点，不通过 OpenAI SDK

# 获取 Agent 列表
response = requests.get(
    "https://your-domain/v1/agents",
    headers={"Authorization": "Bearer sk-xxxx"}
)
agents = response.json()

# 同步调用 Agent（支持 SINGLE/SEQUENTIAL/SUPERVISOR）
response = requests.post(
    "https://your-domain/v1/agents/universal-assistant/completions",
    headers={
        "Authorization": "Bearer sk-xxxx",
        "Content-Type": "application/json"
    },
    json={
        "messages": [{"role": "user", "content": "帮我查询北京天气"}]
    }
)
print(response.json()["choices"][0]["message"]["content"])

# 流式调用 Agent
response = requests.post(
    "https://your-domain/v1/agents/universal-assistant/completions",
    headers={
        "Authorization": "Bearer sk-xxxx",
        "Content-Type": "application/json"
    },
    json={
        "messages": [{"role": "user", "content": "写一篇关于 AI 的文章"}],
        "stream": True
    },
    stream=True
)
for line in response.iter_lines():
    if line:
        print(line.decode())
```

**Java 调用**（使用 HTTP Client）：
```java
// ========== 直接调用 LLM 模型 ==========
HttpClient client = HttpClient.newHttpClient();
String requestBody = """
    {
        "model": "1",
        "messages": [{"role": "user", "content": "你好"}]
    }
    """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://your-domain/v1/chat/completions"))
    .header("Authorization", "Bearer sk-xxxx")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());

// ========== 调用 ReactAgent ==========
String agentRequestBody = """
    {
        "messages": [{"role": "user", "content": "帮我查询北京天气"}]
    }
    """;

HttpRequest agentRequest = HttpRequest.newBuilder()
    .uri(URI.create("https://your-domain/v1/agents/universal-assistant/completions"))
    .header("Authorization", "Bearer sk-xxxx")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(agentRequestBody))
    .build();

HttpResponse<String> agentResponse = client.send(agentRequest, HttpResponse.BodyHandlers.ofString());
System.out.println(agentResponse.body());
```

**curl 调用**：
```bash
# ========== 直接调用 LLM 模型 ==========
# 同步调用
curl -X POST https://your-domain/v1/chat/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "1",
    "messages": [{"role": "user", "content": "你好"}]
  }'

# 流式调用
curl -N -X POST https://your-domain/v1/chat/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "写一首诗"}],
    "stream": true
  }'

# ========== 调用 ReactAgent ==========
# 获取 Agent 列表
curl https://your-domain/v1/agents \
  -H "Authorization: Bearer sk-xxxx"

# 同步调用 Agent
curl -X POST https://your-domain/v1/agents/universal-assistant/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "帮我查询北京天气"}]
  }'

# 流式调用 Agent
curl -N -X POST https://your-domain/v1/agents/universal-assistant/completions \
  -H "Authorization: Bearer sk-xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "研究人工智能的发展趋势"}],
    "stream": true
  }'
```

#### 待实现：Java SDK

```java
// 发布为 Maven 包: llm-manager-client
LlmManagerClient client = new LlmManagerClient("https://your-domain", "sk-xxxx");

// 调用 ReactAgent
ReactAgentResponse response = client.executeReactAgent("universal-assistant", "查询天气");

// 直接调用模型
ChatResponse chatResponse = client.chat("gpt-4", "你好");
```

#### 实施计划

1. **第 1 阶段**：REST API 基础接口 ✅ 已完成
   - 新增 `ExternalReactAgentController`
   - 同步/流式接口
   - 复用 API Key 认证

2. **第 2 阶段**：OpenAI 兼容层 ✅ 已完成
   - 新增 `OpenAiCompatibleController`（LLM 模型调用）
   - 新增 `OpenAiAgentsController`（ReactAgent 调用）
   - API 架构分离：
     - `/v1/chat/completions` → 直接调用 LLM 模型
     - `/v1/agents/{slug}/completions` → 调用 ReactAgent
   - 支持三种 ReactAgent 类型（SINGLE/SEQUENTIAL/SUPERVISOR）
   - 流式输出兼容

3. **第 3 阶段**：SDK 与文档 ⏳
   - 新建 `llm-manager-client` 模块
   - 发布 Maven 包
   - 完善 API 文档
