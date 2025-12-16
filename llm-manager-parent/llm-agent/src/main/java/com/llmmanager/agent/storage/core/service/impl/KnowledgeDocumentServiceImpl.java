package com.llmmanager.agent.storage.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.agent.storage.core.entity.KnowledgeDocument;
import com.llmmanager.agent.storage.core.mapper.KnowledgeDocumentMapper;
import com.llmmanager.agent.storage.core.service.KnowledgeBaseService;
import com.llmmanager.agent.storage.core.service.KnowledgeDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档 Service 实现
 * 仅在 llm.rag.enabled=true 时启用。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.rag.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public KnowledgeDocument getByDocCode(String docCode) {
        return baseMapper.selectByDocCode(docCode);
    }

    @Override
    public List<KnowledgeDocument> listByKbCode(String kbCode) {
        return baseMapper.selectByKbCode(kbCode);
    }

    @Override
    public List<KnowledgeDocument> listEnabledByKbCode(String kbCode) {
        return baseMapper.selectEnabledByKbCode(kbCode);
    }

    @Override
    public List<KnowledgeDocument> listPendingDocuments(int limit) {
        return baseMapper.selectPendingDocuments(limit);
    }

    @Override
    public KnowledgeDocument addTextDocument(String kbCode, String title, String content, Map<String, Object> metadata) {
        KnowledgeDocument doc = KnowledgeDocument.createText(kbCode, title, content);
        doc.setContentHash(computeHash(content));
        doc.setMetadata(metadata);

        save(doc);
        knowledgeBaseService.incrementDocumentCount(kbCode, 1);
        log.info("[KnowledgeDocument] 添加文本文档: {} -> {}", kbCode, title);
        return doc;
    }

    @Override
    public KnowledgeDocument addMarkdownDocument(String kbCode, String title, String content, Map<String, Object> metadata) {
        KnowledgeDocument doc = KnowledgeDocument.createMarkdown(kbCode, title, content);
        doc.setContentHash(computeHash(content));
        doc.setMetadata(metadata);

        save(doc);
        knowledgeBaseService.incrementDocumentCount(kbCode, 1);
        log.info("[KnowledgeDocument] 添加 Markdown 文档: {} -> {}", kbCode, title);
        return doc;
    }

    @Override
    public KnowledgeDocument addUrlDocument(String kbCode, String title, String sourceUrl, Map<String, Object> metadata) {
        KnowledgeDocument doc = KnowledgeDocument.createUrl(kbCode, title, sourceUrl);
        doc.setContentHash(computeHash(sourceUrl));
        doc.setMetadata(metadata);

        save(doc);
        knowledgeBaseService.incrementDocumentCount(kbCode, 1);
        log.info("[KnowledgeDocument] 添加 URL 文档: {} -> {}", kbCode, sourceUrl);
        return doc;
    }

    @Override
    public void updateStatus(String docCode, String status) {
        baseMapper.updateStatus(docCode, status);
    }

    @Override
    public void updateStatusWithError(String docCode, String status, String errorMessage) {
        baseMapper.updateStatusWithError(docCode, status, errorMessage);
    }

    @Override
    public void updateProcessResult(String docCode, int chunkCount, int charCount) {
        // 先获取旧的 chunk_count，用于计算增量
        KnowledgeDocument doc = getByDocCode(docCode);
        int oldChunkCount = (doc != null && doc.getChunkCount() != null) ? doc.getChunkCount() : 0;

        // 更新文档处理结果
        baseMapper.updateProcessResult(docCode, chunkCount, charCount);

        // 更新知识库向量数量（使用增量，避免重复处理时累加错误）
        if (doc != null) {
            int delta = chunkCount - oldChunkCount;
            if (delta != 0) {
                knowledgeBaseService.incrementVectorCount(doc.getKbCode(), delta);
            }
        }
    }

    @Override
    public boolean isDuplicate(String kbCode, String contentHash) {
        return baseMapper.selectByContentHash(kbCode, contentHash) != null;
    }

    /**
     * 计算内容的 SHA-256 哈希
     */
    private String computeHash(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("计算哈希失败", e);
            return null;
        }
    }
}
