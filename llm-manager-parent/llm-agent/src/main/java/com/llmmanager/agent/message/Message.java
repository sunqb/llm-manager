package com.llmmanager.agent.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息基类
 * 所有类型的消息都继承自此类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class Message {

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息内容（文本）
     */
    private String content;

    /**
     * 消息创建时间
     */
    private LocalDateTime timestamp;

    /**
     * 扩展属性（用于存储元数据）
     */
    private Map<String, Object> metadata;

    /**
     * 构造函数
     */
    protected Message(MessageType messageType, String content) {
        this.messageType = messageType;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }
}
