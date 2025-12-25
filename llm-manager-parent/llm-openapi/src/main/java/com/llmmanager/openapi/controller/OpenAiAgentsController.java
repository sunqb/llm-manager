package com.llmmanager.openapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.agent.storage.core.entity.ReactAgent;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.openapi.dto.openai.ChatCompletionRequest;
import com.llmmanager.openapi.dto.openai.ChatCompletionResponse;
import com.llmmanager.openapi.dto.openai.ModelsResponse;
import com.llmmanager.service.orchestration.DynamicReactAgentExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Agents API Controller
 *
 * 提供 ReactAgent 的 OpenAI 风格 API，支持 SINGLE/SEQUENTIAL/SUPERVISOR 三种类型
 *
 * API 设计：
 * - /v1/agents                        - 列出所有可用 Agent
 * - /v1/agents/{slug}                 - 获取 Agent 详情
 * - /v1/agents/{slug}/completions     - Agent 对话（同步/流式）
 *
 * 使用示例（Python）：
 * <pre>
 * import requests
 *
 * # 获取 Agent 列表
 * response = requests.get(
 *     "https://your-domain/v1/agents",
 *     headers={"Authorization": "Bearer sk-xxxx"}
 * )
 *
 * # 调用 Agent（同步）
 * response = requests.post(
 *     "https://your-domain/v1/agents/universal-assistant/completions",
 *     headers={"Authorization": "Bearer sk-xxxx"},
 *     json={
 *         "messages": [{"role": "user", "content": "帮我查询北京天气"}]
 *     }
 * )
 *
 * # 调用 Agent（流式）
 * response = requests.post(
 *     "https://your-domain/v1/agents/universal-assistant/completions",
 *     headers={"Authorization": "Bearer sk-xxxx"},
 *     json={
 *         "messages": [{"role": "user", "content": "帮我查询北京天气"}],
 *         "stream": True
 *     },
 *     stream=True
 * )
 * </pre>
 *
 * @author LLM Manager
 */
@Slf4j
@RestController
@RequestMapping("/v1/agents")
public class OpenAiAgentsController {

    @Resource
    private DynamicReactAgentExecutionService reactAgentService;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== Agent 列表 ====================

    /**
     * 列出所有可用 Agent
     *
     * GET /v1/agents
     */
    @GetMapping
    public ModelsResponse listAgents() {
        List<ModelsResponse.Model> models = new ArrayList<>();

        List<ReactAgent> reactAgents = reactAgentService.getActiveAgents();
        for (ReactAgent agent : reactAgents) {
            models.add(ModelsResponse.Model.builder()
                    .id(agent.getSlug())
                    .description(agent.getDescription())
                    .agentType(agent.getAgentType())
                    .build());
        }

        return ModelsResponse.builder()
                .data(models)
                .build();
    }

    /**
     * 获取单个 Agent 详情
     *
     * GET /v1/agents/{slug}
     */
    @GetMapping("/{slug}")
    public ModelsResponse.Model getAgent(@PathVariable String slug) {
        ReactAgent agent = reactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            throw new BusinessException(ResultCode.REACT_AGENT_NOT_FOUND, "Agent not found: " + slug);
        }

