# LLM Manager

一个基于 Spring AI 和 Vue 3 的大语言模型管理平台，支持多模型管理、智能代理（Agent）配置和实时流式对话。

## 项目简介

LLM Manager 是一个现代化的 LLM 管理系统，旨在简化大语言模型的接入、配置和使用。通过统一的界面管理多个 LLM 提供商（OpenAI、Ollama、Azure OpenAI 等），支持创建智能代理（Agent）并通过 API 对外提供服务。

### 核心特性

- **多模型管理**：支持多个 LLM 提供商，统一管理
- **智能代理（Agent）**：配置系统提示词和参数，创建专用 AI 助手
- **实时流式对话**：基于 SSE 的真正实时流式输出
- **思考模式（Reasoning）**：支持 DeepSeek R1、OpenAI o1、豆包等模型的深度推理模式
- **工具调用（Function Calling）**：支持 LLM 自动调用外部工具（天气查询、计算器等）
- **会话历史管理**：支持多会话管理和历史记忆
- **Markdown 渲染**：完整支持 Markdown 格式，包括代码高亮、表格、列表等
- **API Key 管理**：为外部应用提供安全的 API 访问
- **用户认证**：基于 Sa-Token 的安全认证机制
- **Graph 工作流**：支持硬编码和 JSON 动态配置的工作流编排（DeepResearch 等）
- **ReactAgent 框架**：基于 Spring AI Alibaba 的智能体，支持 SINGLE / SEQUENTIAL / SUPERVISOR 三种模式
- **MCP 集成**：支持 Model Context Protocol，连接外部工具服务器
- **RAG 知识库**：向量检索增强生成，支持 SimpleVectorStore 和 Milvus
- **人工审核（Human Review）**：Agent 可主动提交审核，批准后自动恢复执行，支持跨服务重启持久化

## 技术栈

### 后端
- **Spring Boot 3.2.5** - 应用框架
- **Spring AI OpenAI 1.1.0-M4** - LLM 集成（支持 OpenAI 兼容接口）
- **Java 21** (最低17) - 编程语言
- **MyBatis-Plus 3.5.7** - ORM 框架
- **MySQL 8.x / TiDB** - 数据库
- **Druid 1.2.23** - 连接池
- **Sa-Token 1.37.0** - 认证授权
- **Lombok** - 代码简化
- **Maven** - 构建工具

**多模块架构**：
- `llm-common` - 公共模块（BaseEntity、工具类）
- `llm-agent` - AI 交互层（Spring AI 封装、对话历史管理）
- `llm-service` - 业务逻辑层（实体管理、业务编排）
- `llm-ops` - 管理后台应用
- `llm-openapi` - 外部 API 应用

### 前端
- **Vue 3** - 前端框架
- **Vite 7.2.2** - 构建工具
- **Vue Router 4** - 路由管理
- **TailwindCSS 3.4** - UI 样式
- **Axios 1.13** - HTTP 客户端
- **Marked** - Markdown 解析
- **DOMPurify** - XSS 防护

## 项目结构

```
llm-manager/
├── llm-manager-parent/       # 后端（多模块架构）
│   ├── llm-common/           # 公共模块（BaseEntity、工具类）
│   ├── llm-agent/            # AI 交互层（Spring AI 封装、对话历史管理）
│   ├── llm-service/          # 业务逻辑层（实体管理、业务编排）
│   ├── llm-ops/              # 管理后台应用
│   ├── llm-openapi/          # 外部 API 应用
│   ├── docs/                 # 技术文档（工具调用、功能说明）
│   ├── pom.xml               # 父 POM
│   └── README.md             # 详细文档
│
└── llm-manager-ui/           # 前端项目
    ├── src/
    │   ├── views/            # 页面组件
    │   ├── components/       # 通用组件
    │   ├── services/         # API 服务
    │   ├── router/           # 路由配置
    │   └── utils/            # 工具函数
    ├── package.json
    └── vite.config.js
```

## 环境要求

### 后端
- **JDK 17** 或更高版本（推荐 JDK 21）
- **Maven 3.8+**
- **MySQL 8.x** 或 **TiDB**（兼容 MySQL 协议）
- **内存**：至少 1GB 可用内存

### 前端
- **Node.js 16+**
- **npm 或 yarn**

## 快速开始

完整的部署文档请参考：[llm-manager-parent/README.md](./llm-manager-parent/README.md)

### 1. 克隆项目

