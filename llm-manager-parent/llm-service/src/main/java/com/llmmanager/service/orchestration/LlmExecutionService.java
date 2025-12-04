package com.llmmanager.service.orchestration;

import com.llmmanager.agent.agent.LlmChatAgent;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.message.MediaMessage;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.service.LlmModelService;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.dto.ChatStreamChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * LLM执行服务 - 业务逻辑编排层
 *
 * 核心设计：
 * - 负责获取模型配置、Channel配置
 * - 调用 llm-agent 执行对话
 * - 统一返回 ChatStreamChunk（流式）或 String（同步）
 * - 格式转换（ChatResponse → ChatStreamChunk）在此层处理
 */
@Slf4j
@Service
public class LlmExecutionService {

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private ChannelService channelService;

    @Resource
    private LlmChatAgent llmChatAgent;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    // ==================== 同步对话 ====================

    /**
     * 普通对话
     */
    public String chat(Long modelId, String userMessage) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);
        ChatRequest request = buildRequest(channel, model, userMessage, null, model.getTemperature());
        return llmChatAgent.chat(request);
    }

    /**
     * 带工具调用的对话
     */
    public String chatWithTools(Long modelId, String userMessage, String conversationId, List<String> toolNames) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);
        ChatRequest request = buildRequest(channel, model, userMessage, null, model.getTemperature())
                .toBuilder()
                .enableTools(true)
                .toolNames(toolNames)
                .build();
        return llmChatAgent.chat(request, conversationId);
    }

    /**
     * 多模态同步对话
     */
    public String chatWithMedia(Long modelId, String userMessage,
                                 List<MediaMessage.MediaContent> mediaContents,
                                 String conversationId) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);
        ChatRequest request = buildRequest(channel, model, userMessage, null, model.getTemperature())
                .toBuilder()
                .mediaContents(mediaContents)
                .build();
        return llmChatAgent.chat(request, conversationId);
    }

    /**
     * 使用模板进行对话
     */
    public String chatWithTemplate(Long modelId, String templateContent, Map<String, Object> variables) {
        PromptTemplate template = new PromptTemplate(templateContent);
        String message = template.render(variables);
        return chat(modelId, message);
    }

    // ==================== 流式对话（统一返回 ChatStreamChunk）====================

    /**
     * 统一流式对话
     *
     * @param modelId        模型ID
     * @param userMessage    用户消息
     * @param conversationId 会话ID（null 表示不启用历史）
     * @param thinkingMode   思考模式（enabled/disabled/auto 或 low/medium/high）
     * @param reasoningFormat Reasoning 参数格式（DOUBAO/OPENAI/DEEPSEEK/AUTO，null 则自动推断）
     * @return ChatStreamChunk 流（包含 content + reasoning）
     */
    public Flux<ChatStreamChunk> stream(Long modelId, String userMessage, String conversationId,
                                         String thinkingMode, String reasoningFormat) {
        LlmModel model = getModel(modelId);
        return executeStream(model, userMessage, null, model.getTemperature(), conversationId, null, null, thinkingMode, reasoningFormat);
    }

    /**
     * 统一流式对话（自动推断 reasoningFormat）
     */
    public Flux<ChatStreamChunk> stream(Long modelId, String userMessage, String conversationId, String thinkingMode) {
        return stream(modelId, userMessage, conversationId, thinkingMode, null);
    }

    /**
     * 统一流式对话（简化版）
     */
    public Flux<ChatStreamChunk> stream(Long modelId, String userMessage, String conversationId) {
        return stream(modelId, userMessage, conversationId, null, null);
    }

    /**
     * 智能体流式对话
     */
    public Flux<ChatStreamChunk> streamWithAgent(Agent agent, String userMessage, String conversationId,
                                                   String thinkingMode, String reasoningFormat) {
        LlmModel model = getModel(agent.getLlmModelId());
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();
        return executeStream(model, userMessage, agent.getSystemPrompt(), temp, conversationId, null, null, thinkingMode, reasoningFormat);
    }

    /**
     * 智能体流式对话（自动推断 reasoningFormat）
     */
    public Flux<ChatStreamChunk> streamWithAgent(Agent agent, String userMessage, String conversationId, String thinkingMode) {
        return streamWithAgent(agent, userMessage, conversationId, thinkingMode, null);
    }

    /**
     * 智能体流式对话（简化版）
     */
    public Flux<ChatStreamChunk> streamWithAgent(Agent agent, String userMessage, String conversationId) {
        return streamWithAgent(agent, userMessage, conversationId, null, null);
    }

    /**
     * 智能体同步对话（外部 API 兼容）
     */
    public String chatWithAgent(Agent agent, String userMessage) {
        LlmModel model = getModel(agent.getLlmModelId());
        Channel channel = getChannel(model);
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();

        ChatRequest request = buildRequest(channel, model, userMessage, agent.getSystemPrompt(), temp);
        return llmChatAgent.chat(request);
    }

    /**
     * 智能体流式对话（外部 API 兼容，返回纯 String 流）
     */
    public Flux<String> streamChatWithAgent(Agent agent, String userMessage) {
        return streamWithAgent(agent, userMessage, null)
                .filter(chunk -> !chunk.isDone() && chunk.getContent() != null)
                .map(ChatStreamChunk::getContent);
    }

    /**
     * 带工具调用的流式对话
     */
    public Flux<ChatStreamChunk> streamWithTools(Long modelId, String userMessage,
                                                  String conversationId, List<String> toolNames,
                                                  String thinkingMode, String reasoningFormat) {
        LlmModel model = getModel(modelId);
        return executeStream(model, userMessage, null, model.getTemperature(), conversationId, toolNames, null, thinkingMode, reasoningFormat);
    }

    /**
     * 带工具调用的流式对话（自动推断 reasoningFormat）
     */
    public Flux<ChatStreamChunk> streamWithTools(Long modelId, String userMessage,
                                                  String conversationId, List<String> toolNames, String thinkingMode) {
        return streamWithTools(modelId, userMessage, conversationId, toolNames, thinkingMode, null);
    }

    /**
     * 多模态流式对话
     */
    public Flux<ChatStreamChunk> streamWithMedia(Long modelId, String userMessage,
                                                  List<MediaMessage.MediaContent> mediaContents,
                                                  String conversationId, String thinkingMode, String reasoningFormat) {
        LlmModel model = getModel(modelId);
        return executeStream(model, userMessage, null, model.getTemperature(), conversationId, null, mediaContents, thinkingMode, reasoningFormat);
    }

    /**
     * 多模态流式对话（自动推断 reasoningFormat）
     */
    public Flux<ChatStreamChunk> streamWithMedia(Long modelId, String userMessage,
                                                  List<MediaMessage.MediaContent> mediaContents,
                                                  String conversationId, String thinkingMode) {
        return streamWithMedia(modelId, userMessage, mediaContents, conversationId, thinkingMode, null);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行流式请求并转换为 ChatStreamChunk
     */
    private Flux<ChatStreamChunk> executeStream(LlmModel model, String userMessage, String systemPrompt,
                                                 Double temperature, String conversationId,
                                                 List<String> toolNames,
                                                 List<MediaMessage.MediaContent> mediaContents,
                                                 String thinkingMode, String reasoningFormat) {
        log.info("[LlmExecutionService] executeStream - thinkingMode: '{}', reasoningFormat: '{}'", thinkingMode, reasoningFormat);

        Channel channel = getChannel(model);

        // 构建请求
        ChatRequest.ChatRequestBuilder builder = ChatRequest.builder()
                .channelId(channel.getId())
                .apiKey(StringUtils.hasText(channel.getApiKey()) ? channel.getApiKey() : defaultApiKey)
                .baseUrl(StringUtils.hasText(channel.getBaseUrl()) ? channel.getBaseUrl() : defaultBaseUrl)
                .modelIdentifier(model.getModelIdentifier())
                .temperature(temperature)
                .systemPrompt(systemPrompt)
                .userMessage(userMessage);

        // 工具调用
        if (toolNames != null) {
            builder.enableTools(true).toolNames(toolNames);
        }

        // 多模态
        if (mediaContents != null && !mediaContents.isEmpty()) {
            builder.mediaContents(mediaContents);
        }

        // 思考模式
        if (StringUtils.hasText(thinkingMode)) {
            builder.thinkingMode(thinkingMode);
        }

        // Reasoning 格式（如果指定）
        if (StringUtils.hasText(reasoningFormat)) {
            try {
                ChatRequest.ReasoningFormat format = ChatRequest.ReasoningFormat.valueOf(reasoningFormat.toUpperCase());
                builder.reasoningFormat(format);
            } catch (IllegalArgumentException e) {
                log.warn("[LlmExecutionService] 无效的 reasoningFormat: '{}', 使用 AUTO", reasoningFormat);
            }
        }

        ChatRequest request = builder.build();
        log.info("[LlmExecutionService] ChatRequest - thinkingMode: '{}', reasoningFormat: {}",
                request.getThinkingMode(), request.getReasoningFormat());

        String convId = StringUtils.hasText(conversationId) ? conversationId : null;

        // 调用 Agent 层获取 ChatResponse 流，转换为 ChatStreamChunk
        return llmChatAgent.stream(request, convId)
                .mapNotNull(this::convertToChunk)
                .concatWith(Flux.just(ChatStreamChunk.done()));
    }

    /**
     * 将 ChatResponse 转换为 ChatStreamChunk
     */
    private ChatStreamChunk convertToChunk(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return null;
        }

        var result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return null;
        }

        var output = result.getOutput();
        String content = output.getText();

        // 提取 reasoning（如果存在）
        String reasoning = extractReasoning(output.getMetadata());

        // 如果都为空，跳过
        boolean hasContent = content != null && !content.isEmpty();
        boolean hasReasoning = reasoning != null && !reasoning.isEmpty();
        if (!hasContent && !hasReasoning) {
            return null;
        }

        return ChatStreamChunk.of(content, reasoning);
    }

    /**
     * 从 metadata 提取 reasoning
     */
    private String extractReasoning(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        // Spring AI 1.1+ 使用 "reasoningContent"
        Object reasoningObj = metadata.get("reasoningContent");
        if (reasoningObj == null) {
            // 兼容原始格式
            reasoningObj = metadata.get("reasoning_content");
        }

        return reasoningObj != null ? reasoningObj.toString() : null;
    }

    /**
     * 获取模型
     */
    private LlmModel getModel(Long modelId) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found: " + modelId);
        }
        return model;
    }

    /**
     * 获取 Channel 配置
     */
    private Channel getChannel(LlmModel model) {
        if (model.getChannelId() == null) {
            throw new RuntimeException(String.format(
                "模型 [ID=%d, Name=%s] 未关联任何渠道",
                model.getId(), model.getName()
            ));
        }

        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException(String.format(
                "模型 [ID=%d] 关联的渠道 [ID=%d] 不存在",
                model.getId(), model.getChannelId()
            ));
        }
        return channel;
    }

    /**
     * 构建 ChatRequest
     */
    private ChatRequest buildRequest(Channel channel, LlmModel model,
                                      String userMessage, String systemPrompt, Double temperature) {
        String apiKey = StringUtils.hasText(channel.getApiKey()) ? channel.getApiKey() : defaultApiKey;
        String baseUrl = StringUtils.hasText(channel.getBaseUrl()) ? channel.getBaseUrl() : defaultBaseUrl;

        return ChatRequest.builder()
                .channelId(channel.getId())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelIdentifier(model.getModelIdentifier())
                .temperature(temperature)
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();
    }

    /**
     * 清除指定 Channel 的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        llmChatAgent.clearCacheForChannel(channelId);
    }
}
