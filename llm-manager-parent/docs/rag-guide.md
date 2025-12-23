# RAG（检索增强生成）实现与使用指南

本文档聚焦本项目 `llm.rag` 的实现结构、配置项、以及本地/生产的执行步骤（含 Simple/TiDB/Milvus 向量库与 OpenAI/Ollama Embedding）。

## 1. 总览

RAG 流程（本项目默认实现）：

1. 通过知识库接口写入文档（`KnowledgeDocument`）。
2. `DocumentProcessor` 解析与切分文本块（chunk）。
3. `EmbeddingModel` 为每个 chunk 生成向量（Embedding）。
4. `VectorStore` 保存向量 + chunk 内容 + 元数据（metadata）。
5. 检索时对 query 做 embedding，然后从向量库做相似度检索，结果注入到 Prompt 作为上下文。

## 2. 关键组件与数据流

### 2.1 关键类

- `llm-agent/src/main/java/com/llmmanager/agent/rag/DocumentProcessor.java`
  - 文档内容获取、分割（`TokenTextSplitter`）、写入向量库。
  - 重处理前会按 `docCode` 清理旧向量，避免重复入库。

- `llm-agent/src/main/java/com/llmmanager/agent/rag/VectorStoreManager.java`
  - 为每个知识库 `kbCode` 缓存一个 `VectorStore` 实例。
  - 支持 `simple | tidb | milvus` 三种实现。
  - 提供多知识库合并检索（按 `Document.score`/`metadata.score` 统一排序）。

- `llm-agent/src/main/java/com/llmmanager/agent/rag/RagAdvisorBuilder.java`
  - 基于 Spring AI `RetrievalAugmentationAdvisor` 构建 RAG Advisor。
  - 支持 metadata 精确匹配过滤（`FilterExpressionBuilder.eq` + AND 组合）。

- `llm-agent/src/main/java/com/llmmanager/agent/rag/MultiKbDocumentRetriever.java`
  - 多知识库检索：循环调用 `VectorStoreManager.similaritySearch` 并合并排序。

### 2.2 metadata 约定（用于过滤/回溯/删除）

`DocumentProcessor.splitDocument(...)` 默认写入：

- `docCode`：文档唯一标识
- `kbCode`：知识库标识
- `title`：标题
- `docType`：类型（TEXT/MARKDOWN/URL/…）
- `KnowledgeDocument.metadata`：用户自定义元数据会 merge 进去

向量库检索返回时：

- TiDB：会把 `score`（Float）与 `distance`（Float）写入 `Document.metadata`
- Milvus：`Document.score` 为 Double，并在 metadata 里写入 `distance`（Spring AI 默认行为）

## 3. 配置项说明（`llm-ops/src/main/resources/application.yml`）

```yaml
llm:
  rag:
    enabled: true
    embedding:
      base-url: http://localhost:11434     # 可选；不配则使用 spring.ai.openai.base-url
      api-key: ollama                      # 可选；不配则使用 spring.ai.openai.api-key
      model: nomic-embed-text              # Embedding 模型名
      dimensions: 768                      # 向量维度（必须与模型一致）
    vector-store:
      type: simple                         # simple | tidb | milvus
      persist-path: ./data/vectorstore     # simple 模式可选持久化
      top-k: 5
      similarity-threshold: 0.5
      # tidb-table-name: a_knowledge_vectors
      # milvus-host: localhost
      # milvus-port: 19530
      # milvus-username:
      # milvus-password:
      # milvus-database: default
      # milvus-collection-prefix: llm_kb_
      # milvus-index-type: IVF_FLAT
      # milvus-metric-type: COSINE
    splitter:
      chunk-size: 1000
      chunk-overlap: 200
      min-chunk-size: 100
```

## 4. Embedding 配置与本地 Ollama

### 4.1 OpenAI（云端）

- `model: text-embedding-3-small`（默认 1536 维）
- 配置 `OPENAI_API_KEY`（或 `spring.ai.openai.api-key` / `llm.rag.embedding.api-key`）

### 4.2 Ollama（本地）

注意：`text-embedding-3-small` 是 OpenAI 模型，不能用 Ollama 本地安装。

1) 安装 Ollama 后拉取 embedding 模型（二选一）：

```bash
ollama pull nomic-embed-text   # 768 维
# 或
ollama pull bge-m3            # 1024 维
```

2) 配置（关键点：`base-url` 不要带 `/v1`）：

```yaml
llm:
  rag:
    embedding:
      base-url: http://localhost:11434
      api-key: ollama
      model: nomic-embed-text
      dimensions: 768
```

可选自测（确认 OpenAI 兼容 embeddings 接口可用）：

```bash
curl http://localhost:11434/v1/embeddings \
  -H 'Content-Type: application/json' \
  -d '{"model":"nomic-embed-text","input":"hello"}'
```

