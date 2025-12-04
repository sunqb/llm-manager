package com.llmmanager.ops.controller;

import com.llmmanager.agent.config.ToolFunctionManager;
import com.llmmanager.agent.message.MediaMessage;
import com.llmmanager.agent.storage.core.service.MediaFileService;
import com.llmmanager.ops.dto.StreamChatRequest;
import com.llmmanager.service.dto.StreamResponseFormatter;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.service.AgentService;
import com.llmmanager.service.dto.ChatStreamChunk;
import com.llmmanager.service.orchestration.LlmExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一对话 Controller
 *
 * 提供统一的流式对话入口，支持：
 * - 基础对话（modelId 或 agentSlug）
 * - 工具调用（enableTools）
 * - 思考模式（自动支持，无需额外参数）
 * - 多模态对话（mediaUrls）
 */
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

    @Resource
    private MediaFileService mediaFileService;

    @Resource
    private StreamResponseFormatter responseFormatter;

    // ==================== 统一流式入口 ====================

    /**
     * 统一流式对话接口
     *
     * 支持所有对话场景，通过请求参数区分：
     * - 基础对话：message + modelId
     * - 智能体对话：message + agentSlug
     * - 工具调用：enableTools=true
     * - 多模态：mediaUrls 传入图片URL
     *
     * 注意：reasoning 内容会自动返回（如果模型支持，如 DeepSeek R1）
     *
     * @param request 统一请求参数
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody StreamChatRequest request) {
        log.info("[ChatController] 统一流式对话，modelId: {}, agentSlug: {}, conversationId: {}, " +
                        "enableTools: {}, mediaUrls: {}",
                request.getModelId(), request.getAgentSlug(), request.getConversationId(),
                request.getEnableTools(),
                request.getMediaUrls() != null ? request.getMediaUrls().size() : 0);

        // 参数校验
        if (!StringUtils.hasText(request.getMessage())) {
            return errorResponse("消息内容不能为空");
        }
        if (request.getModelId() == null && !StringUtils.hasText(request.getAgentSlug())) {
            return errorResponse("modelId 和 agentSlug 必须指定一个");
        }

        // 路由到统一处理逻辑
        Flux<ChatStreamChunk> chunkFlux = routeAndExecute(request);
        return responseFormatter.format(chunkFlux);
    }

    /**
     * 路由并执行对话（返回 ChatStreamChunk 流）
     */
    private Flux<ChatStreamChunk> routeAndExecute(StreamChatRequest request) {
        String message = request.getMessage();
        String conversationId = request.getConversationId();
        String thinkingMode = request.getThinkingMode();
        String reasoningFormat = request.getReasoningFormat();

        // 1. 智能体对话
        if (StringUtils.hasText(request.getAgentSlug())) {
            return executeAgentChat(request.getAgentSlug(), message, conversationId, thinkingMode, reasoningFormat);
        }

        Long modelId = request.getModelId();

        // 2. 多模态对话
        if (!CollectionUtils.isEmpty(request.getMediaUrls())) {
            return executeMediaChat(modelId, message, request.getMediaUrls(), conversationId, thinkingMode, reasoningFormat);
        }

        // 3. 工具调用对话
        if (Boolean.TRUE.equals(request.getEnableTools())) {
            return executionService.streamWithTools(modelId, message, conversationId, request.getToolNames(), thinkingMode, reasoningFormat);
        }

        // 4. 基础对话（自动支持 reasoning）
        return executionService.stream(modelId, message, conversationId, thinkingMode, reasoningFormat);
    }

    /**
     * 智能体对话
     */
    private Flux<ChatStreamChunk> executeAgentChat(String slug, String message, String conversationId,
                                                     String thinkingMode, String reasoningFormat) {
        Agent agent = agentService.findBySlug(slug);
        if (agent == null) {
            log.error("[ChatController] 智能体不存在，slug: {}", slug);
            return Flux.just(ChatStreamChunk.ofContent("智能体不存在: " + slug), ChatStreamChunk.done());
        }

        return executionService.streamWithAgent(agent, message, conversationId, thinkingMode, reasoningFormat)
                .doOnError(error -> log.error("[ChatController] 智能体对话失败，slug: {}", slug, error));
    }

    /**
     * 多模态对话
     */
    private Flux<ChatStreamChunk> executeMediaChat(Long modelId, String message,
                                                    List<String> mediaUrls, String conversationId,
                                                    String thinkingMode, String reasoningFormat) {
        List<String> validUrls = mediaUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        if (validUrls.isEmpty()) {
            return Flux.just(ChatStreamChunk.ofContent("没有有效的媒体URL"), ChatStreamChunk.done());
        }

        List<MediaMessage.MediaContent> mediaContents = new ArrayList<>();
        for (String url : validUrls) {
            mediaContents.add(MediaMessage.MediaContent.ofImageUrl(url));
        }

        return executionService.streamWithMedia(modelId, message, mediaContents, conversationId, thinkingMode, reasoningFormat)
                .doOnComplete(() -> saveMediaUrls(conversationId, validUrls))
                .doOnError(error -> log.error("[ChatController] 多模态对话失败", error));
    }

    /**
     * 保存媒体URL到数据库
     */
    private void saveMediaUrls(String conversationId, List<String> mediaUrls) {
        if (conversationId != null && !mediaUrls.isEmpty()) {
            try {
                mediaFileService.saveImageUrlsForLatestUserMessage(conversationId, mediaUrls);
                log.info("[ChatController] 已保存媒体URL，会话: {}, 数量: {}", conversationId, mediaUrls.size());
            } catch (Exception e) {
                log.error("[ChatController] 保存媒体URL失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 构建错误响应
     */
    private Flux<ServerSentEvent<String>> errorResponse(String message) {
        return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"" + message + "\"}")
                .build());
    }

    // ==================== 简化版接口（兼容旧版本） ====================

    /**
     * 同步对话接口
     */
    @PostMapping("/{modelId}")
    public String chat(@PathVariable Long modelId, @RequestBody String message) {
        return executionService.chat(modelId, message);
    }

    /**
     * 获取所有可用的工具列表
     */
    @GetMapping("/tools")
    public Map<String, Object> getAvailableTools() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tools", toolFunctionManager.getAllTools());
        return response;
    }

    /**
     * 同步工具调用对话
     */
    @PostMapping("/{modelId}/with-tools")
    public Map<String, Object> chatWithTools(
            @PathVariable Long modelId,
            @RequestBody String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) List<String> toolNames) {

        Map<String, Object> response = new HashMap<>();
        try {
            String llmResponse = executionService.chatWithTools(modelId, message, conversationId, toolNames);
            response.put("success", true);
            response.put("message", llmResponse);
            response.put("toolsUsed", toolNames != null ? toolNames : toolFunctionManager.getAllToolNames());
        } catch (Exception e) {
            log.error("[ChatController] 工具调用对话失败", e);
            response.put("success", false);
            response.put("error", "对话失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 同步多模态对话
     */
    @PostMapping("/{modelId}/with-media/sync")
    public Map<String, Object> chatWithMediaSync(
            @PathVariable Long modelId,
            @RequestParam String message,
            @RequestParam List<String> mediaUrls,
            @RequestParam(required = false) String conversationId) {

        log.info("[ChatController] 同步多模态对话，模型: {}, 媒体数: {}", modelId, mediaUrls.size());

        List<String> validUrls = mediaUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();

        Map<String, Object> response = new HashMap<>();
        try {
            List<MediaMessage.MediaContent> mediaContents = new ArrayList<>();
            for (String url : validUrls) {
                mediaContents.add(MediaMessage.MediaContent.ofImageUrl(url));
            }

            String result = executionService.chatWithMedia(modelId, message, mediaContents, conversationId);
            response.put("success", true);
            response.put("message", result);

            saveMediaUrls(conversationId, validUrls);
        } catch (Exception e) {
            log.error("[ChatController] 同步多模态对话失败", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }
}
