package com.llmmanager.agent.message;

/**
 * 消息类型枚举
 */
public enum MessageType {
    /**
     * 系统消息：定义 AI 行为和角色
     */
    SYSTEM,

    /**
     * 用户消息：来自用户的输入
     */
    USER,

    /**
     * 助手消息：AI 的回复
     */
    ASSISTANT,

    /**
     * 工具消息：工具调用的结果（预留）
     */
    TOOL
}
