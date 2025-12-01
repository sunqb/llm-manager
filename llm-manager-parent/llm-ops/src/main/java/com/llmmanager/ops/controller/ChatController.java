package com.llmmanager.ops.controller;

import com.llmmanager.service.orchestration.LlmExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final LlmExecutionService executionService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ChatController(LlmExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/{modelId}")
    public String chat(@PathVariable Long modelId, @RequestBody String message) {
        return executionService.chat(modelId, message);
    }

    @PostMapping(value = "/{modelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable Long modelId, @RequestBody String message) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executorService.execute(() -> {
            try {
                System.out.println("[Controller] 开始流式请求");

                executionService.streamChat(modelId, message)
                        .filter(content -> content != null && !content.isEmpty())
                        .subscribe(
                                content -> {
                                    try {
                                        String escapedContent = content.replace("\\", "\\\\")
                                                .replace("\"", "\\\"")
                                                .replace("\n", "\\n")
                                                .replace("\r", "\\r");
                                        String json = "{\"choices\":[{\"delta\":{\"content\":\"" + escapedContent + "\"}}]}";

                                        System.out.println("[Controller] 发送数据: " + content.substring(0, Math.min(20, content.length())));
                                        emitter.send(SseEmitter.event().data(json));
                                    } catch (Exception e) {
                                        System.err.println("[Controller] 发送错误: " + e.getMessage());
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    System.err.println("[Controller] 流错误: " + error.getMessage());
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        System.out.println("[Controller] 流完成");
                                        emitter.send(SseEmitter.event().data("[DONE]"));
                                        emitter.complete();
                                    } catch (Exception e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );
            } catch (Exception e) {
                System.err.println("[Controller] 异常: " + e.getMessage());
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
