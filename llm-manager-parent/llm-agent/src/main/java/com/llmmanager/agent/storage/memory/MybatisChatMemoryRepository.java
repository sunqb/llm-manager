package com.llmmanager.agent.storage.memory;

import com.llmmanager.agent.storage.core.entity.ChatHistory;
import com.llmmanager.agent.storage.core.service.ChatHistoryService;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis 实现的 ChatMemoryRepository
 *
 * 职责：将 Spring AI 的 ChatMemoryRepository 接口适配到 MySQL 存储（通过 Service 层）
 *
 * 数据流向：Spring AI ChatMemory → 本类 → ChatHistoryService → ChatHistoryMapper → MySQL
 */
@Repository
public class MybatisChatMemoryRepository implements ChatMemoryRepository {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public void saveAll(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
        if (conversationId == null || CollectionUtils.isEmpty(messages)) {
            return;
        }

        // 查询数据库中已有的最大消息序号
        Integer maxIndex = chatHistoryService.getMaxMessageIndex(conversationId);
        int startIndex = (maxIndex == null) ? 0 : maxIndex + 1;

        // 只保存增量部分（新增的消息）
        if (messages.size() <= startIndex) {
            // 没有新消息需要保存
            return;
        }

        // 获取需要新增的消息（跳过已存在的部分）
        List<org.springframework.ai.chat.messages.Message> newMessages = messages.subList(startIndex, messages.size());

        List<ChatHistory> histories = new ArrayList<>();
        int currentIndex = startIndex;
        for (org.springframework.ai.chat.messages.Message message : newMessages) {
            ChatHistory chatHistory = new ChatHistory();
            chatHistory.setConversationId(conversationId);
            chatHistory.setMessageIndex(currentIndex);  // 设置消息序号
            chatHistory.setContent(message.getText());
            chatHistory.setMessageType(mapMessageType(message));

            // 保存元数据（可选）
            if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
                chatHistory.setMetadata(message.getMetadata());
            }

            histories.add(chatHistory);
            currentIndex++;
        }

        chatHistoryService.saveBatch(histories);
    }

    @Override
    public List<org.springframework.ai.chat.messages.Message> findByConversationId(String conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryService.findByConversationId(conversationId);

        if (CollectionUtils.isEmpty(histories)) {
            return Collections.emptyList();
        }

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatHistory history : histories) {
            org.springframework.ai.chat.messages.Message message = convertToSpringAiMessage(history);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    /**
     * 查询指定数量的最近消息（扩展方法，非接口要求）
     */
    public List<org.springframework.ai.chat.messages.Message> findByConversationId(String conversationId, int lastN) {
        if (conversationId == null) {
            return Collections.emptyList();
        }

        List<ChatHistory> histories = chatHistoryService.findRecentMessages(conversationId, lastN);

        if (CollectionUtils.isEmpty(histories)) {
            return Collections.emptyList();
        }

        // 转换为 Spring AI Message
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (ChatHistory history : histories) {
            org.springframework.ai.chat.messages.Message message = convertToSpringAiMessage(history);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    @Override
    public List<String> findConversationIds() {
        return chatHistoryService.findAllConversationIds();
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null) {
            return;
        }

        chatHistoryService.deleteByConversationId(conversationId);
    }

    /**
     * 映射消息类型：Spring AI Message → 字符串
     */
    private String mapMessageType(org.springframework.ai.chat.messages.Message message) {
        if (message instanceof org.springframework.ai.chat.messages.SystemMessage) {
            return "SYSTEM";
        } else if (message instanceof org.springframework.ai.chat.messages.UserMessage) {
            return "USER";
        } else if (message instanceof org.springframework.ai.chat.messages.AssistantMessage) {
            return "ASSISTANT";
        } else {
            return "TOOL";
        }
    }

    /**
     * 转换：ChatHistory → Spring AI Message
     */
    private org.springframework.ai.chat.messages.Message convertToSpringAiMessage(ChatHistory history) {
        String messageType = history.getMessageType();
        String content = history.getContent();

        switch (messageType) {
            case "SYSTEM":
                return new org.springframework.ai.chat.messages.SystemMessage(content);
            case "USER":
                return new org.springframework.ai.chat.messages.UserMessage(content);
            case "ASSISTANT":
                return new org.springframework.ai.chat.messages.AssistantMessage(content);
            case "TOOL":
                // Spring AI 的 ToolResponseMessage 需要更多参数，暂时返回 null
                return null;
            default:
                return new org.springframework.ai.chat.messages.UserMessage(content);
        }
    }
}
