# LLM Manager

一个基于 Spring AI 和 Vue 3 的大语言模型管理平台，支持多模型管理、智能代理（Agent）配置和实时流式对话。

## 项目简介

LLM Manager 是一个现代化的 LLM 管理系统，旨在简化大语言模型的接入、配置和使用。通过统一的界面管理多个 LLM 提供商（OpenAI、Ollama、Azure OpenAI 等），支持创建智能代理（Agent）并通过 API 对外提供服务。

### 核心特性

- **多模型管理**：支持多个 LLM 提供商，统一管理
- **智能代理（Agent）**：配置系统提示词和参数，创建专用 AI 助手
- **实时流式对话**：基于 SSE 的真正实时流式输出
- **Markdown 渲染**：完整支持 Markdown 格式，包括代码高亮、表格、列表等
- **API Key 管理**：为外部应用提供安全的 API 访问
- **用户认证**：基于 Sa-Token 的安全认证机制

## 技术栈

### 后端
- **Spring Boot 3.2.5** - 应用框架
- **Spring AI 1.0.0-M1** - LLM 集成
- **Java 21** - 编程语言
- **Spring Data JPA** - 数据持久化
- **H2 Database** - 内存数据库（开发环境）
- **Sa-Token 1.37.0** - 认证授权
- **Lombok** - 代码简化
- **Maven** - 构建工具

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
work_demo/
├── llm-manager/              # 后端项目
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
│   │   │       ├── application.yml     # 默认配置
│   │   │       └── application-custom.yml  # 自定义配置
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
- **JDK 21** 或更高版本
- **Maven 3.8+**
- **内存**：至少 1GB 可用内存

### 前端
- **Node.js 16+**
- **npm 或 yarn**

## 快速开始

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

配置文件位于 `llm-manager/src/main/resources/application-custom.yml`

#### LLM 提供商配置示例

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:your-api-key}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-3.5-turbo
          temperature: 0.7
```

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

#### 添加新的 LLM 提供商

1. 在 `ChatModelFactory.java` 中添加新类型
2. 实现对应的 `ChatModel` 创建逻辑
3. 更新 `ChannelType` 枚举

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

### v1.0.0 (2025-11-24)

- 初始版本发布
- 支持 OpenAI、Ollama、Azure OpenAI
- 实现实时流式对话
- 完整的 Markdown 渲染支持
- Agent 管理功能
- API Key 认证机制
