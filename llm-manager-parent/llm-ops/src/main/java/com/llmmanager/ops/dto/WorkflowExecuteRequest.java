package com.llmmanager.ops.dto;

import lombok.Data;

import java.util.Map;

/**
 * 工作流执行请求 DTO
 */
@Data
public class WorkflowExecuteRequest {

    /**
     * 用户输入/问题（工作流的主要输入）
     */
    private String question;

    /**
     * 会话标识（可选，用于关联上下文）
     */
    private String conversationCode;

    /**
     * 自定义初始状态（可选）
     *
     * 用于传递工作流所需的额外参数，例如：
     * - iteration_count: 迭代计数
     * - max_iterations: 最大迭代次数
     * - quality_threshold: 质量阈值
     * - 其他工作流特定的配置参数
     *
     * 注意：question 会自动添加到初始状态中，无需重复设置
     */
    private Map<String, Object> customState;
}
