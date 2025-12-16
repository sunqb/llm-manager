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
 * 知识库实体
 *
 * 知识库是文档的容器，用于组织和管理知识文档。
 * 每个知识库有独立的向量存储空间，支持按知识库进行 RAG 检索。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_knowledge_bases", autoResultMap = true)
public class KnowledgeBase extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库唯一标识（32位UUID）
     */
    private String kbCode;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 知识库类型
     * - GENERAL: 通用知识库
     * - FAQ: FAQ 问答库
     * - PRODUCT: 产品知识库
     * - CUSTOM: 自定义类型
     */
    private String kbType;

    /**
     * Embedding 模型名称
     * 如 text-embedding-3-small
     */
    private String embeddingModel;

    /**
     * 向量维度
     */
    private Integer embeddingDimensions;

    /**
     * 关联的 Channel ID（用于 Embedding API 调用）
     */
    private Long channelId;

    /**
     * 文档数量（冗余字段，便于查询）
     */
    private Integer documentCount;

    /**
     * 向量数量（文档分割后的块数量）
     */
    private Integer vectorCount;

    /**
     * 是否公开（公开的知识库可被所有 Agent 使用）
     */
    private Boolean isPublic;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 元数据（可存储自定义配置）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 生成知识库唯一标识（32位无连字符的UUID）
     */
    public static String generateKbCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建通用知识库
     */
    public static KnowledgeBase createGeneral(String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbCode(generateKbCode());
        kb.setName(name);
        kb.setDescription(description);
        kb.setKbType("GENERAL");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setEmbeddingDimensions(1536);
        kb.setDocumentCount(0);
        kb.setVectorCount(0);
        kb.setIsPublic(false);
        kb.setEnabled(true);
        kb.setSortOrder(0);
        return kb;
    }

    /**
     * 创建 FAQ 知识库
     */
    public static KnowledgeBase createFaq(String name, String description) {
        KnowledgeBase kb = createGeneral(name, description);
        kb.setKbType("FAQ");
        return kb;
    }
}
