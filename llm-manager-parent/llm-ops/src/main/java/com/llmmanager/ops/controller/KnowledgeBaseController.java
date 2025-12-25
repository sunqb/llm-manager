package com.llmmanager.ops.controller;

import com.llmmanager.agent.rag.DocumentProcessor;
import com.llmmanager.agent.rag.VectorStoreManager;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;
import com.llmmanager.agent.storage.core.entity.KnowledgeDocument;
import com.llmmanager.agent.storage.core.service.KnowledgeBaseService;
import com.llmmanager.agent.storage.core.service.KnowledgeDocumentService;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller
 *
 * 提供知识库和文档的 CRUD 操作，以及文档处理和检索功能。
 * 仅在 llm.rag.enabled=true 时启用。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeDocumentService documentService;

    @Resource
    private VectorStoreManager vectorStoreManager;

    @Resource
    private DocumentProcessor documentProcessor;

    // ==================== 知识库 CRUD ====================

    /**
     * 获取所有知识库
     */
    @GetMapping
    public Result<List<KnowledgeBase>> getAllKnowledgeBases() {
        return Result.success(knowledgeBaseService.list());
    }

    /**
     * 获取已启用的知识库
     */
    @GetMapping("/enabled")
    public Result<List<KnowledgeBase>> getEnabledKnowledgeBases() {
        return Result.success(knowledgeBaseService.listAllEnabled());
    }

    /**
     * 获取公开的知识库
     */
    @GetMapping("/public")
    public Result<List<KnowledgeBase>> getPublicKnowledgeBases() {
        return Result.success(knowledgeBaseService.listAllPublic());
    }

    /**
     * 根据 ID 获取知识库
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getKnowledgeBaseById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + id);
        }
        return Result.success(kb);
    }

    /**
     * 根据 kbCode 获取知识库
     */
    @GetMapping("/code/{kbCode}")
    public Result<KnowledgeBase> getKnowledgeBaseByCode(@PathVariable String kbCode) {
        KnowledgeBase kb = knowledgeBaseService.getByKbCode(kbCode);
        if (kb == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbCode);
        }
        return Result.success(kb);
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.create(request.name, request.description, request.kbType));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public Result<KnowledgeBase> updateKnowledgeBase(@PathVariable Long id, @RequestBody KnowledgeBase updated) {
        KnowledgeBase existing = knowledgeBaseService.getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + id);
        }
        updated.setId(id);
        updated.setKbCode(existing.getKbCode());
        knowledgeBaseService.updateById(updated);
        return Result.success(knowledgeBaseService.getById(id));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + id);
        }
        // 删除关联的 VectorStore
        vectorStoreManager.removeVectorStore(kb.getKbCode());
        knowledgeBaseService.removeById(id);
        return Result.success();
    }

    // ==================== 文档管理 ====================

    /**
     * 获取知识库的所有文档
     */
    @GetMapping("/{kbCode}/documents")
    public Result<List<KnowledgeDocument>> getDocuments(@PathVariable String kbCode) {
        return Result.success(documentService.listByKbCode(kbCode));
    }

    /**
     * 添加文本文档
     */
    @PostMapping("/{kbCode}/documents/text")
    public Result<KnowledgeDocument> addTextDocument(@PathVariable String kbCode,
                                                      @RequestBody AddTextDocumentRequest request) {
        if (knowledgeBaseService.getByKbCode(kbCode) == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbCode);
        }

        KnowledgeDocument doc = documentService.addTextDocument(
                kbCode, request.title, request.content, request.metadata);

        if (Boolean.TRUE.equals(request.processNow)) {
            documentProcessor.processDocument(doc);
        }

        return Result.success(doc);
    }

    /**
     * 添加 Markdown 文档
     */
    @PostMapping("/{kbCode}/documents/markdown")
    public Result<KnowledgeDocument> addMarkdownDocument(@PathVariable String kbCode,
                                                          @RequestBody AddTextDocumentRequest request) {
        if (knowledgeBaseService.getByKbCode(kbCode) == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbCode);
        }

        KnowledgeDocument doc = documentService.addMarkdownDocument(
                kbCode, request.title, request.content, request.metadata);

        if (Boolean.TRUE.equals(request.processNow)) {
            documentProcessor.processDocument(doc);
        }

        return Result.success(doc);
    }

    /**
     * 添加 URL 文档
     */
    @PostMapping("/{kbCode}/documents/url")
    public Result<KnowledgeDocument> addUrlDocument(@PathVariable String kbCode,
                                                     @RequestBody AddUrlDocumentRequest request) {
        if (knowledgeBaseService.getByKbCode(kbCode) == null) {
            throw new BusinessException(ResultCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbCode);
        }

        return Result.success(documentService.addUrlDocument(kbCode, request.title, request.sourceUrl, request.metadata));
    }

    /**
     * 处理文档（执行分割和向量化）
     */
    @PostMapping("/documents/{docCode}/process")
    public Result<Map<String, Object>> processDocument(@PathVariable String docCode) {
        KnowledgeDocument doc = documentService.getByDocCode(docCode);
        if (doc == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在: " + docCode);
        }

        try {
            int chunkCount = documentProcessor.processDocument(doc);
            Map<String, Object> result = new HashMap<>();
            result.put("chunkCount", chunkCount);
            return Result.success(result, "处理成功，生成 " + chunkCount + " 个文本块");
        } catch (Exception e) {
            log.error("[KnowledgeBase] 处理文档失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.DOCUMENT_PROCESS_FAILED, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 批量处理待处理的文档
     */
    @PostMapping("/documents/process-pending")
    public Result<Map<String, Object>> processPendingDocuments(
            @RequestParam(defaultValue = "10") int limit) {
        int successCount = documentProcessor.processPendingDocuments(limit);
        Map<String, Object> result = new HashMap<>();
        result.put("processedCount", successCount);
        return Result.success(result, "处理完成，成功 " + successCount + " 个");
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{docCode}")
    public Result<Void> deleteDocument(@PathVariable String docCode) {
        KnowledgeDocument doc = documentService.getByDocCode(docCode);
        if (doc == null) {
            throw new BusinessException(ResultCode.DOCUMENT_NOT_FOUND, "文档不存在: " + docCode);
        }
        vectorStoreManager.deleteVectorsByDocCode(doc.getKbCode(), docCode);
        documentService.removeById(doc.getId());
        return Result.success();
    }

    // ==================== 检索功能 ====================

    /**
     * 在知识库中检索相似文档
     */
    @PostMapping("/{kbCode}/search")
    public Result<List<Map<String, Object>>> searchDocuments(
            @PathVariable String kbCode,
            @RequestBody SearchRequest request) {

        List<Document> documents = vectorStoreManager.similaritySearch(
                kbCode, request.query, request.topK != null ? request.topK : 5);

        List<Map<String, Object>> results = documents.stream()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", doc.getText());
                    result.put("metadata", doc.getMetadata());
                    return result;
                })
                .toList();

        return Result.success(results);
    }

    /**
     * 在全局知识库中检索
     */
    @PostMapping("/global/search")
    public Result<List<Map<String, Object>>> searchGlobalDocuments(@RequestBody SearchRequest request) {
        List<Document> documents = vectorStoreManager.similaritySearchGlobal(
                request.query, request.topK != null ? request.topK : 5);

        List<Map<String, Object>> results = documents.stream()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", doc.getText());
                    result.put("metadata", doc.getMetadata());
                    return result;
                })
                .toList();

        return Result.success(results);
    }

    // ==================== 知识库操作 ====================

    /**
     * 清空知识库
     */
    @PostMapping("/{kbCode}/clear")
    public Result<Void> clearKnowledgeBase(@PathVariable String kbCode) {
        vectorStoreManager.clearVectorStore(kbCode);
        knowledgeBaseService.updateStatistics(kbCode);
        return Result.success(null, "知识库已清空");
    }

    /**
     * 更新知识库统计信息
     */
    @PostMapping("/{kbCode}/refresh-stats")
    public Result<KnowledgeBase> refreshStats(@PathVariable String kbCode) {
        knowledgeBaseService.updateStatistics(kbCode);
        return Result.success(knowledgeBaseService.getByKbCode(kbCode));
    }

    // ==================== 请求 DTO ====================

    public record CreateKnowledgeBaseRequest(
            String name,
            String description,
            String kbType
    ) {}

    public record AddTextDocumentRequest(
            String title,
            String content,
            Map<String, Object> metadata,
            Boolean processNow
    ) {}

    public record AddUrlDocumentRequest(
            String title,
            String sourceUrl,
            Map<String, Object> metadata
    ) {}

    public record SearchRequest(
            String query,
            Integer topK
    ) {}
}
