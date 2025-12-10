package com.llmmanager.ops.controller;

import com.llmmanager.service.orchestration.GraphExecutionService;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchProgress;
import com.llmmanager.agent.graph.workflow.DeepResearchWorkflow.ResearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.io.IOException;
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
     * 流式执行深度研究（SSE）
     */
    @GetMapping(value = "/research/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter researchStream(
            @PathVariable Long modelId,
            @RequestParam String question) {
        log.info("[Graph] 流式深度研究请求, modelId: {}, question: {}", modelId, question);

        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        Flux<ResearchProgress> progressFlux = graphExecutionService.deepResearchStream(modelId, question);

        progressFlux.subscribe(
                progress -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(progress));
                    } catch (IOException e) {
                        log.error("[Graph] SSE 发送失败", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("[Graph] 流式研究失败", error);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(error.getMessage()));
                    } catch (IOException ignored) {
                    }
                    emitter.completeWithError(error);
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data("[DONE]"));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                }
        );

        return emitter;
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
}
