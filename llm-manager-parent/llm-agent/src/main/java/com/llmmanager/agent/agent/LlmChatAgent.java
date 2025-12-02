package com.llmmanager.agent.agent;

import com.llmmanager.agent.advisor.AdvisorManager;
import com.llmmanager.agent.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM对话代理 - 统一版（基于 Spring AI）
 *
 * 核心设计：
 * - 统一使用 Spring AI ChatClient
 * - 提供封装的简单 API 和原生 API 两种使用方式
 * - 自动管理 ChatModel 缓存
 * - 支持历史对话管理
 * - 集成对话日志记录
 * - 通过 AdvisorManager 统一管理所有 Advisor
 */
@Component
public class LlmChatAgent {

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private AdvisorManager advisorManager;

    // 按需注入 MemoryAdvisor（可能为 null，如果禁用了历史功能）
    @Autowired(required = false)
    private MessageChatMemoryAdvisor memoryAdvisor;

    // 缓存 ChatModel 实例
    private static final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    // ==================== 封装的简单 API ====================

    /**
     * 执行同步对话（简单封装）
     *
     * @param request 对话请求
     * @return 助手回复
     */
    public String chat(ChatRequest request) {
        return chat(request, null);
    }

    /**
     * 执行同步对话（支持历史）
     *
     * @param request        对话请求
     * @param conversationId 会话ID（null 则不启用历史）
     * @return 助手回复
     */
    public String chat(ChatRequest request, String conversationId) {
        ChatClient chatClient = createChatClient(request, conversationId);

        // 构建 OpenAI 选项
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(request.getModelIdentifier());

        if (request.getTemperature() != null) {
            optionsBuilder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            optionsBuilder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            optionsBuilder.maxTokens(request.getMaxTokens());
        }
        if (request.getFrequencyPenalty() != null) {
            optionsBuilder.frequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            optionsBuilder.presencePenalty(request.getPresencePenalty());
        }

        // 构建 prompt
        var promptBuilder = chatClient.prompt();

        // 如果有 systemPrompt，先添加 system
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 添加用户消息、options 和 advisors
        ChatResponse response = promptBuilder
                .user(request.getUserMessage())
                .options(optionsBuilder.build())
                .advisors(advisor -> {
                    if (conversationId != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                })
                .call()
                .chatResponse();

        return response.getResult().getOutput().getText();
    }

    /**
     * 执行流式对话（简单封装）
     *
     * @param request 对话请求
     * @return 流式响应
     */
    public Flux<String> streamChat(ChatRequest request) {
        return streamChat(request, null);
    }

    /**
     * 执行流式对话（支持历史）
     *
     * @param request        对话请求
     * @param conversationId 会话ID（null 则不启用历史）
     * @return 流式响应
     */
    public Flux<String> streamChat(ChatRequest request, String conversationId) {
        ChatClient chatClient = createChatClient(request, conversationId);

        // 构建 OpenAI 选项
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(request.getModelIdentifier());

        if (request.getTemperature() != null) {
            optionsBuilder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            optionsBuilder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            optionsBuilder.maxTokens(request.getMaxTokens());
        }
        if (request.getFrequencyPenalty() != null) {
            optionsBuilder.frequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            optionsBuilder.presencePenalty(request.getPresencePenalty());
        }

        // 构建 prompt
        var promptBuilder = chatClient.prompt();

        // 如果有 systemPrompt，先添加 system
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 添加用户消息、options 和 advisors，执行流式请求
        return promptBuilder
                .user(request.getUserMessage())
                .options(optionsBuilder.build())
                .advisors(advisor -> {
                    if (conversationId != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                })
                .stream()
                .content();
    }

    /**
     * 执行流式对话（返回完整响应，支持 reasoning 等扩展字段）
     *
     * @param request        对话请求
     * @param conversationId 会话ID（null 则不启用历史）
     * @return 流式 ChatResponse
     */
    public Flux<ChatResponse> streamChatResponse(ChatRequest request, String conversationId) {
        ChatClient chatClient = createChatClient(request, conversationId);

        // 构建 OpenAI 选项
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(request.getModelIdentifier());

        if (request.getTemperature() != null) {
            optionsBuilder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            optionsBuilder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            optionsBuilder.maxTokens(request.getMaxTokens());
        }
        if (request.getFrequencyPenalty() != null) {
            optionsBuilder.frequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            optionsBuilder.presencePenalty(request.getPresencePenalty());
        }

        // 构建 prompt
        var promptBuilder = chatClient.prompt();

        // 如果有 systemPrompt，先添加 system
        if (StringUtils.hasText(request.getSystemPrompt())) {
            promptBuilder.system(request.getSystemPrompt());
        }

        // 返回完整的 ChatResponse 流
        return promptBuilder
                .user(request.getUserMessage())
                .options(optionsBuilder.build())
                .advisors(advisor -> {
                    if (conversationId != null) {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                    }
                })
                .stream()
                .chatResponse();
    }

    // ==================== Spring AI 原生 API 暴露出去可以外部直接调用 ====================

    /**
     * 创建 Spring AI ChatClient（带所有已注册的 Advisor）
     *
     * @param chatModel Spring AI 的 ChatModel 实例
     * @return 配置好所有 Advisor 的 ChatClient
     */
    public ChatClient createChatClient(ChatModel chatModel) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        // 使用 AdvisorManager 获取所有已注册的 Advisor
        List<Advisor> advisors = advisorManager.getAllAdvisors();
        if (!advisors.isEmpty()) {
            builder.defaultAdvisors(advisors.toArray(new Advisor[0]));
        }

        return builder.build();
    }

    /**
     * 创建纯净的 Spring AI ChatClient（无预配置）
     *
     * @param chatModel Spring AI 的 ChatModel 实例
     * @return 原始 ChatClient
     */
    public ChatClient createPureChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 创建带自定义 Advisor 的 Spring AI ChatClient
     * 会自动包含 AdvisorManager 中已注册的 Advisor + 额外的自定义 Advisor
     *
     * @param chatModel Spring AI 的 ChatModel 实例
     * @param advisors  额外的自定义 Advisor 列表
     * @return 配置好的 ChatClient
     */
    public ChatClient createChatClientWithAdvisors(
            ChatModel chatModel,
            org.springframework.ai.chat.client.advisor.api.Advisor... advisors) {

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        // 获取 AdvisorManager 中已注册的所有 Advisor
        List<Advisor> allAdvisors = new ArrayList<>(advisorManager.getAllAdvisors());

        // 添加额外的自定义 Advisor
        if (advisors != null && advisors.length > 0) {
            allAdvisors.addAll(Arrays.asList(advisors));
        }

        // 应用所有 Advisor
        if (!allAdvisors.isEmpty()) {
            builder.defaultAdvisors(allAdvisors.toArray(new Advisor[0]));
        }

        return builder.build();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 创建 ChatClient（内部使用）
     *
     * 设计原则：
     * 1. 默认不添加任何 Advisor（性能最优）
     * 2. 只有当 conversationId != null 时，才添加 MemoryAdvisor（按需启用历史）
     * 3. 避免所有请求都查询数据库的性能问题
     */
    private ChatClient createChatClient(ChatRequest request, String conversationId) {
        ChatModel chatModel = getOrCreateChatModel(request);

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        // 按需添加 MemoryAdvisor：只有需要历史对话时才添加
        if (conversationId != null && memoryAdvisor != null) {
            builder.defaultAdvisors(memoryAdvisor);
        }

        return builder.build();
    }

    /**
     * 获取或创建 ChatModel（带缓存）
     * 不设置 defaultOptions，通过 .options() 动态传递，提高缓存复用率
     */
    private ChatModel getOrCreateChatModel(ChatRequest request) {
        String cacheKey = buildCacheKey(request);

        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(request.getBaseUrl())
                    .apiKey(request.getApiKey())
                    .build();

            // 不设置 defaultOptions，通过调用时的 .options() 动态传递
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .build();
        });
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(ChatRequest request) {
        return request.getChannelId() + "_" + request.getApiKey() + "_" + request.getBaseUrl();
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除指定 Channel 的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        chatModelCache.entrySet().removeIf(entry -> entry.getKey().startsWith(channelId + "_"));
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        chatModelCache.clear();
    }

    /**
     * 清除会话历史
     */
    public void clearConversationHistory(String conversationId) {
        if (chatMemory != null && conversationId != null) {
            chatMemory.clear(conversationId);
        }
    }
}
