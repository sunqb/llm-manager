package com.llmmanager.agent.advisor;

import com.llmmanager.agent.message.AssistantMessage;
import com.llmmanager.agent.message.Message;
import com.llmmanager.agent.message.UserMessage;

/**
 * 聊天历史管理器
 * 简化版实现，不依赖 Spring AI 的 Advisor API
 */
public class ChatMemoryManager {

    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final ChatMemoryStore memoryStore;
    private final int historyLimit;

    public ChatMemoryManager(ChatMemoryStore memoryStore) {
        this(memoryStore, DEFAULT_HISTORY_LIMIT);
    }

    public ChatMemoryManager(ChatMemoryStore memoryStore, int historyLimit) {
        this.memoryStore = memoryStore;
        this.historyLimit = historyLimit;
    }

    /**
     * 获取历史消息
     */
    public java.util.List<Message> getHistory(String conversationId) {
        if (conversationId == null) {
            return new java.util.ArrayList<>();
        }
        return memoryStore.getMessages(conversationId, historyLimit);
    }

    /**
     * 保存用户消息
     */
    public void saveUserMessage(String conversationId, String content) {
        if (conversationId != null && content != null) {
            Message userMessage = UserMessage.of(content);
            memoryStore.addMessage(conversationId, userMessage);
        }
    }

    /**
     * 保存助手回复
     */
    public void saveAssistantMessage(String conversationId, String content) {
        if (conversationId != null && content != null) {
            Message assistantMessage = AssistantMessage.of(content);
            memoryStore.addMessage(conversationId, assistantMessage);
        }
    }

    /**
     * 清除会话历史
     */
    public void clearHistory(String conversationId) {
        if (conversationId != null) {
            memoryStore.clearMessages(conversationId);
        }
    }
}
