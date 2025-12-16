package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.llmmanager.agent.storage.core.entity.KnowledgeDocument;

import java.util.List;
import java.util.Map;

/**
 * 知识库文档 Service 接口
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    /**
     * 根据文档 Code 查询
     */
    KnowledgeDocument getByDocCode(String docCode);

    /**
     * 根据知识库 Code 查询所有文档
     */
    List<KnowledgeDocument> listByKbCode(String kbCode);

    /**
     * 根据知识库 Code 查询所有启用的文档
     */
    List<KnowledgeDocument> listEnabledByKbCode(String kbCode);

    /**
     * 查询待处理的文档
     */
    List<KnowledgeDocument> listPendingDocuments(int limit);

    /**
     * 添加文本文档
     */
    KnowledgeDocument addTextDocument(String kbCode, String title, String content, Map<String, Object> metadata);

    /**
     * 添加 Markdown 文档
     */
    KnowledgeDocument addMarkdownDocument(String kbCode, String title, String content, Map<String, Object> metadata);

    /**
     * 添加 URL 文档
     */
    KnowledgeDocument addUrlDocument(String kbCode, String title, String sourceUrl, Map<String, Object> metadata);

    /**
     * 更新文档状态
     */
    void updateStatus(String docCode, String status);

    /**
     * 更新文档状态和错误信息
     */
    void updateStatusWithError(String docCode, String status, String errorMessage);

    /**
     * 更新文档处理结果
     */
    void updateProcessResult(String docCode, int chunkCount, int charCount);

    /**
     * 检查文档是否重复（基于内容哈希）
     */
    boolean isDuplicate(String kbCode, String contentHash);
}
