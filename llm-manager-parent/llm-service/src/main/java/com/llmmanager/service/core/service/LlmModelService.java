package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.LlmModel;

import java.util.List;

/**
 * LLM 模型 Service 接口
 */
public interface LlmModelService {

    /**
     * 查询所有模型
     */
    List<LlmModel> findAll();

    /**
     * 根据 ID 查询模型
     */
    LlmModel findById(Long id);

    /**
     * 创建模型
     */
    LlmModel create(LlmModel model);

    /**
     * 更新模型
     */
    LlmModel update(LlmModel model);

    /**
     * 删除模型
     */
    void delete(Long id);

    /**
     * 根据 ID 获取模型（MyBatis-Plus 方法）
     */
    LlmModel getById(Long id);
}
