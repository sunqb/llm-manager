package com.llmmanager.ops.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.service.orchestration.GraphExecutionService;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;

/**
 * Graph 工作流 API 控制器
 * 提供 DeepResearch 等高级工作流功能
 */
@Slf4j
@RestController
@RequestMapping("/api/graph")
public class GraphWorkflowController {

    @Resource
    private GraphExecutionService graphExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[Graph] JSON 序列化失败", e);
            return "{}";
        }
    }
}
