package com.llmmanager.ops.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.agent.storage.core.entity.GraphWorkflow;
import com.llmmanager.agent.storage.core.service.GraphWorkflowService;
import com.llmmanager.service.graph.DynamicWorkflowExecutionService;
import com.llmmanager.service.orchestration.GraphExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.llmmanager.ops.dto.WorkflowExecuteRequest;

/**
 * Graph 工作流 API 控制器
 *
 * 提供：
 * - DeepResearch 深度研究（硬编码工作流，by modelId / by slug）
 * - 通用动态工作流执行（从数据库读取配置，by slug）
 */
@Slf4j
@RestController
@RequestMapping("/api/graph")
public class GraphWorkflowController {

    @Resource
    private GraphExecutionService graphExecutionService;

    @Resource
    private GraphWorkflowService graphWorkflowService;

    @Resource
    private DynamicWorkflowExecutionService dynamicWorkflowExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== DeepResearch 硬编码工作流 (by modelId) ====================

    /**
     * 同步执行深度研究
     */
    @PostMapping("/research/{modelId}")
    public ResearchResult research(
            @PathVariable Long modelId,
            @RequestBody String question) {
        log.info("[Graph] 深度研究请求, modelId: {}, question: {}", modelId, question);
        return graphExecutionService.deepResearch(modelId, question);
    }

