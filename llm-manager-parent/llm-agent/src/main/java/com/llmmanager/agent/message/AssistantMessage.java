package com.llmmanager.agent.message;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 助手消息
 * 代表 AI 的回复内容
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AssistantMessage extends Message {

    public AssistantMessage(String content) {
        super(MessageType.ASSISTANT, content);
    }

    /**
     * 静态工厂方法
     */
    public static AssistantMessage of(String content) {
        return new AssistantMessage(content);
    }
}
