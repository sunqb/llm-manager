# LLM Manager

一个基于 Spring AI 和 Vue 3 的大语言模型管理平台，支持多模型管理、智能代理（Agent）配置和实时流式对话。

> **⚠️ 后端架构迁移说明**：
>
> 项目当前包含两个后端服务实现，共存期间供选择使用：
>
> - **llm-manager-parent/（新）**：多模块 Maven 架构，采用 MyBatis-Plus + MySQL/TiDB
>   - ✅ 支持软删除和审计日志
>   - ✅ 对话历史持久化到数据库
>   - ✅ 模块化设计，职责清晰
>   - 📂 详细文档：[llm-manager-parent/README.md](./llm-manager-parent/README.md)
>
> - **llm-manager/（旧）**：单体架构，使用 Spring Data JPA + H2 内存数据库
>   - ⚠️ 开发环境使用，数据重启丢失
>   - ⚠️ 功能相对简单
>   - 📂 详细文档：见下文
>
> **待 `llm-manager-parent` 稳定后，将移除旧版 `llm-manager` 后端服务。推荐新项目使用 `llm-manager-parent`。**

## 项目简介

LLM Manager 是一个现代化的 LLM 管理系统，旨在简化大语言模型的接入、配置和使用。通过统一的界面管理多个 LLM 提供商（OpenAI、Ollama、Azure OpenAI 等），支持创建智能代理（Agent）并通过 API 对外提供服务。

### 核心特性

- **多模型管理**：支持多个 LLM 提供商，统一管理
- **智能代理（Agent）**：配置系统提示词和参数，创建专用 AI 助手
- **实时流式对话**：基于 SSE 的真正实时流式输出
- **工具调用（Function Calling）**：支持 LLM 自动调用外部工具（天气查询、计算器等）
- **Markdown 渲染**：完整支持 Markdown 格式，包括代码高亮、表格、列表等
- **API Key 管理**：为外部应用提供安全的 API 访问
- **用户认证**：基于 Sa-Token 的安全认证机制

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
├── llm-manager-parent/       # 🆕 新后端（多模块架构）
│   ├── llm-common/           # 公共模块
│   ├── llm-agent/            # AI 交互层
│   ├── llm-service/          # 业务逻辑层
│   ├── llm-ops/              # 管理后台
│   ├── llm-openapi/          # 外部 API
│   ├── pom.xml               # 父 POM
│   └── README.md             # 详细文档
│
├── llm-manager/              # ⚠️ 旧后端（单体架构，待移除）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/example/llmmanager/
│   │   │   │       ├── config/         # 配置类
│   │   │   │       ├── controller/     # 控制器
│   │   │   │       ├── entity/         # 实体类
│   │   │   │       ├── repository/     # 数据访问层
│   │   │   │       └── service/        # 业务逻辑
│   │   │   └── resources/
│   │   │       └── application.yml     # 应用配置
│   │   └── test/
│   ├── pom.xml
│   └── custom-settings.xml   # Maven 配置（阿里云镜像）
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
- **内存**：至少 1GB 可用内存

### 前端
- **Node.js 16+**
- **npm 或 yarn**

## 快速开始

### 选择后端版本

**推荐使用新架构**（`llm-manager-parent`）：
- ✅ 支持 MySQL/TiDB 持久化存储
- ✅ 对话历史管理
- ✅ 软删除和审计日志
- 📂 详细文档：[llm-manager-parent/README.md](./llm-manager-parent/README.md)

**旧版后端**（`llm-manager`）：
- 仅供开发测试使用
- 使用 H2 内存数据库，重启后数据丢失
- 📂 详细文档：见下文

---

### 使用新后端（llm-manager-parent）

请参考：[llm-manager-parent/README.md](./llm-manager-parent/README.md)

快速启动命令：
```bash
cd llm-manager-parent

# 配置 JDK 21
export JAVA_HOME=/path/to/jdk-21

# 编译项目
mvn clean compile -DskipTests

# 启动管理后台
cd llm-ops
mvn spring-boot:run
```

---

### 使用旧后端（llm-manager）⚠️

> **注意**：此版本将被移除，仅供参考

### 1. 克隆项目

```bash
git clone <repository-url>
cd work_demo
```

