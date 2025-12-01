package com.llmmanager.agent.model.impl;

import com.llmmanager.agent.message.AssistantMessage;
import com.llmmanager.agent.message.Message;
import com.llmmanager.agent.message.MessageConverter;
import com.llmmanager.agent.model.ChatModel;
import com.llmmanager.agent.model.ChatOptions;
import com.llmmanager.agent.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI ChatModel 适配器
 * 将现有的 Spring AI OpenAI 实现适配到新的抽象层
 */
public class OpenAiChatModelAdapter implements ChatModel {

    private final String baseUrl;
    private final String apiKey;
    private final Long channelId;

    // 缓存 OpenAiChatModel 实例
    private static final Map<String, OpenAiChatModel> chatModelCache = new ConcurrentHashMap<>();

    public OpenAiChatModelAdapter(String baseUrl, String apiKey, Long channelId) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.channelId = channelId;
    }

    @Override
    public ChatResponse chat(List<Message> messages, ChatOptions options) {
        ChatClient chatClient = createChatClient();

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> springAiMessages =
                MessageConverter.toSpringAiMessages(messages);

        // 构建 OpenAI 选项
        OpenAiChatOptions openAiOptions = buildOpenAiOptions(options);

        // 执行对话
        ChatClient.CallResponseSpec responseSpec = chatClient.prompt()
                .messages(springAiMessages)
                .options(openAiOptions)
                .call();

        String content = responseSpec.content();

        return ChatResponse.builder()
                .message(AssistantMessage.of(content))
                .model(options.getModel())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public Flux<String> streamChat(List<Message> messages, ChatOptions options) {
        ChatClient chatClient = createChatClient();

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> springAiMessages =
                MessageConverter.toSpringAiMessages(messages);

        // 构建 OpenAI 选项
        OpenAiChatOptions openAiOptions = buildOpenAiOptions(options);

        System.out.println("[OpenAI Adapter] 开始流式请求: " + options.getModel() + " via " + baseUrl);

        // 执行流式对话
        return chatClient.prompt()
                .messages(springAiMessages)
                .options(openAiOptions)
                .stream()
                .content()
                .doOnNext(chunk -> System.out.println("[OpenAI Adapter] 收到数据块"))
                .doOnComplete(() -> System.out.println("[OpenAI Adapter] 流式请求完成"))
                .doOnError(error -> System.err.println("[OpenAI Adapter] 错误: " + error.getMessage()));
    }

    @Override
    public String getModelIdentifier() {
        return "openai";
    }

    @Override
    public void clearCache() {
        String cacheKey = buildCacheKey();
        chatModelCache.remove(cacheKey);
    }

    /**
     * 创建 ChatClient
     */
    private ChatClient createChatClient() {
        String cacheKey = buildCacheKey();

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

    /**
     * 构建 OpenAI 选项
     */
    private OpenAiChatOptions buildOpenAiOptions(ChatOptions options) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(options.getModel())
                .temperature(options.getTemperature());

        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStop() != null && options.getStop().length > 0) {
            builder.stop(List.of(options.getStop()));
        }

        return builder.build();
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey() {
        return channelId + "_" + apiKey + "_" + baseUrl;
    }

    /**
     * 清除指定 Channel 的缓存
     */
    public static void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
    }

    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        chatModelCache.clear();
    }
}
