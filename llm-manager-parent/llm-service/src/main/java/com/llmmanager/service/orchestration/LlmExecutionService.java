package com.llmmanager.service.orchestration;

import com.llmmanager.agent.agent.LlmChatAgent;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.service.LlmModelService;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
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
 * 负责获取模型配置、Channel配置，并调用llm-agent执行对话
 */
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

    /**
     * 普通对话
     */
    public String chat(Long modelId, String userMessage) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        return executeRequest(model, userMessage, null, model.getTemperature());
    }

    /**
     * 使用Agent进行对话
     */
    public String chatWithAgent(Agent agent, String userMessage) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ?
                      agent.getTemperatureOverride() : model.getTemperature();

        return executeRequest(model, userMessage, agent.getSystemPrompt(), temp);
    }

    /**
     * 使用模板进行对话
     */
    public String chatWithTemplate(Long modelId, String templateContent, Map<String, Object> variables) {
        PromptTemplate template = new PromptTemplate(templateContent);
        String message = template.render(variables);
        return chat(modelId, message);
    }

    /**
     * 流式对话（指定 conversationId）
     *
     * @param conversationId 会话ID（前端传递，null 表示不启用历史）
     */
    public Flux<String> streamChat(Long modelId, String userMessage, String conversationId) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        // 只有前端传入了 conversationId 才启用历史对话
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            System.out.println("[StreamChat] 请求模型: " + model.getModelIdentifier() + ", 会话ID: " + conversationId);
            return executeStreamRequest(model, userMessage, null, model.getTemperature(), conversationId);
        } else {
            System.out.println("[StreamChat] 请求模型: " + model.getModelIdentifier() + ", 无会话历史");
            return executeStreamRequest(model, userMessage, null, model.getTemperature(), null);
        }
    }

    /**
     * 流式对话（不启用历史）
     */
    public Flux<String> streamChat(Long modelId, String userMessage) {
        return streamChat(modelId, userMessage, null);
    }

    /**
     * 使用Agent进行流式对话（指定 conversationId）
     */
    public Flux<String> streamChatWithAgent(Agent agent, String userMessage, String conversationId) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ?
                      agent.getTemperatureOverride() : model.getTemperature();

        // 只有前端传入了 conversationId 才启用历史对话
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            return executeStreamRequest(model, userMessage, agent.getSystemPrompt(), temp, conversationId);
        } else {
            return executeStreamRequest(model, userMessage, agent.getSystemPrompt(), temp, null);
        }
    }

    /**
     * 使用Agent进行流式对话（不启用历史）
     */
    public Flux<String> streamChatWithAgent(Agent agent, String userMessage) {
        return streamChatWithAgent(agent, userMessage, null);
    }

    /**
     * 流式对话（返回完整响应，支持 reasoning）
     */
    public Flux<ChatResponse> streamChatResponse(Long modelId, String userMessage, String conversationId) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        // 只有前端传入了 conversationId 才启用历史对话
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            return executeStreamResponseRequest(model, userMessage, null, model.getTemperature(), conversationId);
        } else {
            return executeStreamResponseRequest(model, userMessage, null, model.getTemperature(), null);
        }
    }

    /**
     * 流式对话（返回完整响应，不启用历史）
     */
    public Flux<ChatResponse> streamChatResponse(Long modelId, String userMessage) {
        return streamChatResponse(modelId, userMessage, null);
    }

    /**
     * 使用Agent进行流式对话（返回完整响应，支持 reasoning）
     */
    public Flux<ChatResponse> streamChatResponseWithAgent(Agent agent, String userMessage, String conversationId) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ?
                      agent.getTemperatureOverride() : model.getTemperature();

        // 只有前端传入了 conversationId 才启用历史对话
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            return executeStreamResponseRequest(model, userMessage, agent.getSystemPrompt(), temp, conversationId);
        } else {
            return executeStreamResponseRequest(model, userMessage, agent.getSystemPrompt(), temp, null);
        }
    }

    /**
     * 使用Agent进行流式对话（返回完整响应，不启用历史）
     */
    public Flux<ChatResponse> streamChatResponseWithAgent(Agent agent, String userMessage) {
        return streamChatResponseWithAgent(agent, userMessage, null);
    }

    /**
     * 执行同步请求 - 通过llm-agent
     */
    private String executeRequest(LlmModel model, String userMessage, String systemPrompt, Double temperature) {
        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequest(channel, model, userMessage, systemPrompt, temperature);
        return llmChatAgent.chat(request);
    }

    /**
     * 执行流式请求 - 通过llm-agent
     */
    private Flux<String> executeStreamRequest(LlmModel model, String userMessage, String systemPrompt, Double temperature, String conversationId) {
        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequest(channel, model, userMessage, systemPrompt, temperature);
        return llmChatAgent.streamChat(request, conversationId);
    }

    /**
     * 执行流式请求（返回完整响应）- 通过llm-agent
     */
    private Flux<ChatResponse> executeStreamResponseRequest(LlmModel model, String userMessage, String systemPrompt, Double temperature, String conversationId) {
        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequest(channel, model, userMessage, systemPrompt, temperature);
        return llmChatAgent.streamChatResponse(request, conversationId);
    }

    /**
     * 获取Channel配置
     */
    private Channel getChannel(LlmModel model) {
        if (model.getChannelId() == null) {
            throw new RuntimeException(String.format(
                "模型 [ID=%d, Name=%s] 未关联任何渠道，请先在管理后台为该模型配置渠道",
                model.getId(), model.getName()
            ));
        }

        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException(String.format(
                "模型 [ID=%d, Name=%s] 关联的渠道 [ID=%d] 不存在，请检查渠道配置",
                model.getId(), model.getName(), model.getChannelId()
            ));
        }
        return channel;
    }

    /**
     * 构建ChatRequest
     */
    private ChatRequest buildChatRequest(Channel channel, LlmModel model,
                                        String userMessage, String systemPrompt, Double temperature) {
        // 优先使用 Channel 配置，没有则使用默认配置
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
     * 带工具调用的对话（使用所有工具）
     * LLM 会自动判断是否需要调用工具
     */
    public String chatWithTools(Long modelId, String userMessage, String conversationId) {
        return chatWithTools(modelId, userMessage, conversationId, null);
    }

    /**
     * 带工具调用的对话（指定工具列表）
     * LLM 会自动判断是否需要调用工具
     *
     * @param modelId        模型ID
     * @param userMessage    用户消息
     * @param conversationId 会话ID
     * @param toolNames      指定的工具名称列表（null 表示使用所有已注册的工具）
     */
    public String chatWithTools(Long modelId, String userMessage, String conversationId, List<String> toolNames) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequestWithTools(channel, model, userMessage, null, model.getTemperature(), toolNames);
        return llmChatAgent.chat(request, conversationId);
    }

    /**
     * 带工具调用的流式对话（使用所有工具）
     * LLM 会自动判断是否需要调用工具
     */
    public Flux<String> streamChatWithTools(Long modelId, String userMessage, String conversationId) {
        return streamChatWithTools(modelId, userMessage, conversationId, null);
    }

    /**
     * 带工具调用的流式对话（指定工具列表）
     * LLM 会自动判断是否需要调用工具
     *
     * @param modelId        模型ID
     * @param userMessage    用户消息
     * @param conversationId 会话ID
     * @param toolNames      指定的工具名称列表（null 表示使用所有已注册的工具）
     */
    public Flux<String> streamChatWithTools(Long modelId, String userMessage, String conversationId, List<String> toolNames) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequestWithTools(channel, model, userMessage, null, model.getTemperature(), toolNames);
        return llmChatAgent.streamChat(request, conversationId);
    }

    /**
     * 构建ChatRequest（启用工具调用）
     *
     * @param toolNames 指定的工具名称列表（null 表示使用所有已注册的工具）
     */
    private ChatRequest buildChatRequestWithTools(Channel channel, LlmModel model,
                                                  String userMessage, String systemPrompt, Double temperature,
                                                  List<String> toolNames) {
        // 优先使用 Channel 配置，没有则使用默认配置
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
                .enableTools(true)  // 启用工具调用
                .toolNames(toolNames)  // 指定工具列表（null 表示使用所有）
                .build();
    }

    /**
     * 清除指定 Channel 的缓存（当 Channel 配置更新时调用）
     */
    public void clearCacheForChannel(Long channelId) {
        llmChatAgent.clearCacheForChannel(channelId);
    }
}
