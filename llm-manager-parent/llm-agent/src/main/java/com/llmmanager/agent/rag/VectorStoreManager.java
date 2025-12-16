package com.llmmanager.agent.rag;

import com.llmmanager.agent.rag.config.RagProperties;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;
import com.llmmanager.agent.storage.core.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VectorStore 管理器
 *
 * 负责管理多个知识库的 VectorStore 实例。
 * 每个知识库有独立的 VectorStore，支持按知识库进行文档存储和检索。
 *
 * 当前版本使用 SimpleVectorStore（内存存储），支持可选的文件持久化。
 * 后续可扩展支持 PgVector、Chroma、Milvus 等向量数据库。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class VectorStoreManager {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 知识库 VectorStore 缓存
     * Key: kbCode
     */
    private final Map<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * 全局 VectorStore（用于未指定知识库的场景）
     */
    private VectorStore globalVectorStore;

    @PostConstruct
    public void initialize() {
        log.info("[VectorStoreManager] 初始化 VectorStore 管理器");

        // 创建全局 VectorStore
        globalVectorStore = createVectorStore("_global");

        // 加载所有启用的知识库（优雅处理数据库异常）
        try {
            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.listAllEnabled();
            for (KnowledgeBase kb : knowledgeBases) {
                getOrCreateVectorStore(kb.getKbCode());
            }
            log.info("[VectorStoreManager] 初始化完成，加载 {} 个知识库", knowledgeBases.size());
        } catch (Exception e) {
            log.warn("[VectorStoreManager] 加载知识库列表失败（数据库表可能不存在），RAG 功能将在首次使用时按需加载: {}",
                    e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[VectorStoreManager] 关闭 VectorStore 管理器，持久化数据...");

        // 持久化所有 VectorStore
        String persistPath = ragProperties.getVectorStore().getPersistPath();
        if (persistPath != null && !persistPath.isEmpty()) {
            for (Map.Entry<String, VectorStore> entry : vectorStoreCache.entrySet()) {
                persistVectorStore(entry.getKey(), entry.getValue());
            }
            persistVectorStore("_global", globalVectorStore);
        }
    }

    /**
     * 获取或创建知识库的 VectorStore
     */
    public VectorStore getOrCreateVectorStore(String kbCode) {
        return vectorStoreCache.computeIfAbsent(kbCode, this::createVectorStore);
    }

    /**
     * 获取全局 VectorStore
     */
    public VectorStore getGlobalVectorStore() {
        return globalVectorStore;
    }

    /**
     * 向知识库添加文档
     */
    public void addDocuments(String kbCode, List<Document> documents) {
        VectorStore vectorStore = getOrCreateVectorStore(kbCode);
        vectorStore.add(documents);
        log.info("[VectorStoreManager] 添加 {} 个文档到知识库: {}", documents.size(), kbCode);
    }

    /**
     * 向全局知识库添加文档
     */
    public void addDocumentsToGlobal(List<Document> documents) {
        globalVectorStore.add(documents);
        log.info("[VectorStoreManager] 添加 {} 个文档到全局知识库", documents.size());
    }

    /**
     * 从知识库检索相似文档
     */
    public List<Document> similaritySearch(String kbCode, String query, int topK) {
        VectorStore vectorStore = getOrCreateVectorStore(kbCode);
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(ragProperties.getVectorStore().getSimilarityThreshold())
                        .build()
        );
    }

    /**
     * 从知识库检索相似文档（使用默认 topK）
     */
    public List<Document> similaritySearch(String kbCode, String query) {
        return similaritySearch(kbCode, query, ragProperties.getVectorStore().getTopK());
    }

    /**
     * 从全局知识库检索相似文档
     */
    public List<Document> similaritySearchGlobal(String query, int topK) {
        return globalVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(ragProperties.getVectorStore().getSimilarityThreshold())
                        .build()
        );
    }

    /**
     * 从所有启用的知识库检索相似文档
     *
     * 当未指定具体知识库时使用此方法，会搜索所有已加载的知识库。
     */
    public List<Document> similaritySearchAllEnabled(String query, int topK) {
        List<String> allKbCodes = getAllKbCodes();
        if (allKbCodes.isEmpty()) {
            log.warn("[VectorStoreManager] 没有可用的知识库");
            return new ArrayList<>();
        }
        return similaritySearchMultiple(allKbCodes, query, topK);
    }

    /**
     * 从多个知识库检索相似文档
     */
    public List<Document> similaritySearchMultiple(List<String> kbCodes, String query, int topK) {
        // 合并多个知识库的检索结果
        return kbCodes.stream()
                .flatMap(kbCode -> similaritySearch(kbCode, query, topK).stream())
                .sorted((d1, d2) -> {
                    // 按相似度降序排序
                    Float score1 = d1.getMetadata().get("score") instanceof Float ?
                            (Float) d1.getMetadata().get("score") : 0f;
                    Float score2 = d2.getMetadata().get("score") instanceof Float ?
                            (Float) d2.getMetadata().get("score") : 0f;
                    return score2.compareTo(score1);
                })
                .limit(topK)
                .toList();
    }

    /**
     * 删除知识库的 VectorStore
     */
    public void removeVectorStore(String kbCode) {
        vectorStoreCache.remove(kbCode);
        log.info("[VectorStoreManager] 删除知识库 VectorStore: {}", kbCode);
    }

    /**
     * 清空知识库的所有文档
     */
    public void clearVectorStore(String kbCode) {
        // SimpleVectorStore 没有 clear 方法，需要重新创建
        vectorStoreCache.put(kbCode, createVectorStore(kbCode));
        log.info("[VectorStoreManager] 清空知识库: {}", kbCode);
    }

    /**
     * 获取知识库的 VectorStore（可能为 null）
     */
    public VectorStore getVectorStore(String kbCode) {
        return vectorStoreCache.get(kbCode);
    }

    /**
     * 检查知识库是否存在
     */
    public boolean hasVectorStore(String kbCode) {
        return vectorStoreCache.containsKey(kbCode);
    }

    /**
     * 获取所有知识库 Code
     */
    public List<String> getAllKbCodes() {
        return vectorStoreCache.keySet().stream().toList();
    }

    /**
     * 创建 VectorStore 实例
     */
    private VectorStore createVectorStore(String kbCode) {
        String storeType = ragProperties.getVectorStore().getType();
        log.debug("[VectorStoreManager] 创建 VectorStore: kbCode={}, type={}", kbCode, storeType);

        switch (storeType.toLowerCase()) {
            case "milvus":
                return createMilvusVectorStore(kbCode);
            case "simple":
            default:
                return createSimpleVectorStore(kbCode);
        }
    }

    /**
     * 创建 SimpleVectorStore
     */
    private SimpleVectorStore createSimpleVectorStore(String kbCode) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // 尝试从文件加载
        String persistPath = ragProperties.getVectorStore().getPersistPath();
        if (persistPath != null && !persistPath.isEmpty()) {
            File storeFile = new File(persistPath, kbCode + ".json");
            if (storeFile.exists()) {
                try {
                    vectorStore.load(storeFile);
                    log.info("[VectorStoreManager] 从文件加载 VectorStore: {}", storeFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("[VectorStoreManager] 加载 VectorStore 失败: {}", e.getMessage());
                }
            }
        }

        return vectorStore;
    }

    /**
     * 创建 Milvus VectorStore
     *
     * TODO: 实现 Milvus 支持
     * 需要添加依赖：spring-ai-milvus-store
     *
     * 示例实现：
     * <pre>
     * MilvusServiceClient milvusClient = new MilvusServiceClient(
     *     ConnectParam.newBuilder()
     *         .withHost(ragProperties.getVectorStore().getMilvusHost())
     *         .withPort(ragProperties.getVectorStore().getMilvusPort())
     *         .build()
     * );
     *
     * String collectionName = ragProperties.getVectorStore().getMilvusCollectionPrefix() + kbCode;
     *
     * return MilvusVectorStore.builder(milvusClient, embeddingModel)
     *     .collectionName(collectionName)
     *     .databaseName(ragProperties.getVectorStore().getMilvusDatabase())
     *     .indexType(IndexType.valueOf(ragProperties.getVectorStore().getMilvusIndexType()))
     *     .metricType(MetricType.valueOf(ragProperties.getVectorStore().getMilvusMetricType()))
     *     .build();
     * </pre>
     */
    private VectorStore createMilvusVectorStore(String kbCode) {
        log.error("[VectorStoreManager] Milvus VectorStore 尚未实现，请添加 spring-ai-milvus-store 依赖并实现 createMilvusVectorStore 方法");
        throw new UnsupportedOperationException("Milvus VectorStore 尚未实现，请使用 type=simple 或实现 Milvus 支持");
    }

    /**
     * 持久化 VectorStore 到文件
     */
    private void persistVectorStore(String kbCode, VectorStore vectorStore) {
        String persistPath = ragProperties.getVectorStore().getPersistPath();
        if (persistPath == null || persistPath.isEmpty()) {
            return;
        }

        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            File dir = new File(persistPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File storeFile = new File(dir, kbCode + ".json");
            try {
                simpleStore.save(storeFile);
                log.info("[VectorStoreManager] 持久化 VectorStore: {}", storeFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("[VectorStoreManager] 持久化 VectorStore 失败: {}", e.getMessage(), e);
            }
        }
    }
}
