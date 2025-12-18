package com.llmmanager.agent.reactagent.configurable.pattern;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行结果
 * 
 * 包含工作流执行的完整信息，包括：
 * - 最终结果
 * - 各个 Agent 的执行结果
 * - 执行时间
 * - 执行状态
 * 
 * @author LLM Manager
 */
@Data
@Builder
public class WorkflowResult {

    /**
     * 最终结果
     */
    private String finalResult;

    /**
     * 是否成功
     */
    @Builder.Default
    private boolean success = true;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 各个 Agent 的执行结果
     * Key: Agent 名称, Value: 执行结果
     */
    @Builder.Default
    private Map<String, AgentStepResult> agentResults = new HashMap<>();

    /**
     * 执行步骤记录
     */
    @Builder.Default
    private List<ExecutionStep> executionSteps = new ArrayList<>();

    /**
     * 总执行时间（毫秒）
     */
    private long totalExecutionTimeMs;

    /**
     * 使用的模式
     */
    private String pattern;

    /**
     * 创建成功结果
     */
    public static WorkflowResult success(String finalResult) {
        return WorkflowResult.builder()
                .success(true)
                .finalResult(finalResult)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static WorkflowResult failure(String errorMessage) {
        return WorkflowResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 添加 Agent 执行结果
     */
    public void addAgentResult(String agentName, AgentStepResult result) {
        agentResults.put(agentName, result);
    }

    /**
     * 添加执行步骤
     */
    public void addExecutionStep(ExecutionStep step) {
        executionSteps.add(step);
    }

    /**
     * 单个 Agent 的执行结果
     */
    @Data
    @Builder
    public static class AgentStepResult {
        private String agentName;
        private String input;
        private String output;
        private long executionTimeMs;
        private boolean success;
        private String errorMessage;
    }

    /**
     * 执行步骤记录
     */
    @Data
    @Builder
    public static class ExecutionStep {
        private int stepNumber;
        private String agentName;
        private String action;
        private long timestamp;
        private String details;
    }
}

