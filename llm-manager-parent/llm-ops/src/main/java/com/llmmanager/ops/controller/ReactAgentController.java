package com.llmmanager.ops.controller;

import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.orchestration.DynamicReactAgentExecutionService;
import com.llmmanager.service.orchestration.ReactAgentExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * ReactAgent 智能体 Controller
 *
 * 提供 ReactAgent 模块的三种调用模式：
 * 1. 单个 ReactAgent（ReAct 循环 + 工具调用）
 * 2. ConfigurableAgentWorkflow（配置驱动的多 Agent 协作）
 * 3. SupervisorAgentTeam（Agent 自主协作）
 *
 * 硬编码场景通过 ReactAgentExecutionService 执行
 * 数据库配置场景通过 DynamicReactAgentExecutionService 执行
 */
@Slf4j
@RestController
@RequestMapping("/api/react-agent")
public class ReactAgentController {

    @Resource
    private ReactAgentExecutionService reactAgentExecutionService;

    @Resource
    private DynamicReactAgentExecutionService dynamicReactAgentExecutionService;

    // ==================== 1. 单个 ReactAgent 示例（硬编码） ====================

    /**
     * 单个 ReactAgent 对话（硬编码方式）
     *
     * ReactAgent 会自主进行 ReAct 循环：
     * - Reasoning：分析问题，决定是否需要调用工具
     * - Acting：调用工具获取信息
     * - 循环直到得出最终答案
     */
    @PostMapping("/single/{modelId}")
    public Result<Map<String, Object>> singleAgentChat(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] 单个 Agent 对话，modelId: {}, message: {}", modelId, message);
        try {
            Map<String, Object> result = reactAgentExecutionService.executeAllInOneAgent(modelId, message);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[ReactAgent] 执行失败", e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "Agent 执行失败: " + e.getMessage());
        }
    }

    // ==================== 2. ConfigurableAgentWorkflow（硬编码） ====================

    /**
     * 顺序执行多个 Agent（硬编码方式）
     *
     * 示例：研究 → 分析 → 总结 流水线
     */
    @PostMapping("/sequential/{modelId}")
    public Result<Map<String, Object>> sequentialWorkflow(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] 顺序工作流，modelId: {}, message: {}", modelId, message);
        try {
            Map<String, Object> result = reactAgentExecutionService.executeResearchPipeline(modelId, message);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[ReactAgent] 执行失败", e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "工作流执行失败: " + e.getMessage());
        }
    }

    // ==================== 3. SupervisorAgentTeam（硬编码） ====================

    /**
     * Supervisor 自主协作（硬编码方式）
     *
     * Supervisor 会自主决定调用哪些 Worker Agent，以及调用顺序和次数。
     */
    @PostMapping("/supervisor/{modelId}")
    public Result<Map<String, Object>> supervisorTeam(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] Supervisor 团队，modelId: {}, message: {}", modelId, message);
        try {
            Map<String, Object> result = reactAgentExecutionService.executeEnterpriseTeam(modelId, message);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[ReactAgent] 执行失败", e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "团队执行失败: " + e.getMessage());
        }
    }

    // ==================== 4. 从数据库加载 Agent 配置 ====================

    /**
     * 根据 slug 从数据库加载 Agent 并执行
     *
     * 支持三种类型：SINGLE、SEQUENTIAL、SUPERVISOR
     * 根据数据库中的 agent_type 自动选择执行方式
     * 使用 Agent 配置中的 modelId 创建 ChatModel
     */
    @PostMapping("/db/{slug}")
    public Result<Map<String, Object>> executeFromDatabase(
            @PathVariable String slug,
            @RequestBody String message) {

        log.info("[ReactAgent] 从数据库加载 Agent，slug: {}, message: {}", slug, message);
        try {
            Map<String, Object> result = dynamicReactAgentExecutionService.execute(slug, message);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[ReactAgent] 执行失败", e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "Agent 执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有可用的 Agent 配置列表
     */
    @GetMapping("/db/list")
    public Result<List<ReactAgent>> listAgents() {
        return Result.success(dynamicReactAgentExecutionService.getActiveAgents());
    }

    /**
     * 根据 slug 获取 Agent 配置详情
     */
    @GetMapping("/db/{slug}")
    public Result<ReactAgent> getAgentBySlug(@PathVariable String slug) {
        ReactAgent agent = dynamicReactAgentExecutionService.getAgentBySlug(slug);
        if (agent == null) {
            throw new BusinessException(ResultCode.REACT_AGENT_NOT_FOUND, "Agent 不存在: " + slug);
        }
        return Result.success(agent);
    }
}
