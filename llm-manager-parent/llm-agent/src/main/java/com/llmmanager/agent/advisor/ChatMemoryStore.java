package com.llmmanager.agent.advisor;

import com.llmmanager.agent.message.Message;

import java.util.List;

/**
 * 聊天历史存储接口
 * 用于持久化对话历史到 MySQL
 */
public interface ChatMemoryStore {

    /**
     * 保存对话历史
     *
     * @param conversationId 会话ID
     * @param message        消息
     */
    void addMessage(String conversationId, Message message);

    /**
     * 获取对话历史
     *
     * @param conversationId 会话ID
     * @param limit          限制条数（最近的 N 条）
     * @return 消息列表
     */
    List<Message> getMessages(String conversationId, int limit);

    /**
     * 获取所有对话历史
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<Message> getAllMessages(String conversationId);

    /**
     * 清除对话历史
     *
     * @param conversationId 会话ID
     */
    void clearMessages(String conversationId);

    /**
     * 删除过期的对话历史
     *
     * @param daysToKeep 保留天数
     */
    void deleteExpiredMessages(int daysToKeep);
}
