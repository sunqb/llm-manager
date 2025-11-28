package com.example.llmmanager.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.mapper.AgentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService extends ServiceImpl<AgentMapper, Agent> {

    public List<Agent> findAll() {
        return list();
    }

    public Agent findById(Long id) {
        return getById(id);
    }

    public Agent findBySlug(String slug) {
        QueryWrapper<Agent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("slug", slug);
        return getOne(queryWrapper);
    }

    public Agent create(Agent agent) {
        save(agent);
        return agent;
    }

    public Agent update(Agent agent) {
        updateById(agent);
        return agent;
    }

    public void delete(Long id) {
        removeById(id);
    }
}