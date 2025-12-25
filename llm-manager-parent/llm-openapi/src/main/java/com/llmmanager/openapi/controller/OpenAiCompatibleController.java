package com.llmmanager.openapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.openapi.dto.openai.ChatCompletionRequest;
import com.llmmanager.openapi.dto.openai.ChatCompletionResponse;
import com.llmmanager.openapi.dto.openai.ModelsResponse;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.core.service.LlmModelService;
import com.llmmanager.service.dto.ChatStreamChunk;
import com.llmmanager.service.orchestration.LlmExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容 Chat API Controller
 *
 * 提供标准的 OpenAI Chat Completions API，用于直接调用 LLM 模型
 *
 * API 设计：
 * - /v1/chat/completions     - 标准 Chat Completions（直接调用模型）
 * - /v1/models               - 列出可用模型
 *
 * Model 格式：
 * - {modelId}                - 直接使用模型 ID（如 "1", "2"）
 * - {modelIdentifier}        - 使用模型标识（如 "gpt-4", "qwen-plus"）
 *
 * 使用示例（Python）：
 * <pre>
 * import openai
 *
 * client = openai.OpenAI(
 *     api_key="your-api-key",
 *     base_url="https://your-domain/v1"
 * )
 *
 * # 直接调用模型
 * response = client.chat.completions.create(
 *     model="1",  # 模型 ID
 *     messages=[{"role": "user", "content": "你好"}]
 * )
 * </pre>
 *
 * 注意：如需调用 ReactAgent，请使用 /v1/agents/{slug}/completions
 *
 * @author LLM Manager
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class OpenAiCompatibleController {

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private AgentService agentService;

    @Resource
    private LlmExecutionService llmExecutionService;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== Chat Completions API ====================

    /**
     * Chat Completions 接口（标准 OpenAI 格式）
     *
     * POST /v1/chat/completions
     *
     * 直接调用 LLM 模型，不经过 Agent 逻辑
     */
    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody ChatCompletionRequest request) {
        validateRequest(request);

        if (Boolean.TRUE.equals(request.getStream())) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(chatCompletionsStream(request));
        }

        return ResponseEntity.ok(executeChatCompletion(request));
    }

    /**
     * Chat Completions 流式接口（显式路由）
     *
     * POST /v1/chat/completions/stream
     */
    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatCompletionsStreamExplicit(@RequestBody ChatCompletionRequest request) {
        request.setStream(true);
        return chatCompletionsStream(request);
    }

    // ==================== Models API ====================

    /**
     * 列出可用模型
     *
     * GET /v1/models
     *
     * 返回系统中配置的 LLM 模型列表
     */
    @GetMapping("/models")
    public ModelsResponse listModels() {
        List<ModelsResponse.Model> models = new ArrayList<>();

        // 添加 LLM 模型
        List<LlmModel> llmModels = llmModelService.findAll();
        for (LlmModel model : llmModels) {
            models.add(ModelsResponse.Model.builder()
                    .id(String.valueOf(model.getId()))
                    .description(model.getName() + " (" + model.getModelIdentifier() + ")")
                    .build());
        }

        return ModelsResponse.builder()
                .data(models)
                .build();
    }

    /**
     * 获取单个模型信息
     *
     * GET /v1/models/{model}
     */
    @GetMapping("/models/{model}")
    public ModelsResponse.Model getModel(@PathVariable String model) {
        LlmModel llmModel = findModel(model);
        if (llmModel == null) {
            throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "Model not found: " + model);
        }

        return ModelsResponse.Model.builder()
                .id(String.valueOf(llmModel.getId()))
                .description(llmModel.getName() + " (" + llmModel.getModelIdentifier() + ")")
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 执行同步 Chat Completion
     */
    private ChatCompletionResponse executeChatCompletion(ChatCompletionRequest request) {
        String model = request.getModel();
        String userMessage = request.getUserMessage();
        String systemMessage = request.getSystemMessage();

        log.info("[OpenAI API] 同步请求，model: {}", model);

        try {
            LlmModel llmModel = findModel(model);
            if (llmModel == null) {
                throw new BusinessException(ResultCode.MODEL_NOT_FOUND, "Model not found: " + model);
            }

            // 直接调用模型
            String result = llmExecutionService.chat(llmModel.getId(), userMessage, null, systemMessage);
            return ChatCompletionResponse.success(model, result);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OpenAI API] 执行失败，model: {}", model, e);
            throw new BusinessException(ResultCode.MODEL_CALL_FAILED, "Chat failed: " + e.getMessage());
        }
    }

    /**
     * 执行流式 Chat Completion
     */
    private Flux<ServerSentEvent<String>> chatCompletionsStream(ChatCompletionRequest request) {
        String model = request.getModel();
        String userMessage = request.getUserMessage();
        String systemMessage = request.getSystemMessage();

        log.info("[OpenAI API] 流式请求，model: {}", model);

        LlmModel llmModel = findModel(model);
        if (llmModel == null) {
            return errorStream("Model not found: " + model);
        }

        // 首个 chunk
        Flux<ServerSentEvent<String>> firstChunk = Flux.just(
                toSseEvent(ChatCompletionResponse.streamChunk(model, "", true))
        );

        // 流式执行
        Flux<ServerSentEvent<String>> resultStream = llmExecutionService
                .streamChat(llmModel.getId(), userMessage, null, systemMessage)
                .filter(chunk -> chunk != null && chunk.hasContent())
                .map(chunk -> {
                    String content = chunk.getContent() != null ? chunk.getContent() : "";
                    return toSseEvent(ChatCompletionResponse.streamChunk(model, content, false));
                });

        // 结束 chunk
        Flux<ServerSentEvent<String>> lastChunk = Flux.just(
                toSseEvent(ChatCompletionResponse.streamDone(model)),
                ServerSentEvent.<String>builder().data("[DONE]").build()
        );

        return Flux.concat(firstChunk, resultStream, lastChunk)
                .onErrorResume(error -> {
                    log.error("[OpenAI API] 流式执行失败，model: {}", model, error);
                    return Flux.just(
                            toSseEvent("{\"error\":\"" + error.getMessage() + "\"}"),
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                });
    }

    /**
     * 查找模型
     *
     * 支持按 ID 或 modelIdentifier 查找
     */
    private LlmModel findModel(String model) {
        if (!StringUtils.hasText(model)) {
            return null;
        }

        // 尝试按 ID 查找
        try {
            Long modelId = Long.parseLong(model);
            return llmModelService.findById(modelId);
        } catch (NumberFormatException e) {
            // 不是数字，按 modelIdentifier 查找
            return llmModelService.findByModelIdentifier(model);
        }
    }

    /**
     * 校验请求参数
     */
    private void validateRequest(ChatCompletionRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Request body is required");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Model is required");
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
            log.error("[OpenAI API] JSON 序列化失败", e);
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
