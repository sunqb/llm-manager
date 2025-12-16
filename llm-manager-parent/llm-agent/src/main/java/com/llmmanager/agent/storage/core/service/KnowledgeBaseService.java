package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.llmmanager.agent.storage.core.entity.KnowledgeBase;

import java.util.List;

/**
 * 知识库 Service 接口
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 根据知识库 Code 查询
     */
    KnowledgeBase getByKbCode(String kbCode);

    /**
     * 查询所有启用的知识库
     */
    List<KnowledgeBase> listAllEnabled();

    /**
     * 查询所有公开的知识库
     */
    List<KnowledgeBase> listAllPublic();

    /**
     * 创建知识库
     */
    KnowledgeBase create(String name, String description, String kbType);

    /**
     * 更新知识库统计信息
     */
    void updateStatistics(String kbCode);

    /**
     * 增加文档数量
     */
    void incrementDocumentCount(String kbCode, int delta);

    /**
     * 增加向量数量
     */
    void incrementVectorCount(String kbCode, int delta);
}
