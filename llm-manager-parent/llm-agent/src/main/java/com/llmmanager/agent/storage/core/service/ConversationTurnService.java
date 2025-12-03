package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.ConversationTurn;

import java.util.List;
import java.util.Optional;

/**
 * 对话轮次 Service 接口
 *
 * 职责：封装 ConversationTurn 的 CRUD 操作和业务逻辑
 *
 * 命名规范：
 * - turnCode：轮次业务唯一标识（32位UUID）
 * - conversationCode：会话业务唯一标识
 */
public interface ConversationTurnService {

    /**
     * 创建新轮次
     *
     * @param conversationCode 会话标识
     * @return 创建的轮次实体
     */
    ConversationTurn create(String conversationCode);

    /**
     * 创建新轮次（带模型信息）
     *
     * @param conversationCode 会话标识
     * @param modelId 模型ID
     * @param modelIdentifier 模型标识符
     * @return 创建的轮次实体
     */
    ConversationTurn create(String conversationCode, Long modelId, String modelIdentifier);

    /**
     * 保存轮次
     *
     * @param turn 轮次实体
     */
    void save(ConversationTurn turn);

    /**
     * 更新轮次
     *
     * @param turn 轮次实体
     */
    void update(ConversationTurn turn);

    /**
     * 根据 turnCode 查询
     *
     * @param turnCode 轮次标识
     * @return 轮次实体（可能为空）
     */
    Optional<ConversationTurn> findByTurnCode(String turnCode);

    /**
     * 查询会话的所有轮次
     *
     * @param conversationCode 会话标识
     * @return 轮次列表（按轮次序号升序）
     */
    List<ConversationTurn> findByConversationCode(String conversationCode);

    /**
     * 查询最近 N 个轮次
     *
     * @param conversationCode 会话标识
     * @param limit 限制条数
     * @return 轮次列表（按轮次序号升序）
     */
    List<ConversationTurn> findRecentTurns(String conversationCode, int limit);

    /**
     * 获取下一个轮次序号
     *
     * @param conversationCode 会话标识
     * @return 下一个轮次序号
     */
    int getNextTurnIndex(String conversationCode);

    /**
     * 统计轮次数量
     *
     * @param conversationCode 会话标识
     * @return 轮次数量
     */
    int countByConversationCode(String conversationCode);

    /**
     * 统计成功的轮次数量
     *
     * @param conversationCode 会话标识
     * @return 成功轮次数量
     */
    int countSuccessTurns(String conversationCode);

    /**
     * 统计会话的总 token 消耗
     *
     * @param conversationCode 会话标识
     * @return 总 token 数
     */
    int sumTotalTokens(String conversationCode);

    /**
     * 标记轮次为处理中
     *
     * @param turnCode 轮次标识
     */
    void markProcessing(String turnCode);

    /**
     * 标记轮次为成功
     *
     * @param turnCode 轮次标识
     * @param promptTokens 输入 token 数
     * @param completionTokens 输出 token 数
     * @param latencyMs 耗时（毫秒）
     */
    void markSuccess(String turnCode, int promptTokens, int completionTokens, int latencyMs);

    /**
     * 标记轮次为失败
     *
     * @param turnCode 轮次标识
     * @param errorMessage 错误信息
     */
    void markFailed(String turnCode, String errorMessage);

    /**
     * 更新用户消息标识
     *
     * @param turnCode 轮次标识
     * @param userMessageCode 用户消息标识
     */
    void updateUserMessageCode(String turnCode, String userMessageCode);

    /**
     * 更新助手消息标识
     *
     * @param turnCode 轮次标识
     * @param assistantMessageCode 助手消息标识
     */
    void updateAssistantMessageCode(String turnCode, String assistantMessageCode);

    /**
     * 软删除指定会话的所有轮次
     *
     * @param conversationCode 会话标识
     */
    void deleteByConversationCode(String conversationCode);

    /**
     * 查询会话最近的未完成轮次（PENDING 或 PROCESSING 状态）
     *
     * @param conversationCode 会话标识
     * @return 最近的未完成轮次（可能为空）
     */
    Optional<ConversationTurn> findLatestPendingTurn(String conversationCode);
}
