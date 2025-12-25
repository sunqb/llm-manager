package com.llmmanager.ops.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.agent.storage.core.entity.GraphWorkflow;
import com.llmmanager.agent.storage.core.service.GraphWorkflowService;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.orchestration.DynamicWorkflowExecutionService;
import com.llmmanager.service.orchestration.GraphExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
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
    public Result<ResearchResult> research(
            @PathVariable Long modelId,
            @RequestBody String question) {
        log.info("[Graph] 深度研究请求, modelId: {}, question: {}", modelId, question);
        try {
            ResearchResult result = graphExecutionService.deepResearch(modelId, question);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[Graph] 深度研究失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_EXECUTION_FAILED, "深度研究执行失败: " + e.getMessage());
        }
    }

    /**
     * 流式执行深度研究（WebFlux SSE）
     * 注：流式响应不使用 Result 包装
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
    public Result<List<ResearchProgress>> researchWithProgress(
            @PathVariable Long modelId,
            @RequestBody String question) {
        log.info("[Graph] 带进度深度研究请求, modelId: {}, question: {}", modelId, question);
        try {
            List<ResearchProgress> result = graphExecutionService.deepResearchWithProgress(modelId, question);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[Graph] 深度研究失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_EXECUTION_FAILED, "深度研究执行失败: " + e.getMessage());
        }
    }

    // ==================== DeepResearch 硬编码工作流 (by slug) ====================

    /**
     * 根据工作流 slug 同步执行深度研究
     */
    @PostMapping("/research/by-slug/{slug}")
    public Result<ResearchResult> researchBySlug(
            @PathVariable String slug,
            @RequestBody String question) {
        log.info("[Graph] 深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND, "工作流不存在: " + slug);
        }

        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new BusinessException(ResultCode.WORKFLOW_CONFIG_ERROR, "工作流未配置默认模型: " + slug);
        }

        try {
            ResearchResult result = graphExecutionService.deepResearch(modelId, question);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[Graph] 深度研究失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_EXECUTION_FAILED, "深度研究执行失败: " + e.getMessage());
        }
    }

    /**
     * 根据工作流 slug 流式执行深度研究（SSE）
     * 注：流式响应不使用 Result 包装
     */
    @GetMapping(value = "/research/by-slug/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> researchStreamBySlug(
            @PathVariable String slug,
            @RequestParam String question) {
        log.info("[Graph] 流式深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        if (workflow == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("工作流不存在: " + slug)
                    .build());
        }

        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("工作流未配置默认模型: " + slug)
                    .build());
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
    public Result<List<ResearchProgress>> researchWithProgressBySlug(
            @PathVariable String slug,
            @RequestBody String question) {
        log.info("[Graph] 带进度深度研究请求, workflowSlug: {}, question: {}", slug, question);

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND, "工作流不存在: " + slug);
        }

        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new BusinessException(ResultCode.WORKFLOW_CONFIG_ERROR, "工作流未配置默认模型: " + slug);
        }

        try {
            List<ResearchProgress> result = graphExecutionService.deepResearchWithProgress(modelId, question);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[Graph] 深度研究失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_EXECUTION_FAILED, "深度研究执行失败: " + e.getMessage());
        }
    }

    // ==================== 通用动态工作流（从数据库读取配置执行） ====================

    /**
     * 获取所有可用的节点类型
     */
    @GetMapping("/workflow/node-types")
    public Result<Map<String, String>> getAvailableNodeTypes() {
        log.info("[Graph] 获取可用节点类型");
        Map<String, String> nodeTypes = dynamicWorkflowExecutionService.getAvailableNodeTypes();
        return Result.success(nodeTypes);
    }

    /**
     * 验证工作流配置
     */
    @PostMapping("/workflow/validate")
    public Result<Map<String, Object>> validateWorkflowConfig(@RequestBody String configJson) {
        log.info("[Graph] 验证工作流配置");
        Map<String, Object> result = dynamicWorkflowExecutionService.validateWorkflowConfig(configJson);
        return Result.success(result);
    }

    /**
     * 根据 slug 执行通用动态工作流
     */
    @PostMapping("/workflow/execute/{slug}")
    public Result<Map<String, Object>> executeWorkflowBySlug(
            @PathVariable String slug,
            @RequestBody WorkflowExecuteRequest request) {
        log.info("[Graph] 执行动态工作流, slug: {}, question: {}", slug, request.getQuestion());

        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND, "工作流不存在: " + slug);
        }

        String graphConfig = workflow.getGraphConfig();
        if (!StringUtils.hasText(graphConfig)) {
            throw new BusinessException(ResultCode.WORKFLOW_CONFIG_ERROR, "工作流配置为空: " + slug);
        }

        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new BusinessException(ResultCode.WORKFLOW_CONFIG_ERROR, "工作流未配置默认模型: " + slug);
        }

        Map<String, Object> initialState = buildInitialState(request);

        try {
            Map<String, Object> result = dynamicWorkflowExecutionService.executeWorkflow(
                    graphConfig,
                    modelId,
                    initialState
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("[Graph] 工作流执行失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_EXECUTION_FAILED, "工作流执行失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有工作流配置
     */
    @GetMapping("/workflow/list")
    public Result<List<GraphWorkflow>> listWorkflows() {
        log.info("[Graph] 查询所有工作流");
        return Result.success(graphWorkflowService.findAll());
    }

    /**
     * 查询所有启用的工作流
     */
    @GetMapping("/workflow/list/active")
    public Result<List<GraphWorkflow>> listActiveWorkflows() {
        log.info("[Graph] 查询启用的工作流");
        return Result.success(graphWorkflowService.findActiveWorkflows());
    }

    /**
     * 根据 slug 查询工作流详情
     */
    @GetMapping("/workflow/{slug}")
    public Result<GraphWorkflow> getWorkflowBySlug(@PathVariable String slug) {
        log.info("[Graph] 查询工作流详情, slug: {}", slug);
        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        if (workflow == null) {
            throw new BusinessException(ResultCode.WORKFLOW_NOT_FOUND, "工作流不存在: " + slug);
        }
        return Result.success(workflow);
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> buildInitialState(WorkflowExecuteRequest request) {
        Map<String, Object> state = new HashMap<>();

        if (StringUtils.hasText(request.getQuestion())) {
            state.put("question", request.getQuestion());
        }

        if (StringUtils.hasText(request.getConversationCode())) {
            state.put("conversation_code", request.getConversationCode());
        }

        Map<String, Object> customState = request.getCustomState();
        if (customState != null) {
            customState.forEach((key, value) -> state.putIfAbsent(key, value));
        }

        return state;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[Graph] JSON 序列化失败", e);
            return "{}";
        }
    }
}
