package com.llmmanager.agent.agent;

import com.llmmanager.agent.advisor.ChatMemoryManager;
import com.llmmanager.agent.advisor.ChatMemoryStore;
import com.llmmanager.agent.dto.ChatRequest;
import com.llmmanager.agent.message.Message;
import com.llmmanager.agent.message.SystemMessage;
import com.llmmanager.agent.message.UserMessage;
import com.llmmanager.agent.model.ChatModel;
import com.llmmanager.agent.model.ChatOptions;
import com.llmmanager.agent.model.ChatResponse;
import com.llmmanager.agent.model.impl.OpenAiChatModelAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM对话代理 - 重构版
 * 使用新的抽象层（ChatModel、Message、ChatMemoryManager）
 */
@Component
public class LlmChatAgent {

    private final ChatMemoryManager chatMemoryManager;

    public LlmChatAgent(ChatMemoryStore chatMemoryStore) {
        this.chatMemoryManager = new ChatMemoryManager(chatMemoryStore, 10);
    }

    /**
     * 执行同步对话
     */
    public String chat(ChatRequest request) {
        return chat(request, null);
    }

    /**
     * 执行同步对话（支持会话ID）
     */
    public String chat(ChatRequest request, String conversationId) {
        // 创建 ChatModel
        ChatModel chatModel = createChatModel(request);

        // 构建消息列表（包含历史消息）
        List<Message> messages = buildMessagesWithHistory(request, conversationId);

        // 构建选项
        ChatOptions options = buildChatOptions(request);

        // 保存用户消息
        if (conversationId != null) {
            chatMemoryManager.saveUserMessage(conversationId, request.getUserMessage());
        }

        // 执行对话
        ChatResponse response = chatModel.chat(messages, options);
        String assistantReply = response.getMessage().getContent();

        // 保存助手回复
        if (conversationId != null) {
            chatMemoryManager.saveAssistantMessage(conversationId, assistantReply);
        }

        return assistantReply;
    }

    /**
     * 执行流式对话
     */
    public Flux<String> streamChat(ChatRequest request) {
        return streamChat(request, null);
    }

    /**
     * 执行流式对话（支持会话ID）
     */
    public Flux<String> streamChat(ChatRequest request, String conversationId) {
        // 创建 ChatModel
        ChatModel chatModel = createChatModel(request);

        // 构建消息列表（包含历史消息）
        List<Message> messages = buildMessagesWithHistory(request, conversationId);

        // 构建选项
        ChatOptions options = buildChatOptions(request);

        System.out.println("[LLM Agent] 开始流式请求: " + request.getModelIdentifier() + " via " + request.getBaseUrl());

        // 保存用户消息
        if (conversationId != null) {
            chatMemoryManager.saveUserMessage(conversationId, request.getUserMessage());
        }

        // 用于聚合流式响应
        AtomicReference<StringBuilder> assistantReply = new AtomicReference<>(new StringBuilder());

        // 执行流式对话
        return chatModel.streamChat(messages, options)
                .doOnNext(chunk -> {
                    // 聚合响应内容
                    assistantReply.get().append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式完成后，保存助手回复
                    if (conversationId != null) {
                        String fullReply = assistantReply.get().toString();
                        chatMemoryManager.saveAssistantMessage(conversationId, fullReply);
                    }
                    System.out.println("[LLM Agent] 流式请求完成");
                })
                .doOnError(error -> System.err.println("[LLM Agent] 错误: " + error.getMessage()));
    }

    /**
     * 创建 ChatModel
     */
    private ChatModel createChatModel(ChatRequest request) {
        return new OpenAiChatModelAdapter(
                request.getBaseUrl(),
                request.getApiKey(),
                request.getChannelId()
        );
    }

    /**
     * 构建消息列表（包含历史消息）
     */
    private List<Message> buildMessagesWithHistory(ChatRequest request, String conversationId) {
        List<Message> messages = new ArrayList<>();

        // 添加系统提示词
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(SystemMessage.of(request.getSystemPrompt()));
        }

        // 添加历史消息（如果有 conversationId）
        if (conversationId != null) {
            List<Message> historyMessages = chatMemoryManager.getHistory(conversationId);
            messages.addAll(historyMessages);
        }

        // 添加当前用户消息
        messages.add(UserMessage.of(request.getUserMessage()));

        return messages;
    }

    /**
     * 构建 ChatOptions
     */
    private ChatOptions buildChatOptions(ChatRequest request) {
        return ChatOptions.builder()
                .model(request.getModelIdentifier())
                .temperature(request.getTemperature())
                .build();
    }

    /**
     * 清除指定 Channel 的缓存
     */
    public void clearCacheForChannel(Long channelId) {
        OpenAiChatModelAdapter.clearCacheForChannel(channelId);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        OpenAiChatModelAdapter.clearAllCache();
    }

    /**
     * 清除会话历史
     */
    public void clearConversationHistory(String conversationId) {
        chatMemoryManager.clearHistory(conversationId);
    }
}
