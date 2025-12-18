package com.llmmanager.service.orchestration;

import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.service.core.entity.Channel;
import com.llmmanager.service.core.entity.LlmModel;
import com.llmmanager.service.core.service.ChannelService;
import com.llmmanager.service.core.service.LlmModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatModel 统一提供者
 * 
 * 统一管理 ChatModel/ChatClient 的获取和缓存，避免各服务重复实现
 * 
 * @author LLM Manager
 */
@Slf4j
@Service
public class ChatModelProvider {

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private ChannelService channelService;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    // ChatModel 缓存
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    /**
     * 根据模型 ID 获取 OpenAiChatModel
     */
    public OpenAiChatModel getChatModelByModelId(Long modelId) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);

        String apiKey = getApiKey(channel);
        String baseUrl = getBaseUrl(channel);
        String cacheKey = buildCacheKey(channel.getId(), apiKey, baseUrl, model.getModelIdentifier());

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model.getModelIdentifier())
                            .temperature(model.getTemperature() != null ? model.getTemperature() : 0.7)
                            .build())
                    .build();
        });
    }

    /**
     * 根据模型 ID 获取 ChatClient
     */
    public ChatClient getChatClientByModelId(Long modelId) {
        OpenAiChatModel chatModel = getChatModelByModelId(modelId);
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 根据模型 ID 构建 ChatRequest
     */
    public ChatRequest buildChatRequest(Long modelId) {
        LlmModel model = getModel(modelId);
        Channel channel = getChannel(model);

        return ChatRequest.builder()
                .channelId(channel.getId())
                .apiKey(getApiKey(channel))
                .baseUrl(getBaseUrl(channel))
                .modelIdentifier(model.getModelIdentifier())
                .temperature(model.getTemperature())
                .build();
    }

    /**
     * 获取模型配置
     */
    public LlmModel getModel(Long modelId) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        return model;
    }

    /**
     * 获取渠道配置
     */
    public Channel getChannel(LlmModel model) {
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在: " + model.getChannelId());
        }
        return channel;
    }

    /**
     * 获取 API Key（优先使用渠道配置，否则使用默认值）
     */
    public String getApiKey(Channel channel) {
        return StringUtils.hasText(channel.getApiKey()) ? channel.getApiKey() : defaultApiKey;
    }

    /**
     * 获取 Base URL（优先使用渠道配置，否则使用默认值）
     */
    public String getBaseUrl(Channel channel) {
        return StringUtils.hasText(channel.getBaseUrl()) ? channel.getBaseUrl() : defaultBaseUrl;
    }

    /**
     * 清除指定渠道的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
        log.info("[ChatModelProvider] 已清除渠道 {} 的缓存", channelId);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        chatModelCache.clear();
        log.info("[ChatModelProvider] 已清除所有缓存");
    }

    private String buildCacheKey(Long channelId, String apiKey, String baseUrl, String modelIdentifier) {
        return String.format("%d_%s_%s_%s", channelId, apiKey, baseUrl, modelIdentifier);
    }
}