### 2. 启动后端

#### 方式一：使用 Maven（推荐）

```bash
cd llm-manager

# 设置 JAVA_HOME（macOS/Linux）
export JAVA_HOME=/path/to/jdk-21

# 设置 JAVA_HOME（Windows）
set JAVA_HOME=C:\path\to\jdk-21

# 启动应用（使用自定义配置）
mvn spring-boot:run -s custom-settings.xml -Dspring-boot.run.profiles=custom
```

#### 方式二：使用 IDE

1. 导入项目到 IntelliJ IDEA 或 Eclipse
2. 设置项目 JDK 为 Java 21
3. 运行 `LlmManagerApplication.java`
4. 选择 `custom` profile

后端启动成功后会监听 **8080** 端口。

**默认管理员账号**：
- 用户名：`admin`
- 密码：`123456`

### 3. 启动前端

```bash
cd llm-manager-ui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端启动成功后访问：**http://localhost:5173**

## 配置说明

### 后端配置

配置文件位于 `llm-manager/src/main/resources/application.yml`

#### 默认 LLM 配置

系统支持从数据库 Channel 表动态读取 LLM 配置。以下是默认的 OpenAI 配置（当 Channel 未配置时使用）：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:sk-placeholder}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
```

**配置优先级**：Channel 数据库配置 > 环境变量 > 默认值

#### 支持的 LLM 提供商

系统支持任何兼容 OpenAI API 的服务，包括：
- **OpenAI** - 官方 API
- **Ollama** - 本地模型（需设置 base-url 为 `http://localhost:11434`）
- **Azure OpenAI** - 微软云服务
- **其他兼容服务** - 如 DeepSeek、零一万物等

#### 数据库配置

开发环境使用 H2 内存数据库，生产环境建议切换到 MySQL/PostgreSQL：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/llm_manager
    username: root
    password: your-password
  jpa:
    hibernate:
      ddl-auto: update
```

### 前端配置

API 地址配置在 `llm-manager-ui/src/services/api.js`：

```javascript
const API_BASE_URL = 'http://localhost:8080'
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
3. 输入消息，开始对话

#### 外部 API 调用

```bash
# 非流式调用
curl -X POST http://localhost:8080/api/external/agents/{slug}/chat \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'

# 流式调用（SSE）
curl -N http://localhost:8080/api/external/agents/{slug}/chat/stream \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
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
  "message": "你的问题"
}
```

**响应**：
```json
{
  "response": "AI 的回复内容"
}
```

#### 2. Agent 对话（流式）

```http
POST /api/external/agents/{slug}/chat/stream
Content-Type: application/json
Authorization: Bearer {api-key}

{
  "message": "你的问题"
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

#### 添加新的 LLM 提供商

由于使用 OpenAI 兼容接口，只需在 Channel 配置中设置正确的 `baseUrl` 和 `apiKey` 即可支持任何兼容服务。

#### 自定义 Prompt 模板

在 Service 层使用 `PromptTemplate`：

```java
PromptTemplate template = new PromptTemplate("回答问题：{question}");
String prompt = template.render(Map.of("question", userInput));
```

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
cd llm-manager
mvn clean package -DskipTests
```

生成的 JAR 文件位于 `target/llm-manager-0.0.1-SNAPSHOT.jar`

#### 运行

```bash
java -jar target/llm-manager-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://prod-db:3306/llm_manager \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=prod_password
```

#### Docker 部署

```dockerfile
FROM openjdk:21-slim
WORKDIR /app
COPY target/llm-manager-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
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

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 常见问题

### Q1: 后端启动失败，提示 Java 版本错误

**A**: 确保使用 JDK 21 或更高版本。检查 `JAVA_HOME` 环境变量：

```bash
echo $JAVA_HOME
java -version
```

### Q2: 流式输出不工作，一直显示 loading

**A**: 这是已解决的问题。确保：
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

**A**: 开发环境使用 H2 内存数据库，每次重启数据会清空。生产环境需配置持久化数据库。

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。

## 联系方式

- 问题反馈：提交 Issue
- 功能建议：提交 Feature Request

## 更新日志

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
- ⚠️ 兼容性：旧版后端（`llm-manager`）暂时保留，待新架构稳定后移除

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
