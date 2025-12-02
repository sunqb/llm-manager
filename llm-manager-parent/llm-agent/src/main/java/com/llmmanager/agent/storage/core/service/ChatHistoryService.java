package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.ChatHistory;

import java.util.List;

/**
 * 聊天历史 Service 接口
 *
 * 职责：封装 ChatHistory 的 CRUD 操作
 * 调用方：storage/memory 层的业务类
 */
public interface ChatHistoryService {

    /**
     * 保存单条聊天历史
     *
     * @param chatHistory 聊天历史实体
     */
    void save(ChatHistory chatHistory);

    /**
     * 批量保存聊天历史
     *
     * @param histories 聊天历史列表
     */
    void saveBatch(List<ChatHistory> histories);

    /**
     * 查询会话的所有历史消息（按时间升序）
     *
     * @param conversationId 会话ID
     * @return 聊天历史列表
     */
    List<ChatHistory> findByConversationId(String conversationId);

    /**
     * 查询会话的最近 N 条消息
     *
     * @param conversationId 会话ID
     * @param limit 限制条数
     * @return 聊天历史列表（按时间升序）
     */
    List<ChatHistory> findRecentMessages(String conversationId, int limit);

    /**
     * 查询所有会话ID
     *
     * @return 会话ID列表
     */
    List<String> findAllConversationIds();

    /**
     * 软删除指定会话的所有历史
     *
     * @param conversationId 会话ID
     */
    void deleteByConversationId(String conversationId);

    /**
     * 删除过期的历史消息（硬删除）
     *
     * @param retentionDays 保留天数
     * @return 删除的记录数
     */
    int deleteExpiredMessages(int retentionDays);
}
