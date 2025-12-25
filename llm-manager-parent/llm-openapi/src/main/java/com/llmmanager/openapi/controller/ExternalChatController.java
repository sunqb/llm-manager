package com.llmmanager.openapi.controller;

import com.llmmanager.common.exception.BusinessException;
import com.llmmanager.common.result.Result;
import com.llmmanager.common.result.ResultCode;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.dto.ChatStreamChunk;
import com.llmmanager.service.dto.StreamResponseFormatter;
import com.llmmanager.service.orchestration.LlmExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 外部 API Controller
 *
 * 提供给外部应用调用的 API，通过 ApiKeyAuthFilter 进行认证
 *
 * 接口设计：
 * - 同步接口：POST /api/external/agents/{slug}/chat
 * - 流式接口：POST /api/external/agents/{slug}/chat/stream
 *
 * 复用 LlmExecutionService 底层逻辑，使用 Flux 输出
 */
@Slf4j
@RestController
@RequestMapping("/api/external")
public class ExternalChatController {

    @Resource
    private AgentService agentService;

    @Resource
    private LlmExecutionService executionService;

    @Resource
    private StreamResponseFormatter responseFormatter;

    // ==================== 同步接口 ====================

    /**
     * 同步对话接口
     *
     * @param slug    智能体标识
     * @param payload 请求体，包含 message 字段
     * @return 响应结果
     */
    @PostMapping("/agents/{slug}/chat")
    public Result<Map<String, Object>> chatWithAgent(
            @PathVariable String slug,
            @RequestBody Map<String, String> payload) {

        String userMessage = payload.get("message");
        String conversationCode = payload.get("conversationCode");

        // 参数校验
        if (!StringUtils.hasText(userMessage)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Message content is required");
        }

        // 查找智能体
        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            log.warn("[ExternalChatController] 智能体不存在，slug: {}", slug);
            throw new BusinessException(ResultCode.AGENT_NOT_FOUND, "Agent not found: " + slug);
        }

        try {
            log.info("[ExternalChatController] 同步对话，agent: {}, conversationCode: {}", slug, conversationCode);
            String response = executionService.chatWithAgent(agent, userMessage, conversationCode);
            return Result.success(Map.of("response", response));
        } catch (Exception e) {
            log.error("[ExternalChatController] 同步对话失败，agent: {}", slug, e);
            throw new BusinessException(ResultCode.CHAT_FAILED, "Chat failed: " + e.getMessage());
        }
    }

    // ==================== 流式接口 ====================

    /**
     * 流式对话接口（Flux 输出）
     *
     * @param slug    智能体标识
     * @param payload 请求体，包含 message 和可选的 conversationCode
     * @return SSE 流式响应
     */
    @PostMapping(value = "/agents/{slug}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatWithAgentStream(
            @PathVariable String slug,
            @RequestBody Map<String, String> payload) {

        String userMessage = payload.get("message");
        String conversationCode = payload.get("conversationCode");
        String thinkingMode = payload.get("thinkingMode");
        String reasoningFormat = payload.get("reasoningFormat");

        // 参数校验
        if (!StringUtils.hasText(userMessage)) {
            return errorResponse("Message content is required");
        }

        // 查找智能体
        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            log.warn("[ExternalChatController] 智能体不存在，slug: {}", slug);
            return errorResponse("Agent not found: " + slug);
        }

        log.info("[ExternalChatController] 流式对话，agent: {}, conversationCode: {}, thinkingMode: {}",
                slug, conversationCode, thinkingMode);

        // 调用 Service 层，返回 ChatStreamChunk 流
        Flux<ChatStreamChunk> chunkFlux = executionService.streamWithAgent(
                agent, userMessage, conversationCode, thinkingMode, reasoningFormat
        ).doOnError(error -> log.error("[ExternalChatController] 流式对话失败，agent: {}", slug, error));

        // 使用统一的格式化器转换为 SSE
        return responseFormatter.format(chunkFlux);
    }

    /**
     * 构建错误响应
     */
    private Flux<ServerSentEvent<String>> errorResponse(String message) {
        return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"" + message + "\"}")
                .build());
    }
}