        return ModelsResponse.Model.builder()
                .id(agent.getSlug())
                .description(agent.getDescription())
                .agentType(agent.getAgentType())
                .build();
    }

    // ==================== Agent Completions ====================

    /**
     * Agent 对话接口（支持同步/流式）
     *
     * POST /v1/agents/{slug}/completions
     *
     * 请求体格式兼容 OpenAI Chat Completions：
     * {
     *   "messages": [{"role": "user", "content": "..."}],
     *   "stream": false
     * }
     */
    @PostMapping("/{slug}/completions")
    public Object agentCompletions(
            @PathVariable String slug,
            @RequestBody ChatCompletionRequest request) {

        // 参数校验
        validateRequest(request);

        // 检查 Agent 是否存在
        ReactAgent agent = reactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            throw new BusinessException(ResultCode.REACT_AGENT_NOT_FOUND, "Agent not found: " + slug);
        }

        String modelId = "agent/" + slug;

        // 判断是否流式
        if (Boolean.TRUE.equals(request.getStream())) {
            return agentCompletionsStream(slug, modelId, request);
        }

        // 同步响应
        return executeAgentCompletion(slug, modelId, request);
    }

    /**
     * Agent 对话接口（显式流式路由）
     *
     * POST /v1/agents/{slug}/completions/stream
     */
    @PostMapping(value = "/{slug}/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> agentCompletionsStreamExplicit(
            @PathVariable String slug,
            @RequestBody ChatCompletionRequest request) {

        // 参数校验
        validateRequest(request);

        // 检查 Agent 是否存在
        ReactAgent agent = reactAgentService.getAgentBySlug(slug);
        if (agent == null) {
            return errorStream("Agent not found: " + slug);
        }

        String modelId = "agent/" + slug;
        request.setStream(true);
        return agentCompletionsStream(slug, modelId, request);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行同步 Agent Completion
     */
    private ChatCompletionResponse executeAgentCompletion(String slug, String modelId, ChatCompletionRequest request) {
        String userMessage = request.getUserMessage();

        log.info("[OpenAI Agents API] 同步请求，slug: {}", slug);

        try {
            Map<String, Object> response = reactAgentService.execute(slug, userMessage);
            String result = extractResult(response);
            return ChatCompletionResponse.success(modelId, result);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OpenAI Agents API] 执行失败，slug: {}", slug, e);
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED, "Execution failed: " + e.getMessage());
        }
    }

    /**
     * 执行流式 Agent Completion
     */
    private Flux<ServerSentEvent<String>> agentCompletionsStream(String slug, String modelId, ChatCompletionRequest request) {
        String userMessage = request.getUserMessage();

        log.info("[OpenAI Agents API] 流式请求，slug: {}", slug);

        // 首个 chunk（发送角色）
        Flux<ServerSentEvent<String>> firstChunk = Flux.just(
                toSseEvent(ChatCompletionResponse.streamChunk(modelId, "", true))
        );

        // 执行并返回结果
        Flux<ServerSentEvent<String>> resultStream = Mono.fromCallable(() -> {
                    Map<String, Object> response = reactAgentService.execute(slug, userMessage);
                    return extractResult(response);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(result -> {
                    // 将结果分块输出
                    return splitToChunks(result)
                            .map(chunk -> toSseEvent(ChatCompletionResponse.streamChunk(modelId, chunk, false)))
                            .delayElements(Duration.ofMillis(10));
                });

        // 结束 chunk
        Flux<ServerSentEvent<String>> lastChunk = Flux.just(
                toSseEvent(ChatCompletionResponse.streamDone(modelId)),
                ServerSentEvent.<String>builder().data("[DONE]").build()
        );

        return Flux.concat(firstChunk, resultStream, lastChunk)
                .onErrorResume(error -> {
                    log.error("[OpenAI Agents API] 流式执行失败，slug: {}", slug, error);
                    return Flux.just(
                            toSseEvent("{\"error\":\"" + error.getMessage() + "\"}"),
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                });
    }

    /**
     * 将文本分割为小块
     */
    private Flux<String> splitToChunks(String text) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }

        List<String> chunks = new ArrayList<>();
        int chunkSize = 2;
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }
        return Flux.fromIterable(chunks);
    }

    /**
     * 从执行结果中提取文本内容
     */
    private String extractResult(Map<String, Object> response) {
        if (response.containsKey("result")) {
            return String.valueOf(response.get("result"));
        }
        if (response.containsKey("finalResult")) {
            return String.valueOf(response.get("finalResult"));
        }
        if (response.containsKey("error")) {
            throw new BusinessException(ResultCode.AGENT_EXECUTION_FAILED,
                    String.valueOf(response.get("error")));
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return response.toString();
        }
    }

    /**
     * 校验请求参数
     */
    private void validateRequest(ChatCompletionRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Request body is required");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Messages is required");
        }
        if (!StringUtils.hasText(request.getUserMessage())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User message is required");
        }
    }

    /**
     * 转换为 SSE 事件
     */
    private ServerSentEvent<String> toSseEvent(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return ServerSentEvent.<String>builder().data(json).build();
        } catch (JsonProcessingException e) {
            log.error("[OpenAI Agents API] JSON 序列化失败", e);
            return ServerSentEvent.<String>builder().data("{}").build();
        }
    }

    /**
     * 构建错误流
     */
    private Flux<ServerSentEvent<String>> errorStream(String message) {
        return Flux.just(
                ServerSentEvent.<String>builder().data("{\"error\":\"" + message + "\"}").build(),
                ServerSentEvent.<String>builder().data("[DONE]").build()
        );
    }
}
