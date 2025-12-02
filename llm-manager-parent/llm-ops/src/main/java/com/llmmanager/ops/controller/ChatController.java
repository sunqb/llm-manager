package com.llmmanager.ops.controller;

import com.llmmanager.service.orchestration.LlmExecutionService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private LlmExecutionService executionService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping("/{modelId}")
    public String chat(@PathVariable Long modelId, @RequestBody String message) {
        return executionService.chat(modelId, message);
    }

    /**
     * WebFlux 流式接口（推荐使用）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamFlux(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        return executionService.streamChat(modelId, message, conversationId)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> {
                    String escapedContent = content.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                ));
    }

    /**
     * WebFlux 流式接口（支持 reasoning 内容）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream-with-reasoning", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithReasoning(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        return executionService.streamChatResponse(modelId, message, conversationId)
                .mapNotNull(response -> {
                    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                        return null;
                    }

                    var result = response.getResult();
                    if (result == null || result.getOutput() == null) {
                        return null;
                    }

                    var output = result.getOutput();
                    String content = output.getText();

                    // 尝试获取 reasoning 内容（如果模型支持）
                    // Spring AI 可能将其存储在 metadata 中
                    String reasoning = null;
                    if (output.getMetadata() != null) {
                        Object reasoningObj = output.getMetadata().get("reasoning_content");
                        if (reasoningObj != null) {
                            reasoning = reasoningObj.toString();
                        }
                    }

                    // 构建响应JSON
                    StringBuilder jsonBuilder = new StringBuilder("{\"choices\":[{\"delta\":{");

                    if (reasoning != null && !reasoning.isEmpty()) {
                        String escapedReasoning = reasoning.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
                        jsonBuilder.append("\"reasoning_content\":\"").append(escapedReasoning).append("\"");

                        if (content != null && !content.isEmpty()) {
                            jsonBuilder.append(",");
                        }
                    }

                    if (content != null && !content.isEmpty()) {
                        String escapedContent = content.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");
                        jsonBuilder.append("\"content\":\"").append(escapedContent).append("\"");
                    }

                    jsonBuilder.append("}}]}");

                    return ServerSentEvent.<String>builder()
                            .data(jsonBuilder.toString())
                            .build();
                })
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("[DONE]")
                                .build()
                ));
    }

    /**
     * SseEmitter 流式接口（兼容性）
     *
     * @param conversationId 会话ID（前端传递，用于连续对话）
     */
    @PostMapping(value = "/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        SseEmitter emitter = new SseEmitter(0L);  // 0 表示无超时
        emitter.onTimeout(emitter::complete);

        executorService.execute(() -> {
            try {
                executionService.streamChat(modelId, message, conversationId)
                        .filter(content -> content != null && !content.isEmpty())
                        .subscribe(
                                content -> {
                                    try {
                                        String escapedContent = content.replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", "\\n")
                                                .replace("\r", "\\r");
                                        String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";

                                        emitter.send(SseEmitter.event().data(json));
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    error.printStackTrace();
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        emitter.send(SseEmitter.event().data("[DONE]"));
                                        emitter.complete();
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );
            } catch (Exception e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/{modelId}/template")
    public String chatWithTemplate(@PathVariable Long modelId,
                                   @RequestParam String templateContent,
                                   @RequestBody Map<String, Object> variables) {
        return executionService.chatWithTemplate(modelId, templateContent, variables);
    }
}
