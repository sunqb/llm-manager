package com.llmmanager.agent.storage.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.llmmanager.agent.advisor.ChatMemoryStore;
import com.llmmanager.agent.message.Message;
import com.llmmanager.agent.storage.entity.ChatHistory;
import com.llmmanager.agent.storage.mapper.ChatHistoryMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天历史存储实现（MySQL）
 * 注意：create_time, update_time, create_by, update_by 由 MyBatis-Plus 自动填充
 */
@Service
public class ChatMemoryStoreImpl implements ChatMemoryStore {

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public void addMessage(String conversationId, Message message) {
        ChatHistory history = new ChatHistory();
        history.setConversationId(conversationId);
        history.setMessageType(message.getMessageType().name());
        history.setContent(message.getContent());
        history.setMetadata(message.getMetadata());
        // create_time 由 MyBatis-Plus 自动填充

        chatHistoryMapper.insert(history);
    }

    @Override
    public List<Message> getMessages(String conversationId, int limit) {
        List<ChatHistory> histories = chatHistoryMapper.selectRecentMessages(conversationId, limit);

        // 反转顺序（因为查询是倒序的）
        Collections.reverse(histories);

        return histories.stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> getAllMessages(String conversationId) {
        QueryWrapper<ChatHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .orderByAsc("create_time");

        List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);

        return histories.stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void clearMessages(String conversationId) {
        QueryWrapper<ChatHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);

        // MyBatis-Plus 会自动执行软删除（设置 is_delete = 1）
        chatHistoryMapper.delete(wrapper);
    }

    @Override
    public void deleteExpiredMessages(int daysToKeep) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(daysToKeep);
        chatHistoryMapper.deleteExpiredMessages(expireTime);
    }

    /**
     * 将 ChatHistory 转换为 Message
     */
    private Message convertToMessage(ChatHistory history) {
        Message message = switch (history.getMessageType()) {
            case "SYSTEM" -> new com.llmmanager.agent.message.SystemMessage(history.getContent());
            case "USER" -> new com.llmmanager.agent.message.UserMessage(history.getContent());
            case "ASSISTANT" -> new com.llmmanager.agent.message.AssistantMessage(history.getContent());
            default -> throw new IllegalArgumentException("Unknown message type: " + history.getMessageType());
        };

        if (history.getMetadata() != null) {
            message.setMetadata(history.getMetadata());
        }
        // 使用 BaseEntity 的 createTime 字段
        message.setTimestamp(history.getCreateTime());

        return message;
    }
}
