# 人工审核功能实现文档

**版本**: v2.8.0
**日期**: 2025-01-08
**作者**: LLM Manager Team

---

## 📋 目录

1. [功能概述](#功能概述)
2. [核心设计理念](#核心设计理念)
3. [技术架构](#技术架构)
4. [核心组件详解](#核心组件详解)
5. [实施阶段](#实施阶段)
6. [关键技术实现](#关键技术实现)
7. [配置示例](#配置示例)
8. [API 接口](#api-接口)
9. [数据库设计](#数据库设计)
10. [测试指南](#测试指南)

---

## 功能概述

### 支持的审核场景

本次实现为 LLM Manager 系统添加了完整的人工审核功能，支持以下四种审核场景：

| 审核类型 | 说明 | 触发方式 | 使用场景 |
|---------|------|---------|---------|
| **GRAPH_NODE** | Graph 工作流审核节点 | `HUMAN_REVIEW_NODE` 节点 | 工作流中的关键决策点 |
| **REACT_AGENT_TOOL** | ReactAgent 工具审核 | `@Tool` 注解的 `HumanReviewTool` | Agent 自主决定需要人工确认 |
| **REACT_AGENT_SEQUENTIAL** | Sequential 工作流审核 | 配置驱动的 Agent 间审核 | 流水线处理中的质量检查 |
| **REACT_AGENT_SUPERVISOR** | Supervisor 团队审核 | Worker 调用或整体审核 | 团队协作中的人工介入 |

### 核心能力

- ✅ **异常驱动暂停**：通过 `HumanReviewRequiredException` 暂停执行
- ✅ **状态快照保存**：保存执行上下文，支持恢复
- ✅ **异步恢复执行**：审核通过后自动恢复，不阻塞审核提交
- ✅ **两层审核支持**：ReactAgent 作为 Graph 节点时支持内外两层审核
- ✅ **统一审核抽象**：所有审核类型使用统一的 `PendingReview` 实体

---

## 核心设计理念

### 1. 异常驱动暂停机制

**设计思想**：使用异常作为控制流，暂停工作流执行。

```java
// 当需要人工审核时，抛出异常
throw new HumanReviewRequiredException(reviewCode, reviewPrompt, reviewType);

// 异常会被捕获，工作流暂停，等待人工审核
```

**优势**：
- 无需修改现有执行逻辑
- 异常自动传播到调用栈顶层
- 清晰的控制流语义

### 2. 服务分层设计

**设计理念**：`llm-service` 层可以直接依赖 `llm-agent` 层，无需依赖倒置

**分层职责**：

```
llm-agent (记录层)
    └── HumanReviewRecordService.java    # 创建审核记录

llm-service (编排层)
    └── HumanReviewOrchestrationService.java  # 审核流程编排和恢复执行
        - 直接注入 DynamicReactAgentExecutionService
        - 直接注入 GraphWorkflowExecutor
```

**优势**：
- 无需依赖倒置接口，代码更简洁
- 层级关系清晰：service 层直接调用 service 层和 agent 层

### 3. ThreadLocal 上下文传递

**问题**：Spring AI `@Tool` 方法无法直接获取 `conversationCode` 等上下文信息

**解决方案**：使用 ThreadLocal 传递上下文

```java
// 执行前设置上下文
HumanReviewContextHolder.setContext(context);

try {
    // Agent 执行（内部可能调用 HumanReviewTool）
    agent.execute(message);
} finally {
    // 执行后清理上下文
    HumanReviewContextHolder.clearContext();
}
```

### 4. 状态快照与恢复

**Graph 工作流**：
- 保存 `OverAllState` 的完整状态
- 恢复时重建状态，从下一节点继续

**ReactAgent**：
- 利用 `ChatMemory` 自动保存历史
- 恢复时添加审核结果消息，继续对话

---

## 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         Controller 层                            │
│  HumanReviewController  GraphWorkflowController  ChatController │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Service 层                               │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │      HumanReviewOrchestrationService (编排服务)           │   │
│  │  - approveReview()       批准审核                         │   │
│  │  - rejectReview()        拒绝审核                         │   │
│  │  - resumeExecutionAsync()  异步恢复执行                   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                │                                 │
│           ┌────────────────────┼────────────────────┐            │
│           ▼                    ▼                    ▼            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ GraphWorkflow   │  │ DynamicReact    │  │ DynamicReact    │ │
│  │ Executor        │  │ AgentExecution  │  │ AgentExecution  │ │
│  │                 │  │ Service         │  │ Service         │ │
│  │ resumeFromReview│  │ resumeFromReview│  │ resumeSequential│ │
│  │                 │  │                 │  │ resumeSupervisor│ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Agent 层                                 │
│  HumanReviewNodeExecutor  ReactAgentNodeExecutor  HumanReviewTool│
│  HumanReviewRecordService  SequentialPatternExecutor            │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Storage 层                               │
│  PendingReview  PendingReviewService  PendingReviewMapper       │
└─────────────────────────────────────────────────────────────────┘
```

### 模块依赖关系

```
llm-common (公共模块)
    ↑
llm-agent (AI 交互层)
    ├── review/                    # 审核核心
    │   ├── HumanReviewRecordService  # 创建审核记录
    │   ├── exception/
    │   ├── snapshot/
    │   └── context/
    ├── graph/dynamic/executor/    # Graph 节点执行器
    │   ├── HumanReviewNodeExecutor
    │   └── ReactAgentNodeExecutor
    ├── reactagent/configurable/pattern/  # 工作流模式执行器
    │   └── SequentialPatternExecutor     # resumeFromCheckpoint()
    ├── tools/                     # Agent 工具
    │   └── HumanReviewTool
    └── storage/core/              # 数据持久化
        ├── entity/PendingReview
        ├── mapper/PendingReviewMapper
        └── service/PendingReviewService
    ↑
llm-service (业务逻辑层)
    └── orchestration/
        ├── HumanReviewOrchestrationService  # 编排和恢复执行
        └── DynamicReactAgentExecutionService  # resumeSequential/Supervisor
    ↑
llm-ops (管理后台)
    └── controller/HumanReviewController
```

---

## 核心组件详解

### 1. PendingReview 实体

**位置**：`llm-agent/storage/core/entity/PendingReview.java`

**职责**：统一的审核记录实体，支持所有审核类型

**关键字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `reviewCode` | String(32) | 审核唯一标识（UUID，无连字符） |
| `reviewType` | String | GRAPH_NODE / REACT_AGENT_TOOL / REACT_AGENT_SEQUENTIAL / REACT_AGENT_SUPERVISOR |
| `graphTaskId` | Long | 关联的 Graph 任务 ID |
| `conversationCode` | String | 关联的会话标识 |
| `agentConfigCode` | String | 关联的 Agent 配置 Code |
| `currentNode` | String | 当前节点/Agent 名称 |
| `reviewerPrompt` | Text | 展示给审核人的提示内容 |
| `contextData` | JSON | 状态快照（JSON 格式） |
| `status` | String | PENDING / APPROVED / REJECTED |
| `reviewResult` | Boolean | 审核结果：true=通过，false=拒绝 |
| `reviewComment` | Text | 审核意见/拒绝原因 |
| `resumeAfterApproval` | Boolean | 批准后是否自动恢复执行 |

**静态工厂方法**：

```java
// 创建 Graph 节点审核
PendingReview.createGraphNodeReview(graphTaskId, currentNode, reviewerPrompt, contextData)

// 创建 ReactAgent 工具审核
PendingReview.createReactAgentToolReview(conversationCode, agentConfigCode, reviewerPrompt, contextData)

// 创建 Sequential 审核
PendingReview.createSequentialReview(conversationCode, agentConfigCode, currentAgent, reviewerPrompt, contextData)

// 创建 Supervisor 审核
PendingReview.createSupervisorReview(conversationCode, agentConfigCode, currentNode, reviewerPrompt, contextData)
```

---

### 2. HumanReviewRecordService（llm-agent 层）

**位置**：`llm-agent/review/HumanReviewRecordService.java`

**职责**：创建审核记录（纯数据操作，不负责恢复执行）

**核心方法**：

```java
// 创建审核记录（4种类型）
PendingReview createGraphNodeReview(Long graphTaskId, String currentNode, String reviewerPrompt, GraphStateSnapshot snapshot)
PendingReview createReactAgentToolReview(String conversationCode, String agentConfigCode, String reviewerPrompt, Map<String, Object> contextData)
PendingReview createSequentialReview(String conversationCode, String agentConfigCode, String currentAgent, String reviewerPrompt, SequentialStateSnapshot snapshot)
PendingReview createSupervisorReview(String conversationCode, String agentConfigCode, String currentNode, String reviewerPrompt, SupervisorStateSnapshot snapshot)
```

---

### 3. HumanReviewOrchestrationService（llm-service 层）

**位置**：`llm-service/orchestration/HumanReviewOrchestrationService.java`

**职责**：审核流程编排，负责提交审核结果和恢复执行

**核心方法**：

```java
// 提交审核结果
void approveReview(String reviewCode, Long reviewerId, String reviewComment)
void rejectReview(String reviewCode, Long reviewerId, String reviewComment)

// 异步恢复执行
@Async
void resumeExecutionAsync(String reviewCode)

// 分类型恢复方法
private void resumeGraphWorkflow(PendingReview review)
private void resumeReactAgent(PendingReview review)
private void resumeSequentialAgent(PendingReview review)
private void resumeSupervisorAgent(PendingReview review)
```

**设计理念**：
- 单一职责：只负责审核流程编排，不负责创建审核记录
- 层级清晰：llm-service 层可以直接依赖其他 service 层服务
- 无需依赖倒置：直接注入 `DynamicReactAgentExecutionService` 等服务

**恢复执行流程**：

```
审核批准 → resumeExecutionAsync()
    ↓
检查审核状态和重试次数
    ↓
根据 reviewType 路由到对应恢复方法
    ↓
GRAPH_NODE → resumeGraphWorkflow()
    - 从快照重建 OverAllState
    - 调用 GraphWorkflowExecutor.resumeFromReview()
    ↓
REACT_AGENT_TOOL → resumeReactAgent()
    - 从 ChatMemory 加载历史
    - 调用 DynamicReactAgentExecutionService.resumeFromReview()
    ↓
REACT_AGENT_SEQUENTIAL → resumeSequentialAgent()
    - 从快照恢复中间结果
    - 调用 DynamicReactAgentExecutionService.resumeSequentialFromReview()
    ↓
REACT_AGENT_SUPERVISOR → resumeSupervisorAgent()
    - 根据审核模式（FINAL_REVIEW/WORKER_REVIEW）处理
    - 调用 DynamicReactAgentExecutionService.resumeSupervisorFromReview()
    ↓
失败时增加重试次数（最大 3 次）
```

---

### 4. HumanReviewNodeExecutor

**位置**：`llm-agent/graph/dynamic/executor/HumanReviewNodeExecutor.java`

**职责**：Graph 工作流中的人工审核节点执行器

**节点类型**：`HUMAN_REVIEW_NODE`

**配置参数**：

```json
{
  "id": "review_node",
  "type": "HUMAN_REVIEW_NODE",
  "config": {
    "prompt_template": "请审核以下内容：\n\n{content}\n\n是否批准？",
    "context_keys": ["content", "analysis"],
    "output_key": "review_result"
  }
}
```

**执行流程**：

```java
1. 从 OverAllState 提取 context_keys 指定的字段
2. 使用 prompt_template 格式化审核提示（替换 {key} 占位符）
3. 创建 GraphStateSnapshot 快照
4. 调用 HumanReviewService.createGraphNodeReview()
5. 抛出 HumanReviewRequiredException 暂停执行
```

**状态快照**：

```java
@Data
public class GraphStateSnapshot {
    private String currentNodeId;           // 当前节点 ID
    private Map<String, Object> stateValues; // 状态值
    private String graphConfig;             // Graph 配置 JSON
}
```

---

### 5. HumanReviewTool

**位置**：`llm-agent/tools/HumanReviewTool.java`

**职责**：ReactAgent 可调用的人工审核工具

**Spring AI @Tool 注解**：

```java
@Tool(description = "请求人工审核 - 当你需要人工确认或批准某些内容时使用此工具。" +
                    "适用场景：重要决策、敏感操作、质量检查等。")
public String requestHumanReview(
    @ToolParam(description = "需要审核的内容，清晰描述需要人工确认的事项") String content,
    @ToolParam(description = "审核提示，告诉审核人员需要关注什么") String prompt)
```

**执行流程**：

```java
1. 从 HumanReviewContextHolder 获取上下文（conversationCode, agentConfigCode 等）
2. 构建 contextData（包含 content, prompt, agentName 等）
3. 调用 HumanReviewService.createReactAgentToolReview()
4. 抛出 HumanReviewRequiredException 暂停 Agent 执行
```

**上下文管理**：

```java
// DynamicReactAgentExecutionService 中设置上下文
HumanReviewContext context = HumanReviewContext.builder()
    .conversationCode(conversationCode)
    .agentConfigCode(agentConfig.getAgentConfigCode())
    .agentName(agentConfig.getName())
    .agentType(agentConfig.getAgentType())
    .build();

HumanReviewContextHolder.setContext(context);
try {
    // Agent 执行
} finally {
    HumanReviewContextHolder.clearContext();
}
```

---

### 6. ReactAgentNodeExecutor

**位置**：`llm-agent/graph/dynamic/executor/ReactAgentNodeExecutor.java`

**职责**：将 ReactAgent 作为 Graph 节点使用

**节点类型**：`REACT_AGENT_NODE`

**配置参数**：

```json
{
  "id": "agent_node",
  "type": "REACT_AGENT_NODE",
  "config": {
    "agent_ref": "universal-assistant",
    "input_key": "question",
    "output_key": "agent_result"
  }
}
```

**执行流程**：

```java
1. 从 OverAllState 读取 input_key 指定的输入
2. 获取或生成 conversationCode
3. 调用 ReactAgentExecutorDelegate.execute(agentRef, input, conversationCode)
4. 处理执行结果：
   - success=true: 提取结果，写入 output_key
   - pendingReview=true: 重新抛出 HumanReviewRequiredException（两层审核）
   - success=false: 记录错误
```

**两层审核支持**：

```
Graph 工作流
    ↓
REACT_AGENT_NODE (外层)
    ↓
ReactAgent 执行 (内层)
    ↓
调用 HumanReviewTool → 抛出异常
    ↓
ReactAgentNodeExecutor 捕获异常
    ↓
重新抛出异常到 Graph 层
    ↓
Graph 暂停，等待审核
```

---

### 7. HumanReviewContextHolder

**位置**：`llm-agent/review/context/HumanReviewContextHolder.java`

**职责**：ThreadLocal 上下文持有者

**核心方法**：

```java
public class HumanReviewContextHolder {
    private static final ThreadLocal<HumanReviewContext> CONTEXT_HOLDER = new ThreadLocal<>();

    // 设置上下文
    public static void setContext(HumanReviewContext context)

    // 获取上下文
    public static HumanReviewContext getContext()

    // 清除上下文（必须在 finally 块中调用）
    public static void clearContext()

    // 便捷方法
    public static String getConversationCode()
    public static String getAgentConfigCode()
    public static boolean hasContext()
}
```

**使用模式**：

```java
HumanReviewContextHolder.setContext(context);
try {
    // 执行可能调用 HumanReviewTool 的代码
    agent.execute(message);
} finally {
    // 确保清理，避免内存泄漏
    HumanReviewContextHolder.clearContext();
}
```

---

## 实施阶段

本次实现分为 4 个阶段，历时 3 天完成：

### Phase 1: 核心基础设施 ✅

**时间**：Day 1

**完成内容**：

| 文件 | 说明 |
|------|------|
| `PendingReview.java` | 审核记录实体 |
| `PendingReviewMapper.java` | MyBatis Mapper |
| `PendingReviewService.java` | 服务接口 |
| `PendingReviewServiceImpl.java` | 服务实现 |
| `HumanReviewRequiredException.java` | 审核异常 |
| `HumanReviewRecordService.java` | 创建审核记录服务（llm-agent 层） |
| `GraphStateSnapshot.java` | Graph 状态快照 |
| `SequentialStateSnapshot.java` | Sequential 状态快照 |
| `SupervisorStateSnapshot.java` | Supervisor 状态快照 |

**数据库表**：
```sql
CREATE TABLE a_pending_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_code VARCHAR(32) NOT NULL UNIQUE,
    review_type VARCHAR(50) NOT NULL,
    -- ... 其他字段
);
```

---

### Phase 2: Graph 审核节点 ✅

**时间**：Day 1-2

**完成内容**：

| 文件 | 说明 |
|------|------|
| `HumanReviewNodeExecutor.java` | HUMAN_REVIEW_NODE 执行器 |
| `GraphWorkflowExecutor.java` | 添加 resumeFromReview() 方法 |
| `DynamicGraphBuilder.java` | 注册 HumanReviewNodeExecutor |

**功能验证**：
- ✅ 创建带审核节点的 Graph 工作流
- ✅ 执行到审核节点时暂停
- ✅ 审核通过后恢复执行

---

### Phase 3: ReactAgent 审核工具 ✅

**时间**：Day 2-3

**完成内容**：

| 文件 | 说明 |
|------|------|
| `HumanReviewContext.java` | 上下文数据类 |
| `HumanReviewContextHolder.java` | ThreadLocal 持有者 |
| `HumanReviewTool.java` | @Tool 工具类 |
| `HumanReviewOrchestrationService.java` | 审核编排服务（llm-service 层） |
| `DynamicReactAgentExecutionService.java` | 添加上下文管理和恢复逻辑 |

**功能验证**：
- ✅ ReactAgent 调用 HumanReviewTool
- ✅ 上下文正确传递
- ✅ 审核通过后恢复对话

---

### Phase 4: ReactAgent 作为 Graph 节点 + SEQUENTIAL/SUPERVISOR 恢复 ✅

**时间**：Day 3

**完成内容**：

| 文件 | 说明 |
|------|------|
| `ReactAgentNodeExecutor.java` | REACT_AGENT_NODE 执行器 |
| `DynamicGraphBuilder.java` | 注册 ReactAgentNodeExecutor |
| `SequentialPatternExecutor.java` | 添加 resumeFromCheckpoint() 方法 |
| `DynamicReactAgentExecutionService.java` | 添加 resumeSequentialFromReview/resumeSupervisorFromReview |
| `HumanReviewOrchestrationService.java` | 更新 resumeSequentialAgent/resumeSupervisorAgent |

**功能验证**：
- ✅ ReactAgent 作为 Graph 节点执行
- ✅ 两层审核支持（Agent 内部 + Graph 层）
- ✅ 审核异常正确传播
- ✅ SEQUENTIAL 工作流恢复执行
- ✅ SUPERVISOR 团队恢复执行

---

## 关键技术实现

### 1. 异常驱动暂停机制

**核心异常类**：

```java
public class HumanReviewRequiredException extends RuntimeException {
    private final String reviewCode;
    private final String reviewPrompt;
    private final String reviewType;

    public HumanReviewRequiredException(String reviewCode, String reviewPrompt, String reviewType) {
        super("需要人工审核: " + reviewCode);
        this.reviewCode = reviewCode;
        this.reviewPrompt = reviewPrompt;
        this.reviewType = reviewType;
    }
}
```

**抛出时机**：
- `HumanReviewNodeExecutor`：创建审核记录后立即抛出
- `HumanReviewTool`：Agent 调用工具时抛出
- `ReactAgentNodeExecutor`：捕获 Agent 内部异常后重新抛出

**捕获处理**：
- `GraphWorkflowExecutor`：捕获异常，返回 `pendingReview` 状态
- `DynamicReactAgentExecutionService`：捕获异常，返回审核信息
- `ReactAgentNodeExecutor`：捕获后重新抛出（两层审核）

---

### 2. ThreadLocal 上下文传递

**问题场景**：

```java
// HumanReviewTool 是 Spring AI @Tool 方法
// 无法通过参数传递 conversationCode
@Tool
public String requestHumanReview(String content, String prompt) {
    // 如何获取 conversationCode？
}
```

**解决方案**：

```java
// 1. 执行前设置上下文
HumanReviewContext context = HumanReviewContext.builder()
    .conversationCode(conversationCode)
    .agentConfigCode(agentConfigCode)
    .build();
HumanReviewContextHolder.setContext(context);

try {
    // 2. Agent 执行（可能调用 HumanReviewTool）
    agent.execute(message);
} finally {
    // 3. 执行后清理（避免内存泄漏）
    HumanReviewContextHolder.clearContext();
}

// 4. HumanReviewTool 中获取上下文
@Tool
public String requestHumanReview(String content, String prompt) {
    String conversationCode = HumanReviewContextHolder.getConversationCode();
    // 使用 conversationCode 创建审核记录
}
```

**注意事项**：
- 必须在 `finally` 块中清理，避免 ThreadLocal 泄漏
- 仅在同一线程内有效
- 适用于同步执行场景

---

### 3. 状态快照与恢复

#### Graph 工作流快照

**保存快照**：

```java
// HumanReviewNodeExecutor 中
GraphStateSnapshot snapshot = GraphStateSnapshot.builder()
    .currentNodeId(nodeConfig.getId())
    .stateValues(state.values())
    .graphConfig(graphConfigJson)
    .build();

Map<String, Object> contextData = Map.of(
    "snapshot", snapshot,
    "snapshotType", "GraphStateSnapshot"
);

humanReviewService.createGraphNodeReview(graphTaskId, currentNode, reviewerPrompt, contextData);
```

**恢复执行**：

```java
// HumanReviewService.resumeGraphWorkflow()
GraphStateSnapshot snapshot = objectMapper.convertValue(snapshotMap, GraphStateSnapshot.class);

// 调用 GraphWorkflowExecutor 恢复
Map<String, Object> result = graphWorkflowExecutor.resumeFromReview(review, snapshot);
```

#### ReactAgent 快照（利用 ChatMemory）

**无需显式快照**：
- ChatMemory 自动保存历史到 `a_chat_history` 表
- 恢复时自动加载历史

**恢复执行**：

```java
// HumanReviewOrchestrationService.resumeReactAgent()
String reviewMessage = review.getReviewResult()
    ? "审核通过：" + review.getReviewComment()
    : "审核拒绝：" + review.getReviewComment();

// 调用 DynamicReactAgentExecutionService
Map<String, Object> result = dynamicReactAgentExecutionService.resumeFromReview(
    conversationCode,
    agentConfigCode,
    reviewMessage  // 作为新消息添加到历史
);
```

#### Sequential/Supervisor 快照

**Sequential 快照内容**：
- `lastCompletedAgentIndex`：最后完成的 Agent 索引
- `intermediateResults`：中间结果列表
- `originalMessage`：原始用户消息

**Supervisor 快照内容**：
- `reviewMode`：审核模式（FINAL_REVIEW / WORKER_REVIEW）
- `finalResult`：最终结果（FINAL_REVIEW 模式）
- `executionHistory`：执行历史

**恢复执行**：

```java
// Sequential 恢复
Map<String, Object> result = dynamicReactAgentExecutionService.resumeSequentialFromReview(review, snapshot);

// Supervisor 恢复
Map<String, Object> result = dynamicReactAgentExecutionService.resumeSupervisorFromReview(review, snapshot);
```

---

### 4. 异步恢复执行

**设计目标**：审核提交请求不阻塞，恢复执行在后台异步进行

**实现方式**：

```java
@Service
public class HumanReviewOrchestrationService {

    // 批准审核
    @Transactional
    public void approveReview(String reviewCode, Long reviewerId, String reviewComment) {
        pendingReviewService.approve(reviewCode, reviewerId, reviewComment);

        PendingReview review = pendingReviewService.getByReviewCode(reviewCode);

        // 如果配置了自动恢复，则异步恢复执行
        if (Boolean.TRUE.equals(review.getResumeAfterApproval())) {
            resumeExecutionAsync(reviewCode);  // 异步方法
        }
    }

    // 异步恢复执行
    @Async
    public void resumeExecutionAsync(String reviewCode) {
        try {
            PendingReview review = pendingReviewService.getByReviewCode(reviewCode);

            // 检查审核状态和重试次数
            if (!review.isApproved() || !review.canRetry()) {
                return;
            }

            // 根据审核类型路由到对应恢复方法
            switch (PendingReview.ReviewType.valueOf(review.getReviewType())) {
                case GRAPH_NODE -> resumeGraphWorkflow(review);
                case REACT_AGENT_TOOL -> resumeReactAgent(review);
                case REACT_AGENT_SEQUENTIAL -> resumeSequentialAgent(review);
                case REACT_AGENT_SUPERVISOR -> resumeSupervisorAgent(review);
            }
        } catch (Exception e) {
            log.error("恢复执行失败", e);
            pendingReviewService.incrementRetryCount(reviewCode);
        }
    }
}
```

**配置**：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-resume-");
        executor.initialize();
        return executor;
    }
}
```

**重试机制**：
- 最大重试次数：3 次（可配置）
- 失败时自动增加重试计数
- 超过最大次数后放弃恢复

---

## 配置示例

### 1. Graph 工作流审核节点

```json
{
  "name": "research-with-review",
  "description": "研究工作流（带人工审核）",
  "stateConfig": {
    "keys": [
      {"key": "question", "append": false},
      {"key": "research_result", "append": false},
      {"key": "review_result", "append": false}
    ]
  },
  "nodes": [
    {
      "id": "research",
      "type": "LLM_NODE",
      "config": {
        "input_key": "question",
        "output_key": "research_result",
        "system_prompt": "你是研究助手"
      }
    },
    {
      "id": "human_review",
      "type": "HUMAN_REVIEW_NODE",
      "config": {
        "prompt_template": "请审核研究结果：\n\n{research_result}\n\n是否准确？",
        "context_keys": ["research_result"],
        "output_key": "review_result"
      }
    }
  ],
  "edges": [
    {"from": "START", "to": "research"},
    {"from": "research", "to": "human_review"},
    {"from": "human_review", "to": "END"}
  ]
}
```

### 2. ReactAgent 作为 Graph 节点（两层审核）

```json
{
  "name": "complex-research",
  "description": "复杂研究（Graph + ReactAgent + 两层审核）",
  "stateConfig": {
    "keys": [
      {"key": "topic", "append": false},
      {"key": "research_data", "append": false},
      {"key": "workflow_review_result", "append": false}
    ]
  },
  "nodes": [
    {
      "id": "agent_research",
      "type": "REACT_AGENT_NODE",
      "name": "智能研究 Agent",
      "config": {
        "agent_ref": "universal-assistant",
        "input_key": "topic",
        "output_key": "research_data"
      }
    },
    {
      "id": "workflow_review",
      "type": "HUMAN_REVIEW_NODE",
      "name": "工作流级别审核",
      "config": {
        "prompt_template": "智能体研究结果：\n\n{research_data}\n\n是否批准进入发布流程？",
        "context_keys": ["research_data"],
        "output_key": "workflow_review_result"
      }
    }
  ],
  "edges": [
    {"from": "START", "to": "agent_research"},
    {"from": "agent_research", "to": "workflow_review"},
    {"from": "workflow_review", "to": "END"}
  ]
}
```

---

## API 接口

### HumanReviewController

**基础路径**：`/api/human-review`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/submit/{reviewCode}` | POST | 提交审核结果（批准/拒绝） |
| `/pending` | GET | 查询待审核列表 |
| `/{reviewCode}` | GET | 获取审核详情 |
| `/reject/{reviewCode}` | POST | 拒绝审核（快捷方式） |

**请求示例**：

```bash
# 批准审核
curl -X POST http://localhost:8080/api/human-review/submit/abc123 \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewResult": true,
    "reviewComment": "审核通过"
  }'

# 拒绝审核
curl -X POST http://localhost:8080/api/human-review/reject/abc123 \
  -H "Content-Type: application/json" \
  -d '{
    "reviewerId": 1,
    "reviewComment": "内容不符合要求"
  }'

# 查询待审核列表
curl http://localhost:8080/api/human-review/pending

# 获取审核详情
curl http://localhost:8080/api/human-review/abc123
```

---

## 数据库设计

### a_pending_reviews 表

```sql
CREATE TABLE IF NOT EXISTS a_pending_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    review_code VARCHAR(32) NOT NULL UNIQUE COMMENT '审核唯一标识（32位UUID）',
    review_type VARCHAR(50) NOT NULL COMMENT '审核类型',

    -- 关联任务/会话
    graph_task_id BIGINT COMMENT '关联的 Graph 任务 ID',
    conversation_code VARCHAR(100) COMMENT '关联的会话标识',
    agent_config_code VARCHAR(32) COMMENT '关联的 Agent 配置 Code',

    -- 审核上下文
    current_node VARCHAR(100) COMMENT '当前节点/Agent 名称',
    reviewer_prompt TEXT NOT NULL COMMENT '展示给审核人的提示内容',
    context_keys JSON COMMENT '上下文字段列表',
    context_data JSON NOT NULL COMMENT '上下文数据（状态快照）',

    -- 审核状态
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
    review_result TINYINT(1) COMMENT '审核结果：1=通过，0=拒绝',
    review_comment TEXT COMMENT '审核意见/拒绝原因',
    reviewer_id BIGINT COMMENT '审核人 ID',
    reviewed_at DATETIME COMMENT '审核时间',

    -- 执行控制
    resume_after_approval TINYINT(1) DEFAULT 1 COMMENT '批准后是否自动恢复执行',
    max_retry_count INT DEFAULT 3 COMMENT '最大重试次数',
    current_retry_count INT DEFAULT 0 COMMENT '当前重试次数',

    -- 标准字段
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT DEFAULT 0,

    INDEX idx_review_code (review_code),
    INDEX idx_status_create (status, create_time),
    INDEX idx_graph_task_id (graph_task_id),
    INDEX idx_conversation_code (conversation_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工审核记录表';
```

---

## 测试指南

### 1. Graph 工作流审核测试

**步骤**：

1. 创建带 `HUMAN_REVIEW_NODE` 的工作流配置
2. 执行工作流：`POST /api/workflow/execute/{modelId}`
3. 工作流暂停，返回 `pendingReview` 状态和 `reviewCode`
4. 查询待审核列表：`GET /api/human-review/pending`
5. 提交审核结果：`POST /api/human-review/submit/{reviewCode}`
6. 工作流自动恢复执行

**验证点**：
- ✅ 工作流在审核节点暂停
- ✅ 审核记录正确创建
- ✅ 状态快照正确保存
- ✅ 审核通过后自动恢复
- ✅ 从下一节点继续执行

---

### 2. ReactAgent 工具审核测试

**步骤**：

1. 创建 ReactAgent 配置（启用 `HumanReviewTool`）
2. 执行 Agent：`POST /api/react-agent/db/{slug}`
3. Agent 调用 `HumanReviewTool`，执行暂停
4. 返回 `pendingReview` 状态和 `reviewCode`
5. 提交审核结果：`POST /api/human-review/submit/{reviewCode}`
6. Agent 自动恢复对话

**验证点**：
- ✅ Agent 正确调用 HumanReviewTool
- ✅ 上下文正确传递（conversationCode, agentConfigCode）
- ✅ 审核记录包含完整上下文
- ✅ 审核通过后继续对话
- ✅ ChatMemory 历史正确保存

---

### 3. 两层审核测试

**步骤**：

1. 创建 Graph 工作流，包含 `REACT_AGENT_NODE`
2. ReactAgent 配置启用 `HumanReviewTool`
3. 执行工作流
4. Agent 内部调用 HumanReviewTool（第一层审核）
5. 提交第一层审核
6. Agent 执行完成，返回到 Graph
7. Graph 执行到 `HUMAN_REVIEW_NODE`（第二层审核）
8. 提交第二层审核
9. 工作流完成

**验证点**：
- ✅ 两层审核独立触发
- ✅ 审核异常正确传播
- ✅ 两次审核记录都正确创建
- ✅ 两次恢复执行都正常工作

---

## 总结

### 新增文件

```
llm-agent/src/main/java/com/llmmanager/agent/
├── storage/core/
│   ├── entity/PendingReview.java                 ✅
│   ├── mapper/PendingReviewMapper.java           ✅
│   └── service/
│       ├── PendingReviewService.java             ✅
│       └── impl/PendingReviewServiceImpl.java    ✅
├── review/
│   ├── HumanReviewRecordService.java             ✅ 创建审核记录
│   ├── exception/
│   │   └── HumanReviewRequiredException.java     ✅
│   ├── snapshot/
│   │   ├── GraphStateSnapshot.java               ✅
│   │   ├── SequentialStateSnapshot.java          ✅
│   │   └── SupervisorStateSnapshot.java          ✅
│   └── context/
│       ├── HumanReviewContext.java               ✅
│       └── HumanReviewContextHolder.java         ✅
├── graph/dynamic/executor/
│   ├── HumanReviewNodeExecutor.java              ✅
│   └── ReactAgentNodeExecutor.java               ✅
└── tools/
    └── HumanReviewTool.java                      ✅

llm-service/src/main/java/com/llmmanager/service/orchestration/
└── HumanReviewOrchestrationService.java          ✅ 审核编排和恢复执行
```

### 修改文件

```
llm-agent/src/main/java/com/llmmanager/agent/
├── graph/GraphWorkflowExecutor.java              ✅ 添加 resumeFromReview()
└── reactagent/configurable/pattern/
    └── SequentialPatternExecutor.java            ✅ 添加 resumeFromCheckpoint()

llm-service/src/main/java/com/llmmanager/service/orchestration/
└── DynamicReactAgentExecutionService.java        ✅ 添加上下文管理和所有恢复方法
    - resumeFromReview()
    - resumeSequentialFromReview()
    - resumeSupervisorFromReview()

llm-agent/src/main/java/com/llmmanager/agent/graph/dynamic/
└── DynamicGraphBuilder.java                      ✅ 注册新节点执行器

llm-ops/src/main/java/com/llmmanager/ops/controller/
└── HumanReviewController.java                    ✅ REST API
```

### 核心技术点

1. ✅ **异常驱动暂停**：`HumanReviewRequiredException`
2. ✅ **ThreadLocal 上下文传递**：`HumanReviewContextHolder`
3. ✅ **服务分层设计**：llm-agent（创建记录）+ llm-service（编排恢复）
4. ✅ **状态快照与恢复**：Graph/Sequential/Supervisor 三种快照类型
5. ✅ **异步恢复执行**：`@Async` + 重试机制
6. ✅ **两层审核支持**：ReactAgent 内部 + Graph 层
7. ✅ **四种审核类型完整支持**：GRAPH_NODE/REACT_AGENT_TOOL/SEQUENTIAL/SUPERVISOR

---

**文档版本**: v2.9.0
**完成日期**: 2025-01-08
**状态**: ✅ 已完成，待测试

