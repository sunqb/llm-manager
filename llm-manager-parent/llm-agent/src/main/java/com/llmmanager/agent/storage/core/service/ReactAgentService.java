package com.llmmanager.agent.storage.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.agent.storage.core.mapper.ReactAgentMapper;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ReactAgent 配置服务
 * 提供 Agent 配置的 CRUD 操作
 *
 * @author LLM Manager
 */
@Service
public class ReactAgentService {

    @Resource
    private ReactAgentMapper reactAgentMapper;

    /**
     * 查询所有 Agent
     */
    public List<ReactAgent> findAll() {
        return reactAgentMapper.selectList(
                new LambdaQueryWrapper<ReactAgent>()
                        .eq(ReactAgent::getIsDelete, 0)
                        .orderByAsc(ReactAgent::getId)
        );
    }

    /**
     * 根据 ID 查询
     */
    public ReactAgent findById(Long id) {
        return reactAgentMapper.selectById(id);
    }

    /**
     * 根据 slug 查询
     */
    public ReactAgent findBySlug(String slug) {
        return reactAgentMapper.selectBySlug(slug);
    }

    /**
     * 根据 slug 获取 Agent（不存在则抛出异常）
     */
    public ReactAgent getBySlug(String slug) {
        ReactAgent agent = reactAgentMapper.selectBySlug(slug);
        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + slug);
        }
        return agent;
    }

    /**
     * 查询所有启用的 Agent
     */
    public List<ReactAgent> findActiveAgents() {
        return reactAgentMapper.selectActiveAgents();
    }

    /**
     * 根据类型查询 Agent
     */
    public List<ReactAgent> findByAgentType(String agentType) {
        return reactAgentMapper.selectByAgentType(agentType);
    }

    /**
     * 查询所有启用的指定类型 Agent
     */
    public List<ReactAgent> findActiveByAgentType(String agentType) {
        return reactAgentMapper.selectActiveByAgentType(agentType);
    }

    /**
     * 保存 Agent
     */
    public ReactAgent save(ReactAgent agent) {
        reactAgentMapper.insert(agent);
        return agent;
    }

    /**
     * 更新 Agent
     */
    public void update(ReactAgent agent) {
        reactAgentMapper.updateById(agent);
    }

    /**
     * 删除 Agent（逻辑删除）
     */
    public void delete(Long id) {
        reactAgentMapper.deleteById(id);
    }

    /**
     * 启用/禁用 Agent
     */
    public void setActive(Long id, boolean active) {
        ReactAgent agent = reactAgentMapper.selectById(id);
        if (agent != null) {
            agent.setIsActive(active);
            reactAgentMapper.updateById(agent);
        }
    }

    /**
     * 检查 slug 是否已存在
     */
    public boolean isSlugExists(String slug) {
        return reactAgentMapper.selectBySlug(slug) != null;
    }

    /**
     * 检查 slug 是否已存在（排除指定 ID）
     */
    public boolean isSlugExists(String slug, Long excludeId) {
        ReactAgent agent = reactAgentMapper.selectBySlug(slug);
        return agent != null && !agent.getId().equals(excludeId);
    }
}

