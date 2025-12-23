# 数据库脚本

## schema.sql

包含所有表结构定义，用于初始化数据库。

## schema_vector.sql

包含 TiDB Vector Search 向量表结构（`a_knowledge_vectors`），用于持久化 RAG 向量数据。

> 使用 TiDB 原生向量类型 `VECTOR(D)` 与 `VECTOR INDEX`，执行前请确认你的 TiDB Cloud 集群已支持 Vector Search。

### ⚠️ 重要说明

**本项目不再使用 Spring Boot 自动数据库初始化功能，开发人员必须手动执行 SQL 脚本创建数据库表。**

### 使用方式

**方式 1：MySQL 命令行**
```bash
# 1. 确保已创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS llm_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 执行建表脚本
mysql -u root -p llm_manager < db/schema.sql

# 3. （可选）执行向量表脚本（使用 TiDB Vector Search 时）
mysql -u root -p llm_manager < db/schema_vector.sql
```

**方式 2：MySQL 客户端**
```sql
-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS llm_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 选择数据库
USE llm_manager;

-- 3. 执行建表脚本
SOURCE /path/to/llm-manager-parent/db/schema.sql;
```

### 表说明

#### 业务表（p_ 前缀）
- `p_users` - 用户表
- `p_channel` - 渠道表
- `p_llm_model` - LLM 模型表
- `p_agents` - Agent 表
- `p_prompt` - 提示词模板表
- `p_api_key` - API 密钥表

#### Agent 模块表（a_ 前缀）
- `a_chat_history` - 聊天历史表

### 注意事项

- 所有表使用 `IF NOT EXISTS` 创建，可安全重复执行
- 所有表包含软删除字段 `is_delete`
- 使用 UTF-8MB4 字符集
- 包含审计字段：`create_time`, `update_time`, `create_by`, `update_by`
