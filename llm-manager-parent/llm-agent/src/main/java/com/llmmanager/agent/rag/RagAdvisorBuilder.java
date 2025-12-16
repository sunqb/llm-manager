package com.llmmanager.agent.rag;

import com.llmmanager.agent.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * RAG Advisor 构建器
 *
 * 用于构建 RAG Advisor，支持：
 * 1. 单个知识库检索
 * 2. 多个知识库检索
 * 3. 元数据过滤
 * 4. 自定义相似度阈值和 topK
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class RagAdvisorBuilder {

    @Resource
    private VectorStoreManager vectorStoreManager;

    @Resource
    private RagProperties ragProperties;

    /**
     * 为指定知识库创建 RAG Advisor
     */
    public Advisor buildAdvisor(String kbCode) {
        return buildAdvisor(kbCode, null, null, null);
    }

    /**
     * 为指定知识库创建 RAG Advisor，自定义 topK
     */
    public Advisor buildAdvisor(String kbCode, Integer topK) {
        return buildAdvisor(kbCode, topK, null, null);
    }

    /**
     * 为指定知识库创建 RAG Advisor，自定义 topK 和 similarityThreshold
     */
    public Advisor buildAdvisor(String kbCode, Integer topK, Double similarityThreshold) {
        return buildAdvisor(kbCode, topK, similarityThreshold, null);
    }

    /**
     * 为指定知识库创建 RAG Advisor，支持自定义参数
     *
     * @param kbCode 知识库 Code
     * @param topK 返回文档数量（null 使用默认值）
     * @param similarityThreshold 相似度阈值（null 使用默认值）
     * @param filterExpression 过滤表达式（null 不过滤）
     */
    public Advisor buildAdvisor(String kbCode, Integer topK, Double similarityThreshold,
                                Supplier<org.springframework.ai.vectorstore.filter.Filter.Expression> filterExpression) {

        VectorStore vectorStore = vectorStoreManager.getOrCreateVectorStore(kbCode);
        return buildAdvisorInternal(vectorStore, topK, similarityThreshold, filterExpression);
    }

    /**
     * 为全局知识库创建 RAG Advisor
     *
     * 注意：此方法现在会搜索所有启用的知识库，而非使用空的全局 VectorStore。
     */
    public Advisor buildGlobalAdvisor() {
        return buildGlobalAdvisor(null, null);
    }

    /**
     * 为全局知识库创建 RAG Advisor，支持自定义参数
     *
     * 注意：此方法使用 MultiKbDocumentRetriever 搜索所有启用的知识库。
     */
    public Advisor buildGlobalAdvisor(Integer topK,
                                      Supplier<org.springframework.ai.vectorstore.filter.Filter.Expression> filterExpression) {
        int actualTopK = topK != null ? topK : ragProperties.getVectorStore().getTopK();

        // 使用 MultiKbDocumentRetriever 搜索所有启用的知识库
        MultiKbDocumentRetriever retriever = MultiKbDocumentRetriever.builder(vectorStoreManager)
                .searchAllEnabled(true)
                .topK(actualTopK)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }

    /**
     * 为多个知识库创建 RAG Advisor
     *
     * 使用 MultiKbDocumentRetriever 正确地从多个独立的 VectorStore 中检索并合并结果。
     */
    public Advisor buildMultiKbAdvisor(List<String> kbCodes, Integer topK) {
        if (kbCodes == null || kbCodes.isEmpty()) {
            return buildGlobalAdvisor(topK, null);
        }

        int actualTopK = topK != null ? topK : ragProperties.getVectorStore().getTopK();

        // 使用 MultiKbDocumentRetriever 检索多个知识库
        MultiKbDocumentRetriever retriever = MultiKbDocumentRetriever.builder(vectorStoreManager)
                .kbCodes(kbCodes)
                .topK(actualTopK)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }

    /**
     * 使用元数据过滤创建 RAG Advisor
     *
     * @param kbCode 知识库 Code
     * @param metadata 过滤条件（key-value 精确匹配）
     */
    public Advisor buildAdvisorWithMetadata(String kbCode, Map<String, Object> metadata) {
        VectorStore vectorStore = vectorStoreManager.getOrCreateVectorStore(kbCode);

        Supplier<org.springframework.ai.vectorstore.filter.Filter.Expression> filter = null;
        if (metadata != null && !metadata.isEmpty()) {
            filter = () -> {
                FilterExpressionBuilder builder = new FilterExpressionBuilder();
                org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op op = null;

                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    FilterExpressionBuilder.Op currentOp = builder.eq(entry.getKey(), entry.getValue());
                    if (op == null) {
                        op = currentOp;
                    } else {
                        op = builder.and(op, currentOp);
                    }
                }

                return op != null ? op.build() : null;
            };
        }

        return buildAdvisorInternal(vectorStore, null, null, filter);
    }

    /**
     * 内部构建方法
     *
     * @param vectorStore VectorStore 实例
     * @param topK 返回文档数量（null 使用默认值）
     * @param similarityThreshold 相似度阈值（null 使用默认值）
     * @param filterExpression 过滤表达式（null 不过滤）
     */
    private Advisor buildAdvisorInternal(VectorStore vectorStore, Integer topK, Double similarityThreshold,
                                         Supplier<org.springframework.ai.vectorstore.filter.Filter.Expression> filterExpression) {

        int actualTopK = topK != null ? topK : ragProperties.getVectorStore().getTopK();
        double actualThreshold = similarityThreshold != null ? similarityThreshold : ragProperties.getVectorStore().getSimilarityThreshold();

        // 构建文档检索器
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(actualTopK)
                .similarityThreshold(actualThreshold);

        if (filterExpression != null) {
            retrieverBuilder.filterExpression(filterExpression);
        }

        VectorStoreDocumentRetriever retriever = retrieverBuilder.build();

        // 构建 RAG Advisor
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }

    /**
     * 检查 RAG 是否启用
     */
    public boolean isRagEnabled() {
        return Boolean.TRUE.equals(ragProperties.getEnabled());
    }
}
