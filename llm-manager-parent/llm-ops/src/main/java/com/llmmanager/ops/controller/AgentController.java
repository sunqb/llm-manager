package com.llmmanager.ops.controller;

import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Resource
    private AgentService agentService;

    @GetMapping
    public Result<List<Agent>> getAll() {
        return Result.success(agentService.findAll());
    }

    @PostMapping
    public Result<Agent> create(@RequestBody Agent agent) {
        // 验证必填字段
        if (!StringUtils.hasText(agent.getName())) {
            throw BusinessException.paramError("Agent 名称不能为空");
        }
        if (!StringUtils.hasText(agent.getSlug())) {
            throw BusinessException.paramError("Agent slug 不能为空");
        }
        if (agent.getLlmModelId() == null) {
            throw BusinessException.paramError("Agent 必须关联一个模型（llmModelId 不能为空）");
        }
        return Result.success(agentService.create(agent));
    }

    @GetMapping("/{id}")
    public Result<Agent> get(@PathVariable Long id) {
        Agent agent = agentService.findById(id);
        if (agent == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND, "Agent 不存在: " + id);
        }
        return Result.success(agent);
    }

    @PutMapping("/{id}")
    public Result<Agent> update(@PathVariable Long id, @RequestBody Agent updated) {
        // 验证ID存在
        Agent existing = agentService.findById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND, "Agent 不存在: " + id);
        }

        // 设置ID后直接更新
        updated.setId(id);
        agentService.update(updated);

        return Result.success(agentService.findById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.success();
    }
}
