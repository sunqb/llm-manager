package com.llmmanager.agent.review.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人工审核上下文
 *
 * 保存 ReactAgent 执行过程中需要的上下文信息，
 * 供 HumanReviewTool 使用
 *
 * @author LLM Manager
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanReviewContext {

    /**
     * 会话标识
     */
    private String conversationCode;

    /**
     * Agent 配置 Code
     */
    private String agentConfigCode;

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * Agent 类型（SINGLE/SEQUENTIAL/SUPERVISOR）
     */
    private String agentType;

    /**
     * 原始任务消息（用户发给 Agent 的原始问题/指令）
     * 审核通过恢复执行时，用于重建上下文
     */
    private String originalTask;

    /**
     * 模型 ID（用于 single/modelId 路径，agentConfigCode 为 null 时恢复执行）
     */
    private Long modelId;
}
