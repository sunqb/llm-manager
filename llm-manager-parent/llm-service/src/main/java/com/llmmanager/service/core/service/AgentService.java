package com.llmmanager.service.core.service;

import com.llmmanager.service.core.entity.Agent;

import java.util.List;

/**
 * Agent Service 接口
 */
public interface AgentService {

    /**
     * 查询所有 Agent
     */
    List<Agent> findAll();

    /**
     * 根据 ID 查询 Agent
     */
    Agent findById(Long id);

    /**
     * 根据 slug 查询 Agent
     */
    Agent findBySlug(String slug);

    /**
     * 创建 Agent
     */
    Agent create(Agent agent);

    /**
     * 更新 Agent
     */
    Agent update(Agent agent);

    /**
     * 删除 Agent
     */
    void delete(Long id);
}
