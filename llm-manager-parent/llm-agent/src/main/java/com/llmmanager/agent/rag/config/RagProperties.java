package com.llmmanager.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置属性
 *
 * 配置示例：
 * llm:
 *   rag:
 *     enabled: true
 *     embedding:
 *       model: text-embedding-3-small
 *       dimensions: 1536
 *     vector-store:
 *       type: simple
 *       similarity-threshold: 0.5
 *       top-k: 5
 */
@Data
@ConfigurationProperties(prefix = "llm.rag")
public class RagProperties {

    /**
     * 是否启用 RAG 功能
     * 默认：true
     */
    private Boolean enabled = true;

    /**
     * Embedding 配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * VectorStore 配置
     */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    /**
     * 文档分割配置
     */
    private SplitterConfig splitter = new SplitterConfig();

    /**
     * Embedding 模型配置
     */
    @Data
    public static class EmbeddingConfig {
        /**
         * Embedding API Base URL
         * 默认使用 spring.ai.openai.base-url
         * 可配置为其他兼容 OpenAI API 的服务（如 Ollama、Azure OpenAI、本地服务等）
         */
        private String baseUrl;

        /**
         * Embedding API Key
         * 默认使用 spring.ai.openai.api-key
         */
        private String apiKey;

        /**
         * Embedding 模型名称
         * 默认：text-embedding-3-small
         * 其他选项：text-embedding-ada-002, nomic-embed-text (Ollama), bge-m3 等
         */
        private String model = "text-embedding-3-small";

        /**
         * 向量维度
         * 默认：1536（OpenAI text-embedding-3-small）
         * 注意：不同模型维度不同，需与模型匹配
         * - text-embedding-3-small: 1536
         * - text-embedding-ada-002: 1536
         * - nomic-embed-text: 768
         * - bge-m3: 1024
         */
        private Integer dimensions = 1536;
    }

    /**
     * VectorStore 配置
     */
    @Data
    public static class VectorStoreConfig {
        /**
         * VectorStore 类型
         * 支持：simple, tidb, milvus
         * 默认：simple（内存存储）
         */
        private String type = "simple";

        /**
         * 相似度阈值（0-1）
         * 低于此阈值的结果将被过滤
         * 默认：0.5
         */
        private Double similarityThreshold = 0.5;

        /**
         * 返回的最大文档数量
         * 默认：5
         */
        private Integer topK = 5;

        /**
         * SimpleVectorStore 持久化路径
         * 为空则不持久化
         */
        private String persistPath;

        // ============ TiDB Vector Search 配置 ============

        /**
         * TiDB 向量表名
         * 默认：a_knowledge_vectors
         *
         * 表结构参考：db/schema_vector.sql
         */
        private String tidbTableName = "a_knowledge_vectors";

        // ============ Milvus 配置 ============

        /**
         * Milvus: 服务器地址
         * 格式：host:port
         * 示例：localhost:19530
         */
        private String milvusHost = "localhost";

        /**
         * Milvus: 服务器端口
         */
        private Integer milvusPort = 19530;

        /**
         * Milvus: 用户名（可选）
         */
        private String milvusUsername;

        /**
         * Milvus: 密码（可选）
         */
        private String milvusPassword;

        /**
         * Milvus: 数据库名称
         */
        private String milvusDatabase = "default";

        /**
         * Milvus: Collection 名称
         * 每个知识库会自动添加 kbCode 作为后缀
         */
        private String milvusCollectionPrefix = "llm_kb_";

        /**
         * Milvus: 索引类型
         * 支持：IVF_FLAT, IVF_SQ8, IVF_PQ, HNSW, AUTOINDEX
         */
        private String milvusIndexType = "IVF_FLAT";

        /**
         * Milvus: 度量类型
         * 支持：L2, IP (Inner Product), COSINE
         */
        private String milvusMetricType = "COSINE";
    }

    /**
     * 文档分割配置
     */
    @Data
    public static class SplitterConfig {
        /**
         * 分割后的块大小（字符数）
         * 默认：1000
         */
        private Integer chunkSize = 1000;

        /**
         * 块之间的重叠字符数
         * 默认：200
         */
        private Integer chunkOverlap = 200;

        /**
         * 最小块大小
         * 小于此大小的块会被丢弃
         * 默认：100
         */
        private Integer minChunkSize = 100;
    }
}
