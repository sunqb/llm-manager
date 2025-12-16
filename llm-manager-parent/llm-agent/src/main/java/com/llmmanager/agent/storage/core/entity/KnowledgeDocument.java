package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * 知识库文档实体
 *
 * 存储上传到知识库的原始文档信息。
 * 文档会被分割成多个文本块（chunks），每个块生成向量存储到 VectorStore。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_knowledge_documents", autoResultMap = true)
public class KnowledgeDocument extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档唯一标识（32位UUID）
     */
    private String docCode;

    /**
     * 关联的知识库 Code
     */
    private String kbCode;

    /**
     * 文档标题/名称
     */
    private String title;

    /**
     * 文档类型
     * - TEXT: 纯文本
     * - MARKDOWN: Markdown 文档
     * - PDF: PDF 文件
     * - DOCX: Word 文档
     * - HTML: HTML 页面
     * - JSON: JSON 数据
     * - URL: 网页链接
     */
    private String docType;

    /**
     * 原始文件名（上传时的文件名）
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件 MIME 类型
     */
    private String mimeType;

    /**
     * 文件存储路径（本地路径或 OSS URL）
     */
    private String filePath;

    /**
     * 原始内容（小文件或文本内容直接存储）
     */
    private String content;

    /**
     * 内容哈希（用于去重）
     */
    private String contentHash;

    /**
     * 处理状态
     * - PENDING: 待处理
     * - PROCESSING: 处理中
     * - COMPLETED: 处理完成
     * - FAILED: 处理失败
     */
    private String status;

    /**
     * 错误信息（status=FAILED 时）
     */
    private String errorMessage;

    /**
     * 分割后的块数量
     */
    private Integer chunkCount;

    /**
     * 字符数
     */
    private Integer charCount;

    /**
     * 文档来源 URL（docType=URL 时）
     */
    private String sourceUrl;

    /**
     * 文档元数据（可用于过滤检索）
     * 如：{"author": "张三", "category": "技术文档", "tags": ["AI", "RAG"]}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 是否启用（禁用后不参与检索）
     */
    private Boolean enabled;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 生成文档唯一标识（32位无连字符的UUID）
     */
    public static String generateDocCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建文本文档
     */
    public static KnowledgeDocument createText(String kbCode, String title, String content) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDocCode(generateDocCode());
        doc.setKbCode(kbCode);
        doc.setTitle(title);
        doc.setDocType("TEXT");
        doc.setContent(content);
        doc.setStatus("PENDING");
        doc.setChunkCount(0);
        doc.setCharCount(content != null ? content.length() : 0);
        doc.setEnabled(true);
        doc.setSortOrder(0);
        return doc;
    }

    /**
     * 创建 Markdown 文档
     */
    public static KnowledgeDocument createMarkdown(String kbCode, String title, String content) {
        KnowledgeDocument doc = createText(kbCode, title, content);
        doc.setDocType("MARKDOWN");
        return doc;
    }

    /**
     * 创建 URL 文档
     */
    public static KnowledgeDocument createUrl(String kbCode, String title, String sourceUrl) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDocCode(generateDocCode());
        doc.setKbCode(kbCode);
        doc.setTitle(title);
        doc.setDocType("URL");
        doc.setSourceUrl(sourceUrl);
        doc.setStatus("PENDING");
        doc.setChunkCount(0);
        doc.setEnabled(true);
        doc.setSortOrder(0);
        return doc;
    }

    /**
     * 创建文件文档
     */
    public static KnowledgeDocument createFile(String kbCode, String fileName, String filePath,
                                                String mimeType, Long fileSize) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setDocCode(generateDocCode());
        doc.setKbCode(kbCode);
        doc.setTitle(fileName);
        doc.setFileName(fileName);
        doc.setFilePath(filePath);
        doc.setMimeType(mimeType);
        doc.setFileSize(fileSize);
        doc.setDocType(determineDocType(mimeType, fileName));
        doc.setStatus("PENDING");
        doc.setChunkCount(0);
        doc.setEnabled(true);
        doc.setSortOrder(0);
        return doc;
    }

    /**
     * 根据 MIME 类型和文件名确定文档类型
     */
    private static String determineDocType(String mimeType, String fileName) {
        if (mimeType != null) {
            if (mimeType.contains("pdf")) {
                return "PDF";
            } else if (mimeType.contains("word") || mimeType.contains("docx")) {
                return "DOCX";
            } else if (mimeType.contains("html")) {
                return "HTML";
            } else if (mimeType.contains("json")) {
                return "JSON";
            } else if (mimeType.contains("markdown")) {
                return "MARKDOWN";
            }
        }
        if (fileName != null) {
            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".pdf")) {
                return "PDF";
            } else if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
                return "DOCX";
            } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
                return "HTML";
            } else if (lowerName.endsWith(".json")) {
                return "JSON";
            } else if (lowerName.endsWith(".md")) {
                return "MARKDOWN";
            }
        }
        return "TEXT";
    }

    /**
     * 是否处理完成
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * 是否处理失败
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * 是否待处理
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