```bash
git clone <repository-url>
cd llm-manager
```

### 2. 配置数据库

创建 MySQL 数据库：

```sql
CREATE DATABASE llm_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行初始化脚本：`llm-manager-parent/schema.sql`

### 3. 配置后端

编辑 `llm-manager-parent/llm-ops/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/llm_manager
    username: your_username
    password: your_password

  ai:
    openai:
      api-key: ${OPENAI_API_KEY:your-api-key}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
```

### 4. 启动后端

```bash
cd llm-manager-parent

# 配置 JAVA_HOME（macOS/Linux）
export JAVA_HOME=/path/to/jdk-21

# 编译项目
mvn clean compile -DskipTests

# 启动管理后台（端口 8083）
cd llm-ops
mvn spring-boot:run

# 或启动外部 API（端口 8084）
cd llm-openapi
mvn spring-boot:run
```

**默认管理员账号**：
- 用户名：`admin`
- 密码：`123456`

### 5. 启动前端

```bash
cd llm-manager-ui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端访问地址：**http://localhost:5173**

## 配置说明

### LLM 配置

系统支持从数据库 Channel 表动态读取 LLM 配置。配置优先级：

**Channel 数据库配置 > 环境变量 > 默认值**

#### 支持的 LLM 提供商

系统支持任何兼容 OpenAI API 的服务，包括：
- **OpenAI** - 官方 API
- **Ollama** - 本地模型（需设置 base-url 为 `http://localhost:11434`）
- **Azure OpenAI** - 微软云服务
- **其他兼容服务** - 如 DeepSeek、零一万物等

### 数据库配置

生产环境建议使用 MySQL 8.x 或 TiDB：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/llm_manager?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your-password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 前端配置

API 地址配置在 `llm-manager-ui/src/services/api.js`：

```javascript
const API_BASE_URL = 'http://localhost:8083'  // 管理后台
// const API_BASE_URL = 'http://localhost:8084'  // 外部 API
```

## 使用指南

### 1. 配置 Channel（渠道）

Channel 代表一个 LLM 提供商的连接配置。

1. 登录系统
2. 进入 **Channels** 页面
3. 点击 **Add Channel**
4. 填写配置信息：
   - **Name**: 渠道名称（如 "OpenAI GPT"）
   - **Type**: 选择提供商类型
   - **Base URL**: API 地址
   - **API Key**: 访问密钥
5. 点击保存

### 2. 配置 Model（模型）

Model 关联到 Channel，代表一个具体的 LLM 模型。

1. 进入 **Models** 页面
2. 点击 **Add Model**
3. 填写配置：
   - **Name**: 模型名称
   - **Model Identifier**: 模型 ID（如 `gpt-3.5-turbo`）
   - **Channel**: 选择所属渠道
   - **Temperature**: 温度参数（0-1）
4. 点击保存

### 3. 创建 Agent（智能代理）

Agent 是基于 Model 的定制化 AI 助手。

1. 进入 **Agents** 页面
2. 点击 **Add Agent**
3. 配置参数：
   - **Name**: 代理名称
   - **Slug**: URL 友好的标识符
   - **Model**: 选择基础模型
   - **System Prompt**: 系统提示词（定义代理行为）
   - **Temperature Override**: 可选的温度覆盖
4. 点击保存

### 4. 使用聊天功能

#### 内部聊天（需登录）

1. 进入 **Chat** 页面
2. 选择模式：
   - **原生模型**：直接使用配置的模型
   - **智能体**：使用 Agent（需要 API Key）
3. 选择工具（可选）：勾选需要使用的工具（天气查询、计算器等）
4. 输入消息，开始对话

#### 外部 API 调用

```bash
# 非流式调用
curl -X POST http://localhost:8084/api/external/agents/{slug}/chat \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好",
    "conversationId": "optional-conversation-id"
  }'

# 流式调用（SSE）
curl -N http://localhost:8084/api/external/agents/{slug}/chat/stream \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好",
    "conversationId": "optional-conversation-id",
    "enableTools": true
  }'
```

## API 文档

### 认证

所有 `/api/external/*` 接口需要在请求头中携带 API Key：

```
Authorization: Bearer {your-api-key}
```

在系统中创建 API Key：**Settings** → **API Keys** → **Create Key**

### 核心接口

#### 1. Agent 对话（非流式）

```http
POST /api/external/agents/{slug}/chat
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "message": "你的问题",
  "conversationId": "可选的会话ID"
}
```

