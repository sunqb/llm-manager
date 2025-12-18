package com.llmmanager.ops.controller;

import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.service.orchestration.DynamicReactAgentExecutionService;
import com.llmmanager.service.orchestration.ReactAgentExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
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
 *
 * @author LLM Manager
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
     *
     * 示例请求：
     * POST /api/react-agent/single/{modelId}
     * Body: "北京今天天气怎么样？如果温度超过25度，帮我计算25乘以2"
     */
    @PostMapping("/single/{modelId}")
    public Map<String, Object> singleAgentChat(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] 单个 Agent 对话，modelId: {}, message: {}", modelId, message);
        return reactAgentExecutionService.executeAllInOneAgent(modelId, message);
    }

    // ==================== 2. ConfigurableAgentWorkflow（硬编码） ====================

    /**
     * 顺序执行多个 Agent（硬编码方式）
     *
     * 示例：研究 → 分析 → 总结 流水线
     *
     * 示例请求：
     * POST /api/react-agent/sequential/{modelId}
     * Body: "人工智能的发展趋势"
     */
    @PostMapping("/sequential/{modelId}")
    public Map<String, Object> sequentialWorkflow(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] 顺序工作流，modelId: {}, message: {}", modelId, message);
        return reactAgentExecutionService.executeResearchPipeline(modelId, message);
    }

    // ==================== 3. SupervisorAgentTeam（硬编码） ====================

    /**
     * Supervisor 自主协作（硬编码方式）
     *
     * Supervisor 会自主决定调用哪些 Worker Agent，以及调用顺序和次数。
     *
     * 示例请求：
     * POST /api/react-agent/supervisor/{modelId}
     * Body: "帮我查询北京和上海的天气，然后计算两地温度差"
     */
    @PostMapping("/supervisor/{modelId}")
    public Map<String, Object> supervisorTeam(
            @PathVariable Long modelId,
            @RequestBody String message) {

        log.info("[ReactAgent] Supervisor 团队，modelId: {}, message: {}", modelId, message);
        return reactAgentExecutionService.executeEnterpriseTeam(modelId, message);
    }

    // ==================== 4. 从数据库加载 Agent 配置 ====================

    /**
     * 根据 slug 从数据库加载 Agent 并执行
     *
     * 支持三种类型：SINGLE、SEQUENTIAL、SUPERVISOR
     * 根据数据库中的 agent_type 自动选择执行方式
     * 使用 Agent 配置中的 modelId 创建 ChatModel
     *
     * 示例请求：
     * POST /api/react-agent/db/{slug}
     * Body: "北京今天天气怎么样？"
     */
    @PostMapping("/db/{slug}")
    public Map<String, Object> executeFromDatabase(
            @PathVariable String slug,
            @RequestBody String message) {

        log.info("[ReactAgent] 从数据库加载 Agent，slug: {}, message: {}", slug, message);
        return dynamicReactAgentExecutionService.execute(slug, message);
    }

    /**
     * 获取所有可用的 Agent 配置列表
     */
    @GetMapping("/db/list")
    public Map<String, Object> listAgents() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("agents", dynamicReactAgentExecutionService.getActiveAgents());
        } catch (Exception e) {
            log.error("[ReactAgent] 获取 Agent 列表失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    /**
     * 根据 slug 获取 Agent 配置详情
     */
    @GetMapping("/db/{slug}")
    public Map<String, Object> getAgentBySlug(@PathVariable String slug) {
        Map<String, Object> response = new HashMap<>();
        try {
            ReactAgent agent = dynamicReactAgentExecutionService.getAgentBySlug(slug);
            if (agent != null) {
                response.put("success", true);
                response.put("agent", agent);
            } else {
                response.put("success", false);
                response.put("error", "Agent 不存在: " + slug);
            }
        } catch (Exception e) {
            log.error("[ReactAgent] 获取 Agent 详情失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}

