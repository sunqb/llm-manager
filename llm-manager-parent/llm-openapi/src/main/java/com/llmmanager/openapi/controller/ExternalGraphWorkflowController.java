package com.llmmanager.openapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.storage.core.service.GraphWorkflowService;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import com.llmmanager.agent.storage.core.entity.GraphWorkflow;
import com.llmmanager.service.orchestration.GraphExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 外部 Graph 工作流 API Controller
 *
 * 提供 DeepResearch 等 Graph 工作流对外访问能力。
 * 路径位于 /api/external/** 下，自动受 ApiKeyAuthFilter 保护。
 */
@Slf4j
@RestController
@RequestMapping("/api/external/graph")
public class ExternalGraphWorkflowController {

    @Resource
    private GraphExecutionService graphExecutionService;

    @Resource
    private GraphWorkflowService graphWorkflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 同步执行深度研究
     *
     * 请求体：{"question":"..."}
     */
    @PostMapping("/research/{modelId}")
    public Map<String, Object> research(
            @PathVariable Long modelId,
            @RequestBody Map<String, String> payload) {

        String question = payload.get("question");
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required");
        }

        log.info("[ExternalGraph] 深度研究请求, modelId: {}, question: {}", modelId, question);
        ResearchResult result = graphExecutionService.deepResearch(modelId, question);
        return Map.of("success", true, "result", result);
    }

    /**
     * 根据工作流 slug 同步执行深度研究
     *
     * 请求体：{"question":"..."}
     */
    @PostMapping("/research/by-slug/{slug}")
    public Map<String, Object> researchBySlug(
            @PathVariable String slug,
            @RequestBody Map<String, String> payload) {

        String question = payload.get("question");
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required");
        }

        log.info("[ExternalGraph] 深度研究请求, workflowSlug: {}, question: {}", slug, question);
        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        ResearchResult result = graphExecutionService.deepResearch(modelId, question);
        return Map.of("success", true, "result", result);
    }

    /**
     * 流式执行深度研究（SSE）
     */
    @GetMapping(value = "/research/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> researchStream(
            @PathVariable Long modelId,
            @RequestParam String question) {

        if (!StringUtils.hasText(question)) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"error\":\"question is required\"}")
                    .build());
        }

        log.info("[ExternalGraph] 流式深度研究请求, modelId: {}, question: {}", modelId, question);

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
                    log.error("[ExternalGraph] 流式研究失败", error);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(error.getMessage())
                                    .build());
                });
    }

    /**
     * 根据工作流 slug 流式执行深度研究（SSE）
     */
    @GetMapping(value = "/research/by-slug/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> researchStreamBySlug(
            @PathVariable String slug,
            @RequestParam String question) {

        if (!StringUtils.hasText(question)) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"error\":\"question is required\"}")
                    .build());
        }

        log.info("[ExternalGraph] 流式深度研究请求, workflowSlug: {}, question: {}", slug, question);

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
                    log.error("[ExternalGraph] 流式研究失败", error);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(error.getMessage())
                                    .build());
                });
    }

    /**
     * 同步执行深度研究（带进度）
     *
     * 请求体：{"question":"..."}
     */
    @PostMapping("/research/{modelId}/with-progress")
    public Map<String, Object> researchWithProgress(
            @PathVariable Long modelId,
            @RequestBody Map<String, String> payload) {

        String question = payload.get("question");
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required");
        }

        log.info("[ExternalGraph] 带进度深度研究请求, modelId: {}, question: {}", modelId, question);
        List<ResearchProgress> progressList = graphExecutionService.deepResearchWithProgress(modelId, question);
        return Map.of("success", true, "progress", progressList);
    }

    /**
     * 根据工作流 slug 同步执行深度研究（带进度）
     *
     * 请求体：{"question":"..."}
     */
    @PostMapping("/research/by-slug/{slug}/with-progress")
    public Map<String, Object> researchWithProgressBySlug(
            @PathVariable String slug,
            @RequestBody Map<String, String> payload) {

        String question = payload.get("question");
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question is required");
        }

        log.info("[ExternalGraph] 带进度深度研究请求, workflowSlug: {}, question: {}", slug, question);
        GraphWorkflow workflow = graphWorkflowService.getWorkflowBySlug(slug);
        Long modelId = workflow.getLlmModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("工作流未配置默认模型: " + slug);
        }

        List<ResearchProgress> progressList = graphExecutionService.deepResearchWithProgress(modelId, question);
        return Map.of("success", true, "progress", progressList);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ExternalGraph] JSON 序列化失败", e);
            return "{}";
        }
    }
}
