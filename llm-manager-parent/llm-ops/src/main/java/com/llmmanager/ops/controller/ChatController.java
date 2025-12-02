package com.llmmanager.ops.controller;

import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.orchestration.LlmExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private LlmExecutionService executionService;

    @Resource
    private AgentService agentService;

    @Resource
    private ToolFunctionManager toolFunctionManager;

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

    /**
     * 获取所有可用的工具列表（供前端展示和选择）
     *
     * @return 工具列表 {name -> description}
     */
    @GetMapping("/tools")
    public Map<String, Object> getAvailableTools() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tools", toolFunctionManager.getAllTools());
        return response;
    }

    /**
     * 带工具调用的对话接口（使用 Spring AI 原生 Function Calling）
     *
     * 工作流程（AI 自动决策）：
     * 1. 用户发送消息
     * 2. LLM 自动判断是否需要调用工具（无需手动正则匹配）
     * 3. 如果需要，LLM 自动调用已注册的工具函数
     * 4. LLM 基于工具返回结果生成最终回复
     *
     * 关键：将工具函数注册到 ChatClient，让 AI 自己决定何时调用
     *
     * @param modelId        模型ID
     * @param message        用户消息
     * @param conversationId 会话ID（可选）
     * @param toolNames      指定工具列表（可选，逗号分隔，null 表示使用所有工具）
     * @return LLM 回复
     */
    @PostMapping("/{modelId}/with-tools")
    public Map<String, Object> chatWithTools(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) List<String> toolNames) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 调用带工具支持的对话服务
            // LLM 会自动判断是否需要调用工具
            String llmResponse = executionService.chatWithTools(modelId, message, conversationId, toolNames);

            response.put("success", true);
            response.put("message", llmResponse);
            response.put("toolsUsed", toolNames != null ? toolNames : toolFunctionManager.getAllToolNames());

            return response;

        } catch (Exception e) {
            log.error("[ChatController] 工具调用对话失败", e);
            response.put("success", false);
            response.put("error", "对话失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 带工具调用的流式对话接口（使用 Spring AI 原生 Function Calling）
     *
     * @param modelId        模型ID
     * @param message        用户消息
     * @param conversationId 会话ID（可选）
     * @param toolNames      指定工具列表（可选，逗号分隔，null 表示使用所有工具）
     * @return 流式响应
     */
    @PostMapping(value = "/{modelId}/with-tools/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithToolsStream(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) List<String> toolNames) {

        // 调用带工具支持的流式对话服务
        // LLM 会自动判断是否需要调用工具
        return executionService.streamChatWithTools(modelId, message, conversationId, toolNames)
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
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()));
    }

    /**
     * 智能体流式对话接口（内部管理后台使用）
     *
     * 功能说明：
     * - 根据 slug 查询业务智能体配置（Agent）
     * - 使用智能体关联的模型、系统提示词等配置
     * - 支持会话历史记忆（conversationId）
     * - 返回流式响应
     *
     * @param slug           智能体标识（Agent.slug）
     * @param message        用户消息
     * @param conversationId 会话ID（可选，用于连续对话）
     * @return 流式响应
     */
    @PostMapping(value = "/agents/{slug}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithAgentStream(
            @PathVariable String slug,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 智能体流式对话请求，slug: {}, conversationId: {}", slug, conversationId);

        // 1. 根据 slug 查询智能体配置
        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            log.error("[ChatController] 智能体不存在，slug: {}", slug);
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("{\"error\":\"智能体不存在: " + slug + "\"}")
                    .build());
        }

        // 2. 调用流式对话服务（支持会话历史）
        return executionService.streamChatWithAgent(agent, message, conversationId)
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
                ))
                .doOnError(error -> log.error("[ChatController] 智能体流式对话失败，slug: {}, error: {}",
                        slug, error.getMessage(), error));
    }
}

