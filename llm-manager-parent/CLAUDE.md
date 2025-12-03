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
curl -N -X POST "http://localhost:8080/api/chat/agents/{slug}/stream?conversationId=conv-123" \
  -H "Cookie: satoken={token}" \
  -H "Content-Type: text/plain" \
  -d "继续上次的话题"

# 图片对话（通过 URL，流式）
curl -N -X POST "http://localhost:8080/api/chat/{modelId}/with-image-url?conversationId=conv-123" \
  -H "Cookie: satoken={token}" \
  -d "message=这张图片里有什么？" \
  -d "imageUrls=https://example.com/image.jpg"

# 图片对话（通过 URL，同步）
curl -X POST "http://localhost:8080/api/chat/{modelId}/with-image-url/sync?conversationId=conv-123" \
  -H "Cookie: satoken={token}" \
  -d "message=描述这张图片" \
  -d "imageUrls=https://example.com/image.jpg"

# 图片对话（文件上传）
curl -X POST "http://localhost:8080/api/chat/{modelId}/with-image?conversationId=conv-123" \
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
- `storage/core/`: 核心数据层，直接映射数据库（Entity + Mapper + Service）
- `storage/memory/`: LLM 业务层，实现聊天记忆功能（Spring AI 适配 + 管理器）
- `advisor/`: 预留给未来的自定义 Advisor 实现（如 OpsChatMemoryAdvisor）

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
3. 🎯 **阶段 5**：Super Agent with Spring AI Alibaba（**推荐优先实现**）
4. 🔲 **阶段 3**：多模态支持（可选）
5. 🔲 **阶段 4**：MCP + Vector Store（Spring AI Alibaba 已内置）

### 总结

**强烈建议使用 Spring AI Alibaba 框架**，理由：
- ✅ 官方维护，稳定可靠
- ✅ 功能完整，开箱即用
- ✅ 文档齐全，社区活跃
- ✅ 开发效率提升 3 倍
- ✅ 与 Spring 生态无缝集成

阶段 3、4 可以根据实际需求选择性实现，因为 Spring AI Alibaba 已经内置了大部分功能。
