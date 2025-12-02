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

/**
 * 聊天历史实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "a_chat_history", autoResultMap = true)
public class ChatHistory extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private String conversationId;

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
}
