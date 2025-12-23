package com.llmmanager.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.rag.config.RagProperties;
import com.llmmanager.agent.rag.vectorstore.TidbVectorStore;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;
import com.llmmanager.agent.storage.core.service.KnowledgeBaseService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import io.milvus.param.index.DropIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[0-9a-zA-Z_]+$");

    @Resource
    private RagProperties ragProperties;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ObjectMapper objectMapper;

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

    /**
     * Milvus Client（type=milvus 时按需初始化，全局复用）
     */
    private volatile MilvusServiceClient milvusClient;

    private final Object milvusClientLock = new Object();

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

        // 关闭 Milvus Client
        if (milvusClient != null) {
            try {
                milvusClient.close();
            } catch (Exception e) {
                log.warn("[VectorStoreManager] 关闭 Milvus Client 失败: {}", e.getMessage());
            }
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
        // TiDB 全局 VectorStore 不按 kb_code 过滤，可直接跨库检索；其它实现回退为“搜索所有已加载知识库”
        if (!isTidbVectorStore()) {
            return similaritySearchAllEnabled(query, topK);
        }
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
                .sorted((d1, d2) -> Double.compare(resolveScore(d2), resolveScore(d1)))
                .limit(topK)
                .toList();
    }

    private static double resolveScore(Document document) {
        if (document == null) {
            return 0.0d;
        }
        Double score = document.getScore();
        if (score != null) {
            return score;
        }
        if (document.getMetadata() == null) {
            return 0.0d;
        }
        Object metaScore = document.getMetadata().get("score");
        if (metaScore instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0d;
    }

    /**
     * 删除知识库的 VectorStore
     */
    public void removeVectorStore(String kbCode) {
        if (isTidbVectorStore()) {
            deleteVectorsByKbCode(kbCode);
        } else if (isMilvusVectorStore()) {
            dropMilvusCollection(kbCode);
        }
        // SimpleVectorStore：删除持久化文件，避免下次启动重新加载已删除知识库的向量
        if (kbCode != null && vectorStoreCache.get(kbCode) instanceof SimpleVectorStore) {
            deletePersistedSimpleVectorStoreFile(kbCode);
        }
        vectorStoreCache.remove(kbCode);
        log.info("[VectorStoreManager] 删除知识库 VectorStore: {}", kbCode);
    }

    /**
     * 清空知识库的所有文档
     */
    public void clearVectorStore(String kbCode) {
        if (isTidbVectorStore()) {
            deleteVectorsByKbCode(kbCode);
            vectorStoreCache.remove(kbCode);
            log.info("[VectorStoreManager] 清空知识库（TiDB Vector Search）: {}", kbCode);
            return;
        }
        if (isMilvusVectorStore()) {
            dropMilvusCollection(kbCode);
            vectorStoreCache.remove(kbCode);
            log.info("[VectorStoreManager] 清空知识库（Milvus）: {}", kbCode);
            return;
        }
        // SimpleVectorStore：重新创建空实例（避免持久化场景下 clear 后又从文件 load 回来）
        SimpleVectorStore emptyStore = createSimpleVectorStore(kbCode, false);
        vectorStoreCache.put(kbCode, emptyStore);
        // 立即覆盖/清理持久化文件，避免下次启动仍加载旧数据
        deletePersistedSimpleVectorStoreFile(kbCode);
        persistVectorStore(kbCode, emptyStore);
        log.info("[VectorStoreManager] 清空知识库（SimpleVectorStore）: {}", kbCode);
    }

    /**
     * 删除指定文档的向量（用于避免重复入库）
     */
    public void deleteVectorsByDocCode(String kbCode, String docCode) {
        if (kbCode == null || docCode == null) {
            return;
        }
        if (isTidbVectorStore()) {
            String tableName = getSafeTidbTableName();
            String sql = "UPDATE " + tableName
                    + " SET is_delete=1, update_time=CURRENT_TIMESTAMP"
                    + " WHERE is_delete=0 AND kb_code=? AND doc_code=?";
            jdbcTemplate.update(sql, kbCode, docCode);
            return;
        }
        if (isMilvusVectorStore()) {
            VectorStore vectorStore = getOrCreateVectorStore(kbCode);
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            vectorStore.delete(builder.eq("docCode", docCode).build());
            return;
        }
        // SimpleVectorStore：不支持 filter delete，按 metadata.docCode 扫描并删除
        VectorStore vectorStore = getOrCreateVectorStore(kbCode);
        if (vectorStore instanceof SimpleVectorStore simpleStore) {
            int deleted = deleteByDocCodeFromSimpleVectorStore(simpleStore, docCode);
            if (deleted > 0) {
                persistVectorStore(kbCode, simpleStore);
            }
        }
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
            case "tidb":
                return createTidbVectorStore(kbCode);
            case "milvus":
                return createMilvusVectorStore(kbCode);
            case "simple":
            default:
                return createSimpleVectorStore(kbCode);
        }
    }

    private VectorStore createTidbVectorStore(String kbCode) {
        String tableName = getSafeTidbTableName();
        String kbFilter = "_global".equals(kbCode) ? null : kbCode;
        return new TidbVectorStore(tableName, kbFilter, jdbcTemplate, embeddingModel, objectMapper);
    }

    private boolean isTidbVectorStore() {
        String type = ragProperties.getVectorStore().getType();
        return type != null && "tidb".equalsIgnoreCase(type);
    }

    private boolean isMilvusVectorStore() {
        String type = ragProperties.getVectorStore().getType();
        return type != null && "milvus".equalsIgnoreCase(type);
    }

    private String buildMilvusCollectionName(String kbCode) {
        RagProperties.VectorStoreConfig cfg = ragProperties.getVectorStore();
        String collectionPrefix = StringUtils.hasText(cfg.getMilvusCollectionPrefix())
                ? cfg.getMilvusCollectionPrefix()
                : "llm_kb_";
        return collectionPrefix + kbCode;
    }

    private void dropMilvusCollection(String kbCode) {
        if (kbCode == null || kbCode.isBlank()) {
            return;
        }
        RagProperties.VectorStoreConfig cfg = ragProperties.getVectorStore();
        String collectionName = buildMilvusCollectionName(kbCode);

        MilvusServiceClient client = getOrCreateMilvusClient();

        try {
            Boolean exists = client.hasCollection(HasCollectionParam.newBuilder()
                            .withDatabaseName(cfg.getMilvusDatabase())
                            .withCollectionName(collectionName)
                            .build())
                    .getData();
            if (exists == null || !exists) {
                return;
            }
        } catch (Exception e) {
            log.warn("[VectorStoreManager] 检查 Milvus Collection 是否存在失败: {}，将继续尝试删除", e.getMessage());
        }

        try {
            client.releaseCollection(ReleaseCollectionParam.newBuilder()
                    .withDatabaseName(cfg.getMilvusDatabase())
                    .withCollectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.debug("[VectorStoreManager] releaseCollection 失败（可忽略）: {}", e.getMessage());
        }

        try {
            client.dropIndex(DropIndexParam.newBuilder()
                    .withDatabaseName(cfg.getMilvusDatabase())
                    .withCollectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.debug("[VectorStoreManager] dropIndex 失败（可忽略）: {}", e.getMessage());
        }

        try {
            client.dropCollection(DropCollectionParam.newBuilder()
                    .withDatabaseName(cfg.getMilvusDatabase())
                    .withCollectionName(collectionName)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("[VectorStoreManager] 删除 Milvus Collection 失败: " + collectionName, e);
        }
    }

    private void deleteVectorsByKbCode(String kbCode) {
        if (kbCode == null || kbCode.isBlank()) {
            return;
        }
        String tableName = getSafeTidbTableName();
        String sql = "UPDATE " + tableName
                + " SET is_delete=1, update_time=CURRENT_TIMESTAMP"
                + " WHERE is_delete=0 AND kb_code=?";
        jdbcTemplate.update(sql, kbCode);
    }

    private String getSafeTidbTableName() {
        String tableName = ragProperties.getVectorStore().getTidbTableName();
        if (tableName == null || tableName.isBlank() || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法 TiDB 向量表名: " + tableName);
        }
        return tableName;
    }

    private void deletePersistedSimpleVectorStoreFile(String kbCode) {
        if (!StringUtils.hasText(kbCode)) {
            return;
        }
        String persistPath = ragProperties.getVectorStore().getPersistPath();
        if (!StringUtils.hasText(persistPath)) {
            return;
        }
        try {
            File storeFile = new File(persistPath, kbCode + ".json");
            if (storeFile.exists() && !storeFile.delete()) {
                log.warn("[VectorStoreManager] 删除 SimpleVectorStore 持久化文件失败: {}", storeFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("[VectorStoreManager] 删除 SimpleVectorStore 持久化文件异常: {}", e.getMessage());
        }
    }

    private int deleteByDocCodeFromSimpleVectorStore(SimpleVectorStore simpleStore, String docCode) {
        if (simpleStore == null || !StringUtils.hasText(docCode)) {
            return 0;
        }
        try {
            Field storeField = SimpleVectorStore.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object storeObj = storeField.get(simpleStore);
            if (!(storeObj instanceof Map<?, ?> storeMap) || storeMap.isEmpty()) {
                return 0;
            }

            List<String> idsToDelete = new ArrayList<>();
            // 避免迭代过程中被修改导致异常，先做快照
            List<?> valuesSnapshot = new ArrayList<>(storeMap.values());
            for (Object v : valuesSnapshot) {
                if (!(v instanceof SimpleVectorStoreContent content)) {
                    continue;
                }
                Map<String, Object> metadata = content.getMetadata();
                Object metaDocCode = metadata != null ? metadata.get("docCode") : null;
                if (metaDocCode != null && docCode.equals(String.valueOf(metaDocCode))) {
                    idsToDelete.add(content.getId());
                }
            }
            if (idsToDelete.isEmpty()) {
                return 0;
            }
            simpleStore.delete(idsToDelete);
            return idsToDelete.size();
        } catch (Exception e) {
            log.warn("[VectorStoreManager] SimpleVectorStore 按 docCode 删除失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 创建 SimpleVectorStore
     */
    private SimpleVectorStore createSimpleVectorStore(String kbCode) {
        return createSimpleVectorStore(kbCode, true);
    }

    private SimpleVectorStore createSimpleVectorStore(String kbCode, boolean loadFromDisk) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        // 尝试从文件加载
        if (!loadFromDisk) {
            return vectorStore;
        }
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
     * 基于 spring-ai-milvus-store，自动初始化 collection/schema/index（initializeSchema=true）。
     */
    private VectorStore createMilvusVectorStore(String kbCode) {
        RagProperties.VectorStoreConfig cfg = ragProperties.getVectorStore();
        String collectionName = buildMilvusCollectionName(kbCode);

        MilvusServiceClient client = getOrCreateMilvusClient();

        IndexType indexType = parseMilvusIndexType(cfg.getMilvusIndexType());
        MetricType metricType = parseMilvusMetricType(cfg.getMilvusMetricType());

        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(client, embeddingModel)
                .databaseName(cfg.getMilvusDatabase())
                .collectionName(collectionName)
                .indexType(indexType)
                .metricType(metricType)
                .indexParameters(defaultMilvusIndexParameters(indexType))
                .initializeSchema(true);

        int embeddingDimension = resolveEmbeddingDimension(kbCode);
        if (embeddingDimension > 0) {
            builder.embeddingDimension(embeddingDimension);
        }

        MilvusVectorStore store = builder.build();
        try {
            // 该对象不是 Spring Bean，不会自动触发 afterPropertiesSet
            store.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("[VectorStoreManager] 初始化 Milvus Collection 失败: " + collectionName, e);
        }
        return store;
    }

    private MilvusServiceClient getOrCreateMilvusClient() {
        MilvusServiceClient client = milvusClient;
        if (client != null) {
            return client;
        }
        synchronized (milvusClientLock) {
            client = milvusClient;
            if (client != null) {
                return client;
            }

            RagProperties.VectorStoreConfig cfg = ragProperties.getVectorStore();
            ConnectParam.Builder builder = ConnectParam.newBuilder()
                    .withHost(cfg.getMilvusHost())
                    .withPort(cfg.getMilvusPort() != null ? cfg.getMilvusPort() : 19530)
                    .withDatabaseName(cfg.getMilvusDatabase());

            if (StringUtils.hasText(cfg.getMilvusUsername()) && StringUtils.hasText(cfg.getMilvusPassword())) {
                builder.withAuthorization(cfg.getMilvusUsername(), cfg.getMilvusPassword());
            }

            milvusClient = new MilvusServiceClient(builder.build());
            return milvusClient;
        }
    }

    private IndexType parseMilvusIndexType(String value) {
        if (!StringUtils.hasText(value)) {
            return IndexType.IVF_FLAT;
        }
        String v = value.trim();
        for (IndexType t : IndexType.values()) {
            if (t.name().equalsIgnoreCase(v)) {
                return t;
            }
        }
        log.warn("[VectorStoreManager] 非法 Milvus IndexType: {}，将使用默认 IVF_FLAT", value);
        return IndexType.IVF_FLAT;
    }

    private MetricType parseMilvusMetricType(String value) {
        if (!StringUtils.hasText(value)) {
            return MetricType.COSINE;
        }
        String v = value.trim();
        MetricType parsed = null;
        for (MetricType t : MetricType.values()) {
            if (t.name().equalsIgnoreCase(v)) {
                parsed = t;
                break;
            }
        }
        if (parsed == null) {
            log.warn("[VectorStoreManager] 非法 Milvus MetricType: {}，将使用默认 COSINE", value);
            return MetricType.COSINE;
        }
        if (parsed != MetricType.IP && parsed != MetricType.L2 && parsed != MetricType.COSINE) {
            log.warn("[VectorStoreManager] Milvus MetricType {} 不受支持（仅支持 IP/L2/COSINE），将使用 COSINE", parsed);
            return MetricType.COSINE;
        }
        return parsed;
    }

    private String defaultMilvusIndexParameters(IndexType indexType) {
        if (indexType == null) {
            return "{\"nlist\":1024}";
        }
        return switch (indexType) {
            case AUTOINDEX, FLAT, None -> "{}";
            case HNSW, HNSW_SQ, HNSW_PQ, HNSW_PRQ -> "{\"M\":16,\"efConstruction\":200}";
            case IVF_PQ -> "{\"nlist\":1024,\"m\":16,\"nbits\":8}";
            case IVF_FLAT, IVF_SQ8, GPU_IVF_FLAT, GPU_IVF_PQ, BIN_IVF_FLAT -> "{\"nlist\":1024}";
            default -> "{}";
        };
    }

    private int resolveEmbeddingDimension(String kbCode) {
        if (kbCode != null && !kbCode.isBlank() && !"_global".equals(kbCode)) {
            try {
                KnowledgeBase kb = knowledgeBaseService.getByKbCode(kbCode);
                if (kb != null && kb.getEmbeddingDimensions() != null && kb.getEmbeddingDimensions() > 0) {
                    return kb.getEmbeddingDimensions();
                }
            } catch (Exception e) {
                // 数据库不可用时忽略，回退到 EmbeddingModel.dimensions()
                log.debug("[VectorStoreManager] 获取知识库 embeddingDimensions 失败，将使用 EmbeddingModel: {}", e.getMessage());
            }
        }
        try {
            return embeddingModel.dimensions();
        } catch (Exception e) {
            return -1;
        }
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
