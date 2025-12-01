package com.llmmanager.service.orchestration;

import com.llmmanager.agent.agent.LlmChatAgent;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.service.core.ChannelService;
import com.llmmanager.service.core.LlmModelService;
import com.llmmanager.service.core.entity.Agent;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * LLM执行服务 - 业务逻辑编排层
 * 负责获取模型配置、Channel配置，并调用llm-agent执行对话
 */
@Service
public class LlmExecutionService {

    private final LlmModelService llmModelService;
    private final ChannelService channelService;
    private final LlmChatAgent llmChatAgent;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    public LlmExecutionService(LlmModelService llmModelService,
                              ChannelService channelService,
                              LlmChatAgent llmChatAgent) {
        this.llmModelService = llmModelService;
        this.channelService = channelService;
        this.llmChatAgent = llmChatAgent;
    }

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
     * 流式对话
     */
    public Flux<String> streamChat(Long modelId, String userMessage) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        return executeStreamRequest(model, userMessage, null, model.getTemperature());
    }

    /**
     * 使用Agent进行流式对话
     */
    public Flux<String> streamChatWithAgent(Agent agent, String userMessage) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ?
                      agent.getTemperatureOverride() : model.getTemperature();

        return executeStreamRequest(model, userMessage, agent.getSystemPrompt(), temp);
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
    private Flux<String> executeStreamRequest(LlmModel model, String userMessage, String systemPrompt, Double temperature) {
        Channel channel = getChannel(model);
        ChatRequest request = buildChatRequest(channel, model, userMessage, systemPrompt, temperature);
        return llmChatAgent.streamChat(request);
    }

    /**
     * 获取Channel配置
     */
    private Channel getChannel(LlmModel model) {
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException("Model has no associated channel");
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
     * 清除指定 Channel 的缓存（当 Channel 配置更新时调用）
     */
    public void clearCacheForChannel(Long channelId) {
        llmChatAgent.clearCacheForChannel(channelId);
    }
}