    /**
     * 流式执行深度研究（WebFlux SSE）
     */
    @GetMapping(value = "/research/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> researchStream(
            @PathVariable Long modelId,
            @RequestParam String question) {
        log.info("[Graph] 流式深度研究请求, modelId: {}, question: {}", modelId, question);

        return graphExecutionService.deepResearchStream(modelId, question)
                .map(progress -> ServerSentEvent.<String>builder()
                        .event("progress")
                        .data(toJson(progress))
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("complete")
                                .data("[DONE]")
                                .build()))
                .onErrorResume(error -> {
                    log.error("[Graph] 流式研究失败", error);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(error.getMessage())
                                    .build());
                });
    }

    /**
     * 同步执行深度研究（带进度）
     */
    @PostMapping("/research/{modelId}/with-progress")
    public List<ResearchProgress> researchWithProgress(
            @PathVariable Long modelId,
            @RequestBody String question) {
        log.info("[Graph] 带进度深度研究请求, modelId: {}, question: {}", modelId, question);
        return graphExecutionService.deepResearchWithProgress(modelId, question);
    }

    // ==================== DeepResearch 硬编码工作流 (by slug) ====================

    /**
     * 根据工作流 slug 同步执行深度研究
     */
    @PostMapping("/research/by-slug/{slug}")
    public ResearchResult researchBySlug(
            @PathVariable String slug,
            @RequestBody String question) {
        log.info("[Graph] 深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        return graphExecutionService.deepResearch(modelId, question);
    }

    /**
     * 根据工作流 slug 流式执行深度研究（SSE）
     */
    @GetMapping(value = "/research/by-slug/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> researchStreamBySlug(
            @PathVariable String slug,
            @RequestParam String question) {
        log.info("[Graph] 流式深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        return graphExecutionService.deepResearchStream(modelId, question)
                .map(progress -> ServerSentEvent.<String>builder()
                        .event("progress")
                        .data(toJson(progress))
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("complete")
                                .data("[DONE]")
                                .build()))
                .onErrorResume(error -> {
                    log.error("[Graph] 流式研究失败", error);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(error.getMessage())
                                    .build());
                });
    }

    /**
     * 根据工作流 slug 同步执行深度研究（带进度）
     */
    @PostMapping("/research/by-slug/{slug}/with-progress")
    public List<ResearchProgress> researchWithProgressBySlug(
            @PathVariable String slug,
            @RequestBody String question) {
        log.info("[Graph] 带进度深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        return graphExecutionService.deepResearchWithProgress(modelId, question);
    }

    // ==================== 通用动态工作流（从数据库读取配置执行） ====================

    /**
     * 获取所有可用的节点类型
     *
     * @return 节点类型列表（类型 -> 描述）
     */
    @GetMapping("/workflow/node-types")
    public ResponseEntity<Map<String, String>> getAvailableNodeTypes() {
        log.info("[Graph] 获取可用节点类型");
        Map<String, String> nodeTypes = dynamicWorkflowExecutionService.getAvailableNodeTypes();
        return ResponseEntity.ok(nodeTypes);
    }

    /**
     * 验证工作流配置
     *
     * @param configJson 工作流配置 JSON
     * @return 验证结果
     */
    @PostMapping("/workflow/validate")
    public ResponseEntity<Map<String, Object>> validateWorkflowConfig(
            @RequestBody String configJson) {
        log.info("[Graph] 验证工作流配置");
        Map<String, Object> result = dynamicWorkflowExecutionService.validateWorkflowConfig(configJson);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据 slug 执行通用动态工作流
     *
     * 从数据库读取工作流配置（包含节点、边、关联模型等），然后执行
     *
     * @param slug    工作流唯一标识
     * @param request 执行请求
     * @return 执行结果
     */
    @PostMapping("/workflow/execute/{slug}")
    public ResponseEntity<Map<String, Object>> executeWorkflowBySlug(
            @PathVariable String slug,
            @RequestBody WorkflowExecuteRequest request) {
        log.info("[Graph] 执行动态工作流, slug: {}, question: {}", slug, request.getQuestion());

        // 1. 从数据库读取工作流配置
        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);

        // 2. 验证工作流配置
        String graphConfig = workflow.getGraphConfig();
        if (!StringUtils.hasText(graphConfig)) {
            throw new IllegalArgumentException("工作流配置为空: " + slug);
        }

        // 3. 获取关联的模型 ID
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        // 4. 构建初始状态
        Map<String, Object> initialState = buildInitialState(request);

        // 5. 执行工作流
        Map<String, Object> result = dynamicWorkflowExecutionService.executeWorkflow(
                graphConfig,
                modelId,
                initialState
        );

        return ResponseEntity.ok(result);
    }

    /**
     * 构建工作流初始状态
     */
    private Map<String, Object> buildInitialState(WorkflowExecuteRequest request) {
        Map<String, Object> state = new HashMap<>();

        // 添加主要输入
        if (StringUtils.hasText(request.getQuestion())) {
            state.put("question", request.getQuestion());
        }

        // 添加会话标识
        if (StringUtils.hasText(request.getConversationCode())) {
            state.put("conversation_code", request.getConversationCode());
        }

        // 合并自定义状态（不覆盖已有字段）
        Map<String, Object> customState = request.getCustomState();
        if (customState != null) {
            customState.forEach((key, value) -> state.putIfAbsent(key, value));
        }

        return state;
    }

    /**
     * 查询所有工作流配置
     */
    @GetMapping("/workflow/list")
    public ResponseEntity<List<GraphWorkflow>> listWorkflows() {
        log.info("[Graph] 查询所有工作流");
        List<GraphWorkflow> workflows = graphWorkflowService.findAll();
        return ResponseEntity.ok(workflows);
    }

    /**
     * 查询所有启用的工作流
     */
    @GetMapping("/workflow/list/active")
    public ResponseEntity<List<GraphWorkflow>> listActiveWorkflows() {
        log.info("[Graph] 查询启用的工作流");
        List<GraphWorkflow> workflows = graphWorkflowService.findActiveWorkflows();
        return ResponseEntity.ok(workflows);
    }

    /**
     * 根据 slug 查询工作流详情
     */
    @GetMapping("/workflow/{slug}")
    public ResponseEntity<GraphWorkflow> getWorkflowBySlug(@PathVariable String slug) {
        log.info("[Graph] 查询工作流详情, slug: {}", slug);
        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        return ResponseEntity.ok(workflow);
    }

    // ==================== 私有方法 ====================

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[Graph] JSON 序列化失败", e);
            return "{}";
        }
    }
}
