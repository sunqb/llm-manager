package com.llmmanager.ops.controller;

import com.llmmanager.service.graph.DynamicWorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 动态工作流 REST API Controller
 *
 * 提供工作流管理和执行接口：
 * - 获取可用节点类型
 * - 验证工作流配置
 * - 执行工作流
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
public class DynamicWorkflowController {

    @Resource
    private DynamicWorkflowExecutionService workflowExecutionService;

    /**
     * 获取所有可用的节点类型
     *
     * @return 节点类型列表（类型 -> 描述）
     */
    @GetMapping("/node-types")
    public ResponseEntity<Map<String, String>> getAvailableNodeTypes() {
        log.info("[DynamicWorkflowController] 获取可用节点类型");
        Map<String, String> nodeTypes = workflowExecutionService.getAvailableNodeTypes();
        return ResponseEntity.ok(nodeTypes);
    }

    /**
     * 验证工作流配置
     *
     * @param configJson 工作流配置 JSON
     * @return 验证结果
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateWorkflowConfig(
            @RequestBody String configJson) {
        log.info("[DynamicWorkflowController] 验证工作流配置");
        Map<String, Object> result = workflowExecutionService.validateWorkflowConfig(configJson);
        return ResponseEntity.ok(result);
    }

    /**
     * 执行工作流（同步）
     *
     * @param modelId      模型 ID
     * @param request      执行请求（包含工作流配置和初始状态）
     * @return 执行结果
     */
    @PostMapping("/execute/{modelId}")
    public ResponseEntity<Map<String, Object>> executeWorkflow(
            @PathVariable Long modelId,
            @RequestBody WorkflowExecuteRequest request) {
        log.info("[DynamicWorkflowController] 执行工作流，模型 ID: {}", modelId);

        Map<String, Object> result = workflowExecutionService.executeWorkflow(
                request.getWorkflowConfig(),
                modelId,
                request.getInitialState()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * 执行 DeepResearch 工作流（便捷接口）
     *
     * @param modelId  模型 ID
     * @param question 研究问题
     * @return 执行结果
     */
    @PostMapping("/deep-research/{modelId}")
    public ResponseEntity<Map<String, Object>> executeDeepResearch(
            @PathVariable Long modelId,
            @RequestBody DeepResearchRequest request) {
        log.info("[DynamicWorkflowController] 执行 DeepResearch 工作流，模型 ID: {}, 问题: {}",
                modelId, request.getQuestion());

        // 加载 DeepResearch 工作流配置
        String workflowConfig = loadDeepResearchConfig();

        // 设置初始状态
        Map<String, Object> initialState = Map.of(
                "question", request.getQuestion(),
                "iteration_count", 0
        );

        Map<String, Object> result = workflowExecutionService.executeWorkflow(
                workflowConfig,
                modelId,
                initialState
        );

        return ResponseEntity.ok(result);
    }

    /**
     * 加载 DeepResearch 工作流配置
     */
    private String loadDeepResearchConfig() {
        // 从 classpath 加载预定义的 DeepResearch 配置
        try {
            var inputStream = getClass().getClassLoader()
                    .getResourceAsStream("workflows/deep-research.json");
            if (inputStream != null) {
                return new String(inputStream.readAllBytes());
            }
        } catch (Exception e) {
            log.error("[DynamicWorkflowController] 加载 DeepResearch 配置失败", e);
        }

        // 返回默认配置（简化版）
        return getDefaultDeepResearchConfig();
    }

    /**
     * 获取默认的 DeepResearch 配置（简化版）
     */
    private String getDefaultDeepResearchConfig() {
        return """
                {
                  "name": "DeepResearch-Simple",
                  "description": "简化版深度研究工作流",
                  "stateConfig": {
                    "keys": [
                      {"key": "question", "append": false},
                      {"key": "research_result", "append": false},
                      {"key": "current_node", "append": false}
                    ]
                  },
                  "nodes": [
                    {
                      "id": "research",
                      "type": "LLM_NODE",
                      "name": "深度研究",
                      "config": {
                        "input_key": "question",
                        "output_key": "research_result",
                        "system_prompt": "你是一个专业的研究助手。请对以下问题进行深入研究，提供全面、准确、结构化的分析报告。包括：1.背景介绍 2.主要观点分析 3.深入见解 4.结论建议"
                      }
                    }
                  ],
                  "edges": [
                    {"from": "START", "to": "research", "type": "SIMPLE"},
                    {"from": "research", "to": "END", "type": "SIMPLE"}
                  ]
                }
                """;
    }

    /**
     * 工作流执行请求
     */
    public static class WorkflowExecuteRequest {
        private String workflowConfig;
        private Map<String, Object> initialState;

        public String getWorkflowConfig() {
            return workflowConfig;
        }

        public void setWorkflowConfig(String workflowConfig) {
            this.workflowConfig = workflowConfig;
        }

        public Map<String, Object> getInitialState() {
            return initialState != null ? initialState : Map.of();
        }

        public void setInitialState(Map<String, Object> initialState) {
            this.initialState = initialState;
        }
    }

    /**
     * DeepResearch 请求
     */
    public static class DeepResearchRequest {
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }
}
