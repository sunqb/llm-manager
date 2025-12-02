package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.Prompt;

import java.util.List;

/**
 * Prompt Service 接口
 */
public interface PromptService {

    /**
     * 查询所有 Prompt
     */
    List<Prompt> findAll();

    /**
     * 根据 ID 查询 Prompt
     */
    Prompt findById(Long id);

    /**
     * 创建 Prompt
     */
    Prompt create(Prompt prompt);

    /**
     * 更新 Prompt
     */
    Prompt update(Prompt prompt);

    /**
     * 删除 Prompt
     */
    void delete(Long id);
}
