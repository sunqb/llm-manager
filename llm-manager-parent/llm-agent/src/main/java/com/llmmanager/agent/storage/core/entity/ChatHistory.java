package com.llmmanager.agent.storage.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.llmmanager.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

/**
 * 聊天历史实体
 *
 * 命名规范：
 * - id：主键（自增整数）
 * - messageCode：消息唯一标识（UUID）
 * - conversationCode：关联的会话标识（外键）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_chat_history", autoResultMap = true)
public class ChatHistory extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息唯一标识（UUID）
     */
    private String messageCode;

    /**
     * 会话标识（关联 a_conversations.conversation_code）
     */
    private String conversationCode;

    /**
     * 轮次标识（关联 a_conversation_turns.turn_code）
     */
    private String turnCode;

    /**
     * 消息序号（同一会话内从0开始递增）
     */
    private Integer messageIndex;

    /**
     * 消息类型：SYSTEM/USER/ASSISTANT/TOOL
     */
    private String messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 元数据（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 生成消息唯一标识（32位无连字符的UUID）
     */
    public static String generateMessageCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建消息的工厂方法
     */
    public static ChatHistory create(String conversationCode, int messageIndex, String messageType, String content) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageCode(generateMessageCode());
        chatHistory.setConversationCode(conversationCode);
        chatHistory.setMessageIndex(messageIndex);
        chatHistory.setMessageType(messageType);
        chatHistory.setContent(content);
        return chatHistory;
    }
}
