package com.example.llmmanager.service;

import com.example.llmmanager.entity.Agent;
import com.example.llmmanager.entity.Channel;
import com.example.llmmanager.entity.LlmModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LlmExecutionService {

    private final LlmModelService llmModelService;
    private final ChannelService channelService;
    // 缓存已创建的 ChatModel，避免重复创建
    private final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    public LlmExecutionService(LlmModelService llmModelService, ChannelService channelService) {
        this.llmModelService = llmModelService;
        this.channelService = channelService;
    }

    /**
     * 根据 Channel 配置动态创建 ChatClient
     * 优先使用 Channel 配置，没有则使用默认配置
     */
    private ChatClient createChatClient(Channel channel) {
        // 优先使用 Channel 配置，没有则使用默认配置
        String apiKey = StringUtils.hasText(channel.getApiKey()) ? channel.getApiKey() : defaultApiKey;
        String baseUrl = StringUtils.hasText(channel.getBaseUrl()) ? channel.getBaseUrl() : defaultBaseUrl;

        String cacheKey = channel.getId() + "_" + apiKey + "_" + baseUrl;

        OpenAiChatModel chatModel = chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();

            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .build();
        });

        return ChatClient.builder(chatModel).build();
    }

    public String chat(Long modelId, String userMessage) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        return executeRequest(model, userMessage, null);
    }

    public String chatWithAgent(Agent agent, String userMessage) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();

        return executeRequest(model, userMessage, agent.getSystemPrompt(), temp);
    }

    private String executeRequest(LlmModel model, String userMessage, String systemPrompt) {
        return executeRequest(model, userMessage, systemPrompt, model.getTemperature());
    }

    private String executeRequest(LlmModel model, String userMessageStr, String systemPromptStr, Double temperature) {
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException("Model has no associated channel");
        }

        ChatClient chatClient = createChatClient(channel);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelIdentifier())
                .temperature(temperature)
                .build();

        var promptSpec = chatClient.prompt();
        if (systemPromptStr != null && !systemPromptStr.isEmpty()) {
            promptSpec = promptSpec.system(systemPromptStr);
        }
        return promptSpec
                .user(userMessageStr)
                .options(options)
                .call()
                .content();
    }

    public String chatWithTemplate(Long modelId, String templateContent, Map<String, Object> variables) {
        PromptTemplate template = new PromptTemplate(templateContent);
        String message = template.render(variables);
        return chat(modelId, message);
    }

    // 流式聊天方法
    public Flux<String> streamChat(Long modelId, String userMessage) {
        LlmModel model = llmModelService.getById(modelId);
        if (model == null) {
            throw new RuntimeException("Model not found");
        }

        return executeStreamRequest(model, userMessage, null);
    }

    public Flux<String> streamChatWithAgent(Agent agent, String userMessage) {
        LlmModel model = llmModelService.getById(agent.getLlmModelId());
        if (model == null) {
            throw new RuntimeException("Model not found for agent");
        }
        Double temp = agent.getTemperatureOverride() != null ? agent.getTemperatureOverride() : model.getTemperature();

        return executeStreamRequest(model, userMessage, agent.getSystemPrompt(), temp);
    }

    private Flux<String> executeStreamRequest(LlmModel model, String userMessage, String systemPrompt) {
        return executeStreamRequest(model, userMessage, systemPrompt, model.getTemperature());
    }

    private Flux<String> executeStreamRequest(LlmModel model, String userMessageStr, String systemPromptStr, Double temperature) {
        Channel channel = channelService.getById(model.getChannelId());
        if (channel == null) {
            throw new RuntimeException("Model has no associated channel");
        }

        ChatClient chatClient = createChatClient(channel);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelIdentifier())
                .temperature(temperature)
                .build();

        System.out.println("[LLM Stream] 开始流式请求: " + model.getModelIdentifier() + " via " + channel.getBaseUrl());

        var promptSpec = chatClient.prompt();
        if (systemPromptStr != null && !systemPromptStr.isEmpty()) {
            promptSpec = promptSpec.system(systemPromptStr);
        }

        return promptSpec
                .user(userMessageStr)
                .options(options)
                .stream()
                .content()
                .doOnNext(chunk -> System.out.println("[LLM Stream] 收到数据块"))
                .doOnComplete(() -> System.out.println("[LLM Stream] 流式请求完成"))
                .doOnError(error -> System.err.println("[LLM Stream] 错误: " + error.getMessage()));
    }

    /**
     * 清除指定 Channel 的缓存（当 Channel 配置更新时调用）
     */
    public void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
    }
}
