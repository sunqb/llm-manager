package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.ChatHistory;

import java.util.List;

/**
 * 聊天历史 Service 接口
 *
 * 职责：封装 ChatHistory 的 CRUD 操作
 * 调用方：storage/memory 层的业务类
 *
 * 命名规范：
 * - conversationCode：会话业务唯一标识
 * - messageCode：消息业务唯一标识
 * - turnCode：轮次业务唯一标识
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
     * @param conversationCode 会话标识
     * @return 聊天历史列表
     */
    List<ChatHistory> findByConversationCode(String conversationCode);

    /**
     * 查询轮次的所有消息
     *
     * @param turnCode 轮次标识
     * @return 聊天历史列表（按消息序号升序）
     */
    List<ChatHistory> findByTurnCode(String turnCode);

    /**
     * 查询会话的最近 N 条消息
     *
     * @param conversationCode 会话标识
     * @param limit 限制条数
     * @return 聊天历史列表（按时间升序）
     */
    List<ChatHistory> findRecentMessages(String conversationCode, int limit);

    /**
     * 根据消息标识查询
     *
     * @param messageCode 消息标识
     * @return 聊天历史
     */
    ChatHistory findByMessageCode(String messageCode);

    /**
     * 查询所有会话标识
     *
     * @return 会话标识列表
     */
    List<String> findAllConversationCodes();

    /**
     * 统计指定会话的消息数量
     *
     * @param conversationCode 会话标识
     * @return 消息数量
     */
    int countByConversationCode(String conversationCode);

    /**
     * 获取指定会话的最大消息序号
     *
     * @param conversationCode 会话标识
     * @return 最大消息序号，如果没有记录则返回null
     */
    Integer getMaxMessageIndex(String conversationCode);

    /**
     * 软删除指定会话的所有历史
     *
     * @param conversationCode 会话标识
     */
    void deleteByConversationCode(String conversationCode);

    /**
     * 删除过期的历史消息（硬删除）
     *
     * @param retentionDays 保留天数
     * @return 删除的记录数
     */
    int deleteExpiredMessages(int retentionDays);

    /**
     * 查询指定会话的最新用户消息
     *
     * @param conversationCode 会话标识
     * @return 最新的用户消息，如果没有则返回null
     */
    ChatHistory findLatestUserMessage(String conversationCode);
}
