package com.llmmanager.agent.message;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 用户消息
 * 代表用户的输入内容
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserMessage extends Message {

    public UserMessage(String content) {
        super(MessageType.USER, content);
    }

    /**
     * 静态工厂方法
     */
    public static UserMessage of(String content) {
        return new UserMessage(content);
    }
}
