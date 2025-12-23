package com.llmmanager.agent.rag;

import com.llmmanager.agent.rag.config.RagProperties;
import com.llmmanager.agent.storage.core.entity.KnowledgeDocument;
import com.llmmanager.agent.storage.core.service.KnowledgeDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务
 *
 * 负责文档的解析、分割和向量化：
 * 1. 解析不同格式的文档（文本、Markdown、PDF 等）
 * 2. 使用 TextSplitter 将文档分割成小块
 * 3. 将分割后的文档块添加到 VectorStore
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class DocumentProcessor {

    @Resource
    private RagProperties ragProperties;

    @Resource
    private VectorStoreManager vectorStoreManager;

    @Resource
    private KnowledgeDocumentService documentService;

    /**
     * 处理知识库文档
     *
     * @param knowledgeDocument 知识库文档实体
     * @return 处理生成的文档块数量
     */
    public int processDocument(KnowledgeDocument knowledgeDocument) {
        String docCode = knowledgeDocument.getDocCode();
        String kbCode = knowledgeDocument.getKbCode();

        try {
            // 更新状态为处理中
            documentService.updateStatus(docCode, "PROCESSING");

            // 获取文档内容
            String content = getDocumentContent(knowledgeDocument);
            if (content == null || content.isBlank()) {
                documentService.updateStatusWithError(docCode, "FAILED", "文档内容为空");
                return 0;
            }

            // 分割文档
            List<Document> chunks = splitDocument(content, knowledgeDocument);
            if (chunks.isEmpty()) {
                documentService.updateStatusWithError(docCode, "FAILED", "文档分割后无有效内容");
                return 0;
            }

            // 重新处理时先清理旧向量，避免重复入库
            vectorStoreManager.deleteVectorsByDocCode(kbCode, docCode);

            // 添加到 VectorStore
            vectorStoreManager.addDocuments(kbCode, chunks);

            // 更新处理结果
            documentService.updateProcessResult(docCode, chunks.size(), content.length());

            log.info("[DocumentProcessor] 处理文档成功: docCode={}, chunks={}", docCode, chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("[DocumentProcessor] 处理文档失败: docCode={}", docCode, e);
            documentService.updateStatusWithError(docCode, "FAILED", e.getMessage());
            return 0;
        }
    }

    /**
     * 处理文本内容（不存储到数据库）
     *
     * @param kbCode 知识库 Code
     * @param content 文本内容
     * @param metadata 元数据
     * @return 处理生成的文档块数量
     */
    public int processContent(String kbCode, String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return 0;
        }

        try {
            List<Document> chunks = splitContent(content, metadata);
            if (chunks.isEmpty()) {
                return 0;
            }

            vectorStoreManager.addDocuments(kbCode, chunks);
            log.info("[DocumentProcessor] 处理内容成功: kbCode={}, chunks={}", kbCode, chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("[DocumentProcessor] 处理内容失败: kbCode={}", kbCode, e);
            return 0;
        }
    }

    /**
     * 批量处理待处理的文档
     *
     * @param limit 处理数量限制
     * @return 成功处理的文档数量
     */
    public int processPendingDocuments(int limit) {
        List<KnowledgeDocument> pendingDocs = documentService.listPendingDocuments(limit);
        int successCount = 0;

        for (KnowledgeDocument doc : pendingDocs) {
            int chunkCount = processDocument(doc);
            if (chunkCount > 0) {
                successCount++;
            }
        }

        log.info("[DocumentProcessor] 批量处理完成: 成功={}/{}", successCount, pendingDocs.size());
        return successCount;
    }

    /**
     * 获取文档内容
     */
    private String getDocumentContent(KnowledgeDocument doc) {
        String docType = doc.getDocType();

        switch (docType) {
            case "TEXT":
            case "MARKDOWN":
            case "JSON":
                // 直接返回存储的内容
                return doc.getContent();

            case "URL":
                // TODO: 实现网页内容抓取
                log.warn("[DocumentProcessor] URL 文档解析暂未实现: {}", doc.getSourceUrl());
                return null;

            case "PDF":
            case "DOCX":
            case "HTML":
                // TODO: 使用 Tika 解析文件
                log.warn("[DocumentProcessor] 文件解析暂未实现: {}", doc.getFilePath());
                return null;

            default:
                return doc.getContent();
        }
    }

    /**
     * 分割文档内容
     */
    private List<Document> splitDocument(String content, KnowledgeDocument knowledgeDoc) {
        // 构建文档元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("docCode", knowledgeDoc.getDocCode());
        metadata.put("kbCode", knowledgeDoc.getKbCode());
        metadata.put("title", knowledgeDoc.getTitle());
        metadata.put("docType", knowledgeDoc.getDocType());

        if (knowledgeDoc.getMetadata() != null) {
            metadata.putAll(knowledgeDoc.getMetadata());
        }

        return splitContent(content, metadata);
    }

    /**
     * 分割文本内容
     */
    private List<Document> splitContent(String content, Map<String, Object> metadata) {
        // 创建文档
        Document document = new Document(content, metadata != null ? metadata : new HashMap<>());

        // 创建分割器
        RagProperties.SplitterConfig splitterConfig = ragProperties.getSplitter();
        TokenTextSplitter splitter = new TokenTextSplitter(
                splitterConfig.getChunkSize(),      // 默认块大小
                splitterConfig.getChunkOverlap(),   // 重叠大小
                splitterConfig.getMinChunkSize(),   // 最小块大小
                10000,                              // 最大块数
                true                                // 保留分隔符
        );

        // 分割并过滤空块
        return splitter.split(document).stream()
                .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                .toList();
    }

    /**
     * 快速添加文本到知识库（不分割，直接添加）
     */
    public void addTextDirectly(String kbCode, String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata != null ? metadata : new HashMap<>());
        vectorStoreManager.addDocuments(kbCode, List.of(document));
    }

    /**
     * 快速添加多个文本到知识库
     */
    public void addTextsDirectly(String kbCode, List<String> contents, Map<String, Object> sharedMetadata) {
        List<Document> documents = contents.stream()
                .filter(content -> content != null && !content.isBlank())
                .map(content -> new Document(content, sharedMetadata != null ? new HashMap<>(sharedMetadata) : new HashMap<>()))
                .toList();

        if (!documents.isEmpty()) {
            vectorStoreManager.addDocuments(kbCode, documents);
        }
    }
}