## 5. VectorStore 选型与落地

### 5.1 SimpleVectorStore（默认）

适用：本地开发、小数据量、快速验证。

- 存储：内存（可选落盘到 `persist-path`，文件名为 `${kbCode}.json`）。
- 删除/重处理：
  - 重处理、删除文档会按 `metadata.docCode` 清理旧向量（扫描内存 store 后批量 delete）。
  - `clear` 会创建空 store 并覆盖持久化文件，避免下次启动又 load 回来旧数据。

### 5.2 TiDB Vector Search（`type=tidb`）

适用：已使用 TiDB Cloud / TiDB，且希望直接用数据库原生向量检索。

步骤：

1) 确认 TiDB 支持 Vector Search（`VECTOR` 类型、距离函数与向量索引）。
2) 执行建表脚本：`db/schema_vector.sql`
3) 配置：

```yaml
llm:
  rag:
    vector-store:
      type: tidb
      tidb-table-name: a_knowledge_vectors
```

要点：

- `VECTOR(D)` 的维度 `D` 必须与 Embedding 模型维度一致。
- 采用软删：`is_delete=1`。

### 5.3 Milvus（`type=milvus`）

适用：中大规模数据、独立向量库、追求更高的向量检索性能与扩展能力。

步骤：

1) 按 Milvus 官方文档安装并启动（默认端口 `19530`）。
2) 配置：

```yaml
llm:
  rag:
    vector-store:
      type: milvus
      milvus-host: localhost
      milvus-port: 19530
      milvus-database: default
      milvus-collection-prefix: llm_kb_
      milvus-index-type: IVF_FLAT     # 可选：HNSW/AUTOINDEX
      milvus-metric-type: COSINE
```

要点：

- 每个知识库一个 collection：`${milvus-collection-prefix}${kbCode}`。
- 创建时启用 `initializeSchema=true`：自动建 collection/schema/index（注意：数据库名建议用 `default`，或确保自定义数据库已存在）。
- `clear/remove` 会 drop collection；删除文档按 `metadata.docCode` 过滤删除。

## 6. 本地执行步骤（最小闭环）

### 6.1 准备依赖

- JDK 17+（推荐 JDK 21）
- MySQL 8.x 或 TiDB（存储知识库/文档/配置）
- Redis（按 `application.yml` 配置启动）
- Embedding：
  - 云端：OpenAI（需要 `OPENAI_API_KEY`）
  - 本地：Ollama（推荐）
- 向量库（可选）：
  - `type=simple`：不需要额外服务
  - `type=tidb`：需要 TiDB Vector Search
  - `type=milvus`：需要 Milvus

### 6.2 初始化数据库

在目标数据库中执行：

```bash
mysql -h your-host -u username -p your_database < db/schema.sql
mysql -h your-host -u username -p your_database < db/initdata.sql
```

如使用 TiDB Vector Search，再执行：

```bash
mysql -h your-host -u username -p your_database < db/schema_vector.sql
```

### 6.3 启动服务

```bash
export JAVA_HOME=/path/to/jdk-21
mvn -pl llm-ops -am -DskipTests test
cd llm-ops
mvn spring-boot:run
```

## 7. 关键 API（知识库/文档/RAG 检索）

Controller：`llm-ops/src/main/java/com/llmmanager/ops/controller/KnowledgeBaseController.java`

基础路径：`/api/knowledge-base`

- `GET /enabled`：获取启用的知识库
- `POST /{kbCode}/documents/text`：添加文本文档
- `POST /documents/{docCode}/process`：处理指定文档（切分+向量化）
- `POST /documents/process-pending`：批量处理待处理文档
- `POST /{kbCode}/search`：在知识库内检索
- `POST /global/search`：跨所有已加载知识库检索
- `POST /{kbCode}/clear`：清空知识库向量
- `DELETE /documents/{docCode}`：删除文档（同步清理向量）

## 8. 本次 RAG/向量库相关改动清单（代码位置）

- TiDB Vector Search：
  - 建表：`db/schema_vector.sql`
  - 向量库实现：`llm-agent/src/main/java/com/llmmanager/agent/rag/vectorstore/TidbVectorStore.java`

- Milvus：
  - 依赖：`pom.xml`、`llm-agent/pom.xml`
  - 创建与清理：`llm-agent/src/main/java/com/llmmanager/agent/rag/VectorStoreManager.java`

- 清理链路：
  - 文档重处理前清理旧向量：`llm-agent/src/main/java/com/llmmanager/agent/rag/DocumentProcessor.java`
  - 删除文档时同步清理向量：`llm-ops/src/main/java/com/llmmanager/ops/controller/KnowledgeBaseController.java`

- 多知识库合并排序：
  - 统一按 `Document.score`（兼容 `metadata.score`）：`llm-agent/src/main/java/com/llmmanager/agent/rag/VectorStoreManager.java`

