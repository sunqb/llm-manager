package com.llmmanager.agent.storage.core.service;

import com.llmmanager.agent.storage.core.entity.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * 会话 Service 接口
 *
 * 职责：封装 Conversation 的 CRUD 操作和业务逻辑
 *
 * 命名规范：
 * - conversationCode：会话业务唯一标识（UUID）
 */
public interface ConversationService {

    /**
     * 创建新会话
     *
     * @param conversationCode 会话标识
     * @return 创建的会话实体
     */
    Conversation create(String conversationCode);

    /**
     * 创建新会话（带Agent）
     *
     * @param conversationCode 会话标识
     * @param agentSlug Agent标识
     * @param modelId 模型ID
     * @return 创建的会话实体
     */
    Conversation create(String conversationCode, String agentSlug, Long modelId);

    /**
     * 获取或创建会话
     * 如果会话不存在则创建新会话
     *
     * @param conversationCode 会话标识
     * @return 会话实体
     */
    Conversation getOrCreate(String conversationCode);

    /**
     * 获取或创建会话（带Agent）
     *
     * @param conversationCode 会话标识
     * @param agentSlug Agent标识
     * @param modelId 模型ID
     * @return 会话实体
     */
    Conversation getOrCreate(String conversationCode, String agentSlug, Long modelId);

    /**
     * 根据会话标识查询
     *
     * @param conversationCode 会话标识
     * @return 会话实体（可能为空）
     */
    Optional<Conversation> findByConversationCode(String conversationCode);

    /**
     * 检查会话是否存在
     *
     * @param conversationCode 会话标识
     * @return 是否存在
     */
    boolean exists(String conversationCode);

    /**
     * 根据Agent标识查询会话列表
     *
     * @param agentSlug Agent标识
     * @return 会话列表
     */
    List<Conversation> findByAgentSlug(String agentSlug);

    /**
     * 查询所有会话标识
     *
     * @return 会话标识列表
     */
    List<String> findAllConversationCodes();

    /**
     * 查询会话列表（分页）
     *
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @param archived 是否归档（null表示全部）
     * @return 会话列表
     */
    List<Conversation> findAll(int page, int size, Boolean archived);

    /**
     * 更新会话标题
     *
     * @param conversationCode 会话标识
     * @param title 新标题
     */
    void updateTitle(String conversationCode, String title);

    /**
     * 增加消息数量
     *
     * @param conversationCode 会话标识
     * @param count 增加的数量
     */
    void incrementMessageCount(String conversationCode, int count);

    /**
     * 增加tokens消耗
     *
     * @param conversationCode 会话标识
     * @param tokens 增加的tokens数
     */
    void addTokens(String conversationCode, int tokens);

    /**
     * 归档会话
     *
     * @param conversationCode 会话标识
     */
    void archive(String conversationCode);

    /**
     * 置顶会话
     *
     * @param conversationCode 会话标识
     * @param pinned 是否置顶
     */
    void setPinned(String conversationCode, boolean pinned);

    /**
     * 软删除会话
     *
     * @param conversationCode 会话标识
     */
    void delete(String conversationCode);

    /**
     * 更新会话
     *
     * @param conversation 会话实体
     */
    void update(Conversation conversation);
}
