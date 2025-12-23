-- ====================
-- TiDB Vector Search - 向量数据库表结构
-- ====================
--
-- 说明：
-- 1) 本脚本用于 TiDB 原生 Vector Search（VECTOR 类型 + VECTOR INDEX）。
-- 2) Vector Search 需要 TiDB 支持向量类型/函数/索引（参考 TiDB Cloud 官方文档）。
-- 3) 向量维度必须与 Embedding 模型一致：默认 1536（text-embedding-3-small）。
--    如你使用其它 Embedding 模型，请把 VECTOR(1536) 改成对应维度。
--
-- 建议：使用 VECTOR(D) 固定维度，避免插入不同维度的向量导致数据不一致。

-- 知识库向量表（存储文档分块后的向量与内容）--- 备注：数据库未执行
CREATE TABLE IF NOT EXISTS a_knowledge_vectors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    vector_id VARCHAR(64) NOT NULL UNIQUE COMMENT '向量记录ID（对应 Spring AI Document.id）',
    kb_code VARCHAR(32) NOT NULL COMMENT '知识库 Code',
    doc_code VARCHAR(32) DEFAULT NULL COMMENT '文档 Code（a_knowledge_documents.doc_code）',
    chunk_index INT DEFAULT NULL COMMENT '分块序号（同一 doc_code 内从 0 开始）',
    content MEDIUMTEXT NOT NULL COMMENT '文本块内容',
    embedding VECTOR(1536) NOT NULL COMMENT '向量（Embedding）',
    metadata JSON COMMENT '元数据（JSON 格式）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    is_delete TINYINT(3) UNSIGNED DEFAULT 0 COMMENT '是否删除，0：正常，1：删除',
    INDEX idx_kb_code (kb_code),
    INDEX idx_doc_code (doc_code),
    INDEX idx_kb_doc (kb_code, doc_code),
    INDEX idx_is_delete (is_delete),
    -- 向量索引：使用余弦距离（推荐）
    VECTOR INDEX idx_embedding ((VEC_COSINE_DISTANCE(embedding)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库向量表（TiDB Vector Search）';

