package com.llmmanager.agent.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 工具消息
 * 用于封装工具调用的结果
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolMessage extends Message {

    /**
     * 工具调用ID（关联到具体的工具调用请求）
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具执行结果
     */
    private String toolResult;

    /**
     * 工具执行状态
     */
    private ToolStatus status;

    /**
     * 错误信息（如果执行失败）
     */
    private String errorMessage;

    /**
     * 构造函数
     */
    public ToolMessage(String toolCallId, String toolName, String toolResult) {
        super(MessageType.TOOL, toolResult);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolResult = toolResult;
        this.status = ToolStatus.SUCCESS;
    }

    /**
     * 静态工厂方法 - 成功
     */
    public static ToolMessage success(String toolCallId, String toolName, String toolResult) {
        ToolMessage message = new ToolMessage(toolCallId, toolName, toolResult);
        message.setStatus(ToolStatus.SUCCESS);
        return message;
    }

    /**
     * 静态工厂方法 - 失败
     */
    public static ToolMessage failure(String toolCallId, String toolName, String errorMessage) {
        ToolMessage message = new ToolMessage(toolCallId, toolName, "");
        message.setStatus(ToolStatus.FAILURE);
        message.setErrorMessage(errorMessage);
        return message;
    }

    /**
     * 工具执行状态枚举
     */
    public enum ToolStatus {
        /**
         * 执行成功
         */
        SUCCESS,

        /**
         * 执行失败
         */
        FAILURE,

        /**
         * 执行中
         */
        PENDING
    }
}