**响应**：
```json
{
  "response": "AI 的回复内容",
  "conversationId": "会话ID"
}
```

#### 2. Agent 对话（流式）

```http
POST /api/external/agents/{slug}/chat/stream
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "message": "你的问题",
  "conversationId": "可选的会话ID",
  "enableTools": true
}
```

**响应**（Server-Sent Events）：
```
data: {"choices":[{"delta":{"content":"你"}}]}

data: {"choices":[{"delta":{"content":"好"}}]}

data: [DONE]
```

#### 3. 内部模型对话（需登录）

```http
POST /api/chat/{modelId}/stream
Content-Type: text/plain
Cookie: satoken={token}

你的消息内容
```

## 开发说明

### 后端开发

#### 架构说明

系统采用动态 Channel 配置架构，`LlmExecutionService` 根据数据库中的 Channel 配置动态创建 ChatClient：

```java
// 从 Channel 配置动态创建 ChatClient
private ChatClient createChatClient(Channel channel) {
    String apiKey = channel.getApiKey() != null ? channel.getApiKey() : defaultApiKey;
    String baseUrl = channel.getBaseUrl() != null ? channel.getBaseUrl() : defaultBaseUrl;

    OpenAiApi openAiApi = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build();

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .build();

    return ChatClient.builder(chatModel).build();
}
```

#### 添加新的工具（Function Calling）

使用 Spring AI 的 `@Tool` 注解：

```java
@Component
public class MyTools {

    @Tool(name = "my_tool", description = "工具描述")
    public String myTool(@JsonProperty(required = true, value = "param") String param) {
        // 工具实现
        return "结果";
    }
}
```

工具会被 `ToolFunctionManager` 自动发现并注册。

#### 添加新的 LLM 提供商

由于使用 OpenAI 兼容接口，只需在 Channel 配置中设置正确的 `baseUrl` 和 `apiKey` 即可支持任何兼容服务。

### 前端开发

#### 添加新页面

1. 在 `src/views/` 创建 Vue 组件
2. 在 `src/router/index.js` 添加路由
3. 在导航栏添加链接

#### 自定义主题

修改 `tailwind.config.js`：

```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        primary: '#your-color',
      }
    }
  }
}
```

## 生产部署

### 后端部署

#### 打包应用

```bash
cd llm-manager-parent
mvn clean package -DskipTests
```

生成的 JAR 文件位于各模块的 `target/` 目录。

#### 运行

```bash
# 运行管理后台
java -jar llm-ops/target/llm-ops-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://prod-db:3306/llm_manager \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=prod_password

# 运行外部 API
java -jar llm-openapi/target/llm-openapi-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

#### Docker 部署

```dockerfile
FROM openjdk:21-slim
WORKDIR /app
COPY llm-ops/target/llm-ops-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 前端部署

#### 构建生产版本

```bash
cd llm-manager-ui
npm run build
```

构建产物在 `dist/` 目录。

#### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # 管理后台 API
    location /api {
        proxy_pass http://localhost:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 常见问题

### Q1: 后端启动失败，提示 Java 版本错误

**A**: 确保使用 JDK 17 或更高版本（推荐 JDK 21）。检查 `JAVA_HOME` 环境变量：

```bash
echo $JAVA_HOME
java -version
```

### Q2: 流式输出不工作，一直显示 loading

**A**: 确保：
1. 后端使用最新代码（SseEmitter 实现）
2. 前端代码已更新
3. 清除浏览器缓存后重试

### Q3: Markdown 显示异常

**A**: 检查前端是否正确安装了依赖：

```bash
npm install marked dompurify
```

### Q4: API Key 认证失败

**A**:
1. 确认 API Key 已激活
2. 检查请求头格式：`Authorization: Bearer {key}`
3. 查看后端日志确认错误详情

### Q5: 数据库连接失败

**A**:
1. 确认 MySQL 服务已启动
2. 检查数据库配置是否正确
3. 确认数据库已创建并执行了初始化脚本

### Q6: 工具调用不生效

**A**:
1. 确保工具类上有 `@Component` 注解
2. 确保方法上有 `@Tool` 注解
3. 检查 `enableTools` 参数是否为 true
4. 查看后端日志确认工具是否被正确注册

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。

## 联系方式

- 问题反馈：提交 Issue
- 功能建议：提交 Feature Request

## 更新日志

### v2.8.0 (2026-04-29) 🔒

**人工审核功能 - 跨服务重启持久化恢复**

#### 🆕 新增功能

**人工审核全链路打通**
- **HumanReviewTool**：ReactAgent 可主动调用 `requestHumanReview` 工具提交内容审核
- **审核记录持久化**：审核上下文（`originalTask`、`submittedContent`、`modelId`）完整写入数据库 `context_data` 字段
- **跨重启恢复执行**：服务重启后批准审核，Agent 可从数据库读取完整上下文并继续执行
- **HumanReviewContext 扩展**：新增 `originalTask`、`modelId` 字段，恢复执行时无需依赖内存状态

#### 🐛 Bug 修复

- **`@Select` 不走 `autoResultMap` 问题**：`PendingReviewServiceImpl.findByReviewCode()` 改用 `LambdaQueryWrapper`，确保 JSON 字段 `contextData` 被正确反序列化（MyBatis-Plus `autoResultMap` 对自定义 `@Select` 注解不生效）

#### ✅ 验证场景

```
触发审核（服务运行中）→ 停止服务 → 重启服务 → 批准审核 → Agent 成功恢复执行
```

---


### v2.7.0 (2025-12-25) 🔧

**思考模式参数注入修复**

#### 🐛 Bug 修复
- **工具调用兼容性**：修复思考模式（thinkingMode）与工具调用（enableTools）无法同时生效的问题
- **Spring AI merge 问题**：解决 `ModelOptionsUtils.merge()` 导致 `extraBody` 参数丢失的问题

#### 🔄 架构重构
- **metadata 方案**：采用 metadata 传递 thinking 参数（在 merge 中被保留），替代原有的 ThreadLocal 方案
- **ThinkingAwareOpenAiApi**：在 HTTP 层面将 metadata 展开到 extraBody，实现参数正确注入
- **代码简化**：移除反射依赖，直接使用 OpenAiChatModel

#### 🗑️ 移除组件
- `ThinkingChatModel.java` - metadata 方案无需包装器
- `ThinkingContext.java` - 不再使用 ThreadLocal

#### 📚 参考
- GitHub Issue: https://github.com/spring-projects/spring-ai/issues/4879


### v2.6.0 (2025-12-18) 🚀

**ReactAgent 框架与编排层重构**

#### 🆕 新增功能

**1. ReactAgent 框架**
- **AgentWrapper**：统一的 Agent 封装器，支持 SINGLE、SEQUENTIAL、SUPERVISOR 三种类型
- **ConfigurableAgentWorkflow**：可配置的 Agent 工作流，支持顺序执行模式
- **SupervisorAgentTeam**：Supervisor 模式的多 Agent 协作团队
- **ReactAgentFactory**：从数据库配置动态创建 Agent 的工厂类
- **ToolRegistry**：工具注册中心，支持动态工具发现和注册

**2. 示例工具集**
- `DateTimeTools`：日期时间工具
- `KnowledgeTools`：知识库查询工具
- `NewsTools`：新闻搜索工具
- `StockTools`：股票查询工具
- `TranslationTools`：翻译工具

**3. 示例配置**
- `resources/reactagent/single-agent-example.json`
- `resources/reactagent/sequential-agent-example.json`
- `resources/reactagent/supervisor-agent-example.json`

#### 🔄 架构重构

**1. ChatModelProvider - 统一 ChatModel 管理**
- 集中管理 ChatModel/ChatClient 的创建和缓存
- 消除各服务中重复的 ChatModel 创建逻辑
- 支持按 Channel 清除缓存

**2. ReactAgentExecutionService - 公共执行方法**
- `executeAgent()`：执行单个 Agent
- `executeWorkflow()`：执行顺序工作流
- `executeTeam()`：执行 Supervisor 团队
- `DynamicReactAgentExecutionService` 复用这些公共方法

**3. GraphWorkflowExecutor - 通用执行层**
- `execute(CompiledGraph, initialState)`：同步执行
- `executeStream(CompiledGraph, initialState)`：流式执行
- `executeWithCache()`：带缓存执行
- `GraphExecutionService` 和 `DynamicWorkflowExecutionService` 统一使用

**4. API 简化**
- `executeFromDatabase` 移除冗余 `modelId` 参数（Agent 配置已包含 modelId）

#### 🎯 设计模式

| 模式 | 应用 |
|------|------|
| Provider | ChatModelProvider 统一提供 ChatModel |
| Template Method | 公共执行方法定义执行骨架 |
| Facade | GraphWorkflowExecutor 封装复杂执行逻辑 |
| Cache | ChatModel 和 CompiledGraph 缓存 |
| Factory | ReactAgentFactory 动态创建 Agent |


### v2.4.0 (2025-12-09) 🔧

**MCP 工具调用完善**

- 🐛 修复：MCP 工具注册方式，使用 `toolCallbacks()` 替代 `tools()`
- 🆕 新增：`enableMcpTools` 和 `mcpServerCodes` API 参数支持
- 🆕 新增：本地工具和 MCP 工具混合调用支持
- 🆕 新增：MCP SSE 连接测试类
- 📝 新增：`tools() vs toolCallbacks()` 技术文档
- ✅ 验证：ModelScope MCP Fetch 工具调用测试通过

### v2.3.0 (2025-12-08) 🌐

**MCP (Model Context Protocol) 集成**

- 🆕 新增：MCP 服务器管理（支持 SSE、Streamable HTTP 传输）
- 🆕 新增：McpClientManager 客户端连接管理器
- 🆕 新增：McpServerController REST API（CRUD + 连接管理）
- 🆕 新增：MCP 工具自动发现和注册
- 🆕 新增：`a_mcp_servers` 数据库表
- 📝 新增：MCP 服务器测试数据
- 🔄 重构：分离 DDL 和 DML 数据库脚本

### v2.2.0 (2025-12-02) 🎉

**架构简化**

- 🗑️ 移除：删除旧版单体架构后端（`llm-manager/`）
- ✅ 统一：项目现在只保留多模块架构（`llm-manager-parent/`）
- 📝 更新：简化 README 文档，移除旧版相关内容

### v2.1.0 (2025-12-02) 🆕

**工具调用功能（Function Calling）**

- 🆕 新增：Spring AI 原生 @Tool 注解支持
- 🆕 新增：ToolFunctionManager 工具管理器（自动发现 @Tool 注解）
- 🆕 新增：WeatherTools 天气查询工具
- 🆕 新增：CalculatorTools 计算器工具
- 🆕 新增：ToolController 工具列表接口
- 🆕 新增：前端工具选择面板（全选/取消全选）
- 🆕 新增：带工具调用的流式对话接口
- 🔄 重构：LlmChatAgent 使用 .tools() 传递工具对象
- 🗑️ 移除：旧的自定义 Tool 接口和适配器代码

### v2.0.0-dev (2025-12-01) 🚀

**重大架构升级 - 多模块后端重构**

- 🆕 新增：`llm-manager-parent` 多模块 Maven 架构
  - `llm-common`: 公共基础模块（BaseEntity、MyBatisPlusMetaObjectHandler）
  - `llm-agent`: AI 交互层（Message 抽象、ChatModel 接口、对话历史管理）
  - `llm-service`: 业务逻辑层（实体管理、业务编排）
  - `llm-ops`: 管理后台应用
  - `llm-openapi`: 外部 API 应用
- 🔄 迁移：从 Spring Data JPA → MyBatis-Plus 3.5.7
- 🔄 迁移：从 H2 内存数据库 → MySQL/TiDB 持久化存储
- 🆕 新增：统一的审计日志字段（create_time, update_time, create_by, update_by）
- 🆕 新增：全局软删除支持（is_delete 字段）
- 🆕 新增：对话历史持久化到数据库（a_chat_history 表）
- 🆕 新增：表命名规范（p_前缀用于业务表，a_前缀用于 agent 表）
- 🆕 新增：ChatMemory 管理（支持存储和检索历史对话）
- 🆕 新增：Message 抽象层（SystemMessage, UserMessage, AssistantMessage）
- 🆕 新增：ChatModel 抽象层（支持多 LLM 提供商）

### v1.1.0 (2025-11-28)

- 重构：从 DashScope 迁移到 Spring AI OpenAI Starter 1.1.0-M4
- 新增：支持从数据库 Channel 动态读取 LLM 配置
- 新增：支持任何 OpenAI 兼容接口（Ollama、Azure、DeepSeek 等）
- 改进：Channel 配置与默认配置自动降级
- 优化：ChatClient 缓存机制提升性能
- 删除：移除 ChatModelFactory，简化架构

### v1.0.0 (2025-11-24)

- 初始版本发布
- 支持 OpenAI、Ollama、Azure OpenAI
- 实现实时流式对话
- 完整的 Markdown 渲染支持
- Agent 管理功能
- API Key 认证机制
