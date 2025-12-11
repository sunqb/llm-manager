# 动态工作流配置指南

> 基于 Spring AI Alibaba Graph Core 的动态工作流系统

## 目录

- [概述](#概述)
- [配置值速查表](#配置值速查表)
- [快速开始](#快速开始)
- [JSON 配置规范](#json-配置规范)
  - [顶层结构](#顶层结构)
  - [状态配置 (stateConfig)](#状态配置-stateconfig)
  - [节点配置 (nodes)](#节点配置-nodes)
  - [边配置 (edges)](#边配置-edges)
- [节点类型详解](#节点类型详解)
  - [LLM_NODE](#llm_node)
  - [TRANSFORM_NODE](#transform_node)
  - [CONDITION_NODE](#condition_node)
- [DeepResearch 工作流详解](#deepresearch-工作流详解)
- [API 接口](#api-接口)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

---

## 概述

LLM Manager 的动态工作流系统允许用户通过 JSON 配置创建自定义的 AI 工作流，无需编写代码。

### 核心特性

- **JSON 配置驱动**：通过 JSON 定义工作流结构，存储在数据库中
- **多种节点类型**：支持 LLM 调用、数据转换、条件路由等节点
- **状态驱动**：使用状态管理数据在节点间传递
- **条件路由**：支持基于状态的动态流程控制
- **可扩展性**：易于添加新的节点类型

### 技术基础

- **Spring AI Alibaba Graph Core 1.0.0.2**
- **StateGraph / CompiledGraph**：工作流构建和执行
- **OverAllState**：统一状态管理

---

## 配置值速查表

> 所有配置字段的可选值一览表，方便快速查阅

### 边类型 (EdgeType)

`edges[].type` 字段的可选值：

| 值 | 说明 | 必需参数 |
|---|------|---------|
| `SIMPLE` | 简单边：固定连接，从 `from` 节点直接到 `to` 节点 | `from`, `to` |
| `CONDITIONAL` | 条件边：根据状态中 `next_node` 的值动态路由 | `from`, `routes` |

### 节点类型 (NodeType)

`nodes[].type` 字段的可选值：

| 值 | 说明 | 用途 |
|---|------|------|
| `LLM_NODE` | LLM 调用节点 | 调用语言模型进行文本生成 |
| `TRANSFORM_NODE` | 数据转换节点 | 转换或处理状态数据，不调用 LLM |
| `CONDITION_NODE` | 条件路由节点 | 根据状态值决定下一步路由 |

### 转换类型 (TransformType)

`TRANSFORM_NODE` 节点的 `config.transform_type` 字段可选值：

| 值 | 说明 | 输入 | 输出 | 额外参数 |
|---|------|------|------|---------|
| `MERGE` | 合并多个字段值 | 多个键 | 用换行分隔的字符串 | - |
| `EXTRACT` | 提取单个字段值 | 1个键 | 原值（任意类型） | - |
| `FORMAT` | 格式化输出 | 多个键 | `key: value` 格式字符串 | - |
| `SPLIT_LINES` | 按行分割文本 | 1个键 | `List<String>` | - |
| `PARSE_NUMBER` | 解析数字 | 1个键 | 整数（0-100 范围内） | - |
| `PARSE_JSON` | 解析 JSON 字符串 | 1个键 | `Map` 或 `List` | - |
| `THRESHOLD_CHECK` | 阈值检查 | 1个键 | `"PASS"` 或 `"NEED_IMPROVEMENT"` | `threshold` (默认80) |
| `INCREMENT` | 递增数值 | 1个键 | 整数（原值+1） | - |

### 状态更新策略 (append)

`stateConfig.keys[].append` 字段的可选值：

| 值 | 策略 | 行为 | 适用场景 |
|---|------|------|---------|
| `false` | ReplaceStrategy | 新值覆盖旧值 | 单值状态（问题、答案、评分） |
| `true` | AppendStrategy | 新值追加到列表 | 累积数据（多次搜索结果） |

### 特殊节点 ID

边配置中的 `from` 和 `to` 字段可使用的特殊值：

| 值 | 含义 |
|---|------|
| `START` | 工作流起点（只能用于 `from`） |
| `END` | 工作流终点（只能用于 `to` 或 `routes` 值） |

### THRESHOLD_CHECK 输出值

`THRESHOLD_CHECK` 转换类型的输出到 `next_node` 的可能值：

| 输出值 | 条件 | 典型用途 |
|--------|------|---------|
| `PASS` | 值 >= 阈值 | 质量达标，结束流程 |
| `NEED_IMPROVEMENT` | 值 < 阈值 | 需要改进，回到上一步重试 |

### LLM_NODE 配置参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `input_key` | String | ✅ | - | 从状态读取输入的键名 |
| `output_key` | String | ✅ | - | 输出结果存储的键名 |
| `system_prompt` | String | ❌ | null | 系统提示词，指导 LLM 行为 |
| `temperature` | Number | ❌ | null | 温度参数（0-1），控制随机性 |
| `max_tokens` | Integer | ❌ | null | 最大生成 token 数 |

### TRANSFORM_NODE 配置参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `transform_type` | String | ✅ | - | 转换类型（见上方表格） |
| `input_keys` | Array | ✅ | - | 输入字段列表 |
| `output_key` | String | ✅ | - | 输出结果存储的键名 |
| `delimiter` | String | ❌ | `"\n"` | 分隔符（用于 SPLIT 操作） |
| `threshold` | Number | ❌ | 80 | 阈值（用于 THRESHOLD_CHECK） |

### CONDITION_NODE 配置参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `condition_field` | String | ✅ | - | 条件判断的状态字段名 |
| `routes` | Object | ✅ | - | 路由映射 `{"值": "节点ID"}` |
| `default_route` | String | ❌ | `"END"` | 默认路由（不匹配时使用） |

---

## 快速开始

### 1. 验证工作流配置

```bash
curl -X POST http://localhost:8080/api/workflow/validate \
  -H "Content-Type: application/json" \
  -d '{
    "name": "SimpleWorkflow",
    "stateConfig": {
      "keys": [
        {"key": "input", "append": false},
        {"key": "output", "append": false}
      ]
    },
    "nodes": [
      {
        "id": "process",
        "type": "LLM_NODE",
        "config": {
          "input_key": "input",
          "output_key": "output",
          "system_prompt": "请总结以下内容"
        }
      }
    ],
    "edges": [
      {"from": "START", "to": "process", "type": "SIMPLE"},
      {"from": "process", "to": "END", "type": "SIMPLE"}
    ]
  }'
```

### 2. 执行工作流

```bash
curl -X POST http://localhost:8080/api/workflow/execute/1 \
  -H "Content-Type: application/json" \
  -d '{
    "workflowConfig": "{...完整JSON配置...}",
    "initialState": {
      "input": "这是需要处理的文本内容"
    }
  }'
```

### 3. 执行 DeepResearch（便捷接口）

```bash
curl -X POST http://localhost:8080/api/workflow/deep-research/1 \
  -H "Content-Type: application/json" \
  -d '{"question": "人工智能的发展历史是什么？"}'
```

---

## JSON 配置规范

### 顶层结构

```json
{
  "name": "工作流名称",
  "description": "工作流描述（可选）",
  "version": "版本号（可选）",
  "stateConfig": { ... },
  "nodes": [ ... ],
  "edges": [ ... ]
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | String | ✅ | 工作流唯一名称 |
| `description` | String | ❌ | 工作流描述 |
| `version` | String | ❌ | 版本号（如 "1.0.0"） |
| `stateConfig` | Object | ✅ | 状态键配置 |
| `nodes` | Array | ✅ | 节点列表 |
| `edges` | Array | ✅ | 边列表 |

---

### 状态配置 (stateConfig)

状态是工作流中数据传递的核心。每个状态键需要指定更新策略。

```json
{
  "stateConfig": {
    "keys": [
      {"key": "question", "append": false, "description": "用户问题"},
      {"key": "results", "append": true, "description": "搜索结果（追加模式）"}
    ]
  }
}
```

#### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `key` | String | ✅ | 状态键名称 |
| `append` | Boolean | ✅ | 更新策略：`false` = 替换，`true` = 追加 |
| `description` | String | ❌ | 状态键描述 |

#### 更新策略

| 策略 | `append` 值 | 行为 | 适用场景 |
|------|-------------|------|----------|
| **ReplaceStrategy** | `false` | 新值覆盖旧值 | 单值状态（问题、结果） |
| **AppendStrategy** | `true` | 新值追加到列表 | 累积数据（搜索结果） |

#### 系统预留状态键

| 键名 | 用途 | 自动注册 |
|------|------|----------|
| `next_node` | 条件路由决策 | ✅ 自动 |
| `current_node` | 记录当前节点 ID | ❌ 手动 |

---

### 节点配置 (nodes)

每个节点定义一个处理步骤。

```json
{
  "nodes": [
    {
      "id": "node_unique_id",
      "type": "LLM_NODE",
      "name": "节点显示名称",
      "description": "节点描述",
      "config": { ... }
    }
  ]
}
```

#### 通用字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | String | ✅ | 节点唯一标识（用于边连接） |
| `type` | String | ✅ | 节点类型（见下文） |
| `name` | String | ❌ | 显示名称 |
| `description` | String | ❌ | 节点描述 |
| `config` | Object | ✅ | 节点配置（因类型而异） |

#### 支持的节点类型

| 类型 | 说明 |
|------|------|
| `LLM_NODE` | 调用语言模型 |
| `TRANSFORM_NODE` | 数据转换 |
| `CONDITION_NODE` | 条件路由 |

---

### 边配置 (edges)

定义节点之间的连接关系。

```json
{
  "edges": [
    {"from": "START", "to": "node_1", "type": "SIMPLE"},
    {"from": "node_1", "to": "node_2", "type": "SIMPLE"},
    {
      "from": "node_2",
      "to": null,
      "type": "CONDITIONAL",
      "routes": {
        "PASS": "END",
        "FAIL": "node_1"
      }
    }
  ]
}
```

#### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `from` | String | ✅ | 源节点 ID（`"START"` 表示起点） |
| `to` | String | * | 目标节点 ID（`"END"` 表示终点） |
| `type` | String | ✅ | 边类型：`SIMPLE` 或 `CONDITIONAL` |
| `routes` | Object | ** | 条件路由映射（`CONDITIONAL` 时必需） |

\* `SIMPLE` 边必须指定 `to`，`CONDITIONAL` 边 `to` 可为 `null`
\** 仅 `CONDITIONAL` 边需要

#### 边类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `SIMPLE` | 固定连接 | `{"from": "A", "to": "B", "type": "SIMPLE"}` |
| `CONDITIONAL` | 根据 `next_node` 状态路由 | 见上方示例 |

#### 特殊节点 ID

| ID | 含义 |
|----|------|
| `START` | 工作流起点 |
| `END` | 工作流终点 |

---

## 节点类型详解

### LLM_NODE

调用语言模型进行文本生成。

#### 配置参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `input_key` | String | ✅ | 从状态读取输入的键名 |
| `output_key` | String | ✅ | 输出结果存储的键名 |
| `system_prompt` | String | ❌ | 系统提示词 |
| `temperature` | Number | ❌ | 温度参数（0-1） |
| `max_tokens` | Integer | ❌ | 最大生成 token 数 |

#### 示例

```json
{
  "id": "summarize",
  "type": "LLM_NODE",
  "name": "文本摘要",
  "config": {
    "input_key": "article",
    "output_key": "summary",
    "system_prompt": "请将以下文章总结为3个要点",
    "temperature": 0.5
  }
}
```

#### 执行流程

```
1. 从状态中读取 input_key 对应的值
2. 构建 prompt（system_prompt + 用户输入）
3. 调用 ChatClient 获取响应
4. 将响应写入状态的 output_key
5. 更新 current_node 为当前节点 ID
```

---

### TRANSFORM_NODE

转换或处理状态数据，不调用 LLM。

#### 配置参数（通用）

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `transform_type` | String | ✅ | 转换类型 |
| `input_keys` | Array | ✅ | 输入字段列表 |
| `output_key` | String | ✅ | 输出字段 |

#### 支持的转换类型

| 类型 | 说明 | 输入 | 输出 | 额外参数 |
|------|------|------|------|----------|
| `MERGE` | 合并多字段 | 多个键 | 换行分隔字符串 | - |
| `EXTRACT` | 提取单字段 | 1个键 | 原值 | - |
| `FORMAT` | 格式化 | 多个键 | `key: value` 格式 | - |
| `SPLIT_LINES` | 按行分割 | 1个键 | `List<String>` | - |
| `PARSE_NUMBER` | 解析数字 | 1个键 | 整数（0-100） | - |
| `PARSE_JSON` | 解析 JSON | 1个键 | `Map` 或 `List` | - |
| `THRESHOLD_CHECK` | 阈值检查 | 1个键 | `"PASS"` 或 `"NEED_IMPROVEMENT"` | `threshold` |
| `INCREMENT` | 递增数值 | 1个键 | 整数（原值+1） | - |

#### 示例：解析数字

```json
{
  "id": "parse_score",
  "type": "TRANSFORM_NODE",
  "name": "解析评分",
  "config": {
    "transform_type": "PARSE_NUMBER",
    "input_keys": ["score_raw"],
    "output_key": "score"
  }
}
```

#### 示例：阈值检查

```json
{
  "id": "threshold_check",
  "type": "TRANSFORM_NODE",
  "name": "质量阈值检查",
  "config": {
    "transform_type": "THRESHOLD_CHECK",
    "input_keys": ["quality_score"],
    "output_key": "next_node",
    "threshold": 80
  }
}
```

> **注意**：`THRESHOLD_CHECK` 输出到 `next_node` 可用于条件路由

---

### CONDITION_NODE

根据状态值决定下一步路由（当前使用 `TRANSFORM_NODE + THRESHOLD_CHECK` 替代）。

#### 配置参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `condition_field` | String | ✅ | 条件判断的状态字段 |
| `routes` | Object | ✅ | 路由映射 |
| `default_route` | String | ❌ | 默认路由（默认 `"END"`） |

#### 示例

```json
{
  "id": "route_decision",
  "type": "CONDITION_NODE",
  "config": {
    "condition_field": "status",
    "routes": {
      "approved": "process_node",
      "rejected": "reject_node"
    },
    "default_route": "END"
  }
}
```

---

## DeepResearch 工作流详解

DeepResearch 是一个完整的深度研究工作流示例，演示了所有核心功能。

### 工作流目的

将复杂问题分解、收集信息、分析并综合成高质量研究报告，支持迭代优化。

### 工作流图示

```
START
  │
  ▼
┌─────────────────────┐
│ query_decomposition │ (LLM_NODE) - 问题分解
│ 输入：question       │
│ 输出：sub_questions  │
└─────────────────────┘
  │
  ▼
┌─────────────────────┐
│ information_gathering│ (LLM_NODE) - 信息收集
│ 输入：sub_questions  │
│ 输出：search_results │
└─────────────────────┘
  │
  ▼
┌─────────────────────┐◀────────────────────────┐
│      analysis       │ (LLM_NODE) - 深度分析    │
│ 输入：search_results │                         │
│ 输出：analysis_result│                         │
└─────────────────────┘                         │
  │                                              │
  ▼                                              │
┌─────────────────────┐                         │
│      synthesis      │ (LLM_NODE) - 综合报告    │
│ 输入：analysis_result│                         │
│ 输出：final_answer   │                         │
└─────────────────────┘                         │
  │                                              │
  ▼                                              │
┌─────────────────────┐                         │
│  quality_check_llm  │ (LLM_NODE) - 质量评估    │
│ 输入：final_answer   │                         │
│ 输出：quality_score_raw│                       │
└─────────────────────┘                         │
  │                                              │
  ▼                                              │
┌─────────────────────┐                         │
│ quality_score_parse │ (TRANSFORM) - 评分解析  │
│ 类型：PARSE_NUMBER   │                         │
│ 输出：quality_score  │                         │
└─────────────────────┘                         │
  │                                              │
  ▼                                              │
┌─────────────────────┐                         │
│ iteration_increment │ (TRANSFORM) - 迭代计数  │
│ 类型：INCREMENT      │                         │
│ 输出：iteration_count│                         │
└─────────────────────┘                         │
  │                                              │
  ▼                                              │
┌─────────────────────┐                         │
│quality_threshold_check│ (TRANSFORM) - 阈值检查│
│ 类型：THRESHOLD_CHECK │                        │
│ 阈值：80             │                         │
│ 输出：next_node      │                         │
└─────────────────────┘                         │
  │                                              │
  ├──── PASS ────────────▶ END                  │
  │                                              │
  └── NEED_IMPROVEMENT ─────────────────────────┘
```

### 状态键说明

| 状态键 | 更新策略 | 说明 |
|--------|----------|------|
| `question` | Replace | 原始研究问题（初始输入） |
| `sub_questions` | Replace | 分解后的子问题列表 |
| `search_results` | Append | 收集的信息（可累积） |
| `analysis_result` | Replace | 分析结果 |
| `final_answer` | Replace | 最终研究报告 |
| `quality_score_raw` | Replace | LLM 输出的原始评分文本 |
| `quality_score` | Replace | 解析后的数值评分 |
| `iteration_count` | Replace | 迭代次数计数器 |
| `next_node` | Replace | 路由决策（PASS/NEED_IMPROVEMENT） |
| `current_node` | Replace | 当前执行的节点 ID |

### 节点详解

#### 1. query_decomposition（问题分解）

**目的**：将复杂问题分解为 3-5 个可独立搜索的子问题。

```json
{
  "id": "query_decomposition",
  "type": "LLM_NODE",
  "config": {
    "input_key": "question",
    "output_key": "sub_questions",
    "system_prompt": "你是一个研究助手。请将以下复杂问题分解为3-5个可以独立搜索的子问题...",
    "temperature": 0.7
  }
}
```

**示例输入**：`"人工智能的发展历史是什么？"`

**示例输出**：
```
人工智能的概念是何时提出的？
早期人工智能研究的主要方法是什么？
人工智能发展经历了哪些重要里程碑？
当前人工智能技术的主要应用领域有哪些？
未来人工智能的发展趋势是什么？
```

#### 2. information_gathering（信息收集）

**目的**：针对每个子问题收集详细信息。

```json
{
  "id": "information_gathering",
  "type": "LLM_NODE",
  "config": {
    "input_key": "sub_questions",
    "output_key": "search_results",
    "system_prompt": "你是一个知识渊博的研究助手。请针对以下每个子问题，提供详细、准确的信息...",
    "temperature": 0.5
  }
}
```

> **注意**：`search_results` 使用 `append: true`，多次迭代会累积数据。

#### 3. analysis（深度分析）

**目的**：对收集的信息进行深度分析，识别模式和联系。

```json
{
  "id": "analysis",
  "type": "LLM_NODE",
  "config": {
    "input_key": "search_results",
    "output_key": "analysis_result",
    "system_prompt": "你是一个分析专家。请对以下收集的信息进行深度分析...",
    "temperature": 0.6
  }
}
```

#### 4. synthesis（综合报告）

**目的**：将分析结果综合成结构化的研究报告。

```json
{
  "id": "synthesis",
  "type": "LLM_NODE",
  "config": {
    "input_key": "analysis_result",
    "output_key": "final_answer",
    "system_prompt": "你是一个专业的研究报告撰写专家。请基于以下分析结果，撰写一份全面、结构清晰的研究报告...",
    "temperature": 0.7
  }
}
```

#### 5. quality_check_llm（质量评估）

**目的**：使用 LLM 评估报告质量，输出 0-100 评分。

```json
{
  "id": "quality_check_llm",
  "type": "LLM_NODE",
  "config": {
    "input_key": "final_answer",
    "output_key": "quality_score_raw",
    "system_prompt": "请评估以下研究报告的质量...只需要返回一个总体评分数字（0-100），不要其他任何内容。",
    "temperature": 0.3
  }
}
```

> **注意**：低温度（0.3）确保评分稳定性

#### 6. quality_score_parse（评分解析）

**目的**：从 LLM 输出中解析数字评分。

```json
{
  "id": "quality_score_parse",
  "type": "TRANSFORM_NODE",
  "config": {
    "transform_type": "PARSE_NUMBER",
    "input_keys": ["quality_score_raw"],
    "output_key": "quality_score"
  }
}
```

**示例**：`"85"` → `85`（整数）

#### 7. iteration_increment（迭代计数）

**目的**：增加迭代次数，用于防止无限循环。

```json
{
  "id": "iteration_increment",
  "type": "TRANSFORM_NODE",
  "config": {
    "transform_type": "INCREMENT",
    "input_keys": ["iteration_count"],
    "output_key": "iteration_count"
  }
}
```

#### 8. quality_threshold_check（质量阈值检查）

**目的**：检查评分是否达到阈值（80），决定是结束还是重新分析。

```json
{
  "id": "quality_threshold_check",
  "type": "TRANSFORM_NODE",
  "config": {
    "transform_type": "THRESHOLD_CHECK",
    "input_keys": ["quality_score"],
    "output_key": "next_node",
    "threshold": 80
  }
}
```

**输出**：
- `quality_score >= 80` → `"PASS"`
- `quality_score < 80` → `"NEED_IMPROVEMENT"`

### 条件路由

```json
{
  "from": "quality_threshold_check",
  "to": null,
  "type": "CONDITIONAL",
  "routes": {
    "PASS": "END",
    "NEED_IMPROVEMENT": "analysis"
  }
}
```

当 `next_node = "PASS"` 时结束，否则返回 `analysis` 节点重新分析。

### 完整配置

完整配置文件位于：`llm-agent/src/main/resources/workflows/deep-research.json`

---

## API 接口

### 获取可用节点类型

```bash
GET /api/workflow/node-types
```

**响应示例**：
```json
{
  "LLM_NODE": "调用语言模型进行文本生成",
  "TRANSFORM_NODE": "数据转换和处理",
  "CONDITION_NODE": "条件路由节点"
}
```

### 验证工作流配置

```bash
POST /api/workflow/validate
Content-Type: application/json

{配置JSON}
```

**响应示例**：
```json
{
  "valid": true,
  "name": "MyWorkflow",
  "nodeCount": 5,
  "edgeCount": 6
}
```

### 执行工作流

```bash
POST /api/workflow/execute/{modelId}
Content-Type: application/json

{
  "workflowConfig": "{完整JSON配置字符串}",
  "initialState": {
    "question": "你的问题",
    "iteration_count": 0
  }
}
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "question": "...",
    "final_answer": "...",
    "quality_score": 85
  }
}
```

### 执行 DeepResearch（便捷接口）

```bash
POST /api/workflow/deep-research/{modelId}
Content-Type: application/json

{
  "question": "人工智能的发展历史是什么？"
}
```

---

## 最佳实践

### 1. 状态键命名

- 使用 `snake_case` 命名
- 使用描述性名称：`search_results` 而非 `sr`
- 添加 `description` 字段说明用途

### 2. 节点设计

- **单一职责**：每个节点只做一件事
- **明确输入输出**：清晰定义 `input_key` 和 `output_key`
- **合理温度**：
  - 创意任务：0.7-0.9
  - 分析任务：0.5-0.7
  - 评分/判断：0.2-0.4

### 3. 迭代控制

- 使用 `iteration_count` 跟踪迭代次数
- 设置最大迭代数防止无限循环
- 可在条件检查中加入迭代限制逻辑

### 4. 错误处理

- 使用 `THRESHOLD_CHECK` 提供优雅降级
- 设置合理的默认值
- 验证配置后再执行

### 5. 调试技巧

- 使用 `/api/workflow/validate` 验证配置
- 检查日志中的节点执行顺序
- 关注 `current_node` 状态追踪执行流程

---

## 常见问题

### Q1: 如何添加新的节点类型？

1. 实现 `NodeExecutor` 接口
2. 使用 `@Component` 注解并指定名称
3. 实现 `getNodeType()` 返回类型标识
4. 实现 `createAction()` 返回 `AsyncNodeAction`

### Q2: 条件边的 `routes` 值从哪里来？

条件边从状态的 `next_node` 键读取值，然后在 `routes` 中查找对应的目标节点。通常由 `THRESHOLD_CHECK` 或 `CONDITION_NODE` 设置。

### Q3: `append: true` 和 `append: false` 有什么区别？

- `append: false`（ReplaceStrategy）：新值覆盖旧值
- `append: true`（AppendStrategy）：新值追加到列表

适用场景：
- 中间结果、最终答案 → `append: false`
- 累积数据（如多次搜索结果）→ `append: true`

### Q4: 如何实现复杂的条件判断？

使用 `LLM_NODE` 生成判断结果，然后用 `TRANSFORM_NODE` 解析，最后通过条件边路由。

```json
// 1. LLM 判断
{"id": "judge", "type": "LLM_NODE", "config": {"output_key": "decision", ...}}

// 2. 条件边
{"from": "judge", "to": null, "type": "CONDITIONAL", "routes": {"yes": "A", "no": "B"}}
```

### Q5: 工作流执行失败怎么排查？

1. 检查配置 JSON 格式是否正确
2. 使用 `/api/workflow/validate` 验证配置
3. 检查节点的 `input_key` 是否有对应的状态值
4. 查看应用日志中的 `[DynamicGraphBuilder]` 和 `[*NodeExecutor]` 日志

---

## 附录：完整配置示例

### 简单问答工作流

```json
{
  "name": "SimpleQA",
  "description": "简单问答工作流",
  "stateConfig": {
    "keys": [
      {"key": "question", "append": false, "description": "问题"},
      {"key": "answer", "append": false, "description": "答案"}
    ]
  },
  "nodes": [
    {
      "id": "answer_question",
      "type": "LLM_NODE",
      "name": "回答问题",
      "config": {
        "input_key": "question",
        "output_key": "answer",
        "system_prompt": "请简洁准确地回答以下问题",
        "temperature": 0.7
      }
    }
  ],
  "edges": [
    {"from": "START", "to": "answer_question", "type": "SIMPLE"},
    {"from": "answer_question", "to": "END", "type": "SIMPLE"}
  ]
}
```

### 多步骤处理工作流

```json
{
  "name": "MultiStep",
  "description": "多步骤文本处理",
  "stateConfig": {
    "keys": [
      {"key": "text", "append": false},
      {"key": "translated", "append": false},
      {"key": "summarized", "append": false}
    ]
  },
  "nodes": [
    {
      "id": "translate",
      "type": "LLM_NODE",
      "config": {
        "input_key": "text",
        "output_key": "translated",
        "system_prompt": "请将以下文本翻译成中文"
      }
    },
    {
      "id": "summarize",
      "type": "LLM_NODE",
      "config": {
        "input_key": "translated",
        "output_key": "summarized",
        "system_prompt": "请将以下内容总结为3个要点"
      }
    }
  ],
  "edges": [
    {"from": "START", "to": "translate", "type": "SIMPLE"},
    {"from": "translate", "to": "summarize", "type": "SIMPLE"},
    {"from": "summarize", "to": "END", "type": "SIMPLE"}
  ]
}
```

---

## 更新日志

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0.0 | 2024-12 | 初始版本，支持 LLM_NODE、TRANSFORM_NODE、CONDITION_NODE |

---

**文档版本**：1.0.0
**最后更新**：2024-12-11
**使用模型**：Spring AI Alibaba Graph Core 1.0.0.2
