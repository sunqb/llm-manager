# LLM Manager UI

基于 Vue 3 + Vite + TailwindCSS 的大语言模型管理平台前端。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| Vite | 7.2.2 | 构建工具 |
| Vue Router | 4.x | 路由管理 |
| TailwindCSS | 3.4 | UI 样式 |
| Axios | 1.13 | HTTP 客户端 |
| Marked | - | Markdown 解析 |
| DOMPurify | - | XSS 防护 |

## 功能特性

### 核心功能
- **多模型管理**：支持配置多个 LLM 模型
- **智能代理（Agent）**：配置系统提示词和参数
- **实时流式对话**：基于 SSE 的流式输出
- **Markdown 渲染**：完整支持代码高亮、表格、列表等
- **会话历史管理**：支持多轮对话上下文

### 工具调用（Function Calling）🆕
- **工具开关**：一键启用/禁用工具调用功能
- **工具选择面板**：美观的下拉面板选择要使用的工具
- **全选/取消全选**：快速批量选择工具
- **工具描述展示**：悬停查看工具详细描述

## 项目结构

```
llm-manager-ui/
├── src/
│   ├── views/              # 页面组件
│   │   ├── ChatView.vue    # 聊天页面（含工具调用）
│   │   ├── ModelsView.vue  # 模型管理
│   │   ├── AgentsView.vue  # Agent 管理
│   │   └── ...
│   ├── components/         # 通用组件
│   ├── services/           # API 服务
│   │   └── api.js          # API 封装
│   ├── router/             # 路由配置
│   └── utils/              # 工具函数
├── package.json
├── vite.config.js
└── tailwind.config.js
```

## 快速开始

### 环境要求
- Node.js 16+
- npm 或 yarn

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

访问：http://localhost:5173

### 构建生产版本

```bash
npm run build
```

## 配置说明

### API 地址配置

编辑 `src/services/api.js`：

```javascript
const API_BASE_URL = 'http://localhost:8080'
```

## 使用指南

### 聊天功能

1. **选择模型**：从下拉框选择已配置的模型
2. **启用工具**（可选）：
   - 勾选「🔧 工具」开启工具调用
   - 点击「已选 N/M」按钮打开工具选择面板
   - 选择需要的工具（如天气查询、计算器等）
3. **发送消息**：输入消息后按 Enter 或点击发送按钮
4. **新对话**：点击「✨ 新对话」开始新的会话

### 工具选择面板

```
┌─────────────────────┐
│ 已选 2/3        ▼  │  ← 点击展开面板
└─────────────────────┘
┌─────────────────────┐
│ 选择工具    全选    │  ← 全选/取消全选
├─────────────────────┤
│ ☑ getWeather        │  ← 复选框选择
│   获取天气信息...    │  ← 工具描述
│ ☑ calculate         │
│   执行数学计算...    │
└─────────────────────┘
```

### API 服务方法

```javascript
// 获取可用工具列表
api.getAvailableTools()

// 普通流式对话
api.chatStream(modelId, message, conversationId, onChunk, onComplete, onError)

// 带工具调用的流式对话
api.chatStreamWithTools(modelId, message, conversationId, toolNames, onChunk, onComplete, onError)
```

## 更新日志

### v2.1.0 (2025-12-02) 🆕

**工具调用功能**
- 新增：工具调用开关
- 新增：美观的工具选择下拉面板
- 新增：工具全选/取消全选功能
- 新增：工具描述悬停提示
- 新增：点击外部自动关闭面板
- 优化：从原生 `<select multiple>` 改为自定义 UI

### v2.0.0 (2025-12-01)

**会话历史管理**
- 新增：前端控制 conversationId
- 新增：localStorage 持久化会话
- 新增：新对话/继续对话切换
- 新增：会话自动恢复

### v1.0.0 (2025-11-24)

- 初始版本发布
- 实时流式对话
- Markdown 渲染支持
- 多模型/Agent 支持

## 许可证

MIT License
