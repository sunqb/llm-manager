package com.llmmanager.agent.message;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 系统消息
 * 用于定义 AI 的行为、角色和约束条件
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SystemMessage extends Message {

    public SystemMessage(String content) {
        super(MessageType.SYSTEM, content);
    }

    /**
     * 静态工厂方法
     */
    public static SystemMessage of(String content) {
        return new SystemMessage(content);
    }
}
