package com.llmmanager.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/**
 * 多知识库文档检索器
 *
 * 支持从多个知识库中检索文档，解决单个 VectorStore 无法跨知识库检索的问题。
 *
 * 使用场景：
 * 1. 指定多个知识库进行检索
 * 2. 未指定知识库时，检索所有启用的知识库
 */
@Slf4j
public class MultiKbDocumentRetriever implements DocumentRetriever {

    private final VectorStoreManager vectorStoreManager;
    private final List<String> kbCodes;
    private final int topK;
    private final boolean searchAllEnabled;

    /**
     * 构造检索器（指定知识库）
     *
     * @param vectorStoreManager VectorStore 管理器
     * @param kbCodes 知识库 Code 列表
     * @param topK 返回文档数量
     */
    public MultiKbDocumentRetriever(VectorStoreManager vectorStoreManager,
                                    List<String> kbCodes,
                                    int topK) {
        this.vectorStoreManager = vectorStoreManager;
        this.kbCodes = kbCodes;
        this.topK = topK;
        this.searchAllEnabled = false;
    }

    /**
     * 构造检索器（搜索所有启用的知识库）
     *
     * @param vectorStoreManager VectorStore 管理器
     * @param topK 返回文档数量
     */
    public MultiKbDocumentRetriever(VectorStoreManager vectorStoreManager, int topK) {
        this.vectorStoreManager = vectorStoreManager;
        this.kbCodes = null;
        this.topK = topK;
        this.searchAllEnabled = true;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String queryText = query.text();
        log.debug("[MultiKbDocumentRetriever] 检索查询: {}, topK: {}, searchAllEnabled: {}",
                queryText, topK, searchAllEnabled);

        List<Document> results;
        if (searchAllEnabled) {
            // 搜索所有启用的知识库
            results = vectorStoreManager.similaritySearchAllEnabled(queryText, topK);
            log.debug("[MultiKbDocumentRetriever] 搜索所有知识库，返回 {} 个文档", results.size());
        } else if (kbCodes != null && !kbCodes.isEmpty()) {
            // 搜索指定的知识库
            results = vectorStoreManager.similaritySearchMultiple(kbCodes, queryText, topK);
            log.debug("[MultiKbDocumentRetriever] 搜索知识库 {}，返回 {} 个文档", kbCodes, results.size());
        } else {
            log.warn("[MultiKbDocumentRetriever] 没有指定知识库，返回空结果");
            results = List.of();
        }

        return results;
    }

    /**
     * 构建器
     */
    public static Builder builder(VectorStoreManager vectorStoreManager) {
        return new Builder(vectorStoreManager);
    }

    public static class Builder {
        private final VectorStoreManager vectorStoreManager;
        private List<String> kbCodes;
        private int topK = 5;
        private boolean searchAllEnabled = false;

        public Builder(VectorStoreManager vectorStoreManager) {
            this.vectorStoreManager = vectorStoreManager;
        }

        public Builder kbCodes(List<String> kbCodes) {
            this.kbCodes = kbCodes;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder searchAllEnabled(boolean searchAllEnabled) {
            this.searchAllEnabled = searchAllEnabled;
            return this;
        }

        public MultiKbDocumentRetriever build() {
            if (searchAllEnabled) {
                return new MultiKbDocumentRetriever(vectorStoreManager, topK);
            } else {
                return new MultiKbDocumentRetriever(vectorStoreManager, kbCodes, topK);
            }
        }
    }
}
