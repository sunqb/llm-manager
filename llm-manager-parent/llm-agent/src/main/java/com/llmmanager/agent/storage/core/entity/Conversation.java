package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 会话实体
 * 存储会话级别的元数据
 *
 * 命名规范：
 * - id：主键（自增整数）
 * - conversationCode：业务唯一标识（UUID）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_conversations", autoResultMap = true)
public class Conversation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话唯一标识（UUID）
     */
    private String conversationCode;

    /**
     * 会话标题（自动生成或用户设置）
     */
    private String title;

    /**
     * 关联的Agent标识
     */
    private String agentSlug;

    /**
     * 使用的模型ID
     */
    private Long modelId;

    /**
     * 会话摘要
     */
    private String summary;

    /**
     * 消息总数
     */
    private Integer messageCount;

    /**
     * 总tokens消耗
     */
    private Integer totalTokens;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageTime;

    /**
     * 是否归档，0：否，1：是
     */
    private Integer isArchived;

    /**
     * 是否置顶，0：否，1：是
     */
    private Integer isPinned;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 额外元数据（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建新会话的工厂方法
     */
    public static Conversation create(String conversationCode) {
        Conversation conversation = new Conversation();
        conversation.setConversationCode(conversationCode);
        conversation.setMessageCount(0);
        conversation.setTotalTokens(0);
        conversation.setIsArchived(0);
        conversation.setIsPinned(0);
        return conversation;
    }

    /**
     * 创建新会话的工厂方法（自动生成 code）
     */
    public static Conversation createNew() {
        return create(generateCode());
    }

    /**
     * 创建新会话的工厂方法（带Agent）
     */
    public static Conversation create(String conversationCode, String agentSlug, Long modelId) {
        Conversation conversation = create(conversationCode);
        conversation.setAgentSlug(agentSlug);
        conversation.setModelId(modelId);
        return conversation;
    }

    /**
     * 生成会话唯一标识
     */
    public static String generateCode() {
        return "conv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 增加消息数量
     */
    public void incrementMessageCount() {
        this.messageCount = (this.messageCount == null ? 0 : this.messageCount) + 1;
        this.lastMessageTime = LocalDateTime.now();
    }

    /**
     * 增加消息数量（指定数量）
     */
    public void incrementMessageCount(int count) {
        this.messageCount = (this.messageCount == null ? 0 : this.messageCount) + count;
        this.lastMessageTime = LocalDateTime.now();
    }

    /**
     * 增加tokens消耗
     */
    public void addTokens(int tokens) {
        this.totalTokens = (this.totalTokens == null ? 0 : this.totalTokens) + tokens;
    }
}
