package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.ApiKey;

import java.util.List;

/**
 * API Key Service 接口
 */
public interface ApiKeyService {

    /**
     * 查询所有 API Key
     */
    List<ApiKey> findAll();

    /**
     * 根据 ID 查询 API Key
     */
    ApiKey findById(Long id);

    /**
     * 根据 token 查询 API Key
     */
    ApiKey findByToken(String token);

    /**
     * 创建 API Key
     */
    ApiKey create(ApiKey apiKey);

    /**
     * 更新 API Key
     */
    ApiKey update(ApiKey apiKey);

    /**
     * 删除 API Key
     */
    void delete(Long id);

    /**
     * 吊销 API Key
     */
    ApiKey revoke(Long id);
}
