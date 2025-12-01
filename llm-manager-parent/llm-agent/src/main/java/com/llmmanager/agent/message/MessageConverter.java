package com.llmmanager.agent.message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息转换器
 * 负责在自定义 Message 和 Spring AI Message 之间进行转换
 */
public class MessageConverter {

    /**
     * 将自定义 Message 转换为 Spring AI Message
     */
    public static org.springframework.ai.chat.messages.Message toSpringAiMessage(Message message) {
        if (message == null) {
            return null;
        }

        return switch (message.getMessageType()) {
            case SYSTEM -> new org.springframework.ai.chat.messages.SystemMessage(message.getContent());
            case USER -> new org.springframework.ai.chat.messages.UserMessage(message.getContent());
            case ASSISTANT -> new org.springframework.ai.chat.messages.AssistantMessage(message.getContent());
            default -> throw new IllegalArgumentException("不支持的消息类型: " + message.getMessageType());
        };
    }

    /**
     * 将 Spring AI Message 转换为自定义 Message
     */
    public static Message fromSpringAiMessage(org.springframework.ai.chat.messages.Message springAiMessage) {
        if (springAiMessage == null) {
            return null;
        }

        // 根据消息类型提取内容
        String content;
        if (springAiMessage instanceof org.springframework.ai.chat.messages.SystemMessage systemMsg) {
            content = systemMsg.getText();
            return SystemMessage.of(content);
        } else if (springAiMessage instanceof org.springframework.ai.chat.messages.UserMessage userMsg) {
            content = userMsg.getText();
            return UserMessage.of(content);
        } else if (springAiMessage instanceof org.springframework.ai.chat.messages.AssistantMessage assistantMsg) {
            content = assistantMsg.getText();
            return AssistantMessage.of(content);
        } else {
            throw new IllegalArgumentException("不支持的 Spring AI 消息类型: " + springAiMessage.getClass());
        }
    }

    /**
     * 批量转换为 Spring AI Message 列表
     */
    public static List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        return messages.stream()
                .map(MessageConverter::toSpringAiMessage)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换为自定义 Message 列表
     */
    public static List<Message> fromSpringAiMessages(List<org.springframework.ai.chat.messages.Message> springAiMessages) {
        if (springAiMessages == null || springAiMessages.isEmpty()) {
            return new ArrayList<>();
        }
        return springAiMessages.stream()
                .map(MessageConverter::fromSpringAiMessage)
                .collect(Collectors.toList());
    }
}
