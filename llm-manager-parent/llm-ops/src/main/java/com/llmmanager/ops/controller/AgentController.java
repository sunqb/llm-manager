package com.llmmanager.ops.controller;

import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Resource
    private AgentService agentService;

    @GetMapping
    public List<Agent> getAll() {
        return agentService.findAll();
    }

    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        // 验证必填字段
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent 名称不能为空");
        }
        if (agent.getSlug() == null || agent.getSlug().trim().isEmpty()) {
            throw new IllegalArgumentException("Agent slug 不能为空");
        }
        if (agent.getLlmModelId() == null) {
            throw new IllegalArgumentException("Agent 必须关联一个模型（llmModelId 不能为空）");
        }
        return agentService.create(agent);
    }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        Agent agent = agentService.findById(id);
        if (agent == null) {
            throw new RuntimeException("Not found");
        }
        return agent;
    }

    @PutMapping("/{id}")
    public Agent update(@PathVariable Long id, @RequestBody Agent updated) {
        // 验证ID存在
        Agent existing = get(id);

        // 设置ID后直接更新，MyBatis-Plus 会自动忽略 null 字段
        updated.setId(id);
        agentService.update(updated);

        return agentService.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        agentService.delete(id);
    }
}
