package com.llmmanager.service.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.mapper.AgentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent Service 实现
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    @Override
    public List<Agent> findAll() {
        return list();
    }

    @Override
    public Agent findById(Long id) {
        return getById(id);
    }

    @Override
    public Agent findBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        LambdaQueryWrapper<Agent> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Agent::getSlug, slug);
        return getOne(queryWrapper);
    }

    @Override
    public Agent create(Agent agent) {
        save(agent);
        return agent;
    }

    @Override
    public Agent update(Agent agent) {
        updateById(agent);
        return agent;
    }

    @Override
    public void delete(Long id) {
        removeById(id);
    }
}
