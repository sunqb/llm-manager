package com.example.llmmanager.controller;

import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.service.AgentService;
import com.example.llmmanager.service.LlmExecutionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Public facing API protected by ApiKeyAuthFilter
 */
@RestController
@RequestMapping("/api/external")
public class ExternalChatController {

    private final AgentService agentService;
    private final LlmExecutionService executionService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ExternalChatController(AgentService agentService, LlmExecutionService executionService) {
        this.agentService = agentService;
        this.executionService = executionService;
    }

    @PostMapping("/agents/{slug}/chat")
    public Map<String, String> chatWithAgent(@PathVariable String slug, @RequestBody Map<String, String> payload) {
        String userMessage = payload.get("message");
        if (userMessage == null || userMessage.isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }

        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + slug);
        }

        String response = executionService.chatWithAgent(agent, userMessage);
        
        return Map.of("response", response);
    }

    @PostMapping(value = "/agents/{slug}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatWithAgentStream(@PathVariable String slug, @RequestBody Map<String, String> payload) {
        String userMessage = payload.get("message");
        if (userMessage == null || userMessage.isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }

        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + slug);
        }

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executorService.execute(() -> {
            try {
                executionService.streamChatWithAgent(agent, userMessage)
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
                                error -> emitter.completeWithError(error),
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
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
